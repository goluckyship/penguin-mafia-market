package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sends players to (and back from) the Frozen Realm - a custom dimension
 * added by the penguin_frozen_realm datapack, using the custom
 * "penguinmafia:frozen_wastes" biome everywhere. The dimension only exists
 * once that datapack is installed in world/datapacks and the server has
 * been restarted; until then this command just explains that.
 */
public class FrozenRealmCommand implements CommandExecutor {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");

    private final FrozenRealmStructures structures;

    public FrozenRealmCommand(FrozenRealmStructures structures) {
        this.structures = structures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can travel to the Frozen Realm.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("back")) {
            World overworld = Bukkit.getWorlds().get(0);
            player.teleport(overworld.getSpawnLocation());
            player.sendMessage(ChatColor.AQUA + "Back in the Overworld.");
            return true;
        }

        World frozenRealm = FROZEN_REALM_KEY == null ? null : Bukkit.getWorld(FROZEN_REALM_KEY);
        if (frozenRealm == null) {
            player.sendMessage(ChatColor.RED + "The Frozen Realm isn't loaded on this server yet.");
            player.sendMessage(ChatColor.GRAY + "(An op needs to install the penguin_frozen_realm datapack and restart.)");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("locate")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can use /frozenrealm locate.");
                return true;
            }
            return locate(player, frozenRealm);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("summon")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can use /frozenrealm summon.");
                return true;
            }
            return summon(player, frozenRealm);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("regenerate")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can use /frozenrealm regenerate.");
                return true;
            }
            int radius = 4;
            if (args.length > 1) {
                try {
                    radius = Math.max(1, Math.min(8, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
            return regenerate(player, frozenRealm, radius);
        }

        int surfaceY = frozenRealm.getHighestBlockYAt(0, 0);
        Location dest = new Location(frozenRealm, 0.5, surfaceY + 1, 0.5);
        player.teleport(dest);
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Welcome to the Frozen Realm."
                + ChatColor.RESET + ChatColor.GRAY + " Use /frozenrealm back to return.");
        return true;
    }

    /**
     * Op-only lookup: reports the nearest generated cabin and bridge to the
     * player's current position without teleporting or building anything -
     * just runs the same deterministic seed math the world generator uses.
     */
    private boolean locate(Player player, World frozenRealm) {
        if (!frozenRealm.getKey().equals(player.getWorld().getKey())) {
            player.sendMessage(ChatColor.RED + "You need to be in the Frozen Realm to locate anything there.");
            player.sendMessage(ChatColor.GRAY + "Use /frozenrealm to teleport in first.");
            return true;
        }

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;

        int[] cabin = FrozenRealmStructures.nearestCabin(frozenRealm, chunkX, chunkZ);
        int[] bridge = FrozenRealmStructures.nearestBridge(frozenRealm, chunkX, chunkZ);

        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Nearest Frozen Realm structures:");
        sendStructureLine(player, frozenRealm, "Cabin", cabin);
        sendStructureLine(player, frozenRealm, "Bridge", bridge);
        return true;
    }

    /**
     * Op-only: force-builds a cabin at the nearest cabin grid slot,
     * regardless of whether that chunk has already generated naturally
     * (which is why /frozenrealm locate alone might point at empty terrain
     * - the cabin only auto-builds on genuinely first-time chunk
     * generation). Teleports the op there afterwards.
     */
    private boolean summon(Player player, World frozenRealm) {
        if (!frozenRealm.getKey().equals(player.getWorld().getKey())) {
            player.sendMessage(ChatColor.RED + "You need to be in the Frozen Realm to summon anything there.");
            player.sendMessage(ChatColor.GRAY + "Use /frozenrealm to teleport in first.");
            return true;
        }

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;

        int[] cabin = FrozenRealmStructures.nearestCabin(frozenRealm, chunkX, chunkZ);
        if (cabin == null) {
            player.sendMessage(ChatColor.RED + "Couldn't find a cabin slot nearby - try again.");
            return true;
        }
        int blockX = (cabin[0] << 4) + 8;
        int blockZ = (cabin[1] << 4) + 8;

        // Force the chunk (and a little buffer around it) to actually
        // generate/load before we place blocks in it.
        frozenRealm.getChunkAt(cabin[0], cabin[1]).load(true);
        structures.forceBuildCabin(frozenRealm, blockX, blockZ);

        int[] bridge = FrozenRealmStructures.nearestBridge(frozenRealm, chunkX, chunkZ);
        if (bridge != null) {
            int bridgeX = (bridge[0] << 4) + 8;
            int bridgeZ = (bridge[1] << 4) + 8;
            frozenRealm.getChunkAt(bridge[0], bridge[1]).load(true);
            structures.forceBuildBridge(frozenRealm, bridgeX, bridgeZ);
        }

        int surfaceY = frozenRealm.getHighestBlockYAt(blockX, blockZ);
        player.teleport(new Location(frozenRealm, blockX + 0.5, surfaceY + 2, blockZ + 6.5));
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Summoned a cabin"
                + ChatColor.RESET + ChatColor.GRAY + " at " + blockX + ", " + surfaceY + ", " + blockZ
                + (bridge != null ? " (and a bridge nearby)." : "."));
        return true;
    }

    /**
     * The ceiling used by {@link #regenerate}: any block above this Y in
     * the affected columns gets cleared to air. Chosen well above normal
     * overworld-noise ground level (sea level 63) so it only strips
     * leftover amplified-generator floating islands/spikes, not normal
     * terrain.
     */
    private static final int FLATTEN_CEILING_Y = 110;

    /**
     * Op-only: strips floating terrain (leftover amplified-generator
     * islands/spikes, and any stacked-up test cabins) out of the chunks
     * around the player, down to {@link #FLATTEN_CEILING_Y}.
     *
     * This used to call {@code World#regenerateChunk}, but that throws
     * UnsupportedOperationException on this Paper/Minecraft version (it's
     * simply not implemented there - "Not supported in this Minecraft
     * version! This is not a bug."), so a datapack generator change (e.g.
     * away from amplified noise) can't be retroactively replayed onto
     * already-generated chunks. Manually clearing everything above a
     * fixed ceiling gets the same practical result (no more floating
     * islands, no more absurd peaks) without needing that API.
     *
     * This is destructive: anything built or dropped above the ceiling in
     * the affected chunks is gone.
     */
    private boolean regenerate(Player player, World frozenRealm, int radiusChunks) {
        if (!frozenRealm.getKey().equals(player.getWorld().getKey())) {
            player.sendMessage(ChatColor.RED + "You need to be in the Frozen Realm to regenerate it.");
            player.sendMessage(ChatColor.GRAY + "Use /frozenrealm to teleport in first.");
            return true;
        }

        int centerChunkX = player.getLocation().getBlockX() >> 4;
        int centerChunkZ = player.getLocation().getBlockZ() >> 4;

        player.sendMessage(ChatColor.AQUA + "Flattening a " + (radiusChunks * 2 + 1) + "x" + (radiusChunks * 2 + 1)
                + " chunk area around you" + ChatColor.GRAY + " - clearing anything above y=" + FLATTEN_CEILING_Y
                + ", including floating islands and anything built or dropped up there.");

        int columnsCleared = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                frozenRealm.getChunkAt(chunkX, chunkZ).load(true);
                int baseX = chunkX << 4;
                int baseZ = chunkZ << 4;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = baseX + x;
                        int worldZ = baseZ + z;
                        int highest = frozenRealm.getHighestBlockYAt(worldX, worldZ);
                        if (highest <= FLATTEN_CEILING_Y) {
                            continue;
                        }
                        for (int y = FLATTEN_CEILING_Y + 1; y <= highest; y++) {
                            frozenRealm.getBlockAt(worldX, y, worldZ).setType(Material.AIR, false);
                        }
                        columnsCleared++;
                    }
                }
            }
        }

        int surfaceY = frozenRealm.getHighestBlockYAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        player.teleport(new Location(frozenRealm, player.getLocation().getBlockX() + 0.5, surfaceY + 1,
                player.getLocation().getBlockZ() + 0.5));
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Done: " + ChatColor.RESET + ChatColor.GRAY
                + columnsCleared + " columns flattened.");
        return true;
    }

    private void sendStructureLine(Player player, World world, String label, int[] chunkCoords) {
        if (chunkCoords == null) {
            player.sendMessage(ChatColor.GRAY + label + ": none found nearby, try again after moving further out.");
            return;
        }
        int blockX = (chunkCoords[0] << 4) + 8;
        int blockZ = (chunkCoords[1] << 4) + 8;
        int surfaceY = world.getHighestBlockYAt(blockX, blockZ);
        double distance = player.getLocation().distance(new Location(world, blockX, surfaceY, blockZ));
        player.sendMessage(ChatColor.AQUA + label + ": " + ChatColor.WHITE + blockX + ", " + surfaceY + ", " + blockZ
                + ChatColor.GRAY + " (~" + Math.round(distance) + " blocks away)");
    }
}
