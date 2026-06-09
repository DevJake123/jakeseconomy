# Jake's Economy

A dynamic, player-driven economy mod for **Minecraft 1.21.1** (Fabric).

Players buy and sell items through an in-game market whose prices shift in real time based on supply and demand. Includes a full coin currency system, per-player transaction history, price trend arrows, achievement-locked items, a player-to-player Auction House, and extensive server-side configuration.

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
| **Price history graph** | Click the trend arrow to view detailed price charts: Day (20-min snapshots), Week, Month |
| **Achievement locks** | Items can require a specific advancement before trading becomes available |
| **Transaction history** | Per-player log of every buy, sell, and withdrawal with timestamps |
| **Sort & search** | A/Z toggle sort and real-time full-text search across all categories |
| **Price sync** | Prices broadcast to all online clients after each trade |
| **Admin commands** | Balance management, live price overrides, and more via `/jecon` |

---

## Usage

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
| Create a listing | Click **New Listing** → pick item from inventory → set price, duration, type → Confirm |
| Bid on an auction | Click a listing → enter amount → **Place Bid** (minimum: current top bid + configurable %) |
| Buy It Now | Click a BIN listing → **Buy Now** once to arm, again within 3 s to confirm |
| Cancel your listing | Click your own listing → **Cancel** (listing fee is not refunded) |
| Claim winnings/items | Click **Claims** → **Claim All** |

> **Anti-snipe:** bids within the last 2 minutes extend the auction by 2 minutes (configurable via `antiSnipeExtensionMs`).  
> **Escrow:** all currency and items are held server-side — nothing is lost if the server crashes.  
> **Offline notifications:** missed outbid and auction-ended alerts are delivered as chat messages on your next login.

### Price History Graph

| Action | How |
|---|---|
| View price trend graph | Click the **↑** / **↓** / **—** trend arrow on any market item |
| Switch time views | Click **Day**, **Week**, or **Month** tabs at the top of the graph |
| Hover for exact price | Move mouse over any point on the line to see timestamp + exact price |

> **Day view:** 20-minute snapshots over the last 24 hours — see intraday price fluctuations  
> **Week view:** Hourly data for the last 7 days — perfect for spotting daily patterns  
> **Month view:** Daily averages for the last 30 days — long-term trend overview

---

## Configuration

All files are created in `config/` on first launch and are in simple JSON.

### `jakeseconomy-server.json`

| Key | Default | Description |
|---|---|---|
| `priceDecayEnabled` | `true` (SP) / `false` (MP) | Whether prices recover toward base over time |
| `priceDecayRatePercent` | `5.0` | % of deficit removed per decay tick |
| `priceDecayIntervalHours` | `1.0` | Real-world hours between decay ticks |
| `marketDepth` | `5000` | Items needed to meaningfully shift price. Raise for automation-heavy packs. |
| `sensitivity` | `2.3` | Steepness of price swings |
| `deficitLimitPerWindow` | `64` | Max items one player can trade per time window |
| `deficitWindowHours` | `1.0` | Hours before a player's buy window resets |
| `antiSnipeExtensionMs` | `120000` | ms to extend an auction when a late bid lands (0 = disabled) |
| `minBidIncrementPercent` | `1.0` | Minimum % a new bid must exceed the current top bid |
| `listingFeePercent` | `1.0` | Non-refundable listing fee as % of listing price (0 = no fee) |
| `maxListingPrice` | `1000000000000` | Hard cap on any single listing price |

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
- Changes take effect on the next server start, or immediately via `/jecon market setprice`

---

## Admin Commands

All commands require operator permission level 2.

```
/jecon balance                                           — check your own balance
/jecon balance <player>                                  — check another player's balance
/jecon give    <player> <amount>                         — add currency to a player
/jecon set     <player> <amount>                         — set a player's balance exactly
/jecon take    <player> <amount>                         — remove currency from a player
/jecon market setprice <item> <price> <category> [lockId]  — add or update an item's price; optional achievement lock
/jecon market removeprice <item>                           — remove an item from the market
/jecon market addcategory <name>                           — create an empty category tab
/jecon market setlock <item> <lockId>                      — change an item's achievement lock (0 = remove)
/jecon market price <item>                                 — check an item's current live price
```

