# Penguin Mafia Server

Everything for the Penguin Mafia custom Minecraft server lives in this repo:
a data pack (custom biomes, mobs, structures, loot) and a Paper plugin
(the `/bm` Black Market command + GUI). They install to different places
on the server, so keep them separate when you deploy.

## What's in here

- **`datapack/penguin_mafia/`** — a vanilla data pack. Goes in your
  server's `world/datapacks/` folder.
- **`src/`, `pom.xml`, `plugin.yml`** — the Black Market plugin's Java
  source. Compiled automatically by GitHub Actions on every push (see
  below) into **`dist/PenguinMafiaMarket.jar`**, which goes in your
  server's `plugins/` folder.
- **`.github/workflows/build.yml`** — the auto-build. Every push to
  `main` compiles the plugin fresh and commits the resulting jar to
  `dist/PenguinMafiaMarket.jar`, so that file is always the current build
  — just pull the repo (or download that one file) to get the latest jar.

## Installing on your server (exaroton or any Paper server)

1. Copy `datapack/penguin_mafia/` (the whole folder) into `world/datapacks/`
   on your server.
2. Copy `dist/PenguinMafiaMarket.jar` into `plugins/` on your server.
3. Restart the server (a new plugin needs a real restart, not just
   `/reload`).

## Data pack — what it adds

- `mafia_tundra` and `mafia_peaks` custom biomes (colors, fog, particles,
  fox/wolf/goat/polar bear spawns)
- Chickens that wander into those biomes turn into glowing "Mafia
  Penguin" mobs
- Hideout Cabins (rare structure): gold-under-snow floors, loot, a
  Black Market Fence villager inside
- Fancy Mountain Bridges in the peaks
- The Don (rare boss) and Rival Gang Enforcers
- Blizzards over the peaks (cabins shelter you from them)
- Taming penguins with bread
- Frozen Coin currency drops
- A "Penguin Mafia" advancement tab

The biomes are fully wired into overworld generation: `mafia_tundra`
replaces every `snowy_plains` slot and `mafia_peaks` replaces every
`jagged_peaks` slot in the vanilla noise-parameter table (pulled from
Paper build 26.2-129's own data generator), so they'll generate in any
newly-explored chunk automatically — no further setup needed. Existing
chunks are never touched (Minecraft only ever generates a chunk's biome
once, the first time it's created).

## Plugin — what it adds

- `/bm` (aliases `/blackmarket`, `/market`) — opens a chest-style GUI
  listing every player's items for sale, who's selling, and the price.
  Click to buy instantly.
- `/bm sell <price>` — lists whatever's in your hand.
- `/bm list` / `/bm cancel <id>` — manage your own listings.
- `/bm balance` / `/bm deposit` / `/bm withdraw <amount>` — Frozen Coin
  balance, convertible to/from the physical item so loot from the data
  pack side (cabins, The Don, Rival Enforcers) feeds straight into the
  market.

Listings and balances persist across restarts (`listings.yml` and
`economy.yml` in the plugin's data folder on the server).

## How the auto-build works

Paper's plugin library isn't reachable from every environment, so
compiling happens on GitHub's own build runners instead, which always
have full internet access. Every push to `main` triggers
`.github/workflows/build.yml`, which:
1. Builds the plugin with Maven (JDK 25, matching Paper's current
   requirement)
2. On success, commits the fresh jar to `dist/PenguinMafiaMarket.jar`
3. On failure, opens a GitHub Issue in this repo with the compile error

So `dist/PenguinMafiaMarket.jar` is always the latest working build —
no manual compiling needed for future changes, just push and pull.

## Known limitations / next steps

- No admin command yet to wipe a stuck listing or refund someone.
- No listing expiration — items stay listed until bought or cancelled.
- No search/filter in the GUI past the first couple of pages (currently
  paginated 45-per-page with Next/Previous arrows).
