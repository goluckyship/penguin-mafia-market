# Penguin Mafia Server

Everything for the Penguin Mafia custom Minecraft server — custom biomes,
mobs, structures, loot, AND the `/bm` Black Market command + GUI — ships as
**one single file: `dist/PenguinMafiaMarket.jar`**. The data pack is bundled
inside the plugin jar, and the plugin installs it into `world/datapacks/`
for you automatically the first time it runs.

## What's in here

- **`dist/PenguinMafiaMarket.jar`** — the only file you need to install.
  Contains the compiled plugin AND the full data pack. Rebuilt
  automatically by GitHub Actions on every push (see below), so this file
  is always the current build.
- **`src/`, `pom.xml`, `plugin.yml`** — the plugin's Java source.
- **`datapack/penguin_mafia/`** — the data pack source (also bundled into
  the jar at build time — kept here too so it's easy to read/edit).
- **`.github/workflows/build.yml`** — the auto-build.

## Installing on your server (exaroton or any Paper server)

1. Copy `dist/PenguinMafiaMarket.jar` into `plugins/` on your server.
   That's it — nothing else to upload.
2. Start the server (a new plugin needs a real restart, not just
   `/reload`).
3. On this first boot, the plugin drops the bundled data pack into
   `world/datapacks/penguin_mafia/` and logs a warning that **one more
   restart** is needed — this is a hard Minecraft limitation (new biomes
   and structures are only loaded when the world starts up, so they can't
   take effect on the same boot that just installed them). Any op who
   joins during this state also gets a chat reminder.
4. Restart the server one more time. After that, everything — biomes,
   mobs, structures, `/bm` — is fully active, and future updates (just
   replacing the jar) won't need the extra restart again unless the data
   pack itself changes.

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