### Debug Commands

```
/jecon debug fillhistory <item> [hours]  — inject synthetic price history for graph testing (default: 720 hours)
```

> Fills both 20-minute recent snapshots (last 24h) and hourly archive snapshots (up to 30 days) with sinusoidal test data centered on the item's current price. Useful for verifying the price graph display without waiting hours for real data to accumulate.

### Player shortcuts

```
/balance    — check your own balance (abbreviated + exact); only registered if no other mod claims this command
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

## For Mod Developers

Jake's Economy exposes a public API under `com.github.devjake123.jakeseconomy.api` so other mods can read/write player balances, query market state, and react to economy events.

> All API calls must be made from the **server tick thread**.  
> All write methods automatically sync the updated balance to the player's client if they are online.

### Balance access

```java
import com.github.devjake123.jakeseconomy.api.JakesEconomyApi;

// Read
long balance = JakesEconomyApi.getBalance(uuid, server);
boolean canAfford = JakesEconomyApi.hasEnoughBalance(uuid, 500L, server);

// Write — each fires BALANCE_CHANGED and syncs the client HUD automatically
JakesEconomyApi.deposit(uuid, 1000L, server);
boolean success = JakesEconomyApi.withdraw(uuid, 500L, server);
JakesEconomyApi.setBalance(uuid, 0L, server);

// Atomic player-to-player transfer — returns false if sender has insufficient funds
boolean ok = JakesEconomyApi.transferBalance(fromUUID, toUUID, 250L, server);

// Market queries
boolean tradeable = JakesEconomyApi.isMarketItem("minecraft:diamond", server);
double price = JakesEconomyApi.getMarketPrice("minecraft:diamond", server); // -1 if not listed

// Manual client HUD sync (the write methods above do this automatically)
JakesEconomyApi.syncBalance(uuid, server);
```

### Events

Register listeners in your mod's `onInitialize()`:

```java
import com.github.devjake123.jakeseconomy.api.EconomyApiEvents;

// Fired on every virtual-balance mutation (deposit, withdraw, set)
EconomyApiEvents.BALANCE_CHANGED.register(e ->
        LOGGER.info("{}: {} → {}", e.playerId(), e.oldBalance(), e.newBalance()));

// Fired after a market buy completes
EconomyApiEvents.MARKET_BUY.register(e ->
        LOGGER.info("{} bought {}x {} for {}", e.playerId(), e.quantity(), e.itemId(), e.totalCost()));

// Fired after a market sell completes
EconomyApiEvents.MARKET_SELL.register(e ->
        LOGGER.info("{} sold {}x {} for {}", e.playerId(), e.quantity(), e.itemId(), e.totalPayout()));

// Fired when an auction finalizes (winnerId is null if no bids)
EconomyApiEvents.AUCTION_FINALIZED.register(e -> {
    if (e.winnerId() != null) {
        LOGGER.info("Auction {} sold {} to {} for {}",
                e.auctionId(), e.itemId(), e.winnerId(), e.finalPrice());
    }
});

// Fired when a bid is accepted on an open auction
EconomyApiEvents.AUCTION_BID_PLACED.register(e ->
        LOGGER.info("{} bid {} on {} (anti-snipe: {})",
                e.bidderId(), e.bidAmount(), e.auctionId(), e.antiSniped()));

// Fired when a seller cancels their listing
EconomyApiEvents.AUCTION_CANCELLED.register(e ->
        LOGGER.info("Auction {} for {} cancelled by {}",
                e.auctionId(), e.itemId(), e.sellerId()));
```

All events carry typed record payloads — see `com.github.devjake123.jakeseconomy.api.event` for full field documentation.

---

## License

GNU Lesser General Public License Version 2.1 — see [LICENSE](LICENSE).
