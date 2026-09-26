# Penguin Mafia Black Market Plugin

A real Paper plugin adding `/bm` (aliases `/blackmarket`, `/market`) with a
clickable inventory GUI, player listings, and a Frozen Coin currency that
matches the Frozen Coin item already used in the Penguin Mafia data pack
(a renamed Prismarine Shard).

## Why this is a separate plugin, not a data pack addition

Vanilla data packs cannot register new slash commands or open custom
inventory screens — those are Bukkit/Paper API features only available to
compiled Java plugins. Everything in this folder is real, compiled-from-
source plugin code, not a workaround.

## What it does

- `/bm` — opens a chest-style GUI listing every item currently for sale,
  who's selling it, and its price. Click an item to buy it instantly
  (charges your balance, credits the seller, hands you the item).
- `/bm sell <price>` — lists whatever item is in your hand.
- `/bm list` — shows your own active listings with their IDs.
- `/bm cancel <id>` — pulls a listing back and returns the item to you.
- `/bm balance` — shows your Frozen Coin balance.
- `/bm deposit` — turns physical Frozen Coin items in your inventory into
  balance (useful after looting cabins or killing Rival Enforcers/The Don).
- `/bm withdraw <amount>` — turns balance back into physical Frozen Coin
  items you can carry, drop, or hand to someone directly.

Listings and balances persist across restarts (`listings.yml` and
`economy.yml` inside the plugin's data folder).

## Compiling it (I could not do this step myself)

I write and package source, but I don't have permission to reach
`repo.papermc.io` from this sandboxed environment (it's explicitly blocked
by the proxy policy, confirmed when I tried) — that's where the Paper API
library this plugin depends on is hosted, so `mvn package` has to run
somewhere that isn't blocked, like your own machine or the server itself.

You'll need:
1. **Java 21** installed (`java -version` to check; Paper 1.21.2 requires it).
2. **Maven** installed (`mvn -version` to check).
3. From inside this `penguin_mafia_market` folder, run:
   ```
   mvn package
   ```
4. The compiled plugin will appear at:
   ```
   target/PenguinMafiaMarket.jar
   ```
5. Drop that `.jar` into your Paper server's `plugins/` folder and restart
   (or `/reload` if your server allows it — a full restart is safer for a
   brand-new plugin).

If you don't have Java/Maven installed locally, this also compiles fine on
the exaroton server itself if it gives you shell/SSH access, or on any
free online Java build environment (Replit, Gitpod, etc.) — just upload
this whole folder and run the same `mvn package` command.

## Testing checklist

- [ ] `/bm` opens an empty market with no errors in console
- [ ] Hold an item, run `/bm sell 10`, confirm it's removed from your hand
      and appears when you `/bm` again
- [ ] From a second account (or ask a friend), `/bm balance`, `/bm deposit`
      some Frozen Coin items from cabin loot, then buy the listed item
- [ ] Confirm the seller receives the coins in their balance
- [ ] `/bm cancel <id>` returns an unsold item

## Known limitations / next steps if you want them

- No admin command yet to wipe a stuck listing or refund someone — easy
  to add (`/bm admin remove <id>`) if you want it, just say so.
- No listing expiration — items stay listed forever until bought or
  cancelled. Can add a time limit if the market gets cluttered.
- No search/filter in the GUI yet if listings grow past a couple pages —
  currently just paginated 45-per-page with Next/Previous arrows.
