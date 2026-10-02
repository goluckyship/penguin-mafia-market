package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-funded bounties: anyone can put coins on another player's head, any
 * number of players can contribute to the same bounty (it just stacks), and
 * whoever lands the killing PvP blow collects the whole pot. A straightforward
 * extra PvP/economy sink that needs no new UI - just /bounty and a single
 * death-event hook.
 *
 * Persisted to bounties.yml. Each target's bounty is a running total plus the
 * list of who contributed what, so /bounty check can show the breakdown
 * instead of just one anonymous number.
 */
public class BountyManager implements Listener {

    public static class Contribution {
        public final String placerName;
        public final long amount;

        public Contribution(String placerName, long amount) {
            this.placerName = placerName;
            this.amount = amount;
        }
    }

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    /** target uuid -> total coins currently on their head. */
    private final Map<UUID, Long> totals = new HashMap<>();
    /** target uuid -> who put coins up and how much, oldest first. */
    private final Map<UUID, List<Contribution>> contributions = new HashMap<>();

    public BountyManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        if (!config.contains("bounties")) return;
        for (String uuidKey : config.getConfigurationSection("bounties").getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String base = "bounties." + uuidKey;
            totals.put(id, config.getLong(base + ".total", 0L));

            List<Contribution> list = new ArrayList<>();
            for (Map<?, ?> row : config.getMapList(base + ".contributions")) {
                String name = String.valueOf(row.get("placer"));
                long amount = row.get("amount") instanceof Number ? ((Number) row.get("amount")).longValue() : 0L;
                list.add(new Contribution(name, amount));
            }
            contributions.put(id, list);
        }
    }

    public void save() {
        config.set("bounties", null);
        for (Map.Entry<UUID, Long> entry : totals.entrySet()) {
            if (entry.getValue() <= 0) continue;
            String base = "bounties." + entry.getKey();
            config.set(base + ".total", entry.getValue());

            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Contribution c : contributions.getOrDefault(entry.getKey(), List.of())) {
                Map<String, Object> row = new HashMap<>();
                row.put("placer", c.placerName);
                row.put("amount", c.amount);
                serialized.add(row);
            }
            config.set(base + ".contributions", serialized);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save bounties.yml: " + e.getMessage());
        }
    }

    public long getBounty(UUID targetId) {
        return totals.getOrDefault(targetId, 0L);
    }

    public List<Contribution> getContributions(UUID targetId) {
        return contributions.getOrDefault(targetId, new ArrayList<>());
    }

    /** Every player with an active bounty, highest pot first. */
    public List<Map.Entry<UUID, Long>> getTopBounties(int limit) {
        List<Map.Entry<UUID, Long>> all = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : totals.entrySet()) {
            if (entry.getValue() > 0) all.add(entry);
        }
        all.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public enum PlaceResult { OK, SELF, TOO_LOW, CANT_AFFORD }

    public PlaceResult place(Player placer, OfflinePlayer target, long amount) {
        if (placer.getUniqueId().equals(target.getUniqueId())) return PlaceResult.SELF;
        if (amount <= 0) return PlaceResult.TOO_LOW;
        if (!economy.removeBalance(placer, amount)) return PlaceResult.CANT_AFFORD;

        totals.merge(target.getUniqueId(), amount, Long::sum);
        contributions.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>())
                .add(new Contribution(placer.getName(), amount));
        save();
        return PlaceResult.OK;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        long bounty = getBounty(victim.getUniqueId());
        if (bounty <= 0) return;

        totals.remove(victim.getUniqueId());
        contributions.remove(victim.getUniqueId());
        save();

        economy.addBalance(killer, bounty);
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[Bounty] " + ChatColor.GRAY + killer.getName()
                + " collected " + ChatColor.AQUA + CoinFormat.format(bounty) + ChatColor.GRAY
                + " Frozen Coins for taking out " + victim.getName() + "'s bounty!");
    }
}
