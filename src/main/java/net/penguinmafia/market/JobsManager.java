package net.penguinmafia.market;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which jobs each player has joined, how much XP they've earned in
 * each one, and pays them out through Economy whenever JobsListener awards
 * a job action. Backed by jobs.yml, loaded once at startup and cached in
 * memory from then on (same pattern as MarketPreferencesManager), with a
 * save after every change so progress survives a crash, not just a clean
 * shutdown.
 *
 * Players can hold up to MAX_CONCURRENT_JOBS jobs at once - enough to mix
 * and match (say, Miner + Hunter) without turning the whole server into a
 * five-job grind simultaneously. Leveling is a simple increasing XP curve:
 * each level needs a bit more XP than the last, and a player's level in a
 * job gives their payout from that job a small percentage bonus, so sticking
 * with one job over time is modestly better than spreading XP thin.
 */
public class JobsManager {

    /** How many jobs a single player can have joined at the same time. */
    public static final int MAX_CONCURRENT_JOBS = 2;

    /** Hard level cap - payout bonus stops growing past this, so a job never becomes a money printer. */
    public static final int MAX_LEVEL = 50;

    /** Each level needs this many more XP than the previous one needed, on top of a flat base. */
    private static final long XP_BASE = 100L;
    private static final long XP_PER_LEVEL_STEP = 35L;

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    /** uuid -> (job -> xp). Loaded once, mutated in place, written back out on every change. */
    private final Map<UUID, Map<Job, Long>> xpByPlayer = new HashMap<>();

    public JobsManager(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jobs.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        if (!config.contains("players")) return;
        for (String uuidKey : config.getConfigurationSection("players").getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<Job, Long> jobs = new EnumMap<>(Job.class);
            String base = "players." + uuidKey + ".jobs";
            if (config.contains(base)) {
                for (String jobKey : config.getConfigurationSection(base).getKeys(false)) {
                    Job job = Job.fromString(jobKey);
                    if (job == null) continue;
                    jobs.put(job, config.getLong(base + "." + jobKey + ".xp", 0L));
                }
            }
            xpByPlayer.put(id, jobs);
        }
    }

    public void save() {
        config.set("players", null);
        for (Map.Entry<UUID, Map<Job, Long>> entry : xpByPlayer.entrySet()) {
            String base = "players." + entry.getKey() + ".jobs";
            for (Map.Entry<Job, Long> jobEntry : entry.getValue().entrySet()) {
                config.set(base + "." + jobEntry.getKey().name() + ".xp", jobEntry.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save jobs.yml: " + e.getMessage());
        }
    }

    private Map<Job, Long> jobsOf(UUID id) {
        return xpByPlayer.computeIfAbsent(id, k -> new EnumMap<>(Job.class));
    }

    public boolean hasJoined(OfflinePlayer player, Job job) {
        return jobsOf(player.getUniqueId()).containsKey(job);
    }

    public java.util.Set<Job> getJoinedJobs(OfflinePlayer player) {
        return jobsOf(player.getUniqueId()).keySet();
    }

    public int getJoinedCount(OfflinePlayer player) {
        return jobsOf(player.getUniqueId()).size();
    }

    /**
     * Joins a job if there's room (under MAX_CONCURRENT_JOBS) and the player
     * hasn't already joined it. Returns false (no change made) if either
     * condition fails - the caller is responsible for telling the player why.
     */
    public boolean join(OfflinePlayer player, Job job) {
        Map<Job, Long> jobs = jobsOf(player.getUniqueId());
        if (jobs.containsKey(job) || jobs.size() >= MAX_CONCURRENT_JOBS) return false;
        jobs.put(job, 0L);
        save();
        return true;
    }

    /** Leaves a job, discarding its accumulated XP/level. Returns false if they hadn't joined it. */
    public boolean leave(OfflinePlayer player, Job job) {
        Map<Job, Long> jobs = jobsOf(player.getUniqueId());
        if (jobs.remove(job) == null) return false;
        save();
        return true;
    }

    public long getXp(OfflinePlayer player, Job job) {
        return jobsOf(player.getUniqueId()).getOrDefault(job, 0L);
    }

    /**
     * Awards XP and pays out coins for one job action in a single call -
     * JobsListener always wants both together, so this keeps the two from
     * ever drifting out of sync (e.g. XP saved but the payout lost to some
     * early return). No-ops entirely if the player hasn't joined this job.
     *
     * @return the coins actually paid (0 if the player hasn't joined the job)
     */
    public long awardAction(OfflinePlayer player, Job job, long baseXp, long baseCoins) {
        Map<Job, Long> jobs = jobsOf(player.getUniqueId());
        if (!jobs.containsKey(job)) return 0L;

        long currentXp = jobs.get(job);
        int levelBefore = levelForXp(currentXp);
        long newXp = currentXp + Math.max(0L, baseXp);
        jobs.put(job, newXp);
        save();

        long payout = Math.round(baseCoins * payoutMultiplier(levelForXp(newXp)));
        payout = Math.max(1L, payout);
        return payout;
    }

    /** Levels start at 1 and increase every time cumulative XP clears the next threshold, capped at MAX_LEVEL. */
    public int levelForXp(long xp) {
        int level = 1;
        long remaining = xp;
        while (level < MAX_LEVEL) {
            long needed = xpForNextLevel(level);
            if (remaining < needed) break;
            remaining -= needed;
            level++;
        }
        return level;
    }

    /** How much additional XP is needed to go from `level` to `level + 1`. */
    public long xpForNextLevel(int level) {
        return XP_BASE + (long) (level - 1) * XP_PER_LEVEL_STEP;
    }

    /** XP already earned towards the current level (i.e. past the last level-up threshold). */
    public long xpIntoCurrentLevel(long xp) {
        long remaining = xp;
        int level = 1;
        while (level < MAX_LEVEL) {
            long needed = xpForNextLevel(level);
            if (remaining < needed) return remaining;
            remaining -= needed;
            level++;
        }
        return remaining;
    }

    /** Flat +1% payout per level above 1, capped at +49% at MAX_LEVEL - a nice-to-have, never a huge multiplier. */
    private double payoutMultiplier(int level) {
        return 1.0 + (level - 1) * 0.01;
    }

    /**
     * Every player with any job XP at all, for /job top - sorted by total XP
     * across every job they've ever earned in, highest first. Fine to build
     * this fresh each call since /job top is an infrequent, player-initiated
     * lookup, not something called in a hot loop.
     */
    public java.util.List<Map.Entry<UUID, Long>> topByJob(Job job, int limit) {
        java.util.List<Map.Entry<UUID, Long>> all = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Map<Job, Long>> entry : xpByPlayer.entrySet()) {
            Long xp = entry.getValue().get(job);
            if (xp != null && xp > 0) {
                all.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), xp));
            }
        }
        all.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return all.subList(0, Math.min(limit, all.size()));
    }
}
