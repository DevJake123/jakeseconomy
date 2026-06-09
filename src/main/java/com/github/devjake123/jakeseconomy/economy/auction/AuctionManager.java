package com.github.devjake123.jakeseconomy.economy.auction;

import com.github.devjake123.jakeseconomy.JakesEconomy;
import com.github.devjake123.jakeseconomy.api.EconomyApiEvents;
import com.github.devjake123.jakeseconomy.api.event.AuctionBidPlacedEvent;
import com.github.devjake123.jakeseconomy.api.event.AuctionCancelledEvent;
import com.github.devjake123.jakeseconomy.api.event.AuctionFinalizedEvent;
import com.github.devjake123.jakeseconomy.config.JakesEconomyConfigManager;
import com.github.devjake123.jakeseconomy.config.JakesEconomyServerConfig;
import com.github.devjake123.jakeseconomy.economy.AuctionEntry;
import com.github.devjake123.jakeseconomy.economy.CurrencyFormatter;
import com.github.devjake123.jakeseconomy.economy.EconomyState;
import com.github.devjake123.jakeseconomy.economy.MarketManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Server-side singleton that handles all auction business logic:
 *   - Creating auctions (removes item from inventory, persists to AuctionState)
 *   - Placing bids (configurable % minimum increment, anti-snipe extension, instant previous-bidder refund)
 *   - BIN (instant buy)
 *   - Cancelling (seller only, refunds current top bidder, returns item to seller escrow)
 *   - Finalizing (on expiry — item to winner escrow, proceeds to seller escrow)
 *   - Claiming (give escrow items/currency to player, respects inventory space)
 *
 * All operations that move money or items go through AuctionState escrow to prevent loss of items.
 */
public class AuctionManager {

    private static AuctionManager instance;

    /** Allowed listing durations in milliseconds — must match the client's DURATION_MS array. */
    private static final long[] ALLOWED_DURATIONS =
            { 3_600_000L, 21_600_000L, 43_200_000L, 86_400_000L, 172_800_000L };

    /** How long to keep finalized auction records before pruning (30 minutes). */
    private static final long PRUNE_KEEP_MS = 30 * 60 * 1_000L;
    /** Prune runs once every 5 minutes of server uptime (300 tick() calls = 300 seconds). */
    private int pruneCountdown = 300;

    private AuctionManager() {}

    public static AuctionManager get() {
        if (instance == null) throw new IllegalStateException("AuctionManager not initialized");
        return instance;
    }

