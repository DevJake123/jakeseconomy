# Changelog

All notable changes to Jake's Economy will be documented here.

## [1.0.0+1.21.1] — 2026-05-24

### Initial release

#### Economy system
- Virtual player balances stored per-world in persistent NBT save data
- Logarithmic price curve: prices rise with scarcity, fall with surplus
- Per-player buy rate limiting (configurable window + cap) to prevent price manipulation
- Price decay: prices slowly return to base over time (on by default in singleplayer, off in multiplayer)
- Price trend snapshots every ~3 minutes — ↑/↓/— arrows reflect recent movement.

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
- `/jakeseconomy market setprice / removeprice`

