# Changelog

All notable changes to Jake's Economy will be documented here.

## [1.1.0+1.21.1] — 2026-05-30

### Auction House update

#### Auction House
- Accessible via the sidebar button inside the Market GUI
- Players can list any item **not** already in the market as an auction or Buy It Now (BIN) listing
- Five listing durations available: 1 h, 6 h, 12 h, 24 h, 48 h
- Configurable listing fee (% of price) deducted at creation — non-refundable even on cancellation
- Configurable maximum listing price guard to prevent overflow and unreasonable listings
- Open auctions: bids must exceed the current top bid by at least 1% (always ≥ 1 unit)
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
- `/jakeseconomy market setlock <item> <lockId>` — change an item's achievement lock live without editing JSON
- `/jakeseconomy market setprice` gains an optional `[achievementLock]` argument

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
- `/jakeseconomy balance / give / set / take`
- `/jakeseconomy market setprice <item> <price> <category>`
- `/jakeseconomy market removeprice <item>`
- `/jakeseconomy market addcategory <name>`
- `/jakeseconomy market price <item>`
