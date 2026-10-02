package net.penguinmafia.market;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A simple /daily login bonus with a streak multiplier - claim once every
 * real-world day for a payout that grows the longer the streak runs, reset
 * back to day one if a day is missed entirely. A small, no-grind reward for
 * just showing up, on top of everything else this plugin pays for actually
 * playing (Jobs, the Black Market, Auction House, bounties).
 */
public class DailyRewardManager {

    /** Coins on day one of a streak. */
    private static final long BASE_REWARD = 50L;
    /** Extra coins added per additional consecutive day, up to the cap below. */
    private static final long PER_DAY_BONUS = 10L;
    /** The streak bonus stops growing past this many days, so it's never a runaway number. */
    private static final int MAX_BONUS_DAYS = 30;

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    /** uuid -> epoch day of their last successful claim. */
    private final Map<UUID, Long> lastClaimDay = new HashMap<>();
    /** uuid -> current consecutive-day streak. */
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public DailyRewardManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "daily_rewards.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        if (!config.contains("players")) return;
        for (String uuidKey : config.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuidKey);
                lastClaimDay.put(id, config.getLong("players." + uuidKey + ".lastClaimDay"));
                streaks.put(id, config.getInt("players." + uuidKey + ".streak", 1));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }

    public void save() {
        config.set("players", null);
        for (UUID id : lastClaimDay.keySet()) {
            String base = "players." + id;
            config.set(base + ".lastClaimDay", lastClaimDay.get(id));
            config.set(base + ".streak", streaks.getOrDefault(id, 1));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save daily_rewards.yml: " + e.getMessage());
        }
    }

    private static long currentEpochDay() {
        return Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond() / 86400L;
    }

    public boolean canClaim(UUID playerId) {
        Long last = lastClaimDay.get(playerId);
        return last == null || last < currentEpochDay();
    }

    public int getStreak(UUID playerId) {
        return streaks.getOrDefault(playerId, 0);
    }

    /** How many whole days until the player could claim again - 0 if they can claim right now. */
    public long hoursUntilNextClaim(UUID playerId) {
        if (canClaim(playerId)) return 0L;
        long nextDayStartSeconds = (currentEpochDay() + 1) * 86400L;
        long remainingSeconds = nextDayStartSeconds - Instant.now().getEpochSecond();
        return Math.max(0L, remainingSeconds / 3600L);
    }

    public static class ClaimResult {
        public final long payout;
        public final int streak;

        public ClaimResult(long payout, int streak) {
            this.payout = payout;
            this.streak = streak;
        }
    }

    /** The longest-running current streaks on the server, highest first, for /daily top. */
    public java.util.List<java.util.Map.Entry<UUID, Integer>> getTopStreaks(int limit) {
        java.util.List<java.util.Map.Entry<UUID, Integer>> all = new java.util.ArrayList<>(streaks.entrySet());
        all.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** Returns null if the player already claimed today. */
    public ClaimResult claim(OfflinePlayer player) {
        UUID id = player.getUniqueId();
        if (!canClaim(id)) return null;

        long today = currentEpochDay();
        Long last = lastClaimDay.get(id);
        int streak = (last != null && last == today - 1) ? streaks.getOrDefault(id, 1) + 1 : 1;

        lastClaimDay.put(id, today);
        streaks.put(id, streak);
        save();

        long bonusDays = Math.min(streak - 1, MAX_BONUS_DAYS);
        long payout = BASE_REWARD + bonusDays * PER_DAY_BONUS;
        economy.addBalance(player, payout);

        return new ClaimResult(payout, streak);
    }
}
