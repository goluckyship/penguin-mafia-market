package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the new /afk: an all-in-one AFK Farm toggle for ops. Turning it on
 * at a location does three things, forever, even while that op is fully
 * disconnected - the server's had connection hiccups that boot people
 * randomly, so this is built to keep working straight through that:
 *
 *  1. Force-loads the chunks around that spot, same as the old /afk, so
 *     farms, redstone, and any /gen generator there keep ticking with
 *     nobody nearby.
 *  2. Every second, vacuums up any dropped Frozen Coins in the area and
 *     credits them straight to that op's balance - no player entity or
 *     login required, so it works the same whether they're online,
 *     offline, or mid-disconnect.
 *  3. Anything else it picks up (any non-coin item, e.g. from a normal
 *     farm) is held in a per-player mailbox and handed to them - dropped at
 *     their feet if their inventory's full - the next time they join,
 *     along with a summary of everything their farm collected while they
 *     were away.
 */
public class AfkFarmManager implements Listener {

    private static final int MAX_RADIUS = 3; // 3 -> a 7x7 chunk force-load area

    private static class FarmZone {
        final String world;
        final int chunkX;
        final int chunkZ;
        final int radius;
        final double anchorX;
        final double anchorY;
        final double anchorZ;

        FarmZone(String world, int chunkX, int chunkZ, int radius, double anchorX, double anchorY, double anchorZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.radius = radius;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
        }
    }

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File zonesFile;
    private final YamlConfiguration zonesConfig;
    private final File mailFile;
    private final YamlConfiguration mailConfig;

    private final Map<UUID, FarmZone> zonesByPlayer = new HashMap<>();
    private final Map<String, Map<Long, Integer>> refCounts = new HashMap<>();
    private final Map<UUID, Long> coinsSinceLastSeen = new HashMap<>();
    private final Map<UUID, List<ItemStack>> mailbox = new HashMap<>();

