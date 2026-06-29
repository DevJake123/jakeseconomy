package com.github.devjake123.jakeseconomy.client.screen;

import com.github.devjake123.jakeseconomy.client.ClientAuctionCache;
import com.github.devjake123.jakeseconomy.client.ClientAuctionConfigCache;
import com.github.devjake123.jakeseconomy.client.ClientBalanceCache;
import com.github.devjake123.jakeseconomy.client.ClientMarketListingCache;
import com.github.devjake123.jakeseconomy.client.network.MarketPacketSender;
import com.github.devjake123.jakeseconomy.economy.CurrencyFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auction House screen — standalone screen opened from the MarketScreen sidebar.
 *
 * Sub-views:
 *   BROWSE   — list of all active auctions (filterable to "Mine")
 *   DETAIL   — single auction: bid, BIN, cancel
 *   CREATE   — pick an item from inventory, set price/duration/type, confirm
 */
public class AuctionScreen extends Screen {

    // ─── Layout constants ────────────────────────────────────────────────────
    private static final int SIDEBAR_W   = 68;
    private static final int ROW_HEIGHT  = 20;
    private static final int CHUNK_SIZE  = 20; // auctions per page request

    // ─── View enum ───────────────────────────────────────────────────────────
    private enum View { BROWSE, DETAIL, CREATE }
    private View view = View.BROWSE;

    // ─── Panel geometry ──────────────────────────────────────────────────────
    private int guiLeft, guiTop, panelWidth, panelHeight;

    private final Screen parent;

    // ─── Browse state ────────────────────────────────────────────────────────
    private boolean showMineOnly = false;
    private int     scrollOffset = 0;
    private int     maxVisible   = 8;
    // Scrollbar drag
    private boolean scrollDragging  = false;
    private int     dragStartY, dragStartOffset, scrollTrackTop, scrollTrackH, scrollThumbH;

    // ─── Detail state ─────────────────────────────────────────────────────────
    private ClientAuctionCache.AuctionDto selectedAuction = null;
    private final StringBuilder bidBuffer = new StringBuilder();
    private boolean bidFieldFocused = false;
    private int bidFieldX1, bidFieldX2, bidFieldY1, bidFieldY2;
    /** BIN double-confirm: first click arms it, second click (within 3 s) executes. */
    private boolean binConfirmArmed = false;
    private long    binConfirmArmedAt = 0L;

    // ─── Browse search state ──────────────────────────────────────────────────
    private final StringBuilder searchBuffer = new StringBuilder();
    private boolean searchFocused = false;
    private int searchFieldX1, searchFieldX2, searchFieldY1, searchFieldY2;

    // ─── Create state ─────────────────────────────────────────────────────────
    private int  selectedInvSlot  = -1;
    private final StringBuilder priceBuffer = new StringBuilder();
    private boolean priceFieldFocused = false;
    private int     selectedDuration  = 2; // index into DURATION_OPTIONS
    private boolean createIsBin       = false;
    private int     invScrollOffset   = 0;

    private int priceFieldX1, priceFieldX2, priceFieldY1, priceFieldY2;
    /** Y positions of duration-picker and AUC/BIN toggle rows — set during drawCreate, read by click handler. */
    private int durBtnY = -1, togBtnY = -1;
    /** Tooltip stack to render at end of frame (set during draw methods). */
    private ItemStack pendingTooltipStack = ItemStack.EMPTY;
    private int pendingTooltipX, pendingTooltipY;

    private static final String[] DURATION_LABELS = { "1h", "6h", "12h", "24h", "48h" };
    private static final long[]   DURATION_MS     = {
            3_600_000L, 21_600_000L, 43_200_000L, 86_400_000L, 172_800_000L };

    // ─── Constructor ─────────────────────────────────────────────────────────

    public AuctionScreen(Screen parent) {
        super(Component.literal("Auction House"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth  = Math.min(420, width  - 20);
        panelHeight = Math.min(260, height - 40);
        guiLeft = (width  - panelWidth)  / 2;
        guiTop  = (height - panelHeight) / 2;

        // Request auction list on open
        ClientAuctionCache.setLoading(true);
        MarketPacketSender.requestAuctionList(0);
    }

    // ─── Render ───────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // Panel background + border
        g.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + panelHeight, 0xF0101010);
        drawBorder(g, guiLeft, guiTop, panelWidth, panelHeight, 0xFFAAAAAA);

        // Left sidebar
        int sx = guiLeft + 1;
        int sBottom = guiTop + panelHeight - 1;
        g.fill(sx, guiTop + 1, sx + SIDEBAR_W, sBottom, 0xF0181818);
        g.fill(sx + SIDEBAR_W, guiTop + 1, sx + SIDEBAR_W + 1, sBottom, 0xFF444444);

        drawSidebar(g, mx, my, sx);

        // Content
        int cx = guiLeft + SIDEBAR_W + 2;
        int cw = panelWidth - SIDEBAR_W - 2;
        pendingTooltipStack = ItemStack.EMPTY; // reset each frame
        switch (view) {
            case BROWSE -> drawBrowse(g, mx, my, cx, cw);
            case DETAIL -> drawDetail(g, mx, my, cx, cw);
            case CREATE -> drawCreate(g, mx, my, cx, cw);
        }

        // Close button
        boolean closeHov = mx >= guiLeft + panelWidth - 16 && mx <= guiLeft + panelWidth - 2
                && my >= guiTop + 2 && my <= guiTop + 14;
        g.fill(guiLeft + panelWidth - 16, guiTop + 2, guiLeft + panelWidth - 2, guiTop + 14,
                closeHov ? 0xFF8B0000 : 0xFF550000);
        g.drawString(font, "X", guiLeft + panelWidth - 12, guiTop + 4, 0xFFFFFFFF);

        // Render any queued item tooltip on top of everything
        if (!pendingTooltipStack.isEmpty()) {
            g.renderTooltip(font, pendingTooltipStack, pendingTooltipX, pendingTooltipY);
        }

        super.render(g, mx, my, delta);
    }

