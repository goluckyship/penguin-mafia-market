package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Hooks the normal survival actions each Job pays for and hands out XP +
 * coins through JobsManager.awardAction() whenever one happens. Every
 * handler bails out immediately if the player hasn't joined the relevant
 * job, so none of this costs anything for a player with no jobs at all.
 *
 * Deliberately simple and trust-based, in keeping with the rest of this
 * plugin's economy (the existing /gen item generators and AFK Farm are both
 * built the same way): there's no placed-block tracking to stop someone
 * from, say, placing a log and re-breaking it for Lumberjack XP. The payouts
 * are small enough on their own (a few coins a swing) that grinding them is
 * never better than just playing, selling drops on the Black Market, or
 * working an actual job the intended way.
 */
public class JobsListener implements Listener {

    private final JobsManager jobs;
    private final Economy economy;

    /** Ore and ancient-debris blocks the Miner job pays for, with per-block (xp, coins). */
    private static final Map<Material, long[]> MINER_BLOCKS = new EnumMap<>(Material.class);
    static {
        MINER_BLOCKS.put(Material.COAL_ORE, new long[]{2, 2});
        MINER_BLOCKS.put(Material.DEEPSLATE_COAL_ORE, new long[]{2, 2});
        MINER_BLOCKS.put(Material.COPPER_ORE, new long[]{2, 2});
        MINER_BLOCKS.put(Material.DEEPSLATE_COPPER_ORE, new long[]{2, 2});
        MINER_BLOCKS.put(Material.IRON_ORE, new long[]{3, 4});
        MINER_BLOCKS.put(Material.DEEPSLATE_IRON_ORE, new long[]{3, 4});
        MINER_BLOCKS.put(Material.NETHER_GOLD_ORE, new long[]{3, 4});
        MINER_BLOCKS.put(Material.NETHER_QUARTZ_ORE, new long[]{2, 3});
        MINER_BLOCKS.put(Material.GOLD_ORE, new long[]{5, 7});
        MINER_BLOCKS.put(Material.DEEPSLATE_GOLD_ORE, new long[]{5, 7});
        MINER_BLOCKS.put(Material.REDSTONE_ORE, new long[]{3, 3});
        MINER_BLOCKS.put(Material.DEEPSLATE_REDSTONE_ORE, new long[]{3, 3});
        MINER_BLOCKS.put(Material.LAPIS_ORE, new long[]{3, 3});
        MINER_BLOCKS.put(Material.DEEPSLATE_LAPIS_ORE, new long[]{3, 3});
        MINER_BLOCKS.put(Material.DIAMOND_ORE, new long[]{12, 30});
        MINER_BLOCKS.put(Material.DEEPSLATE_DIAMOND_ORE, new long[]{12, 30});
        MINER_BLOCKS.put(Material.EMERALD_ORE, new long[]{10, 22});
        MINER_BLOCKS.put(Material.DEEPSLATE_EMERALD_ORE, new long[]{10, 22});
        MINER_BLOCKS.put(Material.ANCIENT_DEBRIS, new long[]{25, 60});
    }

    /** Fully-grown crop/produce blocks the Farmer job pays for. Ageable ones only count at max age. */
    private static final Set<Material> AGEABLE_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );
    private static final Set<Material> ALWAYS_READY_CROPS = EnumSet.of(Material.PUMPKIN, Material.MELON);

    /** Every log type the Lumberjack job pays for (stripped logs included, same payout). */
    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
            Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM
    );

    /**
     * Common building blocks the Builder job pays for - full-recovery blocks
     * like these are what the class javadoc's "place-and-rebreak" caveat is
     * about, so this list deliberately sticks to ordinary construction
     * material (woods, stone types, concrete/terracotta/wool) rather than
     * anything rare, and the per-block payout is kept token-small.
     */
    private static final Set<Material> BUILDER_BLOCKS = EnumSet.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS,
            Material.COBBLESTONE, Material.STONE, Material.STONE_BRICKS, Material.BRICKS,
            Material.SMOOTH_STONE, Material.SANDSTONE, Material.RED_SANDSTONE,
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE,
            Material.WHITE_CONCRETE, Material.WHITE_TERRACOTTA, Material.WHITE_WOOL
    );

    public JobsListener(JobsManager jobs, Economy economy) {
        this.jobs = jobs;
        this.economy = economy;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (MINER_BLOCKS.containsKey(type)) {
            long[] reward = MINER_BLOCKS.get(type);
            pay(player, Job.MINER, reward[0], reward[1]);
            return;
        }

        if (LOGS.contains(type)) {
            pay(player, Job.LUMBERJACK, 2, 2);
            return;
        }

        if (ALWAYS_READY_CROPS.contains(type)) {
            pay(player, Job.FARMER, 2, 2);
            return;
        }

        if (AGEABLE_CROPS.contains(type)) {
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
                pay(player, Job.FARMER, 2, 2);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (BUILDER_BLOCKS.contains(event.getBlock().getType())) {
            pay(event.getPlayer(), Job.BUILDER, 1, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        // Pays more for a pricier enchant (more levels spent / higher enchantment count), same
        // small-token spirit as everything else here - a lucky top-tier enchant is worth a bit
        // more than a cheap Fortune I, but never anywhere near what the enchant itself is worth.
        int enchantCount = event.getEnchantsToAdd().size();
        long baseXp = 3L + enchantCount;
        long baseCoins = 2L + enchantCount;
        pay(event.getEnchanter(), Job.ENCHANTER, baseXp, baseCoins);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Monster)) return;

        // A handful of notably tougher hostiles pay a bit more than a plain
        // zombie/skeleton/spider - still small change next to what the
        // Black Market or a real loot drop is worth, just a nudge.
        String typeName = entity.getType().name();
        long baseXp = 4;
        long baseCoins = 3;
        if (typeName.equals("ENDERMAN") || typeName.equals("WITCH") || typeName.equals("PILLAGER")
                || typeName.equals("VINDICATOR") || typeName.equals("EVOKER")) {
            baseXp = 7;
            baseCoins = 6;
        } else if (typeName.equals("WITHER_SKELETON") || typeName.equals("RAVAGER")
                || typeName.equals("WARDEN")) {
            baseXp = 15;
            baseCoins = 14;
        }

        pay(killer, Job.HUNTER, baseXp, baseCoins);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();

        long baseXp = 4;
        long baseCoins = 3;
        if (event.getCaught() instanceof Item caughtItem) {
            ItemStack caught = caughtItem.getItemStack();
            // A non-fish catch (an enchanted book, a bow, a name tag, a
            // nautilus shell...) is vanilla's "treasure" roll, rarer than an
            // ordinary fish - worth a little more.
            boolean plainFish = caught.getType() == Material.COD || caught.getType() == Material.SALMON
                    || caught.getType() == Material.PUFFERFISH || caught.getType() == Material.TROPICAL_FISH;
            if (!plainFish) {
                baseXp = 8;
                baseCoins = 7;
            }
        }

        pay(player, Job.FISHERMAN, baseXp, baseCoins);
    }

    private void pay(Player player, Job job, long baseXp, long baseCoins) {
        long payout = jobs.awardAction(player, job, baseXp, baseCoins);
        if (payout <= 0) return; // player hasn't joined this job

        economy.addBalance(player, payout);
        if (!economy.isChatQuiet(player)) {
            player.sendMessage(job.color + "[" + job.displayName + "] " + ChatColor.GRAY
                    + "+" + ChatColor.AQUA + payout + ChatColor.GRAY + " Frozen Coins");
        }
    }
}
