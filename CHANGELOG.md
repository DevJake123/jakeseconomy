# Changelog

All notable changes to Jake's Economy will be documented here.

## [1.3.0+1.21.1] — 2026-06-29

### New config fields in `jakeseconomy-server.json`

> **Note for existing servers / map-makers:** These fields are fully backwards-compatible.  
> If your existing config file is missing any of them, the mod will automatically apply the  
> defaults shown below on next load. No manual editing is required unless you want to change behaviour.

#### GUI Tab Visibility
Control which tabs appear in the market GUI — useful for adventure maps that only want to expose certain features.

| Field | Default | Description |
|---|---|---|
| `showMarketTab` | `true` | Show or hide the Market tab |
| `showWithdrawTab` | `true` | Show or hide the Withdraw tab (set `false` to keep all money digital) |
| `showHistoryTab` | `true` | Show or hide the Transaction History tab |
| `showAuctionTab` | `true` | Show or hide the Auction House button |
| `allowHotkeyOpen` | `true` | If `false`, the `;` keybind does nothing — GUI can only be opened via `/jecon market open` or an NPC command |

#### Auction Item Control
Fine-grained control over what players can list in the Auction House.

| Field | Default | Description |
|---|---|---|
| `auctionItemMode` | `"all"` | `"all"` — anything not in market; `"whitelist"` — only listed items; `"blacklist"` — anything except listed items |
| `auctionWhitelist` | `[]` | Item IDs allowed when mode is `"whitelist"` (e.g. `["minecraft:bread"]`). Whitelisted items bypass the market-item restriction. |
| `auctionBlacklist` | `[]` | Item IDs blocked when mode is `"blacklist"` |
| `allowMarketItemsInAuction` | `false` | If `true`, items also in the main market can be auctioned |

#### Command Permission Levels
Each command group now has an independently configurable permission level. Set inside the `"permissions"` object.

| Field | Default | Controls |
|---|---|---|
| `permissions.balanceOther` | `2` | `/jecon balance <player>` |
| `permissions.give` | `2` | `/jecon give` |
| `permissions.set` | `2` | `/jecon set` |
| `permissions.take` | `2` | `/jecon take` |
| `permissions.marketOpen` | `0` | `/jecon market open` (0 = all players) |
| `permissions.marketAdmin` | `2` | `setprice`, `addcategory`, `removeprice`, `setlock`, `price` |
| `permissions.auctionOpen` | `0` | `/jecon auction open` (0 = all players) |
| `permissions.debug` | `2` | `/jecon debug` |

*Minecraft permission levels: 0 = all players · 1 = moderator · 2 = operator · 3 = admin · 4 = owner*

#### Config file comments
`jakeseconomy-server.json` now generates with inline `//` comments describing every field. Existing hand-edited configs still load correctly — the reader strips comments before parsing.

---

### New features

#### Map-maker / server control
- **GUI tab visibility** — hide any tab via the config; changes sync to clients on join
- **Hotkey lockdown** — `allowHotkeyOpen: false` prevents players from opening the market with the keybind; they must interact with an NPC or use a command trigger instead
- **Per-command permission levels** — each admin command group has its own configurable op level; set `marketOpen`/`auctionOpen` to `2` during testing to restrict access

#### Auction House item filtering
- **Whitelist mode** — only the exact items you list in `auctionWhitelist` can be auctioned; the Create Listing item picker shows only those items (and correctly overrides the "market items can't be auctioned" restriction for whitelisted entries)
- **Blacklist mode** — block specific items from the auction house
- **`allowMarketItemsInAuction`** — opt in to allowing market items to also appear in auctions

#### New commands
- `/jecon market open` — opens the Market GUI for the executing player (permission: `marketOpen`)
- `/jecon auction open` — opens the Auction House GUI for the executing player (permission: `auctionOpen`)

---

### Bug fixes

#### Auction House
- Fixed Create Listing item picker click targets being offset from what was displayed — draw list and click list now always use the same filtered slot array
- Fixed items outside the auction whitelist appearing in the Create Listing picker when whitelist mode was active
- Fixed `OpenScreenPayload` not being registered in `PayloadTypeRegistry.playS2C()`, causing a crash on client startup (`Cannot register handler as no payload type has been registered`)
- Fixed auction browse list not filtering out non-whitelisted entries when `auctionItemMode` is `"whitelist"`

---

## [1.2.1+1.21.1] — 2026-06-22

### Integrations
- Added native integration with CobbleDollars mod — both mods sync their currency values automatically

---

## [1.2.0+1.21.1] — 2026-06-08

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