    public AfkFarmManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.zonesFile = new File(plugin.getDataFolder(), "afk_farm_zones.yml");
        this.zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
        this.mailFile = new File(plugin.getDataFolder(), "afk_farm_mailbox.yml");
        this.mailConfig = YamlConfiguration.loadConfiguration(mailFile);
        loadZones();
        loadMailbox();
        startTicking();
    }

    private void loadZones() {
        if (!zonesConfig.contains("zones")) return;
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            try {
                UUID id = UUID.fromString(key);
                String world = zonesConfig.getString(path + ".world");
                int cx = zonesConfig.getInt(path + ".chunkX");
                int cz = zonesConfig.getInt(path + ".chunkZ");
                int radius = zonesConfig.getInt(path + ".radius");
                double ax = zonesConfig.getDouble(path + ".anchorX");
                double ay = zonesConfig.getDouble(path + ".anchorY");
                double az = zonesConfig.getDouble(path + ".anchorZ");
                FarmZone zone = new FarmZone(world, cx, cz, radius, ax, ay, az);
                zonesByPlayer.put(id, zone);
                apply(zone);
            } catch (IllegalArgumentException ignored) {
                // malformed entry from hand-editing the file; skip it
            }
        }
        plugin.getLogger().info("Restored " + zonesByPlayer.size() + " AFK Farm zone(s).");
    }

    private void loadMailbox() {
        ConfigurationSection section = mailConfig.getConfigurationSection("mailbox");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<?> raw = mailConfig.getList("mailbox." + key);
                    List<ItemStack> items = new ArrayList<>();
                    if (raw != null) {
                        for (Object o : raw) {
                            if (o instanceof ItemStack) items.add((ItemStack) o);
                        }
                    }
                    if (!items.isEmpty()) mailbox.put(id, items);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection coinSection = mailConfig.getConfigurationSection("coins-since-last-seen");
        if (coinSection != null) {
            for (String key : coinSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    coinsSinceLastSeen.put(id, mailConfig.getLong("coins-since-last-seen." + key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void startTicking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, FarmZone> entry : zonesByPlayer.entrySet()) {
                    collect(entry.getKey(), entry.getValue());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void collect(UUID ownerId, FarmZone zone) {
        World world = Bukkit.getWorld(zone.world);
        if (world == null) return;
        Location anchor = new Location(world, zone.anchorX, zone.anchorY, zone.anchorZ);
        double reach = (zone.radius + 1) * 16.0;

        long coinsFound = 0;
        List<ItemStack> otherFound = new ArrayList<>();

        for (Entity entity : world.getNearbyEntities(anchor, reach, reach, reach)) {
            if (!(entity instanceof Item)) continue;
            Item itemEntity = (Item) entity;
            ItemStack stack = itemEntity.getItemStack();
            if (economy.isCoinItem(stack)) {
                coinsFound += stack.getAmount();
            } else {
                otherFound.add(stack.clone());
            }
            itemEntity.remove();
        }

        if (coinsFound <= 0 && otherFound.isEmpty()) return;

        if (coinsFound > 0) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
            economy.addBalance(owner, coinsFound);

            Player online = Bukkit.getPlayer(ownerId);
            if (online != null) {
                online.sendMessage(ChatColor.AQUA + "[AFK Farm] " + ChatColor.GRAY + "+" + coinsFound
                        + " Frozen Coins. Balance: " + ChatColor.AQUA + economy.getBalance(owner));
            } else {
                coinsSinceLastSeen.merge(ownerId, coinsFound, Long::sum);
            }
        }

        if (!otherFound.isEmpty()) {
            mailbox.computeIfAbsent(ownerId, k -> new ArrayList<>()).addAll(otherFound);
        }

        saveMailbox();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        List<ItemStack> items = mailbox.remove(id);
        Long removedCoins = coinsSinceLastSeen.remove(id);
        long coins = removedCoins == null ? 0L : removedCoins;

        boolean gotItems = items != null && !items.isEmpty();
        if (!gotItems && coins <= 0) return;

        if (gotItems) {
            for (ItemStack stack : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), extra);
                }
            }
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.AQUA).append(ChatColor.BOLD).append("[AFK Farm] ")
                .append(ChatColor.RESET).append(ChatColor.GRAY).append("While you were away: ");
        if (coins > 0) message.append("+").append(coins).append(" Frozen Coins");
        if (coins > 0 && gotItems) message.append(", ");
        if (gotItems) message.append(items.size()).append(" item stack(s) added to your inventory (dropped at your feet if it was full)");
        message.append(".");
        player.sendMessage(message.toString());

        saveMailbox();
    }

    public boolean isActive(Player player) {
        return zonesByPlayer.containsKey(player.getUniqueId());
    }

    /** Starts a new AFK Farm zone for this player at their current location and fires it once right away. */
    public int start(Player player, int radius) {
        int clamped = Math.max(0, Math.min(MAX_RADIUS, radius));
        Location loc = player.getLocation();
        Chunk chunk = loc.getChunk();
        FarmZone zone = new FarmZone(loc.getWorld().getName(), chunk.getX(), chunk.getZ(), clamped,
                loc.getX(), loc.getY(), loc.getZ());
        zonesByPlayer.put(player.getUniqueId(), zone);
        apply(zone);
        saveZones();
        collect(player.getUniqueId(), zone);
        return clamped;
    }

    /** Stops this player's AFK Farm, wherever it is - no need to be standing at it. */
    public void stop(Player player) {
        FarmZone zone = zonesByPlayer.remove(player.getUniqueId());
        if (zone != null) {
            unapply(zone);
            saveZones();
        }
    }

    private void apply(FarmZone zone) {
        World world = Bukkit.getWorld(zone.world);
        if (world == null) return;
        Map<Long, Integer> counts = refCounts.computeIfAbsent(zone.world, w -> new HashMap<>());
        for (int dx = -zone.radius; dx <= zone.radius; dx++) {
            for (int dz = -zone.radius; dz <= zone.radius; dz++) {
                int x = zone.chunkX + dx;
                int z = zone.chunkZ + dz;
                long key = chunkKey(x, z);
                int newCount = counts.merge(key, 1, Integer::sum);
                if (newCount == 1) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
        }
    }

    private void unapply(FarmZone zone) {
        World world = Bukkit.getWorld(zone.world);
        Map<Long, Integer> counts = refCounts.get(zone.world);
        for (int dx = -zone.radius; dx <= zone.radius; dx++) {
            for (int dz = -zone.radius; dz <= zone.radius; dz++) {
                int x = zone.chunkX + dx;
                int z = zone.chunkZ + dz;
                long key = chunkKey(x, z);
                if (counts == null) continue;
                Integer remaining = counts.computeIfPresent(key, (k, v) -> v - 1 <= 0 ? null : v - 1);
                if (remaining == null && world != null) {
                    world.setChunkForceLoaded(x, z, false);
                }
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private void saveZones() {
        zonesConfig.set("zones", null);
        for (Map.Entry<UUID, FarmZone> entry : zonesByPlayer.entrySet()) {
            String path = "zones." + entry.getKey();
            FarmZone zone = entry.getValue();
            zonesConfig.set(path + ".world", zone.world);
            zonesConfig.set(path + ".chunkX", zone.chunkX);
            zonesConfig.set(path + ".chunkZ", zone.chunkZ);
            zonesConfig.set(path + ".radius", zone.radius);
            zonesConfig.set(path + ".anchorX", zone.anchorX);
            zonesConfig.set(path + ".anchorY", zone.anchorY);
            zonesConfig.set(path + ".anchorZ", zone.anchorZ);
        }
        try {
            zonesConfig.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save afk_farm_zones.yml: " + e.getMessage());
        }
    }

    private void saveMailbox() {
        mailConfig.set("mailbox", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : mailbox.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                mailConfig.set("mailbox." + entry.getKey(), entry.getValue());
            }
        }
        mailConfig.set("coins-since-last-seen", null);
        for (Map.Entry<UUID, Long> entry : coinsSinceLastSeen.entrySet()) {
            if (entry.getValue() > 0) {
                mailConfig.set("coins-since-last-seen." + entry.getKey(), entry.getValue());
            }
        }
        try {
            mailConfig.save(mailFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save afk_farm_mailbox.yml: " + e.getMessage());
        }
    }
}
