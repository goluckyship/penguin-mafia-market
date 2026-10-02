package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Light-touch staff tooling for the handful of moderation actions a small
 * survival server actually needs day to day: temporarily freezing someone in
 * place (for an AFK-check or while sorting out a dispute), muting someone's
 * chat for a set time, and keeping a permanent warning history so a repeat
 * problem is visible to every op, not just whoever handled it last time.
 *
 * Deliberately simple and file-backed, same as the rest of this plugin's
 * managers - no ban/kick wrapping (Bukkit/the server software already do
 * that fine on their own), just the things vanilla ops have no built-in
 * command for. Everything persists to moderation.yml so freezes/mutes
 * survive a restart and warning history is never lost.
 */
public class ModerationManager {

    public static class Warning {
        public final String issuerName;
        public final String reason;
        public final long timestampMillis;

        public Warning(String issuerName, String reason, long timestampMillis) {
            this.issuerName = issuerName;
            this.reason = reason;
            this.timestampMillis = timestampMillis;
        }
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneOffset.UTC);

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    /** Players currently frozen in place - cleared on unfreeze, never time-limited (an op lifts it explicitly). */
    private final Set<UUID> frozen = new HashSet<>();

    /** uuid -> mute expiry (epoch millis). A player present here with an expiry in the past is simply treated as unmuted. */
    private final Map<UUID, Long> muteExpiry = new HashMap<>();

    /** uuid -> every warning ever issued to them, oldest first. */
    private final Map<UUID, List<Warning>> warnings = new HashMap<>();

    /** uuid -> ban expiry (epoch millis). Like mutes, an expiry in the past just means no longer banned. */
    private final Map<UUID, Long> banExpiry = new HashMap<>();
    /** uuid -> the reason given for their current/most recent tempban. */
    private final Map<UUID, String> banReason = new HashMap<>();