    // ─── Sidebar ──────────────────────────────────────────────────────────────

    private void drawSidebar(GuiGraphics g, int mx, int my, int sx) {
        g.drawString(font, "Auctions", sx + 4, guiTop + 4, 0xFFFFAA00);

        // Nav buttons
        String[]  labels = { "Browse", "My Listings" };
        boolean[] active = { view != View.CREATE && !showMineOnly || view == View.DETAIL,
                             view != View.CREATE && showMineOnly };
        // Simplification: "Browse" = all/detail views, "My Listings" = showMineOnly
        active[0] = (view == View.BROWSE && !showMineOnly) || view == View.DETAIL;
        active[1] = (view == View.BROWSE && showMineOnly);

        int btnY = guiTop + 18;
        for (int i = 0; i < labels.length; i++) {
            boolean hov = mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3
                    && my >= btnY && my < btnY + 20;
            g.fill(sx + 3, btnY, sx + SIDEBAR_W - 3, btnY + 20,
                    active[i] ? 0xFF333333 : (hov ? 0xFF252525 : 0xFF1A1A1A));
            if (active[i]) g.fill(sx + 3, btnY, sx + 5, btnY + 20, 0xFFFFAA00);
            int lx = sx + 3 + (SIDEBAR_W - 6 - font.width(labels[i])) / 2;
            g.drawString(font, labels[i], lx, btnY + 6, active[i] ? 0xFFFFFFFF : 0xFF999999);
            btnY += 23;
        }

        // + New Listing button
        int newBtnY = btnY + 4;
        boolean newHov = mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3
                && my >= newBtnY && my < newBtnY + 20;
        g.fill(sx + 3, newBtnY, sx + SIDEBAR_W - 3, newBtnY + 20,
                newHov ? 0xFF1A3A1A : 0xFF0F220F);
        g.fill(sx + 3, newBtnY, sx + SIDEBAR_W - 3, newBtnY + 1, 0xFF446644);
        g.fill(sx + 3, newBtnY + 19, sx + SIDEBAR_W - 3, newBtnY + 20, 0xFF446644);
        String newLabel = "+ List Item";
        g.drawString(font, newLabel, sx + 3 + (SIDEBAR_W - 6 - font.width(newLabel)) / 2, newBtnY + 6, 0xFF88FF88);

        // Claim button — amber glow if claims available
        int claimBtnY = guiTop + panelHeight - 28;
        boolean claimHov = mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3
                && my >= claimBtnY && my < claimBtnY + 20;
        boolean hasClaims = ClientAuctionCache.hasClaims();
        int claimBg = hasClaims ? (claimHov ? 0xFF554400 : 0xFF332200)
                                : (claimHov ? 0xFF252525 : 0xFF1A1A1A);
        g.fill(sx + 3, claimBtnY, sx + SIDEBAR_W - 3, claimBtnY + 20, claimBg);
        if (hasClaims) g.fill(sx + 3, claimBtnY, sx + SIDEBAR_W - 3, claimBtnY + 1, 0xFFFFAA00);
        String claimLabel = "Claim";
        int claimColor = hasClaims ? 0xFFFFDD55 : 0xFF666666;
        g.drawString(font, claimLabel, sx + 3 + (SIDEBAR_W - 6 - font.width(claimLabel)) / 2, claimBtnY + 6, claimColor);

        // ← Back button
        int backY = claimBtnY - 25;
        boolean backHov = mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3
                && my >= backY && my < backY + 18;
        g.fill(sx + 3, backY, sx + SIDEBAR_W - 3, backY + 18, backHov ? 0xFF252525 : 0xFF1A1A1A);
        String backLabel = "← Back";
        g.drawString(font, backLabel, sx + 3 + (SIDEBAR_W - 6 - font.width(backLabel)) / 2, backY + 5, 0xFF888888);
    }

    // ─── Browse view ──────────────────────────────────────────────────────────

