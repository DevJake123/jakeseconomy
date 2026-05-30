# Jake's Economy

A dynamic, player-driven economy mod for **Minecraft 1.21.1** (Fabric).

Players buy and sell items through an in-game market whose prices shift in real time based on supply and demand. Includes a full coin currency system, per-player transaction history, price trend arrows, achievement-locked items, an Auction House for player-to-player trading, and extensive server-side configuration.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | 0.116.12+1.21.1 or newer |
| Java | 21 |

## Installation

1. Download the latest `jakeseconomy-<version>.jar` from [Releases](../../releases).
2. Drop it into your `mods/` folder alongside Fabric API.
3. Launch the game — default config files are generated automatically in `config/`.

---

## Features

| Feature | Description |
|---|---|
| **Live market** | Every buy/sell shifts price up/down logarithmically across the whole server |
| **Auction House** | List any non-market item as an open auction or Buy It Now; fully escrow-backed |
| **Coin currency** | Copper → Silver → Gold → Platinum coins and sacks, craftable and droppable in loot chests |
| **Withdrawal** | Convert virtual balance to physical coins at any time via the Withdraw tab |
| **Price trends** | ↑/↓/— arrows show whether each item has been rising or falling recently |
| **Achievement locks** | Items can require a specific advancement before trading becomes available |
| **Transaction history** | Per-player log of every buy, sell, and withdrawal with timestamps |
| **Sort & search** | A/Z toggle sort and real-time full-text search across all categories |
| **Price sync** | Prices broadcast to all online clients after each trade — no stale GUI data in multiplayer |
| **Atomic config saves** | Config written to `.tmp` then renamed — data survives a mid-write crash |
| **Admin commands** | Balance management, live price overrides, auction config, and more via `/jakeseconomy` |

---

## Usage

### Market

| Action | How |
|---|---|
| Open market | Press **;** (rebindable under Controls → Jake's Economy) |
| Buy × 1 | Click item → left-click **BUY** |
| Buy × 64 | Click item → **Shift + left-click** BUY |
| Buy custom amount | Click item → **right-click** BUY |
| Sell × 1 | Click item → left-click **SELL** |
| Sell all held | Click item → **Shift + left-click** SELL (sells however many you carry) |
| Withdraw coins | Open market → **Withdraw** tab → set amounts → Confirm |
| Deposit coins | **Right-click** a coin or coin sack while holding it |
| Sort items | Click the **A / Z** button in the market header to toggle alphabetical order |

### Auction House

| Action | How |
|---|---|
| Open Auction House | Open market → click the **Auction House** button in the sidebar |
| Browse listings | Scroll through active auctions; listings sorted soonest-expiring first |
| Search listings | Type in the search bar to filter by item name |
| Create a listing | Click **New Listing** → select item from inventory → set price, duration, type (Auction or BIN) → Confirm |
| Bid on an auction | Click a listing → enter bid amount → **Place Bid** (minimum: current top bid + 1%) |
| Buy It Now | Click a BIN listing → click **Buy Now** |
| Cancel your listing | Click your own listing → **Cancel** (listing fee is not refunded) |
| Claim winnings/items | Click **Claims** → **Claim All** |

> **Anti-snipe:** Any bid placed within the last 2 minutes of an auction extends it by 2 minutes, giving others a chance to respond.

> **Escrow:** All currency and items are held server-side in persistent escrow from the moment they're committed. Nothing is lost even if the server crashes mid-auction.

---

## Configuration

All files are created in `config/` on first launch and are in simple JSON.

### `jakeseconomy-server.json`

| Key | Default | Description |
|---|---|---|
| `priceDecayEnabled` | `true` (SP) / `false` (MP) | Whether prices recover toward base over time |
| `priceDecayRatePercent` | `5.0` | % of deficit removed per decay tick |
| `priceDecayIntervalHours` | `1.0` | Real-world hours between decay ticks |
| `marketDepth` | `5000` | Items needed to double/half the original price |
| `sensitivity` | `2.3` | Steepness of price swings |
| `deficitLimitPerWindow` | `64` | Max items one player can trade per time window |
| `deficitWindowHours` | `1.0` | Hours before a player's buy window resets |
| `antiSnipeExtensionMs` | `120000` | Milliseconds to extend an auction when a late bid lands (0 = disabled) |
| `minBidIncrementPercent` | `1.0` | Minimum % a bid must exceed the current top bid |
| `listingFeePercent` | `1.0` | Non-refundable listing fee as % of listing price (0 = no fee) |
| `maxListingPrice` | `1000000000000` | Hard cap on any single listing price (1 trillion by default) |

### `jakeseconomy-prices.json`

Lists every tradeable item grouped by category. Each entry looks like:

```json
{
  "categories": {
    "Materials": {
      "minecraft:iron_ingot": { "basePrice": 500, "marketDepth": -1, "achievementLock": 0 }
    }
  },
  "achievementLocks": {
    "1": { "displayName": "Getting an Upgrade", "advancementId": "minecraft:story/obtain_armor" }
  }
}
```

- `basePrice` — price in virtual currency units when supply equals demand
- `marketDepth` — per-item override (`-1` = use global value from server config)
- `achievementLock` — `0` = always unlocked; any other integer key maps to an `achievementLocks` entry
- Changes take effect on the next server start, or immediately via `/jakeseconomy market setprice`

---

## Admin Commands

All commands require operator permission level 2.

```
/jakeseconomy balance                                                — check your own balance
/jakeseconomy balance <player>                                       — check another player's balance
/jakeseconomy give    <player> <amount>                              — add currency to a player
/jakeseconomy set     <player> <amount>                              — set a player's balance exactly
/jakeseconomy take    <player> <amount>                              — remove currency from a player
/jakeseconomy market setprice <item> <price> <category> [lockId]     — add/update an item; optional achievement lock
/jakeseconomy market removeprice <item>                              — remove an item from the market
/jakeseconomy market addcategory <name>                              — create an empty category tab
/jakeseconomy market setlock <item> <lockId>                         — change an item's achievement lock (0 = remove lock)
/jakeseconomy market price <item>                                    — check an item's current live price
```

---

## Currency Values

| Coin | Value |
|---|---|
| Copper Coin | 10 |
| Sack of Copper Coins | 100 |
| Silver Coin | 1 000 |
| Sack of Silver Coins | 10 000 |
| Gold Coin | 100 000 |
| Sack of Gold Coins | 1 000 000 |
| Platinum Coin | 10 000 000 |
| Sack of Platinum Coins | 100 000 000 |

---

## License

GNU Lesser General Public License Version 2.1 — see [LICENSE](LICENSE).