    public ModerationManager(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (String uuidKey : config.getStringList("frozen")) {
            try {
                frozen.add(UUID.fromString(uuidKey));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }

        if (config.contains("mutes")) {
            for (String uuidKey : config.getConfigurationSection("mutes").getKeys(false)) {
                try {
                    muteExpiry.put(UUID.fromString(uuidKey), config.getLong("mutes." + uuidKey));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed entry
                }
            }
        }

        if (config.contains("warnings")) {
            ConfigurationSection section = config.getConfigurationSection("warnings");
            for (String uuidKey : section.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(uuidKey);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                List<Warning> list = new ArrayList<>();
                List<Map<?, ?>> raw = section.getMapList(uuidKey);
                for (Map<?, ?> entry : raw) {
                    String issuer = String.valueOf(entry.get("issuer"));
                    String reason = String.valueOf(entry.get("reason"));
                    long timestamp = entry.get("time") instanceof Number ? ((Number) entry.get("time")).longValue() : 0L;
                    list.add(new Warning(issuer, reason, timestamp));
                }
                warnings.put(id, list);
            }
        }

        if (config.contains("bans")) {
            for (String uuidKey : config.getConfigurationSection("bans").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuidKey);
                    banExpiry.put(id, config.getLong("bans." + uuidKey + ".expiry"));
                    banReason.put(id, config.getString("bans." + uuidKey + ".reason", "No reason given"));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed entry
                }
            }
        }
    }

    public void save() {
        List<String> frozenList = new ArrayList<>();
        for (UUID id : frozen) frozenList.add(id.toString());
        config.set("frozen", frozenList);

        config.set("mutes", null);
        for (Map.Entry<UUID, Long> entry : muteExpiry.entrySet()) {
            config.set("mutes." + entry.getKey(), entry.getValue());
        }

        config.set("warnings", null);
        for (Map.Entry<UUID, List<Warning>> entry : warnings.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Warning warning : entry.getValue()) {
                Map<String, Object> row = new HashMap<>();
                row.put("issuer", warning.issuerName);
                row.put("reason", warning.reason);
                row.put("time", warning.timestampMillis);
                serialized.add(row);
            }
            config.set("warnings." + entry.getKey(), serialized);
        }

        config.set("bans", null);
        for (Map.Entry<UUID, Long> entry : banExpiry.entrySet()) {
            String base = "bans." + entry.getKey();
            config.set(base + ".expiry", entry.getValue());
            config.set(base + ".reason", banReason.getOrDefault(entry.getKey(), "No reason given"));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save moderation.yml: " + e.getMessage());
        }
    }

    // ---- Freeze ----

    public boolean isFrozen(UUID playerId) {
        return frozen.contains(playerId);
    }

    /** Returns false if the player was already frozen (no change made). */
    public boolean freeze(UUID playerId) {
        boolean changed = frozen.add(playerId);
        if (changed) save();
        return changed;
    }

    /** Returns false if the player wasn't frozen to begin with. */
    public boolean unfreeze(UUID playerId) {
        boolean changed = frozen.remove(playerId);
        if (changed) save();
        return changed;
    }

    public List<UUID> getFrozenPlayers() {
        return new ArrayList<>(frozen);
    }

    /** Every player with a currently-active mute, for /mod list's fuller staff overview. */
    public List<UUID> getMutedPlayers() {
        List<UUID> active = new ArrayList<>();
        for (UUID id : muteExpiry.keySet()) {
            if (isMuted(id)) active.add(id);
        }
        return active;
    }

    /** Every player with a currently-active tempban, for /mod list's fuller staff overview. */
    public List<UUID> getBannedPlayers() {
        List<UUID> active = new ArrayList<>();
        for (UUID id : banExpiry.keySet()) {
            if (isBanned(id)) active.add(id);
        }
        return active;
    }

    // ---- Mute ----

    public boolean isMuted(UUID playerId) {
        Long expiry = muteExpiry.get(playerId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** How much longer a mute has, formatted like "14m" / "1h 5m" - empty string if not currently muted. */
    public String muteTimeRemaining(UUID playerId) {
        Long expiry = muteExpiry.get(playerId);
        if (expiry == null) return "";
        long remainingMillis = expiry - System.currentTimeMillis();
        if (remainingMillis <= 0) return "";
        long minutes = remainingMillis / 60000L;
        long hours = minutes / 60;
        minutes %= 60;
        return hours > 0 ? (hours + "h " + minutes + "m") : (minutes + "m");
    }

    public void mute(UUID playerId, long minutes) {
        muteExpiry.put(playerId, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes));
        save();
    }

    /** Returns false if the player wasn't muted (or their mute had already expired). */
    public boolean unmute(UUID playerId) {
        boolean wasMuted = isMuted(playerId);
        muteExpiry.remove(playerId);
        save();
        return wasMuted;
    }

    // ---- Warnings ----

    public void warn(OfflinePlayer target, String issuerName, String reason) {
        warnings.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>())
                .add(new Warning(issuerName, reason, System.currentTimeMillis()));
        save();
    }

    public List<Warning> getWarnings(UUID playerId) {
        return warnings.getOrDefault(playerId, new ArrayList<>());
    }

    public int getWarningCount(UUID playerId) {
        return getWarnings(playerId).size();
    }

    /**
     * Removes one warning by its 1-based position in /mod history's listing
     * (oldest first, same order getWarnings() returns) - for correcting a
     * warning that was issued by mistake. Returns false if the index is out
     * of range.
     */
    public boolean removeWarning(UUID playerId, int oneBasedIndex) {
        List<Warning> list = warnings.get(playerId);
        if (list == null || oneBasedIndex < 1 || oneBasedIndex > list.size()) return false;
        list.remove(oneBasedIndex - 1);
        save();
        return true;
    }

    // ---- Tempban ----

    /**
     * A lightweight, plugin-level tempban that works alongside (not instead
     * of) Bukkit's own ban list - useful when an op wants a ban that's
     * automatically lifted after a set time with no need to remember to run
     * /pardon later, and that shows the player exactly how long is left.
     */
    public boolean isBanned(UUID playerId) {
        Long expiry = banExpiry.get(playerId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public void tempban(UUID playerId, long minutes, String reason) {
        banExpiry.put(playerId, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes));
        banReason.put(playerId, reason == null || reason.isBlank() ? "No reason given" : reason);
        save();
    }

    /** Returns false if the player wasn't currently banned. */
    public boolean unban(UUID playerId) {
        boolean wasBanned = isBanned(playerId);
        banExpiry.remove(playerId);
        save();
        return wasBanned;
    }

    public String getBanReason(UUID playerId) {
        return banReason.getOrDefault(playerId, "No reason given");
    }

    /** Formatted like muteTimeRemaining() - "1h 5m" / "14m" - empty string if not currently banned. */
    public String banTimeRemaining(UUID playerId) {
        Long expiry = banExpiry.get(playerId);
        if (expiry == null) return "";
        long remainingMillis = expiry - System.currentTimeMillis();
        if (remainingMillis <= 0) return "";
        long minutes = remainingMillis / 60000L;
        long hours = minutes / 60;
        minutes %= 60;
        return hours > 0 ? (hours + "h " + minutes + "m") : (minutes + "m");
    }

    public static String formatTimestamp(long millis) {
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    /** Every player this server has ever frozen, muted, or warned - the roster /mod list draws from for "anyone with history" lookups. */
    public Set<UUID> getAllKnownPlayers() {
        Set<UUID> all = new HashSet<>();
        all.addAll(frozen);
        all.addAll(muteExpiry.keySet());
        all.addAll(warnings.keySet());
        return all;
    }

    public static String nameOf(UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() != null ? player.getName() : id.toString();
    }
}
