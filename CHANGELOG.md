# Changelog

All notable changes to Jake's Economy will be documented here.

## [1.2.0+1.21.1] — 2026-06-08

### Price trend graph improvements

#### Market GUI — Price History Graph
- **Tiered snapshot system**: Fine-grained 20-minute snapshots for intraday detail (Day view), hourly snapshots for long-term trends (Week / Month views)
- Graph now captures data every 20 minutes instead of every hour — Day view shows 72 points across the last 24 hours for much smoother intraday curves
- Week and Month views continue to use hourly archive data — no change in long-term resolution, storage remains efficient at ~5.4 MB for 137 items
- Tooltip hover now shows "Just now" for very recent points and properly displays minute-level timestamps
- "No data" message now shows the correct snapshot interval per view ("Data is recorded every 20 minutes" for Day, "Data is recorded hourly" for Week/Month)
- `/jecon debug fillhistory` test command now injects both recent (20-min intraday cycle) and archive (hourly) test data for comprehensive graph testing

### Security & quality fixes

#### Market
- Fixed duplicate achievement lock check in `sell()` — now uses the extracted helper method `isAchievementLocked()` 
- Fixed pre-existing code quality warnings (redundant cast, dangling Javadoc)

#### Auction House
- Fixed expired BIN listings being purchasable between tick-loop scans (up to 1-second window) — `instantBuy()` now checks expiry before allowing purchase
- Fixed full bid history being serialized and sent over the network to every client — only top bid data is transmitted now (bandwidth optimization)
- Fixed control characters in modded item names or player profile names breaking auction JSON — all user-visible strings now use full JSON escaping
- Fixed `minBidIncrementPercent` config field not being read; minimum bid increment is now correctly driven by the configured value instead of always using 1 %
- Sellers who cancel a listing while offline now receive a chat notification on next login (mirrors the existing outbid / auction-ended offline notification system)
- Fixed missing balance sync in `placeBid` and `instantBuy` — buyer's HUD balance now updates immediately after funds are deducted
- Fixed confusing "Items delivered to your inventory" message showing even when no items fit in the player's inventory


#### Save data
- `AuctionState.load()` visibility lowered to package-private — external callers must use `safeLoad()` which includes corruption recovery
- All three event schedulers (`AuctionExpireScheduler`, `TrendSnapshotScheduler`, `PriceDecayScheduler`) now guard against double-registration on world reload
- `applyDecay()` no longer calls `setDirty()` twice per tick when changes are made

#### Commands
- Main command root renamed from `/jakeseconomy` to `/jecon` (shorter alias used everywhere)
- Added `/balance` player shortcut — shows abbreviated + exact balance; only registers if no competing mod claims the literal `/balance` command
- Fixed `/jecon take` not syncing the updated balance to the target player's client HUD when the player is online

#### Market GUI
- Balance label now shows an exact-value tooltip on hover

#### Economy API (`com.github.devjake123.jakeseconomy.api`)
- New `JakesEconomyApi.hasEnoughBalance(uuid, amount, server)` — null-safe check without callers needing to read and compare manually
- New `JakesEconomyApi.transferBalance(from, to, amount, server)` — atomic player-to-player transfer; returns `false` on insufficient funds
- New `JakesEconomyApi.isMarketItem(itemId, server)` — returns `true` if the item is listed in the active price config
- New `JakesEconomyApi.getMarketPrice(itemId, server)` — returns current live price or `-1` if not listed
- New `JakesEconomyApi.syncBalance(uuid, server)` — explicit manual client-HUD sync (write methods already call this automatically)
- All API write methods (`deposit`, `withdraw`, `setBalance`, `transferBalance`) now automatically sync the updated balance to the player's client if they are online
- Fixed `BALANCE_CHANGED` event firing twice per `deposit`/`withdraw` call
- New `EconomyApiEvents.AUCTION_BID_PLACED` event — fires when a bid is accepted; payload: `auctionId`, `bidderId`, `bidAmount`, `antiSniped`
- New `EconomyApiEvents.AUCTION_CANCELLED` event — fires when a seller cancels a listing; payload: `auctionId`, `sellerId`, `itemId`



---

## [1.1.0+1.21.1] — 2026-05-30

### Auction House update

#### Auction House
- Accessible via the sidebar button inside the Market GUI
- Players can list any item **not** already in the market as an auction or Buy It Now (BIN) listing
- Five listing durations available: 1 h, 6 h, 12 h, 24 h, 48 h
- Configurable listing fee (% of price) deducted at creation — non-refundable even on cancellation
- Configurable maximum listing price guard to prevent overflow and unreasonable listings
- Open auctions: bids must exceed the current top bid by at least the configured `minBidIncrementPercent` (default 1 %, always ≥ 1 unit)
- BIN listings: first click arms a 3-second confirmation window; second click executes
- **Anti-snipe**: bids placed within the last 2 minutes of an auction extend it by 2 minutes (configurable)
- Previous top bidder refunded to their escrow immediately when outbid; notified if online
- On auction end: item goes to winner's escrow, proceeds go to seller's escrow
- Auctions that expire while the server is offline are finalized automatically on next start
- Hard cap of 20 active listings per player
- **Escrow system**: all money and items held in persistent escrow — nothing is lost if the server crashes between an auction ending and the player claiming
- Players claim items and currency via a dedicated "Claims" button
- Finalized auction records pruned automatically every 5 minutes (records kept for 30 minutes post-expiry)

#### New admin commands
- `/jecon market setlock <item> <lockId>` — change an item's achievement lock live without editing JSON
- `/jecon market setprice` gains an optional `[achievementLock]` argument

---

## [1.0.0+1.21.1] — 2026-05-24

### Initial release

#### Economy system
- Virtual player balances stored per-world in persistent NBT save data
- Logarithmic price curve: prices rise with scarcity, fall with surplus
- Per-player buy rate limiting (configurable window + cap) to prevent price manipulation
- Price decay: prices slowly return to base over time (on by default in singleplayer, off in multiplayer)
- Price trend snapshots every ~3 minutes — ↑/↓/— arrows reflect recent movement

#### Market GUI
- Category tabs with full-text cross-tab search
- A/Z alphabetical sort toggle (resets to A→Z on each screen open)
- Locked items pushed to the bottom of the list until the required advancement is completed
- Per-item detail screen with BUY / SELL buttons: ×1 left-click, ×64 Shift+click, custom right-click
- Shift+SELL fills with however many the player holds (no hard-fail on < 64)
- Transaction history panel with type badge, item name, quantity, amount, and timestamp
- Withdraw tab to convert virtual balance to physical coins

#### Currency
- 8 denominations: Copper Coin, Sack of Copper Coins, Silver Coin, Sack of Silver Coins, Gold Coin, Sack of Gold Coins, Platinum Coin, Sack of Platinum Coins
- Coin sack crafting recipes (9× smaller coin → 1 sack)
- Right-click to deposit physical coins into virtual balance
- Some coins drop in vanilla loot chests

#### Multiplayer / dedicated server
- Full price config synced to each client on join (market shows correct categories/items even on fresh clients)
- Live price + trend data broadcast to all online players after every trade

#### Configuration
- `jakeseconomy-server.json` — market depth, sensitivity, decay, rate limits
- `jakeseconomy-prices.json` — categories, item prices, achievement locks

#### Admin commands
- `/jecon balance / give / set / take`
- `/jecon market setprice <item> <price> <category>`
- `/jecon market removeprice <item>`
- `/jecon market addcategory <name>`
- `/jecon market price <item>`