    /**
     * Called on SERVER_STARTED. Creates the singleton and finalizes any auctions
     * that expired while the server was offline.
     */
    public static void initialize(MinecraftServer server) {
        instance = new AuctionManager();
        AuctionState state = AuctionState.get(server);
        long now = System.currentTimeMillis();
        int expired = 0;
        for (AuctionEntry entry : new ArrayList<>(state.auctions.values())) {
            if (entry.active && entry.endTimeEpochMs <= now) {
                finalizeInternal(entry, state, server);
                expired++;
            }
        }
        if (expired > 0) {
            state.setDirty();
            JakesEconomy.LOGGER.info("[Auction] Finalized {} auction(s) that expired during downtime.", expired);
        }
        long activeCount = state.auctions.values().stream().filter(a -> a.active).count();
        JakesEconomy.LOGGER.info("[Auction] AuctionManager initialized — {} active auction(s).", activeCount);
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * Posts a new auction or BIN listing. Removes the item from the player's inventory
     * immediately so it cannot be used or traded while listed.
     *
     * @param inventorySlot index into player's full inventory (0-35 main, 36-39 armour, 40 offhand)
     * @param price         starting/minimum bid (auction) or instant buy price (BIN)
     * @param durationMs    how long the listing is live (milliseconds)
     * @param isBin         true = Buy It Now, false = open auction
     */
    public void createAuction(ServerPlayer player, int inventorySlot, long price,
                              long durationMs, boolean isBin, MinecraftServer server) {
        Inventory inv = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= inv.getContainerSize()) {
            player.sendSystemMessage(Component.literal("Invalid inventory slot."));
            return;
        }
        ItemStack stack = inv.getItem(inventorySlot);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("No item in that slot."));
            return;
        }
        if (price <= 0) {
            player.sendSystemMessage(Component.literal("Price must be greater than 0."));
            return;
        }
        if (durationMs <= 0) {
            player.sendSystemMessage(Component.literal("Invalid auction duration."));
            return;
        }

        // Validate duration is one of the 5 allowed values (prevents tampered packets)
        boolean validDuration = false;
        for (long allowed : ALLOWED_DURATIONS) if (durationMs == allowed) { validDuration = true; break; }
        if (!validDuration) {
            player.sendSystemMessage(Component.literal("Invalid auction duration."));
            JakesEconomy.LOGGER.warn("[Auction] {} sent invalid duration {}ms — rejected.", player.getName().getString(), durationMs);
            return;
        }

        JakesEconomyServerConfig config = JakesEconomyConfigManager.getServer();

        // Max price guard — treat 0 as "unset/legacy config" and fall back to the hardcoded default
        long effectiveMaxPrice = config.maxListingPrice > 0 ? config.maxListingPrice : 1_000_000_000_000L;
        if (price > effectiveMaxPrice) {
            player.sendSystemMessage(Component.literal(
                    "Listing price exceeds the maximum of " + CurrencyFormatter.format(effectiveMaxPrice, true) + "."));
            return;
        }

        HolderLookup.Provider reg = server.registryAccess();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AuctionState state = AuctionState.get(server);

        // Block items that are already in the market from being listed
        if (MarketManager.get().isMarketItem(itemId)) {
            player.sendSystemMessage(Component.literal(
                    "Items available in the market cannot be listed in the Auction House."));
            return;
        }

        // Hard cap: 20 active listings per player
        long activeByPlayer = state.auctions.values().stream()
                .filter(e -> e.active && e.sellerId.equals(player.getUUID()))
                .count();
        if (activeByPlayer >= 20) {
            player.sendSystemMessage(Component.literal(
                    "You have reached the maximum of 20 active auction listings."));
            return;
        }


        CompoundTag itemTag = (CompoundTag) stack.save(reg);
        UUID auctionId = UUID.randomUUID();
        long endTime   = System.currentTimeMillis() + durationMs;

        // Listing fee — deducted at creation time (non-refundable, even on cancel)
        long listingFee = 0;
        if (config.listingFeePercent > 0) {
            listingFee = Math.max(1L, Math.round(price * config.listingFeePercent / 100.0));
            EconomyState economy = EconomyState.get(server);
            if (economy.getBalance(player.getUUID()) < listingFee) {
                player.sendSystemMessage(Component.literal(
                        "You cannot afford the listing fee of " + CurrencyFormatter.format(listingFee, true) +
                        " (" + config.listingFeePercent + "% of listing price)."));
                return;
            }
            economy.withdraw(player.getUUID(), listingFee);
            EconomyState.syncBalance(player, server);
        }

        AuctionEntry entry = new AuctionEntry(auctionId, player.getUUID(),
                itemTag, itemId, price, isBin, endTime);
        state.auctions.put(auctionId, entry);
        state.setDirty();

        // Remove item from inventory
        inv.setItem(inventorySlot, ItemStack.EMPTY);

        String feeNote = listingFee > 0 ? " (fee: " + CurrencyFormatter.format(listingFee, true) + ")" : "";
        JakesEconomy.LOGGER.info("[Auction] {} listed {}x {} for {} ({}) — expires at {}.{}",
                player.getName().getString(), stack.getCount(),
                MarketManager.friendlyName(itemId), price,
                isBin ? "BIN" : "Auction", endTime, feeNote);
        if (listingFee > 0) {
            player.sendSystemMessage(Component.literal(
                    "Listing created! Listing fee of " + CurrencyFormatter.format(listingFee, true) + " deducted."));
        } else {
            player.sendSystemMessage(Component.literal("Listing created!"));
        }
    }

    // ─── Bid ──────────────────────────────────────────────────────────────────

    /**
     * Places a bid on an open auction.
     * Rules:
     *   - Bid must be >= topBid + max(1, ceil(topBid * minBidIncrementPercent/100))
     *   - Player must have sufficient balance
     *   - Previous top bidder is immediately refunded to their escrow
     *   - If bid arrives within the anti-snipe window, the expiry is extended
     */
    public void placeBid(ServerPlayer player, UUID auctionId, long bidAmount, MinecraftServer server) {
        AuctionState state = AuctionState.get(server);
        AuctionEntry auction = state.auctions.get(auctionId);

        if (auction == null || !auction.active) {
            player.sendSystemMessage(Component.literal("Auction not found or has already ended."));
            return;
        }
        if (auction.isBin) {
            player.sendSystemMessage(Component.literal("This is a BIN listing — use Buy Now to purchase."));
            return;
        }
        if (auction.sellerId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You cannot bid on your own auction."));
            return;
        }

        long now = System.currentTimeMillis();
        if (auction.endTimeEpochMs <= now) {
            player.sendSystemMessage(Component.literal("This auction has already ended."));
            finalizeInternal(auction, state, server);
            state.setDirty();
            return;
        }

        // Minimum bid increment — reads from server config so admins can tune it
        JakesEconomyServerConfig config = JakesEconomyConfigManager.getServer();
        double incrementPct = Math.max(0.001, config.minBidIncrementPercent / 100.0);
        long currentTop   = auction.getTopBid();
        long minIncrement = Math.max(1L, (long) Math.ceil(currentTop * incrementPct));
        long minBid       = currentTop + minIncrement;

        if (bidAmount < minBid) {
            player.sendSystemMessage(Component.literal(
                    "Minimum bid is " + CurrencyFormatter.format(minBid, true) +
                    " — increment: " + CurrencyFormatter.format(minIncrement, true) +
                    " (" + config.minBidIncrementPercent + "% of current bid)."));
            return;
        }

        EconomyState economy = EconomyState.get(server);
        long balance = economy.getBalance(player.getUUID());
        if (balance < bidAmount) {
            player.sendSystemMessage(Component.literal(
                    "Insufficient funds. Balance: " + CurrencyFormatter.format(balance, true) +
                    ", need: " + CurrencyFormatter.format(bidAmount, true) + "."));
            return;
        }

        // Refund the previous top bidder immediately into their escrow
        UUID prevTopBidder = auction.getTopBidder();
        long prevTopAmount = auction.getTopBid();
        if (prevTopBidder != null) {
            state.addPendingPayout(prevTopBidder, prevTopAmount);
            // Notify previous leader if online; queue if offline
            double nextIncrementPct = Math.max(0.001, config.minBidIncrementPercent / 100.0);
            long nextMin = bidAmount + Math.max(1L, (long) Math.ceil(bidAmount * nextIncrementPct));
            String outbidMsg = "Your bid on " + MarketManager.friendlyName(auction.itemId)
                    + " was outbid! Your " + CurrencyFormatter.format(prevTopAmount, true)
                    + " has been refunded. Minimum to retake the lead: "
                    + CurrencyFormatter.format(nextMin, true) + ".";
            ServerPlayer prevPlayer = server.getPlayerList().getPlayer(prevTopBidder);
            if (prevPlayer != null) {
                prevPlayer.sendSystemMessage(Component.literal(outbidMsg));
            } else {
                state.addPendingNotification(prevTopBidder, outbidMsg);
            }
        }

        // Deduct the new bid from new bidder's wallet and sync their HUD immediately
        economy.withdraw(player.getUUID(), bidAmount);
        EconomyState.syncBalance(player, server);
        auction.bids.add(new BidEntry(player.getUUID(), bidAmount, now));

        // Anti-snipe: extend if bid arrives within the configured window
        long timeLeft = auction.endTimeEpochMs - now;
        boolean extended = false;
        if (timeLeft < config.antiSnipeExtensionMs) {
            auction.endTimeEpochMs = now + config.antiSnipeExtensionMs;
            extended = true;
        }

        state.setDirty();

        player.sendSystemMessage(Component.literal(
                "Bid of " + CurrencyFormatter.format(bidAmount, true) +
                " placed on " + MarketManager.friendlyName(auction.itemId) + "." +
                (extended ? " (Auction extended — anti-snipe triggered.)" : "")));

        // Fire API event for downstream mods
        EconomyApiEvents.AUCTION_BID_PLACED.invoker().onAuctionBidPlaced(
                new AuctionBidPlacedEvent(auctionId, player.getUUID(), bidAmount, auction.itemId, extended));

        JakesEconomy.LOGGER.info("[Auction] {} bid {} on auction {} ({}){}.",
                player.getName().getString(), bidAmount, auctionId,
                MarketManager.friendlyName(auction.itemId),
                extended ? " [anti-snipe extended]" : "");
    }

    // ─── BIN ─────────────────────────────────────────────────────────────────

    /**
     * Instant buy — only valid on BIN listings.
     * Deducts the BIN price, finalizes the auction immediately.
     */
    public void instantBuy(ServerPlayer player, UUID auctionId, MinecraftServer server) {
        AuctionState state  = AuctionState.get(server);
        AuctionEntry auction = state.auctions.get(auctionId);

        if (auction == null || !auction.active) {
            player.sendSystemMessage(Component.literal("Listing not found or has already ended."));
            return;
        }
        if (!auction.isBin) {
            player.sendSystemMessage(Component.literal("This is an auction listing — use bidding to participate."));
            return;
        }
        if (auction.sellerId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You cannot buy your own listing."));
            return;
        }

        // Expiry guard — a BIN listing may have passed its end time between
        // the tick loop's last scan and the moment the packet arrived.
        long now = System.currentTimeMillis();
        if (auction.endTimeEpochMs <= now) {
            player.sendSystemMessage(Component.literal("This listing has already expired."));
            finalizeInternal(auction, state, server);
            state.setDirty();
            return;
        }

        EconomyState economy = EconomyState.get(server);
        if (economy.getBalance(player.getUUID()) < auction.startingPrice) {
            player.sendSystemMessage(Component.literal(
                    "Insufficient funds. Need: " + CurrencyFormatter.format(auction.startingPrice, true) + "."));
            return;
        }

        economy.withdraw(player.getUUID(), auction.startingPrice);
        EconomyState.syncBalance(player, server);  // sync HUD — balance dropped immediately
        // Record as a bid so finalizeInternal sees a winner
        auction.bids.add(new BidEntry(player.getUUID(), auction.startingPrice, System.currentTimeMillis()));
        finalizeInternal(auction, state, server);
        state.setDirty();

        JakesEconomy.LOGGER.info("[Auction] {} bought {} via BIN for {}.",
                player.getName().getString(),
                MarketManager.friendlyName(auction.itemId), auction.startingPrice);
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────

    /**
     * Cancels an active auction (seller only).
     * Returns the item to seller's escrow.
     * Refunds the current top bidder (all previous bidders were already refunded when outbid).
     */
    public void cancelAuction(ServerPlayer player, UUID auctionId, MinecraftServer server) {
        AuctionState state   = AuctionState.get(server);
        AuctionEntry auction = state.auctions.get(auctionId);

        if (auction == null || !auction.active) {
            player.sendSystemMessage(Component.literal("Auction not found or already ended."));
            return;
        }
        if (!auction.sellerId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You can only cancel your own auctions."));
            return;
        }

        auction.active = false;
        // Return item to seller's escrow
        state.addPendingItem(auction.sellerId, auction.itemTag);
        // Refund the current top bidder (all others were already refunded when outbid)
        UUID topBidder = auction.getTopBidder();
        long topBid = 0L;
        if (topBidder != null) {
            topBid = auction.getTopBid();
            state.addPendingPayout(topBidder, topBid);
            String cancelRefundMsg = "The auction for " + MarketManager.friendlyName(auction.itemId)
                    + " was cancelled. Your bid has been refunded to your claims.";
            ServerPlayer topPlayer = server.getPlayerList().getPlayer(topBidder);
            if (topPlayer != null) {
                topPlayer.sendSystemMessage(Component.literal(cancelRefundMsg));
            } else {
                state.addPendingNotification(topBidder, cancelRefundMsg);
            }
        }
        state.setDirty();

        player.sendSystemMessage(Component.literal(
                "Auction cancelled. Your item is available to claim in the Auction House."));
        JakesEconomy.LOGGER.info("[Auction] {} cancelled auction {} ({}).",
                player.getName().getString(), auctionId,
                MarketManager.friendlyName(auction.itemId));

        // Fire API event for downstream mods
        EconomyApiEvents.AUCTION_CANCELLED.invoker().onAuctionCancelled(
                new AuctionCancelledEvent(auctionId, auction.sellerId, auction.itemId, topBidder, topBid));
    }

    // ─── Finalize (internal) ─────────────────────────────────────────────────

    /**
     * Closes an auction, distributes item to winner's escrow and proceeds to seller's escrow,
     * and sends chat notifications to relevant online players.
     * Safe to call multiple times — early-exits if already inactive.
     */
    static void finalizeInternal(AuctionEntry auction, AuctionState state, MinecraftServer server) {
        if (!auction.active) return;
        auction.active = false;

        UUID winner    = auction.getTopBidder();
        long winAmount = auction.getTopBid();

        if (winner == null) {
            // No bids — return item to seller
            state.addPendingItem(auction.sellerId, auction.itemTag);
            ServerPlayer seller = server.getPlayerList().getPlayer(auction.sellerId);
            String noBidMsg = "Your auction for " + MarketManager.friendlyName(auction.itemId)
                    + " ended with no bids. Your item is ready to claim.";
            if (seller != null) {
                seller.sendSystemMessage(Component.literal(noBidMsg));
            } else {
                state.addPendingNotification(auction.sellerId, noBidMsg);
            }
        } else {
            // Item → winner escrow, proceeds → seller escrow
            state.addPendingItem(winner, auction.itemTag);
            state.addPendingPayout(auction.sellerId, winAmount);

            ServerPlayer winnerPlayer = server.getPlayerList().getPlayer(winner);
            String wonMsg = "You won the auction for " + MarketManager.friendlyName(auction.itemId)
                    + " at " + CurrencyFormatter.format(winAmount, true)
                    + "! Claim your item in the Auction House.";
            if (winnerPlayer != null) {
                winnerPlayer.sendSystemMessage(Component.literal(wonMsg));
            } else {
                state.addPendingNotification(winner, wonMsg);
            }

            ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(auction.sellerId);
            String soldMsg = "Your " + MarketManager.friendlyName(auction.itemId)
                    + " sold for " + CurrencyFormatter.format(winAmount, true)
                    + "! Claim your earnings in the Auction House.";
            if (sellerPlayer != null) {
                sellerPlayer.sendSystemMessage(Component.literal(soldMsg));
            } else {
                state.addPendingNotification(auction.sellerId, soldMsg);
            }

            // Notify other bidders who lost (they were already refunded when outbid,
            // but they may not know the auction ended)
            Set<UUID> notified = new HashSet<>();
            notified.add(winner);
            notified.add(auction.sellerId);
            for (int i = auction.bids.size() - 2; i >= 0; i--) {
                UUID loser = auction.bids.get(i).bidderId();
                if (notified.add(loser)) {
                    String lostMsg = "The auction for " + MarketManager.friendlyName(auction.itemId)
                            + " has ended. You did not win.";
                    ServerPlayer loserPlayer = server.getPlayerList().getPlayer(loser);
                    if (loserPlayer != null) {
                        loserPlayer.sendSystemMessage(Component.literal(lostMsg));
                    } else {
                        state.addPendingNotification(loser, lostMsg);
                    }
                }
            }
        }

        // Fire API event for downstream mods
        EconomyApiEvents.AUCTION_FINALIZED.invoker().onAuctionFinalized(
                new AuctionFinalizedEvent(auction.auctionId, auction.sellerId, winner,
                        auction.itemId, winner != null ? winAmount : 0L));
    }

    // ─── Claim ───────────────────────────────────────────────────────────────

    /**
     * Delivers all pending escrow items that fit in the player's inventory,
     * and deposits all pending currency in full.
     * Items that don't fit stay in escrow until the player claims again.
     */
    public void claimAll(ServerPlayer player, MinecraftServer server) {
        AuctionState state  = AuctionState.get(server);
        EconomyState economy = EconomyState.get(server);
        UUID playerId = player.getUUID();
        HolderLookup.Provider reg = server.registryAccess();

        // Currency — always pay out in full (virtual, no slot limit)
        long payout = state.pendingPayouts.getOrDefault(playerId, 0L);
        if (payout > 0) {
            economy.deposit(playerId, payout);
            state.pendingPayouts.remove(playerId);
            EconomyState.syncBalance(player, server);
        }

        // Items — only give what fits; leave the rest in escrow
        List<CompoundTag> items = state.pendingItems.get(playerId);
        int itemsDelivered = 0;
        if (items != null && !items.isEmpty()) {
            List<CompoundTag> remaining = new ArrayList<>();
            for (CompoundTag itemTag : items) {
                ItemStack stack = ItemStack.parseOptional(reg, itemTag);
                if (stack.isEmpty()) {
                    // Corrupt tag — discard to prevent a permanent claim block
                    JakesEconomy.LOGGER.warn("[Auction] Discarding unparseable item tag for player {}.", playerId);
                    continue;
                }
                if (!player.getInventory().add(stack)) {
                    remaining.add(itemTag);
                } else {
                    itemsDelivered++;
                }
            }
            if (remaining.isEmpty()) {
                state.pendingItems.remove(playerId);
            } else {
                state.pendingItems.put(playerId, remaining);
                player.sendSystemMessage(Component.literal(
                        "Some items couldn't fit in your inventory. Free up space and claim again."));
            }
        }

        state.setDirty();

        if (payout > 0) {
            player.sendSystemMessage(Component.literal(
                    "Claimed " + CurrencyFormatter.format(payout, true) + " from the Auction House."));
        }
        if (itemsDelivered > 0) {
            player.sendSystemMessage(Component.literal("Items delivered to your inventory."));
        }

        JakesEconomy.LOGGER.info("[Auction] {} claimed auction rewards (currency: {}).",
                player.getName().getString(), payout);
    }

    // ─── Tick ────────────────────────────────────────────────────────────────

    /**
     * Called every second by AuctionExpireScheduler.
     * Scans all active auctions and finalizes any that have passed their end time.
     */
    public void tick(MinecraftServer server) {
        AuctionState state = AuctionState.get(server);
        long now = System.currentTimeMillis();
        boolean anyFinalized = false;
        for (AuctionEntry entry : new ArrayList<>(state.auctions.values())) {
            if (entry.active && entry.endTimeEpochMs <= now) {
                finalizeInternal(entry, state, server);
                anyFinalized = true;
            }
        }
        if (anyFinalized) state.setDirty();

        // Prune old finalized records every 5 minutes to keep the save file tidy
        if (--pruneCountdown <= 0) {
            pruneCountdown = 300;
            int pruned = state.pruneFinalized(PRUNE_KEEP_MS);
            if (pruned > 0) {
                JakesEconomy.LOGGER.info("[Auction] Pruned {} old finalized auction record(s).", pruned);
            }
        }
    }

    // ─── JSON builders for network ────────────────────────────────────────────

    /**
     * Builds a paginated JSON payload of active auctions for S2C transport.
     * Entries are sorted by end time (soonest first) so players see expiring auctions first.
     *
     * @param offset starting index (0-based)
     * @param count  max entries to include per chunk
     */
    public String buildListJson(MinecraftServer server, int offset, int count) {
        AuctionState state = AuctionState.get(server);
        List<AuctionEntry> active = state.auctions.values().stream()
                .filter(a -> a.active)
                .sorted(Comparator.comparingLong(a -> a.endTimeEpochMs))
                .toList();
        int total = active.size();
        int end   = Math.min(offset + count, total);
        StringBuilder sb = new StringBuilder("{\"total\":").append(total)
                .append(",\"offset\":").append(offset)
                .append(",\"entries\":[");
        for (int i = offset; i < end; i++) {
            if (i > offset) sb.append(',');
            appendEntryJson(sb, active.get(i), server);
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Builds a single-entry delta JSON for broadcast after a mutation. */
    public String buildDeltaJson(AuctionEntry entry, MinecraftServer server) {
        StringBuilder sb = new StringBuilder("{\"total\":1,\"offset\":0,\"entries\":[");
        appendEntryJson(sb, entry, server);
        sb.append("]}");
        return sb.toString();
    }

    private void appendEntryJson(StringBuilder sb, AuctionEntry a, MinecraftServer server) {
        // Try to resolve seller display name
        String sellerName = "Unknown";
        try {
            ServerPlayer p = server.getPlayerList().getPlayer(a.sellerId);
            if (p != null) {
                sellerName = p.getName().getString();
            } else if (server.getProfileCache() != null) {
                var profile = server.getProfileCache().get(a.sellerId).orElse(null);
                if (profile != null) sellerName = profile.getName();
            }
        } catch (Exception ignored) {}

        String safeId          = a.auctionId.toString();
        String safeSellerId    = a.sellerId.toString();
        // Use escJson for all user-visible strings — esc() only handles backslash and double-quote,
        // missing control chars (\n, \t, etc.) that can appear in profile names or modded item names.
        String safeSellerName  = escJson(sellerName);
        String safeItemId      = escJson(a.itemId);
        String safeDisplayName = escJson(MarketManager.friendlyName(a.itemId));
        long   topBid          = a.getTopBid();
        String topBidderId     = a.getTopBidder() != null ? a.getTopBidder().toString() : "";

        // Use escJson (full JSON escaping) for SNBT — esc() misses \n, \t, and other
        // control chars that can appear in CompoundTag SNBT and will break JSON parsing.
        // getAsString() is the proper SNBT method; toString() may return the Java default.
        String snbt = "";
        try { snbt = escJson(a.itemTag.getAsString()); } catch (Exception ignored) {}

        // We only send the top bid (topBid + topBidderId fields) — not the full historical bid
        // list. Historical bids are only needed to refund the previous leader, which the server
        // handles internally. Sending all bids would transmit unbounded history to every client.
        sb.append("{\"id\":\"").append(safeId).append("\"")
          .append(",\"seller\":\"").append(safeSellerId).append("\"")
          .append(",\"sellerName\":\"").append(safeSellerName).append("\"")
          .append(",\"itemId\":\"").append(safeItemId).append("\"")
          .append(",\"displayName\":\"").append(safeDisplayName).append("\"")
          .append(",\"snbt\":\"").append(snbt).append("\"")
          .append(",\"startingPrice\":").append(a.startingPrice)
          .append(",\"isBin\":").append(a.isBin)
          .append(",\"topBid\":").append(topBid)
          .append(",\"topBidderId\":\"").append(topBidderId).append("\"")
          .append(",\"endTime\":").append(a.endTimeEpochMs)
          .append(",\"active\":").append(a.active)
          .append(",\"bidCount\":").append(a.bids.size())
          .append(",\"bids\":[]}");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Full JSON string content escaping — handles ALL characters that are illegal
     * in a JSON string value: control chars, backslash, double-quote.
     * Use this for machine-generated strings like SNBT where esc() is not enough.
     */
    private static String escJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    // ─── Lookup helpers ───────────────────────────────────────────────────────

    public AuctionEntry getAuction(UUID auctionId, MinecraftServer server) {
        return AuctionState.get(server).auctions.get(auctionId);
    }
}