    private void drawBrowse(GuiGraphics g, int mx, int my, int cx, int cw) {
        // Header
        String title = showMineOnly ? "My Listings" : "Auction House";
        g.drawString(font, title, cx + 4, guiTop + 6, 0xFFFFAA00);
        String balStr = CurrencyFormatter.format(ClientBalanceCache.get(), true);
        g.drawString(font, balStr, cx + cw - font.width(balStr) - 20, guiTop + 6, 0xFF88FF88);

        // Search field
        int sfW = Math.min(120, cw / 3);
        int sfX = cx + (cw - sfW) / 2;
        int sfY = guiTop + 2;
        searchFieldX1 = sfX; searchFieldX2 = sfX + sfW;
        searchFieldY1 = sfY; searchFieldY2 = sfY + 12;
        g.fill(sfX, sfY, sfX + sfW, sfY + 12, searchFocused ? 0xFF1A1A3A : 0xFF111111);
        g.fill(sfX, sfY, sfX + sfW, sfY + 1, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(sfX, sfY + 11, sfX + sfW, sfY + 12, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(sfX, sfY, sfX + 1, sfY + 12, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(sfX + sfW - 1, sfY, sfX + sfW, sfY + 12, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        String searchShow = searchBuffer.isEmpty()
                ? "Search..."
                : searchBuffer.toString();
        if (searchFocused)
            searchShow = searchBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        String searchRendered = searchShow;
        int searchUsable = sfW - 6;
        while (font.width(searchRendered) > searchUsable && searchRendered.length() > 1)
            searchRendered = searchRendered.substring(1);
        g.drawString(font, searchRendered, sfX + 3, sfY + 2, searchBuffer.isEmpty() ? 0xFF555555 : 0xFFFFFFFF);

        if (ClientAuctionCache.isLoading()) {
            g.drawString(font, "Loading...", cx + (cw - font.width("Loading...")) / 2, guiTop + 80, 0xFF666666);
            return;
        }

        List<ClientAuctionCache.AuctionDto> all  = ClientAuctionCache.get();
        List<ClientAuctionCache.AuctionDto> display = new ArrayList<>();
        String myIdStr = minecraft != null && minecraft.player != null
                ? minecraft.player.getUUID().toString() : "";
        String searchLower = searchBuffer.toString().toLowerCase();

        for (ClientAuctionCache.AuctionDto dto : all) {
            if (!dto.active) continue;
            if (showMineOnly && !dto.sellerId.equals(myIdStr)) continue;
            if (!searchLower.isEmpty() && !dto.displayName.toLowerCase().contains(searchLower)) continue;
            display.add(dto);
        }

        // Column headers
        int listTop = guiTop + 20;
        g.drawString(font, "Item",   cx + 22,         listTop, 0xFFCCCCCC);
        g.drawString(font, "Type",   cx + cw - 128,   listTop, 0xFFCCCCCC);
        g.drawString(font, "Price",  cx + cw - 95,    listTop, 0xFFCCCCCC);
        g.drawString(font, "Ends",   cx + cw - 40,    listTop, 0xFFCCCCCC);
        g.fill(cx + 2, listTop + 10, cx + cw - 8, listTop + 11, 0xFF444444);

        int rowsTop = guiTop + 34;
        int rowsBottom = guiTop + panelHeight - 4;
        maxVisible = Math.max(1, (rowsBottom - rowsTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, display.size() - maxVisible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Scrollbar geometry
        int scrollBarX = cx + cw - 6;
        scrollTrackTop = rowsTop;
        scrollTrackH   = maxVisible * ROW_HEIGHT;
        scrollThumbH   = display.size() > maxVisible
                ? Math.max(10, scrollTrackH * maxVisible / display.size()) : 0;

        long now = System.currentTimeMillis();
        int rowY = rowsTop;
        int end  = Math.min(scrollOffset + maxVisible, display.size());
        for (int i = scrollOffset; i < end; i++) {
            if (rowY + ROW_HEIGHT > rowsBottom) break;
            ClientAuctionCache.AuctionDto dto = display.get(i);
            boolean hov = mx >= cx + 2 && mx <= cx + cw - 10
                    && my >= rowY && my < rowY + ROW_HEIGHT;
            if (hov) g.fill(cx + 2, rowY, cx + cw - 10, rowY + ROW_HEIGHT, 0x33FFFFFF);

            // Item icon
            renderIcon(g, dto.itemId, cx + 3, rowY + 2, 0.75f);

            // Tooltip on row hover — use the full item NBT so enchants etc. are visible
            if (hov) {
                ItemStack ts = buildTooltipStack(dto.itemSnbt);
                if (!ts.isEmpty()) { pendingTooltipStack = ts; pendingTooltipX = mx; pendingTooltipY = my; }
            }

            // Item name — ellipsed at 30 chars, then further pixel-clamped
            String name = ellipsis(dto.displayName, 30);
            int maxNameW = cw - 130;
            while (font.width(name) > maxNameW && name.length() > 1) name = name.substring(0, name.length() - 1);
            g.drawString(font, name, cx + 22, rowY + 6, 0xFFFFFFFF);

            // BIN / AUC badge
            String typeLabel = dto.isBin ? "BIN" : "AUC";
            int typeColor    = dto.isBin ? 0xFF55AAFF : 0xFFFFAA55;
            g.drawString(font, typeLabel, cx + cw - 128, rowY + 6, typeColor);

            // Price
            String priceStr = CurrencyFormatter.format(dto.topBid, true);
            g.drawString(font, priceStr, cx + cw - 95, rowY + 6, 0xFFFFDD55);

            // Time remaining
            long msLeft = dto.endTimeMs - now;
            String timeStr = formatTimeLeft(msLeft);
            int timeColor = msLeft < 300_000 ? 0xFFFF4444 : 0xFF888888; // red if <5 min
            g.drawString(font, timeStr, cx + cw - 40, rowY + 6, timeColor);

            rowY += ROW_HEIGHT;
        }

        if (display.isEmpty()) {
            String msg = showMineOnly ? "You have no active listings." : "No auctions listed.";
            g.drawString(font, msg, cx + (cw - font.width(msg)) / 2, guiTop + 80, 0xFF666666);
        }

        // Scrollbar
        if (display.size() > maxVisible) {
            int thumbY = scrollTrackTop + scrollOffset * (scrollTrackH - scrollThumbH) / Math.max(1, maxScroll);
            thumbY = Math.min(thumbY, scrollTrackTop + scrollTrackH - scrollThumbH);
            g.fill(scrollBarX, scrollTrackTop, scrollBarX + 4, scrollTrackTop + scrollTrackH, 0x33FFFFFF);
            g.fill(scrollBarX, thumbY,         scrollBarX + 4, thumbY + scrollThumbH,         0xFFAAAAAA);
        }
    }

    // ─── Detail view ──────────────────────────────────────────────────────────

    private void drawDetail(GuiGraphics g, int mx, int my, int cx, int cw) {
        if (selectedAuction == null) { view = View.BROWSE; return; }
        ClientAuctionCache.AuctionDto a = selectedAuction;
        // Re-fetch from cache in case it was updated by a delta sync
        for (ClientAuctionCache.AuctionDto d : ClientAuctionCache.get()) {
            if (d.id.equals(a.id)) { selectedAuction = d; a = d; break; }
        }

        // If the top bid rose above what's in our bid buffer, auto-update the buffer
        if (!bidFieldFocused && !a.isBin) {
            long minNext = a.topBid + Math.max(1L, (long) Math.ceil(a.topBid * 0.01));
            long bufVal  = parseLong(bidBuffer.toString());
            if (bufVal < minNext) {
                bidBuffer.setLength(0);
                bidBuffer.append(minNext);
            }
        }

        // Expire BIN confirm arm after 3 seconds
        if (binConfirmArmed && System.currentTimeMillis() - binConfirmArmedAt > 3_000L) {
            binConfirmArmed = false;
        }

        String myIdStr = minecraft != null && minecraft.player != null
                ? minecraft.player.getUUID().toString() : "";
        boolean isMySelling = a.sellerId.equals(myIdStr);
        long    myBid       = a.getMyBid(minecraft != null && minecraft.player != null
                ? minecraft.player.getUUID() : UUID.randomUUID());
        long    topBid      = a.topBid;
        long    minIncrement= Math.max(1L, (long) Math.ceil(topBid * 0.01));
        long    minNextBid  = topBid + minIncrement;

        // ← Back
        drawFlatButton(g, cx + 2, guiTop + 4, 52, 13, "← Back", mx, my, 0xFF222222, 0xFF333333);

        // Item icon (large, 2×)
        int iconX = cx + cw / 2 - 16;
        int iconY = guiTop + 22;
        renderIcon(g, a.itemId, iconX, iconY, 2.0f);

        // Tooltip on icon hover
        boolean iconHov = mx >= iconX && mx <= iconX + 32 && my >= iconY && my <= iconY + 32;
        if (iconHov) {
            ItemStack ts = buildTooltipStack(a.itemSnbt);
            if (!ts.isEmpty()) { pendingTooltipStack = ts; pendingTooltipX = mx; pendingTooltipY = my; }
        }

        int textCX = cx + cw / 2;

        // Item name
        String name = a.displayName;
        g.drawString(font, name, textCX - font.width(name) / 2, iconY + 36, 0xFFFFFFFF);

        // Seller
        String seller = "Seller: " + a.sellerName;
        g.drawString(font, seller, textCX - font.width(seller) / 2, iconY + 48, 0xFF888888);

        // BIN label or bid info
        int infoY = iconY + 62;
        if (a.isBin) {
            String binLabel = "Buy It Now: " + CurrencyFormatter.format(a.startingPrice, true);
            g.drawString(font, binLabel, textCX - font.width(binLabel) / 2, infoY, 0xFF55AAFF);
            infoY += 14; // push "Ends:" below "Buy It Now"
        } else {
            String topBidLabel = "Current bid: " + CurrencyFormatter.format(topBid, true) +
                    " (" + a.bidCount + (a.bidCount == 1 ? " bid)" : " bids)");
            g.drawString(font, topBidLabel, textCX - font.width(topBidLabel) / 2, infoY, 0xFFFFDD55);
            infoY += 12;

            if (myBid > 0) {
                boolean amLeading = a.topBidderId.equals(myIdStr);
                String myBidStr = amLeading
                        ? "Your bid: " + CurrencyFormatter.format(myBid, true) + " ✓ (Leading)"
                        : "Your bid: " + CurrencyFormatter.format(myBid, true) +
                          " — need " + CurrencyFormatter.format(minNextBid, true) + " to retake lead";
                int myBidColor = amLeading ? 0xFF44FF88 : 0xFFFF8844;
                g.drawString(font, myBidStr, textCX - font.width(myBidStr) / 2, infoY, myBidColor);
                infoY += 12;
            }

            String minLabel = "Min next bid: " + CurrencyFormatter.format(minNextBid, true) +
                    " (+" + CurrencyFormatter.format(minIncrement, true) + " / 1%)";
            g.drawString(font, minLabel, textCX - font.width(minLabel) / 2, infoY, 0xFF666666);
            infoY += 12;
        }

        // Time remaining
        long msLeft = a.endTimeMs - System.currentTimeMillis();
        String timeStr = "Ends: " + formatTimeLeft(msLeft);
        int timeColor = msLeft < 300_000 ? 0xFFFF4444 : 0xFF888888;
        g.drawString(font, timeStr, textCX - font.width(timeStr) / 2, infoY + 4, timeColor);

        // Buttons row near bottom
        int btnY = guiTop + panelHeight - 36;
        if (a.isBin && !isMySelling) {
            int binBtnW = 90;
            int binBtnX = textCX - binBtnW / 2;
            if (binConfirmArmed) {
                // Show "Confirm?" in red — second click executes
                drawFlatButton(g, binBtnX, btnY, binBtnW, 20, "Confirm?", mx, my, 0xFF660000, 0xFF990000);
            } else {
                drawFlatButton(g, binBtnX, btnY, binBtnW, 20, "Buy Now", mx, my, 0xFF003399, 0xFF0044CC);
            }
        } else if (!a.isBin && !isMySelling) {
            // Bid input field
            int fieldW = 80; int fieldH = 14;
            int fieldX = textCX - fieldW - 4;
            bidFieldX1 = fieldX; bidFieldX2 = fieldX + fieldW;
            bidFieldY1 = btnY + 3; bidFieldY2 = btnY + 3 + fieldH;
            g.fill(fieldX, btnY + 3, fieldX + fieldW, btnY + 3 + fieldH,
                    bidFieldFocused ? 0xFF1A1A3A : 0xFF111111);
            g.fill(fieldX, btnY + 3, fieldX + fieldW, btnY + 4,
                    bidFieldFocused ? 0xFFFFAA00 : 0xFF444444);
            g.fill(fieldX, btnY + 2 + fieldH, fieldX + fieldW, btnY + 3 + fieldH,
                    bidFieldFocused ? 0xFFFFAA00 : 0xFF444444);
            g.fill(fieldX, btnY + 3, fieldX + 1, btnY + 3 + fieldH,
                    bidFieldFocused ? 0xFFFFAA00 : 0xFF444444);
            g.fill(fieldX + fieldW - 1, btnY + 3, fieldX + fieldW, btnY + 3 + fieldH,
                    bidFieldFocused ? 0xFFFFAA00 : 0xFF444444);
            String bidShow = bidBuffer.toString().isEmpty() ? String.valueOf(minNextBid) : bidBuffer.toString();
            if (bidFieldFocused) bidShow = bidBuffer.toString() + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
            // Clip from the left so the cursor/end of number is always visible
            String bidRendered = bidShow;
            int bidUsableW = fieldW - 6;
            while (font.width(bidRendered) > bidUsableW && bidRendered.length() > 1)
                bidRendered = bidRendered.substring(1);
            g.drawString(font, bidRendered, fieldX + 3, btnY + 6, bidBuffer.isEmpty() ? 0xFF555555 : 0xFFFFFFFF);

            int placeBtnX = textCX + 4;
            drawFlatButton(g, placeBtnX, btnY, 70, 20, "Place Bid", mx, my, 0xFF004D00, 0xFF006400);
        }

        // Cancel button (seller only)
        if (isMySelling) {
            int cancelX = textCX - 50;
            drawFlatButton(g, cancelX, btnY, 100, 20, "Cancel Listing", mx, my, 0xFF4D0000, 0xFF6B0000);
        }
    }

    // ─── Create view ──────────────────────────────────────────────────────────

    private void drawCreate(GuiGraphics g, int mx, int my, int cx, int cw) {
        // Title only — back is handled by the sidebar ← Back button
        g.drawString(font, "New Listing", cx + 4, guiTop + 6, 0xFFFFAA00);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var inv = mc.player.getInventory();

        // Left: inventory list
        int listX = cx + 2;
        int listW = cw / 2 - 4;
        int listTop = guiTop + 20;
        int listBottom = guiTop + panelHeight - 4;
        int invVisible = Math.max(1, (listBottom - listTop) / 16);

        // Build slots the player is allowed to list — respects whitelist/blacklist/all mode.
        // Uses the same helper as handleCreateClick so draw and click are always in sync.
        List<Integer> slots = buildAuctionableSlots(inv);

        int maxInvScroll = Math.max(0, slots.size() - invVisible);
        if (invScrollOffset > maxInvScroll) invScrollOffset = maxInvScroll;

        g.fill(listX, listTop, listX + listW, listBottom, 0xFF0D0D0D);
        g.drawString(font, "Select item:", listX + 2, listTop + 2, 0xFF999999);
        int rowY = listTop + 14;
        int endIdx = Math.min(invScrollOffset + invVisible, slots.size());
        for (int i = invScrollOffset; i < endIdx; i++) {
            int slot = slots.get(i);
            ItemStack stack = inv.getItem(slot);
            boolean sel = selectedInvSlot == slot;
            boolean hov = mx >= listX && mx <= listX + listW && my >= rowY && my < rowY + 16;
            g.fill(listX, rowY, listX + listW, rowY + 16, sel ? 0xFF1A2A1A : (hov ? 0xFF1A1A2A : 0xFF111111));
            if (sel) g.fill(listX, rowY, listX + 2, rowY + 16, 0xFF44FF88);

            // Item icon
            g.pose().pushPose();
            g.pose().translate(listX + 2, rowY, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();

            String sname = stack.getHoverName().getString();
            if (stack.getCount() > 1) sname = sname + " ×" + stack.getCount();
            sname = ellipsis(sname, 30);
            while (font.width(sname) > listW - 18 && sname.length() > 1) sname = sname.substring(0, sname.length() - 1);
            g.drawString(font, sname, listX + 14, rowY + 4, 0xFFCCCCCC);
            rowY += 16;
        }
        if (slots.isEmpty()) {
            g.drawString(font, "No items in inventory.", listX + 2, listTop + 30, 0xFF555555);
        }

        // Right: config panel
        int configX = cx + cw / 2 + 2;
        int configW = cw / 2 - 6;
        int configY = guiTop + 20;

        // Preview selected item
        if (selectedInvSlot >= 0 && !inv.getItem(selectedInvSlot).isEmpty()) {
            ItemStack sel = inv.getItem(selectedInvSlot);
            g.pose().pushPose();
            g.pose().translate(configX + configW / 2 - 8, configY, 0);
            g.pose().scale(1.0f, 1.0f, 1f);
            g.renderItem(sel, 0, 0);
            g.pose().popPose();
            // Tooltip on hover over the preview icon
            int previewX = configX + configW / 2 - 8, previewY = configY;
            if (mx >= previewX && mx <= previewX + 16 && my >= previewY && my <= previewY + 16) {
                pendingTooltipStack = sel; pendingTooltipX = mx; pendingTooltipY = my;
            }
            String selName = ellipsis(sel.getHoverName().getString(), 30);
            g.drawString(font, selName, configX + configW / 2 - font.width(selName) / 2, configY + 20, 0xFFFFFFFF);
            configY += 34;
        } else {
            g.drawString(font, "← Pick item", configX + 2, configY + 4, 0xFF555555);
            configY += 16;
        }

        // Price field
        g.drawString(font, "Price:", configX + 2, configY, 0xFF999999);
        configY += 10;
        int pfW = configW - 4; int pfH = 14;
        priceFieldX1 = configX + 2; priceFieldX2 = configX + 2 + pfW;
        priceFieldY1 = configY; priceFieldY2 = configY + pfH;
        g.fill(priceFieldX1, priceFieldY1, priceFieldX2, priceFieldY2, priceFieldFocused ? 0xFF1A1A3A : 0xFF111111);
        g.fill(priceFieldX1, priceFieldY1, priceFieldX2, priceFieldY1 + 1, priceFieldFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(priceFieldX1, priceFieldY2 - 1, priceFieldX2, priceFieldY2, priceFieldFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(priceFieldX1, priceFieldY1, priceFieldX1 + 1, priceFieldY2, priceFieldFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(priceFieldX2 - 1, priceFieldY1, priceFieldX2, priceFieldY2, priceFieldFocused ? 0xFFFFAA00 : 0xFF444444);
        String priceShow = priceBuffer.toString().isEmpty() ? "Enter price..." : priceBuffer.toString();
        if (priceFieldFocused) priceShow = priceBuffer.toString() + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        // Clip from left so end of number is always visible when overflowing
        String priceRendered = priceShow;
        int priceUsableW = pfW - 6;
        while (font.width(priceRendered) > priceUsableW && priceRendered.length() > 1)
            priceRendered = priceRendered.substring(1);
        g.drawString(font, priceRendered, priceFieldX1 + 3, priceFieldY1 + 3,
                priceBuffer.isEmpty() ? 0xFF555555 : 0xFFFFFFFF);
        configY += pfH + 6;

        // Duration picker
        g.drawString(font, "Duration:", configX + 2, configY, 0xFF999999);
        configY += 10;
        durBtnY = configY; // store for click handler
        int durBtnW = (configW - 4) / DURATION_LABELS.length;
        for (int i = 0; i < DURATION_LABELS.length; i++) {
            int bx = configX + 2 + i * durBtnW;
            boolean selDur = selectedDuration == i;
            boolean hovDur = mx >= bx && mx < bx + durBtnW - 1 && my >= configY && my < configY + 14;
            g.fill(bx, configY, bx + durBtnW - 1, configY + 14,
                    selDur ? 0xFF444400 : (hovDur ? 0xFF252525 : 0xFF1A1A1A));
            if (selDur) g.fill(bx, configY + 13, bx + durBtnW - 1, configY + 14, 0xFFFFAA00);
            g.drawString(font, DURATION_LABELS[i], bx + (durBtnW - 1 - font.width(DURATION_LABELS[i])) / 2,
                    configY + 3, selDur ? 0xFFFFFFFF : 0xFF888888);
        }
        configY += 18;
        togBtnY = configY; // store for click handler

        // Auction / BIN toggle
        int togW = (configW - 4) / 2;
        int togX = configX + 2;
        boolean hovAuc = mx >= togX && mx < togX + togW - 1 && my >= configY && my < configY + 16;
        boolean hovBin = mx >= togX + togW && mx < togX + togW * 2 - 1 && my >= configY && my < configY + 16;
        g.fill(togX, configY, togX + togW - 1, configY + 16,
                !createIsBin ? 0xFF444400 : (hovAuc ? 0xFF252525 : 0xFF1A1A1A));
        g.fill(togX + togW, configY, togX + togW * 2 - 1, configY + 16,
                createIsBin ? 0xFF003344 : (hovBin ? 0xFF252525 : 0xFF1A1A1A));
        if (!createIsBin) g.fill(togX, configY + 15, togX + togW - 1, configY + 16, 0xFFFFAA00);
        if (createIsBin)  g.fill(togX + togW, configY + 15, togX + togW * 2 - 1, configY + 16, 0xFF55AAFF);
        g.drawString(font, "Auction", togX + (togW - 1 - font.width("Auction")) / 2, configY + 4,
                !createIsBin ? 0xFFFFFFFF : 0xFF666666);
        g.drawString(font, "BIN", togX + togW + (togW - 1 - font.width("BIN")) / 2, configY + 4,
                createIsBin ? 0xFFFFFFFF : 0xFF666666);
        configY += 22;

        // Create button
        boolean canCreate = selectedInvSlot >= 0 && !inv.getItem(selectedInvSlot).isEmpty()
                && !priceBuffer.isEmpty();
        int createBtnW = configW - 4;
        boolean hovCreate = mx >= configX + 2 && mx < configX + 2 + createBtnW
                && my >= configY && my < configY + 18;
        g.fill(configX + 2, configY, configX + 2 + createBtnW, configY + 18,
                canCreate ? (hovCreate ? 0xFF005500 : 0xFF003300) : 0xFF1A1A1A);
        String createLabel = "Create Listing";
        g.drawString(font, createLabel, configX + 2 + (createBtnW - font.width(createLabel)) / 2, configY + 5,
                canCreate ? 0xFFFFFFFF : 0xFF555555);
    }

    // ─── Mouse & keyboard ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int sx = guiLeft + 1;

        // Close
        if (mx >= guiLeft + panelWidth - 16 && mx <= guiLeft + panelWidth - 2
                && my >= guiTop + 2 && my <= guiTop + 14) { onClose(); return true; }

        // Sidebar: Browse
        if (mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3 && my >= guiTop + 18 && my < guiTop + 38) {
            showMineOnly = false; view = View.BROWSE; scrollOffset = 0; binConfirmArmed = false; return true;
        }
        // Sidebar: My Listings
        if (mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3 && my >= guiTop + 41 && my < guiTop + 61) {
            showMineOnly = true; view = View.BROWSE; scrollOffset = 0; binConfirmArmed = false; return true;
        }
        // Sidebar: + New Listing
        int newBtnY = guiTop + 69;
        if (mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3 && my >= newBtnY && my < newBtnY + 20) {
            view = View.CREATE; selectedInvSlot = -1; priceBuffer.setLength(0); return true;
        }
        // Sidebar: Claim
        int claimBtnY = guiTop + panelHeight - 28;
        if (mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3 && my >= claimBtnY && my < claimBtnY + 20) {
            MarketPacketSender.sendAuctionClaim();
            return true;
        }
        // Sidebar: ← Back
        int backY = claimBtnY - 25;
        if (mx >= sx + 3 && mx <= sx + SIDEBAR_W - 3 && my >= backY && my < backY + 18) {
            binConfirmArmed = false;
            if (view != View.BROWSE) { view = View.BROWSE; }
            else { minecraft.setScreen(parent); }
            return true;
        }

        int cx = guiLeft + SIDEBAR_W + 2;
        int cw = panelWidth - SIDEBAR_W - 2;
        return switch (view) {
            case BROWSE -> handleBrowseClick(mx, my, btn, cx, cw);
            case DETAIL -> handleDetailClick(mx, my, btn, cx, cw);
            case CREATE -> handleCreateClick(mx, my, btn, cx, cw);
        };
    }

    private boolean handleBrowseClick(double mx, double my, int btn, int cx, int cw) {
        // Search field focus
        if (mx >= searchFieldX1 && mx <= searchFieldX2 && my >= searchFieldY1 && my <= searchFieldY2) {
            searchFocused = true; return true;
        }
        searchFocused = false;

        // Scrollbar
        int scrollBarX = cx + cw - 6;
        List<ClientAuctionCache.AuctionDto> display = getDisplayList();
        if (display.size() > maxVisible && mx >= scrollBarX && mx <= scrollBarX + 4
                && my >= scrollTrackTop && my <= scrollTrackTop + scrollTrackH) {
            scrollDragging   = true;
            dragStartY       = (int) my;
            dragStartOffset  = scrollOffset;
            return true;
        }

        // Row click → open detail
        int rowsTop    = guiTop + 34;
        int rowsBottom = guiTop + panelHeight - 4;
        int rowY = rowsTop;
        int end  = Math.min(scrollOffset + maxVisible, display.size());
        for (int i = scrollOffset; i < end; i++) {
            if (rowY + ROW_HEIGHT > rowsBottom) break;
            if (mx >= cx + 2 && mx <= cx + cw - 10 && my >= rowY && my < rowY + ROW_HEIGHT) {
                selectedAuction = display.get(i);
                view = View.DETAIL;
                bidBuffer.setLength(0);
                bidFieldFocused = false;
                // Autofill the bid field with the minimum next bid
                if (!selectedAuction.isBin) {
                    long tb = selectedAuction.topBid;
                    long inc = Math.max(1L, (long) Math.ceil(tb * 0.01));
                    bidBuffer.append(tb + inc);
                }
                return true;
            }
            rowY += ROW_HEIGHT;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleDetailClick(double mx, double my, int btn, int cx, int cw) {
        // ← Back
        if (mx >= cx + 2 && mx <= cx + 54 && my >= guiTop + 4 && my <= guiTop + 17) {
            view = View.BROWSE; return true;
        }
        if (selectedAuction == null) return false;
        ClientAuctionCache.AuctionDto a = selectedAuction;
        String myIdStr = minecraft != null && minecraft.player != null
                ? minecraft.player.getUUID().toString() : "";

        int btnY   = guiTop + panelHeight - 36;
        int textCX = cx + cw / 2;
        boolean isMySelling = a.sellerId.equals(myIdStr);

        if (a.isBin && !isMySelling) {
            int bw = 90; int bx = textCX - bw / 2;
            if (mx >= bx && mx < bx + bw && my >= btnY && my < btnY + 20) {
                if (binConfirmArmed) {
                    // Second click — execute
                    binConfirmArmed = false;
                    MarketPacketSender.sendAuctionBin(a.id);
                    view = View.BROWSE;
                } else {
                    // First click — arm confirm
                    binConfirmArmed = true;
                    binConfirmArmedAt = System.currentTimeMillis();
                }
                return true;
            }
            // Clicked elsewhere — disarm
            binConfirmArmed = false;
        } else if (!a.isBin && !isMySelling) {
            // Bid field focus
            if (mx >= bidFieldX1 && mx <= bidFieldX2 && my >= bidFieldY1 && my <= bidFieldY2) {
                bidFieldFocused = true; return true;
            }
            bidFieldFocused = false;
            // Place Bid button
            if (mx >= textCX + 4 && mx < textCX + 74 && my >= btnY && my < btnY + 20) {
                long amount = parseLong(bidBuffer.toString());
                if (amount > 0) {
                    MarketPacketSender.sendAuctionBid(a.id, amount);
                    bidBuffer.setLength(0);
                }
                return true;
            }
        }
        if (isMySelling) {
            int cancelX = textCX - 50;
            if (mx >= cancelX && mx < cancelX + 100 && my >= btnY && my < btnY + 20) {
                MarketPacketSender.sendAuctionCancel(a.id);
                view = View.BROWSE;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleCreateClick(double mx, double my, int btn, int cx, int cw) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        List<Integer> slots = buildAuctionableSlots(inv);

        // Inventory click
        int listX = cx + 2; int listW = cw / 2 - 4;
        int listTop = guiTop + 34;
        int listBottom = guiTop + panelHeight - 4;
        int invVisible = Math.max(1, (listBottom - listTop) / 16);
        int rowY = listTop;
        for (int i = invScrollOffset; i < Math.min(invScrollOffset + invVisible, slots.size()); i++) {
            if (mx >= listX && mx < listX + listW && my >= rowY && my < rowY + 16) {
                selectedInvSlot = slots.get(i);
                priceFieldFocused = false;
                return true;
            }
            rowY += 16;
        }

        int configX = cx + cw / 2 + 2;
        int configW = cw / 2 - 6;
        // Price field
        if (mx >= priceFieldX1 && mx <= priceFieldX2 && my >= priceFieldY1 && my <= priceFieldY2) {
            priceFieldFocused = true; return true;
        }
        priceFieldFocused = false;

        // Duration buttons — use Y stored by drawCreate to avoid recompute drift
        if (durBtnY >= 0 && my >= durBtnY && my < durBtnY + 14) {
            int durBtnW = (configW - 4) / DURATION_LABELS.length;
            for (int i = 0; i < DURATION_LABELS.length; i++) {
                int bx = configX + 2 + i * durBtnW;
                if (mx >= bx && mx < bx + durBtnW - 1) { selectedDuration = i; return true; }
            }
        }
        // AUC/BIN toggle
        if (togBtnY >= 0 && my >= togBtnY && my < togBtnY + 16) {
            int togW = (configW - 4) / 2;
            if (mx >= configX + 2 && mx < configX + 2 + togW - 1) { createIsBin = false; return true; }
            if (mx >= configX + 2 + togW && mx < configX + 2 + togW * 2 - 1) { createIsBin = true; return true; }
        }
        // Create button — Y is right after toggle (togBtnY + 16 + 6 = togBtnY + 22)
        int createBtnY = togBtnY >= 0 ? togBtnY + 22 : priceFieldY2 + 46;
        int createBtnW = configW - 4;
        boolean canCreate = selectedInvSlot >= 0 && !inv.getItem(selectedInvSlot).isEmpty()
                && !priceBuffer.isEmpty();
        if (canCreate && mx >= configX + 2 && mx < configX + 2 + createBtnW
                && my >= createBtnY && my < createBtnY + 18) {
            long price = parseLong(priceBuffer.toString());
            if (price > 0) {
                MarketPacketSender.sendAuctionCreate(selectedInvSlot, price, DURATION_MS[selectedDuration], createIsBin);
                view = View.BROWSE;
                priceBuffer.setLength(0);
                ClientAuctionCache.setLoading(true);
                MarketPacketSender.requestAuctionList(0);
            }
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (view == View.BROWSE && searchFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && searchBuffer.length() > 0) {
                searchBuffer.deleteCharAt(searchBuffer.length() - 1);
                scrollOffset = 0; // reset scroll when search changes
                return true;
            }
            return true;
        }
        if (view == View.DETAIL && bidFieldFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { bidFieldFocused = false; return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && bidBuffer.length() > 0) {
                bidBuffer.deleteCharAt(bidBuffer.length() - 1); return true;
            }
            return true;
        }
        if (view == View.CREATE && priceFieldFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { priceFieldFocused = false; return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && priceBuffer.length() > 0) {
                priceBuffer.deleteCharAt(priceBuffer.length() - 1); return true;
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (view != View.BROWSE) { view = View.BROWSE; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (view == View.BROWSE && searchFocused && searchBuffer.length() < 32) {
            searchBuffer.append(c);
            scrollOffset = 0; // reset scroll when filter changes
            return true;
        }
        if (view == View.DETAIL && bidFieldFocused && Character.isDigit(c) && bidBuffer.length() < 16) {
            bidBuffer.append(c); return true;
        }
        if (view == View.CREATE && priceFieldFocused && Character.isDigit(c) && priceBuffer.length() < 16) {
            priceBuffer.append(c); return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollDragging && scrollTrackH > scrollThumbH) {
            List<ClientAuctionCache.AuctionDto> display = getDisplayList();
            int maxScroll = Math.max(0, display.size() - maxVisible);
            int travel    = scrollTrackH - scrollThumbH;
            int delta     = (int) ((my - dragStartY) * maxScroll / (double) travel);
            scrollOffset  = (int) Math.clamp(dragStartOffset + delta, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrollDragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (view == View.BROWSE) {
            List<ClientAuctionCache.AuctionDto> display = getDisplayList();
            int maxScroll = Math.max(0, display.size() - maxVisible);
            scrollOffset  = (int) Math.clamp(scrollOffset - v, 0, maxScroll);
        } else if (view == View.CREATE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var inv = mc.player.getInventory();
                int totalSlots = buildAuctionableSlots(inv).size();
                int listBottom = guiTop + panelHeight - 4;
                int invVisible = Math.max(1, (listBottom - (guiTop + 34)) / 16);
                int maxS = Math.max(0, totalSlots - invVisible);
                invScrollOffset = (int) Math.clamp(invScrollOffset - v, 0, maxS);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds the list of inventory slot indices the player is allowed to list in
     * the auction house, based on the server's auction config synced on join.
     *
     * Rules applied client-side (server re-validates on create):
     *   whitelist mode → only items whose ID is in the whitelist are shown
     *                    (whitelist entries bypass the market-item restriction)
     *   blacklist mode → items on the blacklist are hidden;
     *                    market items are also hidden unless allowMarketItems=true
     *   all mode       → market items are hidden unless allowMarketItems=true
     *
     * Using a single shared helper ensures drawCreate and handleCreateClick always
     * operate on exactly the same list, preventing click-target drift.
     */
    private static List<Integer> buildAuctionableSlots(net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> slots = new ArrayList<>();
        String mode = ClientAuctionConfigCache.mode();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if ("whitelist".equals(mode)) {
                // Whitelist mode: only show items explicitly on the whitelist.
                // Being on the whitelist overrides the market-item restriction.
                if (ClientAuctionConfigCache.isWhitelisted(itemId)) slots.add(i);
            } else if ("blacklist".equals(mode)) {
                if (ClientAuctionConfigCache.isBlacklisted(itemId)) continue;
                if (!ClientAuctionConfigCache.allowMarketItems()
                        && ClientMarketListingCache.isMarketItem(itemId)) continue;
                slots.add(i);
            } else {
                // "all" mode
                if (!ClientAuctionConfigCache.allowMarketItems()
                        && ClientMarketListingCache.isMarketItem(itemId)) continue;
                slots.add(i);
            }
        }
        return slots;
    }

    private List<ClientAuctionCache.AuctionDto> getDisplayList() {
        String myIdStr = minecraft != null && minecraft.player != null
                ? minecraft.player.getUUID().toString() : "";
        String searchLower = searchBuffer.toString().toLowerCase();
        List<ClientAuctionCache.AuctionDto> out = new ArrayList<>();
        for (ClientAuctionCache.AuctionDto d : ClientAuctionCache.get()) {
            if (!d.active) continue;
            if (showMineOnly && !d.sellerId.equals(myIdStr)) continue;
            if (!searchLower.isEmpty() && !d.displayName.toLowerCase().contains(searchLower)) continue;
            out.add(d);
        }
        return out;
    }

    private void renderIcon(GuiGraphics g, String itemId, int x, int y, float scale) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(scale, scale, 1f);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            }
        } catch (Exception ignored) {}
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawFlatButton(GuiGraphics g, int x, int y, int w, int h, String label,
                                 double mx, double my, int col, int hoverCol) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hov ? hoverCol : col);
        drawBorder(g, x, y, w, h, 0xFFAAAAAA);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    /** Truncates s to at most maxChars characters, appending "…" if trimmed. */
    private static String ellipsis(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 1) + "…";
    }

    /**
     * Rebuilds an ItemStack from its SNBT string so we can show the full MC tooltip.
     * Returns EMPTY on any parse failure.
     */
    private static ItemStack buildTooltipStack(String snbt) {
        if (snbt == null || snbt.isEmpty()) return ItemStack.EMPTY;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return ItemStack.EMPTY;
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.parseOptional(mc.level.registryAccess(), tag);
        } catch (Exception ignored) { return ItemStack.EMPTY; }
    }

    private static String formatTimeLeft(long ms) {
        if (ms <= 0) return "Ended";
        long sec  = ms / 1000;
        long min  = sec / 60;
        long hrs  = min / 60;
        long days = hrs / 24;
        if (days > 0)  return days + "d " + (hrs % 24) + "h";
        if (hrs > 0)   return hrs + "h " + (min % 60) + "m";
        if (min > 0)   return min + "m";
        return sec + "s";
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}


