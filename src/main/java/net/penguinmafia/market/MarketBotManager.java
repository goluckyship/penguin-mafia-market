package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the Black Market feeling alive even when no real players are
 * selling: every REFRESH_INTERVAL, the "Black Market Dealer" (a system
 * seller, not a real player) walks every single item in its curated,
 * vanilla-survival-obtainable price list (BASE_PRICES) and, for each one,
 * makes sure there are at least MIN_STOCK_STACKS separate listings of it
 * (20 by default) - each its own full stack (64 of a block, 16 of an
 * ender pearl, 1 of a sword/armor piece - whatever that material's real
 * max stack size is). So "20 stacks" of diamond blocks means 20 individual
 * stack-of-64 listings a player can browse and buy one at a time, not one
 * giant listing holding 1280 - Paper's own item serialization rejects any
 * single stack's count outside 1-99 (discovered the hard way: an earlier
 * version of this class tried one oversized listing per material and that
 * limit made every /bm save throw and broke the market plugin-wide). Once
 * a material already has MIN_STOCK_STACKS listings, further rolls of it
 * are skipped; as players buy them down, the next restock tops the count
 * back up. Past, still-unsold listings are never removed or replaced by a
 * restock; an op can also trigger an extra round early with /bm restock.
 *
 * Deliberately excludes anything creative-only, admin-only, or otherwise
 * not obtainable by a normal survival player - no dragon eggs, spawners,
 * command/structure blocks, barriers, etc (see BLOCKED, enforced on top of
 * BASE_PRICES as a belt-and-suspenders check). Rare/end-game items that
 * genuinely are vanilla-obtainable (netherite, elytra, totems...) are
 * allowed, just priced steeply so a deep stock of them isn't a cheap
 * shortcut.
 *
 * Two prices-aren't-static systems live here too: every new listing's price
 * is scaled by an inflation multiplier tracking the server's total Frozen
 * Coin supply (see computeInflationMultiplier()), and Enchanted Books are
 * stocked with real, fixed enchantments rather than sold blank (see
 * ENCHANT_PRESETS/restockEnchantedBooks()).
 */
public class MarketBotManager {

    public static final UUID SELLER_ID = UUID.fromString("00000000-0000-4000-a000-000000000bee");
    public static final String SELLER_NAME = "Black Market Dealer";

    /**
     * Every item the dealer carries is kept stocked up to this many
     * separate full-stack listings at minimum - 20 individual listings of
     * (say) 64 diamond blocks each, not one listing of 1280. Paper's own
     * item serialization caps a single stack's count at 99, so a material's
     * real max stack size (capped again at 99, belt-and-suspenders) is as
     * big as any one listing can ever get.
     */
    private static final int MIN_STOCK_STACKS = 20;

    /** Hard ceiling matching Paper/Minecraft's own item-stack serialization limit. */
    private static final int MAX_SERIALIZABLE_STACK = 99;

    private static final long REFRESH_INTERVAL_TICKS = 20L * 60L * 10L; // 10 minutes

    /**
     * Materials that must never appear in the Black Market no matter what,
     * even if someone adds one to BASE_PRICES below by mistake later -
     * creative-only / admin-only / structurally special blocks that a
     * normal survival player could never legitimately hold.
     */
    private static final Set<Material> BLOCKED = EnumSet.of(
            Material.DRAGON_EGG, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.BARRIER,
            Material.DEBUG_STICK, Material.JIGSAW, Material.LIGHT, Material.BEDROCK,
            Material.END_PORTAL_FRAME, Material.SPAWNER, Material.REINFORCED_DEEPSLATE,
            Material.PETRIFIED_OAK_SLAB, Material.KNOWLEDGE_BOOK
    );

    /** material -> fair base price per single item, hand-tuned by rarity/usefulness. */
    private static final Map<Material, Long> BASE_PRICES = new LinkedHashMap<>();

    /**
     * Read-only view of BASE_PRICES, so other systems (the /shop GUI, for
     * one) that also sell vanilla items can reuse the same hand-tuned
     * per-item prices instead of inventing their own and drifting out of
     * sync with what the Black Market charges for the same material.
     */
    public static Map<Material, Long> getBasePrices() {
        return java.util.Collections.unmodifiableMap(BASE_PRICES);
    }
    static {
        // --- Common building blocks / resources: cheap, sell in bulk ---
        put(Material.DIRT, 1); put(Material.COBBLESTONE, 2); put(Material.STONE, 3);
        put(Material.SAND, 2); put(Material.GRAVEL, 2); put(Material.OAK_LOG, 5);
        put(Material.SPRUCE_LOG, 5); put(Material.BIRCH_LOG, 5); put(Material.JUNGLE_LOG, 5);
        put(Material.ACACIA_LOG, 5); put(Material.DARK_OAK_LOG, 5); put(Material.MANGROVE_LOG, 5);
        put(Material.CHERRY_LOG, 6); put(Material.OAK_PLANKS, 3); put(Material.GLASS, 4);
        put(Material.SANDSTONE, 4); put(Material.NETHERRACK, 3); put(Material.BASALT, 4);
        put(Material.CLAY_BALL, 4); put(Material.TERRACOTTA, 5); put(Material.WHITE_WOOL, 6);
        put(Material.WHITE_CONCRETE, 8); put(Material.ANDESITE, 3); put(Material.DIORITE, 3);
        put(Material.GRANITE, 3); put(Material.DEEPSLATE, 4); put(Material.TUFF, 4);
        put(Material.MOSS_BLOCK, 10); put(Material.MUD, 3); put(Material.PACKED_ICE, 12);

        // --- Common farm/mob-drop resources ---
        put(Material.COAL, 18); put(Material.IRON_INGOT, 30); put(Material.COPPER_INGOT, 14);
        put(Material.REDSTONE, 10); put(Material.LAPIS_LAZULI, 10); put(Material.QUARTZ, 16);
        put(Material.FLINT, 6); put(Material.STRING, 5); put(Material.FEATHER, 6);
        put(Material.LEATHER, 12); put(Material.BONE, 6); put(Material.GUNPOWDER, 14);
        put(Material.ROTTEN_FLESH, 1); put(Material.SPIDER_EYE, 8); put(Material.SLIME_BALL, 20);
        put(Material.INK_SAC, 6); put(Material.GLOW_INK_SAC, 20); put(Material.HONEYCOMB, 14);
        put(Material.HONEY_BOTTLE, 18); put(Material.SUGAR, 5); put(Material.WHEAT, 5);
        put(Material.CARROT, 4); put(Material.POTATO, 4); put(Material.BEETROOT, 4);
        put(Material.APPLE, 10); put(Material.BREAD, 8); put(Material.EGG, 4);
        put(Material.MILK_BUCKET, 10); put(Material.COD, 6); put(Material.SALMON, 7);
        put(Material.KELP, 2); put(Material.BAMBOO, 2); put(Material.SUGAR_CANE, 3);
        put(Material.CACTUS, 3); put(Material.PUMPKIN, 10); put(Material.MELON, 6);
        put(Material.CHORUS_FRUIT, 12); put(Material.NETHER_WART, 10); put(Material.OBSIDIAN, 35);
        put(Material.GLOWSTONE_DUST, 14); put(Material.BLAZE_POWDER, 40); put(Material.BLAZE_ROD, 70);
        put(Material.MAGMA_CREAM, 30); put(Material.ENDER_PEARL, 90); put(Material.EXPERIENCE_BOTTLE, 60);
        put(Material.RABBIT_FOOT, 50); put(Material.RABBIT_HIDE, 10); put(Material.PHANTOM_MEMBRANE, 60);
        put(Material.GHAST_TEAR, 150); put(Material.TURTLE_SCUTE, 80);
        put(Material.AMETHYST_SHARD, 35); put(Material.PRISMARINE_SHARD, 12); put(Material.PRISMARINE_CRYSTALS, 20);
        put(Material.NAUTILUS_SHELL, 220); put(Material.DRAGON_BREATH, 300);

        // --- Uncommon ---
        put(Material.GOLD_INGOT, 60); put(Material.GOLD_NUGGET, 8);

        // --- Rare ---
        put(Material.DIAMOND, 350); put(Material.EMERALD, 220); put(Material.HEART_OF_THE_SEA, 1200);
        put(Material.NETHERITE_SCRAP, 1500); put(Material.ANCIENT_DEBRIS, 1800);
        put(Material.SHULKER_SHELL, 900); put(Material.TRIDENT, 4000); put(Material.NETHER_STAR, 6000);

        // --- Very rare / end-game - vanilla-obtainable, priced steeply so they're a real sink ---
        put(Material.NETHERITE_INGOT, 5500); put(Material.ENCHANTED_GOLDEN_APPLE, 8000);
        put(Material.TOTEM_OF_UNDYING, 7000); put(Material.ELYTRA, 9000);

        // --- Plain, unenchanted tools/armor - flat prices, not tier-derived ---
        put(Material.IRON_SWORD, 200); put(Material.IRON_PICKAXE, 220); put(Material.IRON_AXE, 220);
        put(Material.DIAMOND_SWORD, 900); put(Material.DIAMOND_PICKAXE, 1000); put(Material.DIAMOND_AXE, 1000);
        put(Material.DIAMOND_HELMET, 700); put(Material.DIAMOND_CHESTPLATE, 1100);
        put(Material.DIAMOND_LEGGINGS, 1000); put(Material.DIAMOND_BOOTS, 650);
        put(Material.NETHERITE_SWORD, 6500); put(Material.NETHERITE_PICKAXE, 7000);
        put(Material.NETHERITE_HELMET, 5500); put(Material.NETHERITE_CHESTPLATE, 8500);
        put(Material.NETHERITE_LEGGINGS, 7500); put(Material.NETHERITE_BOOTS, 5000);
        put(Material.BOW, 150); put(Material.CROSSBOW, 200); put(Material.SHIELD, 120);

        // --- Hoes & shovels, every tier - these were missing entirely, which
        // meant they fell through to the flat 15-coin catch-all default no
        // matter the tier (a Netherite Hoe for 15 coins is exactly the kind
        // of "super underpriced" bug this pass exists to fix). Priced in
        // line with that tier's sword/axe/pickaxe above.
        put(Material.WOODEN_HOE, 15); put(Material.WOODEN_SHOVEL, 12);
        put(Material.STONE_HOE, 28); put(Material.STONE_SHOVEL, 22);
        put(Material.IRON_HOE, 160); put(Material.IRON_SHOVEL, 110);
        put(Material.GOLDEN_HOE, 70); put(Material.GOLDEN_SHOVEL, 60);
        put(Material.DIAMOND_HOE, 750); put(Material.DIAMOND_SHOVEL, 650);
        put(Material.NETHERITE_AXE, 7000); put(Material.NETHERITE_HOE, 6000); put(Material.NETHERITE_SHOVEL, 5500);

        // --- High-value items that were missing a price entirely and so
        // were also falling into the flat 15-coin catch-all - another big
        // source of "super underpriced" items. ---
        put(Material.BEACON, 6500); put(Material.END_CRYSTAL, 3500);
        put(Material.ENCHANTING_TABLE, 900); put(Material.NAME_TAG, 120);
        put(Material.DRAGON_HEAD, 1500); put(Material.WITHER_SKELETON_SKULL, 400);
        put(Material.BREEZE_ROD, 200); put(Material.WIND_CHARGE, 25);
        // Heavy Core/Mace: these were briefly sellable for 15 coins through
        // the same catch-all bug, so any already bought (or crafted into a
        // Mace) are being clawed back on top of this repricing (see
        // ContrabandSweep). Heavy Cores are an ominous-vault-only drop from
        // Trial Chambers - about as rare an item as exists in survival right
        // now - so this is priced deliberately steep: 1,350,000 each, which
        // (even at the restock loop's lowest -15% price-variance roll) puts
        // a full stack of 64 at roughly 73,400,000 coins, never under 73M.
        put(Material.HEAVY_CORE, 1_350_000); put(Material.MACE, 1_500_000);

        // --- Rockets - the requested specialty item ---
        put(Material.FIREWORK_ROCKET, 20);

        // --- More wood/wool/decor variety ---
        put(Material.SPRUCE_PLANKS, 3); put(Material.BIRCH_PLANKS, 3); put(Material.JUNGLE_PLANKS, 3);
        put(Material.ACACIA_PLANKS, 3); put(Material.DARK_OAK_PLANKS, 3); put(Material.MANGROVE_PLANKS, 3);
        put(Material.CHERRY_PLANKS, 4); put(Material.BAMBOO_PLANKS, 3);
        put(Material.ORANGE_WOOL, 6); put(Material.MAGENTA_WOOL, 6); put(Material.LIGHT_BLUE_WOOL, 6);
        put(Material.YELLOW_WOOL, 6); put(Material.LIME_WOOL, 6); put(Material.PINK_WOOL, 6);
        put(Material.GRAY_WOOL, 6); put(Material.LIGHT_GRAY_WOOL, 6); put(Material.CYAN_WOOL, 6);
        put(Material.PURPLE_WOOL, 6); put(Material.BLUE_WOOL, 6); put(Material.BROWN_WOOL, 6);
        put(Material.GREEN_WOOL, 6); put(Material.RED_WOOL, 6); put(Material.BLACK_WOOL, 6);
        put(Material.BLACK_CONCRETE, 8); put(Material.RED_CONCRETE, 8); put(Material.BLUE_CONCRETE, 8);
        put(Material.WHITE_GLAZED_TERRACOTTA, 20); put(Material.BLUE_GLAZED_TERRACOTTA, 20);
        put(Material.POPPY, 4); put(Material.DANDELION, 4); put(Material.BLUE_ORCHID, 6);
        put(Material.ALLIUM, 6); put(Material.AZURE_BLUET, 6); put(Material.ORANGE_TULIP, 5);
        put(Material.PINK_TULIP, 5); put(Material.RED_TULIP, 5); put(Material.WHITE_TULIP, 5);
        put(Material.OXEYE_DAISY, 4); put(Material.CORNFLOWER, 5); put(Material.LILY_OF_THE_VALLEY, 8);
        put(Material.SUNFLOWER, 8); put(Material.LILAC, 8); put(Material.ROSE_BUSH, 8); put(Material.PEONY, 8);

        // --- Food variety ---
        put(Material.BEEF, 6); put(Material.COOKED_BEEF, 12); put(Material.PORKCHOP, 6);
        put(Material.COOKED_PORKCHOP, 12); put(Material.CHICKEN, 4); put(Material.COOKED_CHICKEN, 8);
        put(Material.MUTTON, 5); put(Material.COOKED_MUTTON, 10); put(Material.RABBIT, 5);
        put(Material.COOKED_RABBIT, 10); put(Material.BAKED_POTATO, 6); put(Material.PUMPKIN_PIE, 16);
        put(Material.COOKIE, 3); put(Material.CAKE, 40); put(Material.GOLDEN_CARROT, 40);
        put(Material.GOLDEN_APPLE, 300); put(Material.MUSHROOM_STEW, 10); put(Material.RABBIT_STEW, 14);
        put(Material.SUSPICIOUS_STEW, 10); put(Material.DRIED_KELP, 2); put(Material.SWEET_BERRIES, 4);
        put(Material.GLOW_BERRIES, 6); put(Material.TROPICAL_FISH, 10); put(Material.PUFFERFISH, 8);
        put(Material.FERMENTED_SPIDER_EYE, 18); put(Material.GLISTERING_MELON_SLICE, 30);

        // --- Redstone & utility blocks ---
        put(Material.REPEATER, 20); put(Material.COMPARATOR, 30); put(Material.PISTON, 25);
        put(Material.STICKY_PISTON, 35); put(Material.OBSERVER, 40); put(Material.HOPPER, 60);
        put(Material.DROPPER, 15); put(Material.DISPENSER, 20); put(Material.NOTE_BLOCK, 10);
        put(Material.TARGET, 25); put(Material.TRIPWIRE_HOOK, 8); put(Material.DAYLIGHT_DETECTOR, 50);
        put(Material.LECTERN, 20); put(Material.COMPOSTER, 15); put(Material.BARREL, 15);
        put(Material.SMOKER, 25); put(Material.BLAST_FURNACE, 35); put(Material.CARTOGRAPHY_TABLE, 20);
        put(Material.FLETCHING_TABLE, 20); put(Material.SMITHING_TABLE, 20); put(Material.GRINDSTONE, 25);
        put(Material.STONECUTTER, 25); put(Material.LOOM, 15); put(Material.LODESTONE, 80);
        put(Material.RESPAWN_ANCHOR, 400); put(Material.CRYING_OBSIDIAN, 60);

        // --- Nether, deep dark & nature decoration ---
        put(Material.WARPED_STEM, 8); put(Material.CRIMSON_STEM, 8); put(Material.WARPED_NYLIUM, 6);
        put(Material.CRIMSON_NYLIUM, 6); put(Material.WARPED_FUNGUS, 5); put(Material.CRIMSON_FUNGUS, 5);
        put(Material.SHROOMLIGHT, 15); put(Material.NETHER_WART_BLOCK, 10); put(Material.SOUL_SAND, 5);
        put(Material.SOUL_SOIL, 5); put(Material.MAGMA_BLOCK, 10); put(Material.BLACKSTONE, 4);
        put(Material.GILDED_BLACKSTONE, 60); put(Material.SCULK, 20); put(Material.GLOW_LICHEN, 8);
        put(Material.MOSS_CARPET, 6); put(Material.AZALEA, 10); put(Material.FLOWERING_AZALEA, 15);
        put(Material.MANGROVE_ROOTS, 8); put(Material.MUD_BRICKS, 5); put(Material.PACKED_MUD, 4);
        put(Material.CALCITE, 4); put(Material.DRIPSTONE_BLOCK, 6); put(Material.POINTED_DRIPSTONE, 10);
        put(Material.AMETHYST_BLOCK, 50);

        // --- Other tool/armor tiers and handy gear ---
        put(Material.WOODEN_SWORD, 15); put(Material.WOODEN_PICKAXE, 15); put(Material.WOODEN_AXE, 15);
        put(Material.STONE_SWORD, 30); put(Material.STONE_PICKAXE, 35); put(Material.STONE_AXE, 35);
        put(Material.GOLDEN_SWORD, 80); put(Material.GOLDEN_PICKAXE, 90); put(Material.GOLDEN_AXE, 90);
        put(Material.GOLDEN_HELMET, 70); put(Material.GOLDEN_CHESTPLATE, 110); put(Material.GOLDEN_LEGGINGS, 100);
        put(Material.GOLDEN_BOOTS, 65); put(Material.LEATHER_HELMET, 40); put(Material.LEATHER_CHESTPLATE, 60);
        put(Material.LEATHER_LEGGINGS, 55); put(Material.LEATHER_BOOTS, 35); put(Material.CHAINMAIL_HELMET, 120);
        put(Material.CHAINMAIL_CHESTPLATE, 180); put(Material.CHAINMAIL_LEGGINGS, 160); put(Material.CHAINMAIL_BOOTS, 110);
        put(Material.IRON_HELMET, 150); put(Material.IRON_CHESTPLATE, 240); put(Material.IRON_LEGGINGS, 220);
        put(Material.IRON_BOOTS, 130); put(Material.FISHING_ROD, 90); put(Material.SHEARS, 60);
        put(Material.FLINT_AND_STEEL, 70); put(Material.COMPASS, 50); put(Material.CLOCK, 50);
        put(Material.SPYGLASS, 300); put(Material.LEAD, 20); put(Material.SADDLE, 150);
        put(Material.CARROT_ON_A_STICK, 60);

        // --- Music discs - rare creeper-blast-drop collectibles, fun chase items ---
        put(Material.MUSIC_DISC_13, 500); put(Material.MUSIC_DISC_CAT, 500); put(Material.MUSIC_DISC_BLOCKS, 500);
        put(Material.MUSIC_DISC_CHIRP, 500); put(Material.MUSIC_DISC_FAR, 500); put(Material.MUSIC_DISC_MALL, 500);
        put(Material.MUSIC_DISC_MELLOHI, 500); put(Material.MUSIC_DISC_STAL, 500); put(Material.MUSIC_DISC_STRAD, 500);
        put(Material.MUSIC_DISC_WARD, 500); put(Material.MUSIC_DISC_11, 500); put(Material.MUSIC_DISC_WAIT, 500);
        put(Material.MUSIC_DISC_PIGSTEP, 800); put(Material.MUSIC_DISC_OTHERSIDE, 800);
        put(Material.MUSIC_DISC_5, 800); put(Material.MUSIC_DISC_RELIC, 800);
        put(Material.DISC_FRAGMENT_5, 350);

        // ================================================================
        // Everything below was added to make the dealer carry close to the
        // full range of vanilla-survival-obtainable items - every ordinary
        // wood/stone build style, every dye/color variant of the
        // decorative blocks, and a long tail of individual items that
        // didn't fit neatly into the categories above. Still excludes
        // anything creative-only or otherwise not obtainable on a normal
        // survival server (see BLOCKED and the class javadoc).
        // ================================================================

        // --- Stairs: one entry per wood/stone style a player can actually craft ---
        put(Material.SPRUCE_STAIRS, 4); put(Material.BIRCH_STAIRS, 4);
        put(Material.JUNGLE_STAIRS, 4); put(Material.ACACIA_STAIRS, 4);
        put(Material.DARK_OAK_STAIRS, 4); put(Material.MANGROVE_STAIRS, 4);
        put(Material.CHERRY_STAIRS, 5); put(Material.BAMBOO_STAIRS, 4);
        put(Material.CRIMSON_STAIRS, 9); put(Material.WARPED_STAIRS, 9);
        put(Material.COBBLESTONE_STAIRS, 3); put(Material.MOSSY_COBBLESTONE_STAIRS, 11);
        put(Material.STONE_BRICK_STAIRS, 4); put(Material.MOSSY_STONE_BRICK_STAIRS, 11);
        put(Material.SANDSTONE_STAIRS, 5); put(Material.SMOOTH_SANDSTONE_STAIRS, 5);
        put(Material.RED_SANDSTONE_STAIRS, 5); put(Material.SMOOTH_RED_SANDSTONE_STAIRS, 5);
        put(Material.GRANITE_STAIRS, 4); put(Material.POLISHED_GRANITE_STAIRS, 4);
        put(Material.DIORITE_STAIRS, 4); put(Material.POLISHED_DIORITE_STAIRS, 4);
        put(Material.ANDESITE_STAIRS, 4); put(Material.POLISHED_ANDESITE_STAIRS, 4);
        put(Material.BRICK_STAIRS, 6); put(Material.NETHER_BRICK_STAIRS, 6);
        put(Material.RED_NETHER_BRICK_STAIRS, 7); put(Material.QUARTZ_STAIRS, 18);
        put(Material.SMOOTH_QUARTZ_STAIRS, 18); put(Material.PURPUR_STAIRS, 25);
        put(Material.PRISMARINE_STAIRS, 14); put(Material.PRISMARINE_BRICK_STAIRS, 18);
        put(Material.DARK_PRISMARINE_STAIRS, 18); put(Material.END_STONE_BRICK_STAIRS, 14);
        put(Material.BLACKSTONE_STAIRS, 5); put(Material.POLISHED_BLACKSTONE_STAIRS, 5);
        put(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, 5); put(Material.MUD_BRICK_STAIRS, 6);
        put(Material.COBBLED_DEEPSLATE_STAIRS, 5); put(Material.POLISHED_DEEPSLATE_STAIRS, 5);
        put(Material.DEEPSLATE_BRICK_STAIRS, 5); put(Material.DEEPSLATE_TILE_STAIRS, 6);
        put(Material.CUT_COPPER_STAIRS, 17); put(Material.EXPOSED_CUT_COPPER_STAIRS, 17);
        put(Material.WEATHERED_CUT_COPPER_STAIRS, 17); put(Material.OXIDIZED_CUT_COPPER_STAIRS, 17);
        put(Material.WAXED_CUT_COPPER_STAIRS, 18); put(Material.WAXED_EXPOSED_CUT_COPPER_STAIRS, 18);
        put(Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, 18); put(Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, 18);

        // --- Slabs: the same build-style sets, half-height ---
        put(Material.OAK_SLAB, 2); put(Material.SPRUCE_SLAB, 2); put(Material.BIRCH_SLAB, 2);
        put(Material.JUNGLE_SLAB, 2); put(Material.ACACIA_SLAB, 2); put(Material.DARK_OAK_SLAB, 2);
        put(Material.MANGROVE_SLAB, 2); put(Material.CHERRY_SLAB, 3); put(Material.BAMBOO_SLAB, 2);
        put(Material.BAMBOO_MOSAIC_SLAB, 3); put(Material.CRIMSON_SLAB, 5); put(Material.WARPED_SLAB, 5);
        put(Material.COBBLESTONE_SLAB, 2); put(Material.MOSSY_COBBLESTONE_SLAB, 6);
        put(Material.STONE_SLAB, 2); put(Material.SMOOTH_STONE_SLAB, 2);
        put(Material.STONE_BRICK_SLAB, 2); put(Material.MOSSY_STONE_BRICK_SLAB, 6);
        put(Material.SANDSTONE_SLAB, 3); put(Material.SMOOTH_SANDSTONE_SLAB, 3);
        put(Material.CUT_SANDSTONE_SLAB, 3); put(Material.RED_SANDSTONE_SLAB, 3);
        put(Material.SMOOTH_RED_SANDSTONE_SLAB, 3); put(Material.CUT_RED_SANDSTONE_SLAB, 3);
        put(Material.GRANITE_SLAB, 2); put(Material.POLISHED_GRANITE_SLAB, 2);
        put(Material.DIORITE_SLAB, 2); put(Material.POLISHED_DIORITE_SLAB, 2);
        put(Material.ANDESITE_SLAB, 2); put(Material.POLISHED_ANDESITE_SLAB, 2);
        put(Material.BRICK_SLAB, 3); put(Material.NETHER_BRICK_SLAB, 3);
        put(Material.RED_NETHER_BRICK_SLAB, 4); put(Material.QUARTZ_SLAB, 9);
        put(Material.SMOOTH_QUARTZ_SLAB, 9); put(Material.PURPUR_SLAB, 13);
        put(Material.PRISMARINE_SLAB, 7); put(Material.PRISMARINE_BRICK_SLAB, 9);
        put(Material.DARK_PRISMARINE_SLAB, 9); put(Material.END_STONE_BRICK_SLAB, 7);
        put(Material.BLACKSTONE_SLAB, 3); put(Material.POLISHED_BLACKSTONE_SLAB, 3);
        put(Material.POLISHED_BLACKSTONE_BRICK_SLAB, 3); put(Material.MUD_BRICK_SLAB, 3);
        put(Material.COBBLED_DEEPSLATE_SLAB, 3); put(Material.POLISHED_DEEPSLATE_SLAB, 3);
        put(Material.DEEPSLATE_BRICK_SLAB, 3); put(Material.DEEPSLATE_TILE_SLAB, 3);
        put(Material.CUT_COPPER_SLAB, 9); put(Material.EXPOSED_CUT_COPPER_SLAB, 9);
        put(Material.WEATHERED_CUT_COPPER_SLAB, 9); put(Material.OXIDIZED_CUT_COPPER_SLAB, 9);
        put(Material.WAXED_CUT_COPPER_SLAB, 9); put(Material.WAXED_EXPOSED_CUT_COPPER_SLAB, 9);
        put(Material.WAXED_WEATHERED_CUT_COPPER_SLAB, 9); put(Material.WAXED_OXIDIZED_CUT_COPPER_SLAB, 9);

        // --- Walls ---
        put(Material.COBBLESTONE_WALL, 3); put(Material.MOSSY_COBBLESTONE_WALL, 8);
        put(Material.STONE_BRICK_WALL, 3); put(Material.MOSSY_STONE_BRICK_WALL, 8);
        put(Material.GRANITE_WALL, 3); put(Material.DIORITE_WALL, 3); put(Material.ANDESITE_WALL, 3);
        put(Material.SANDSTONE_WALL, 4); put(Material.RED_SANDSTONE_WALL, 4);
        put(Material.BRICK_WALL, 5); put(Material.NETHER_BRICK_WALL, 5); put(Material.RED_NETHER_BRICK_WALL, 6);
        put(Material.PRISMARINE_WALL, 11); put(Material.END_STONE_BRICK_WALL, 11);
        put(Material.BLACKSTONE_WALL, 4); put(Material.POLISHED_BLACKSTONE_WALL, 4);
        put(Material.POLISHED_BLACKSTONE_BRICK_WALL, 4); put(Material.MUD_BRICK_WALL, 4);
        put(Material.COBBLED_DEEPSLATE_WALL, 4); put(Material.POLISHED_DEEPSLATE_WALL, 4);
        put(Material.DEEPSLATE_BRICK_WALL, 4); put(Material.DEEPSLATE_TILE_WALL, 4);

        // --- Fences & gates ---
        put(Material.SPRUCE_FENCE, 3); put(Material.BIRCH_FENCE, 3); put(Material.JUNGLE_FENCE, 3);
        put(Material.ACACIA_FENCE, 3); put(Material.DARK_OAK_FENCE, 3); put(Material.MANGROVE_FENCE, 3);
        put(Material.CHERRY_FENCE, 4); put(Material.BAMBOO_FENCE, 3);
        put(Material.CRIMSON_FENCE, 8); put(Material.WARPED_FENCE, 8); put(Material.NETHER_BRICK_FENCE, 5);
        put(Material.OAK_FENCE_GATE, 3); put(Material.SPRUCE_FENCE_GATE, 3); put(Material.BIRCH_FENCE_GATE, 3);
        put(Material.JUNGLE_FENCE_GATE, 3); put(Material.ACACIA_FENCE_GATE, 3); put(Material.DARK_OAK_FENCE_GATE, 3);
        put(Material.MANGROVE_FENCE_GATE, 3); put(Material.CHERRY_FENCE_GATE, 4); put(Material.BAMBOO_FENCE_GATE, 3);
        put(Material.CRIMSON_FENCE_GATE, 8); put(Material.WARPED_FENCE_GATE, 8);

        // --- Doors & trapdoors ---
        put(Material.OAK_DOOR, 4); put(Material.SPRUCE_DOOR, 4); put(Material.BIRCH_DOOR, 4);
        put(Material.JUNGLE_DOOR, 4); put(Material.ACACIA_DOOR, 4); put(Material.DARK_OAK_DOOR, 4);
        put(Material.MANGROVE_DOOR, 4); put(Material.CHERRY_DOOR, 5); put(Material.BAMBOO_DOOR, 4);
        put(Material.CRIMSON_DOOR, 9); put(Material.WARPED_DOOR, 9); put(Material.IRON_DOOR, 70);
        put(Material.OAK_TRAPDOOR, 4); put(Material.SPRUCE_TRAPDOOR, 4); put(Material.BIRCH_TRAPDOOR, 4);
        put(Material.JUNGLE_TRAPDOOR, 4); put(Material.ACACIA_TRAPDOOR, 4); put(Material.DARK_OAK_TRAPDOOR, 4);
        put(Material.MANGROVE_TRAPDOOR, 4); put(Material.CHERRY_TRAPDOOR, 5); put(Material.BAMBOO_TRAPDOOR, 4);
        put(Material.CRIMSON_TRAPDOOR, 9); put(Material.WARPED_TRAPDOOR, 9); put(Material.IRON_TRAPDOOR, 70);

        // --- Dyes - every color, since they're the input to half the blocks below ---
        put(Material.WHITE_DYE, 4); put(Material.ORANGE_DYE, 5); put(Material.MAGENTA_DYE, 6);
        put(Material.LIGHT_BLUE_DYE, 5); put(Material.YELLOW_DYE, 5); put(Material.LIME_DYE, 5);
        put(Material.PINK_DYE, 5); put(Material.GRAY_DYE, 5); put(Material.LIGHT_GRAY_DYE, 5);
        put(Material.CYAN_DYE, 6); put(Material.PURPLE_DYE, 6); put(Material.BLUE_DYE, 6);
        put(Material.BROWN_DYE, 5); put(Material.GREEN_DYE, 5); put(Material.RED_DYE, 5);
        put(Material.BLACK_DYE, 5); put(Material.BONE_MEAL, 3);

        // --- Colored blocks: carpets, beds, banners, candles, stained glass + panes ---
        put(Material.WHITE_CARPET, 3); put(Material.ORANGE_CARPET, 3); put(Material.MAGENTA_CARPET, 3);
        put(Material.LIGHT_BLUE_CARPET, 3); put(Material.YELLOW_CARPET, 3); put(Material.LIME_CARPET, 3);
        put(Material.PINK_CARPET, 3); put(Material.GRAY_CARPET, 3); put(Material.LIGHT_GRAY_CARPET, 3);
        put(Material.CYAN_CARPET, 3); put(Material.PURPLE_CARPET, 3); put(Material.BLUE_CARPET, 3);
        put(Material.BROWN_CARPET, 3); put(Material.GREEN_CARPET, 3); put(Material.RED_CARPET, 3);
        put(Material.BLACK_CARPET, 3);
        put(Material.WHITE_BED, 15); put(Material.ORANGE_BED, 15); put(Material.MAGENTA_BED, 15);
        put(Material.LIGHT_BLUE_BED, 15); put(Material.YELLOW_BED, 15); put(Material.LIME_BED, 15);
        put(Material.PINK_BED, 15); put(Material.GRAY_BED, 15); put(Material.LIGHT_GRAY_BED, 15);
        put(Material.CYAN_BED, 15); put(Material.PURPLE_BED, 15); put(Material.BLUE_BED, 15);
        put(Material.BROWN_BED, 15); put(Material.GREEN_BED, 15); put(Material.RED_BED, 15);
        put(Material.BLACK_BED, 15);
        put(Material.WHITE_BANNER, 14); put(Material.ORANGE_BANNER, 14); put(Material.MAGENTA_BANNER, 14);
        put(Material.LIGHT_BLUE_BANNER, 14); put(Material.YELLOW_BANNER, 14); put(Material.LIME_BANNER, 14);
        put(Material.PINK_BANNER, 14); put(Material.GRAY_BANNER, 14); put(Material.LIGHT_GRAY_BANNER, 14);
        put(Material.CYAN_BANNER, 14); put(Material.PURPLE_BANNER, 14); put(Material.BLUE_BANNER, 14);
        put(Material.BROWN_BANNER, 14); put(Material.GREEN_BANNER, 14); put(Material.RED_BANNER, 14);
        put(Material.BLACK_BANNER, 14);
        put(Material.CANDLE, 6); put(Material.WHITE_CANDLE, 6); put(Material.ORANGE_CANDLE, 6);
        put(Material.MAGENTA_CANDLE, 6); put(Material.LIGHT_BLUE_CANDLE, 6); put(Material.YELLOW_CANDLE, 6);
        put(Material.LIME_CANDLE, 6); put(Material.PINK_CANDLE, 6); put(Material.GRAY_CANDLE, 6);
        put(Material.LIGHT_GRAY_CANDLE, 6); put(Material.CYAN_CANDLE, 6); put(Material.PURPLE_CANDLE, 6);
        put(Material.BLUE_CANDLE, 6); put(Material.BROWN_CANDLE, 6); put(Material.GREEN_CANDLE, 6);
        put(Material.RED_CANDLE, 6); put(Material.BLACK_CANDLE, 6);
        put(Material.SHULKER_BOX, 950); put(Material.WHITE_SHULKER_BOX, 960); put(Material.ORANGE_SHULKER_BOX, 960);
        put(Material.MAGENTA_SHULKER_BOX, 960); put(Material.LIGHT_BLUE_SHULKER_BOX, 960); put(Material.YELLOW_SHULKER_BOX, 960);
        put(Material.LIME_SHULKER_BOX, 960); put(Material.PINK_SHULKER_BOX, 960); put(Material.GRAY_SHULKER_BOX, 960);
        put(Material.LIGHT_GRAY_SHULKER_BOX, 960); put(Material.CYAN_SHULKER_BOX, 960); put(Material.PURPLE_SHULKER_BOX, 960);
        put(Material.BLUE_SHULKER_BOX, 960); put(Material.BROWN_SHULKER_BOX, 960); put(Material.GREEN_SHULKER_BOX, 960);
        put(Material.RED_SHULKER_BOX, 960); put(Material.BLACK_SHULKER_BOX, 960);
        put(Material.WHITE_STAINED_GLASS, 5); put(Material.ORANGE_STAINED_GLASS, 5); put(Material.MAGENTA_STAINED_GLASS, 5);
        put(Material.LIGHT_BLUE_STAINED_GLASS, 5); put(Material.YELLOW_STAINED_GLASS, 5); put(Material.LIME_STAINED_GLASS, 5);
        put(Material.PINK_STAINED_GLASS, 5); put(Material.GRAY_STAINED_GLASS, 5); put(Material.LIGHT_GRAY_STAINED_GLASS, 5);
        put(Material.CYAN_STAINED_GLASS, 5); put(Material.PURPLE_STAINED_GLASS, 5); put(Material.BLUE_STAINED_GLASS, 5);
        put(Material.BROWN_STAINED_GLASS, 5); put(Material.GREEN_STAINED_GLASS, 5); put(Material.RED_STAINED_GLASS, 5);
        put(Material.BLACK_STAINED_GLASS, 5);
        put(Material.WHITE_STAINED_GLASS_PANE, 2); put(Material.ORANGE_STAINED_GLASS_PANE, 2);
        put(Material.MAGENTA_STAINED_GLASS_PANE, 2); put(Material.LIGHT_BLUE_STAINED_GLASS_PANE, 2);
        put(Material.YELLOW_STAINED_GLASS_PANE, 2); put(Material.LIME_STAINED_GLASS_PANE, 2);
        put(Material.PINK_STAINED_GLASS_PANE, 2); put(Material.GRAY_STAINED_GLASS_PANE, 2);
        put(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 2); put(Material.CYAN_STAINED_GLASS_PANE, 2);
        put(Material.PURPLE_STAINED_GLASS_PANE, 2); put(Material.BLUE_STAINED_GLASS_PANE, 2);
        put(Material.BROWN_STAINED_GLASS_PANE, 2); put(Material.GREEN_STAINED_GLASS_PANE, 2);
        put(Material.RED_STAINED_GLASS_PANE, 2); put(Material.BLACK_STAINED_GLASS_PANE, 2);
        put(Material.GLASS_PANE, 2); put(Material.TINTED_GLASS, 10);

        // --- Remaining concrete/concrete powder/terracotta/glazed terracotta colors ---
        put(Material.ORANGE_CONCRETE, 8); put(Material.MAGENTA_CONCRETE, 8); put(Material.LIGHT_BLUE_CONCRETE, 8);
        put(Material.YELLOW_CONCRETE, 8); put(Material.LIME_CONCRETE, 8); put(Material.PINK_CONCRETE, 8);
        put(Material.GRAY_CONCRETE, 8); put(Material.LIGHT_GRAY_CONCRETE, 8); put(Material.CYAN_CONCRETE, 8);
        put(Material.PURPLE_CONCRETE, 8); put(Material.BROWN_CONCRETE, 8); put(Material.GREEN_CONCRETE, 8);
        put(Material.WHITE_CONCRETE_POWDER, 6); put(Material.ORANGE_CONCRETE_POWDER, 6);
        put(Material.MAGENTA_CONCRETE_POWDER, 6); put(Material.LIGHT_BLUE_CONCRETE_POWDER, 6);
        put(Material.YELLOW_CONCRETE_POWDER, 6); put(Material.LIME_CONCRETE_POWDER, 6);
        put(Material.PINK_CONCRETE_POWDER, 6); put(Material.GRAY_CONCRETE_POWDER, 6);
        put(Material.LIGHT_GRAY_CONCRETE_POWDER, 6); put(Material.CYAN_CONCRETE_POWDER, 6);
        put(Material.PURPLE_CONCRETE_POWDER, 6); put(Material.BLUE_CONCRETE_POWDER, 6);
        put(Material.BROWN_CONCRETE_POWDER, 6); put(Material.GREEN_CONCRETE_POWDER, 6);
        put(Material.RED_CONCRETE_POWDER, 6); put(Material.BLACK_CONCRETE_POWDER, 6);
        put(Material.ORANGE_TERRACOTTA, 5); put(Material.MAGENTA_TERRACOTTA, 5); put(Material.LIGHT_BLUE_TERRACOTTA, 5);
        put(Material.YELLOW_TERRACOTTA, 5); put(Material.LIME_TERRACOTTA, 5); put(Material.PINK_TERRACOTTA, 5);
        put(Material.GRAY_TERRACOTTA, 5); put(Material.LIGHT_GRAY_TERRACOTTA, 5); put(Material.CYAN_TERRACOTTA, 5);
        put(Material.PURPLE_TERRACOTTA, 5); put(Material.BROWN_TERRACOTTA, 5); put(Material.GREEN_TERRACOTTA, 5);
        put(Material.RED_TERRACOTTA, 5); put(Material.BLACK_TERRACOTTA, 5);
        put(Material.ORANGE_GLAZED_TERRACOTTA, 20); put(Material.MAGENTA_GLAZED_TERRACOTTA, 20);
        put(Material.LIGHT_BLUE_GLAZED_TERRACOTTA, 20); put(Material.YELLOW_GLAZED_TERRACOTTA, 20);
        put(Material.LIME_GLAZED_TERRACOTTA, 20); put(Material.PINK_GLAZED_TERRACOTTA, 20);
        put(Material.GRAY_GLAZED_TERRACOTTA, 20); put(Material.LIGHT_GRAY_GLAZED_TERRACOTTA, 20);
        put(Material.CYAN_GLAZED_TERRACOTTA, 20); put(Material.PURPLE_GLAZED_TERRACOTTA, 20);
        put(Material.BROWN_GLAZED_TERRACOTTA, 20); put(Material.GREEN_GLAZED_TERRACOTTA, 20);
        put(Material.RED_GLAZED_TERRACOTTA, 20); put(Material.BLACK_GLAZED_TERRACOTTA, 20);

        // --- Copper family: every oxidation stage, waxed and not ---
        put(Material.COPPER_BLOCK, 16); put(Material.EXPOSED_COPPER, 16); put(Material.WEATHERED_COPPER, 16);
        put(Material.OXIDIZED_COPPER, 16); put(Material.WAXED_COPPER_BLOCK, 17); put(Material.WAXED_EXPOSED_COPPER, 17);
        put(Material.WAXED_WEATHERED_COPPER, 17); put(Material.WAXED_OXIDIZED_COPPER, 17);
        put(Material.CUT_COPPER, 17); put(Material.EXPOSED_CUT_COPPER, 17); put(Material.WEATHERED_CUT_COPPER, 17);
        put(Material.OXIDIZED_CUT_COPPER, 17); put(Material.WAXED_CUT_COPPER, 18); put(Material.WAXED_EXPOSED_CUT_COPPER, 18);
        put(Material.WAXED_WEATHERED_CUT_COPPER, 18); put(Material.WAXED_OXIDIZED_CUT_COPPER, 18);
        put(Material.RAW_COPPER, 12); put(Material.RAW_COPPER_BLOCK, 100);
        put(Material.RAW_IRON, 26); put(Material.RAW_IRON_BLOCK, 230);
        put(Material.RAW_GOLD, 55); put(Material.RAW_GOLD_BLOCK, 480);
        put(Material.LIGHTNING_ROD, 40);

        // --- Ore blocks (both stone and deepslate variants) - decoration as much as resource ---
        put(Material.COAL_ORE, 20); put(Material.DEEPSLATE_COAL_ORE, 22);
        put(Material.IRON_ORE, 34); put(Material.DEEPSLATE_IRON_ORE, 38);
        put(Material.COPPER_ORE, 18); put(Material.DEEPSLATE_COPPER_ORE, 20);
        put(Material.GOLD_ORE, 70); put(Material.DEEPSLATE_GOLD_ORE, 78);
        put(Material.REDSTONE_ORE, 14); put(Material.DEEPSLATE_REDSTONE_ORE, 16);
        put(Material.LAPIS_ORE, 14); put(Material.DEEPSLATE_LAPIS_ORE, 16);
        put(Material.DIAMOND_ORE, 400); put(Material.DEEPSLATE_DIAMOND_ORE, 440);
        put(Material.EMERALD_ORE, 260); put(Material.DEEPSLATE_EMERALD_ORE, 280);
        put(Material.NETHER_GOLD_ORE, 55); put(Material.NETHER_QUARTZ_ORE, 18);

        // --- Saplings & leaves ---
        put(Material.OAK_SAPLING, 3); put(Material.SPRUCE_SAPLING, 3); put(Material.BIRCH_SAPLING, 3);
        put(Material.JUNGLE_SAPLING, 3); put(Material.ACACIA_SAPLING, 3); put(Material.DARK_OAK_SAPLING, 3);
        put(Material.CHERRY_SAPLING, 4); put(Material.MANGROVE_PROPAGULE, 5);
        put(Material.OAK_LEAVES, 2); put(Material.SPRUCE_LEAVES, 2); put(Material.BIRCH_LEAVES, 2);
        put(Material.JUNGLE_LEAVES, 2); put(Material.ACACIA_LEAVES, 2); put(Material.DARK_OAK_LEAVES, 2);
        put(Material.MANGROVE_LEAVES, 2); put(Material.CHERRY_LEAVES, 3);
        put(Material.AZALEA_LEAVES, 3); put(Material.FLOWERING_AZALEA_LEAVES, 4);

        // --- Mushrooms ---
        put(Material.BROWN_MUSHROOM, 5); put(Material.RED_MUSHROOM, 5);
        put(Material.BROWN_MUSHROOM_BLOCK, 3); put(Material.RED_MUSHROOM_BLOCK, 3); put(Material.MUSHROOM_STEM, 3);

        // --- Ice & snow ---
        put(Material.ICE, 8); put(Material.BLUE_ICE, 20); put(Material.SNOW_BLOCK, 3); put(Material.SNOW, 1);

        // --- Coral - vanilla-obtainable via silk touch, great reef decoration ---
        put(Material.TUBE_CORAL_BLOCK, 25); put(Material.BRAIN_CORAL_BLOCK, 25); put(Material.BUBBLE_CORAL_BLOCK, 25);
        put(Material.FIRE_CORAL_BLOCK, 25); put(Material.HORN_CORAL_BLOCK, 25);
        put(Material.DEAD_TUBE_CORAL_BLOCK, 15); put(Material.DEAD_BRAIN_CORAL_BLOCK, 15);
        put(Material.DEAD_BUBBLE_CORAL_BLOCK, 15); put(Material.DEAD_FIRE_CORAL_BLOCK, 15);
        put(Material.DEAD_HORN_CORAL_BLOCK, 15);
        put(Material.TUBE_CORAL, 15); put(Material.BRAIN_CORAL, 15); put(Material.BUBBLE_CORAL, 15);
        put(Material.FIRE_CORAL, 15); put(Material.HORN_CORAL, 15);
        put(Material.SEAGRASS, 2); put(Material.SEA_PICKLE, 8); put(Material.SEA_LANTERN, 45);

        // --- Light sources & misc decor ---
        put(Material.TORCH, 1); put(Material.SOUL_TORCH, 6); put(Material.REDSTONE_TORCH, 3);
        put(Material.LANTERN, 12); put(Material.SOUL_LANTERN, 18); put(Material.END_ROD, 20);
        put(Material.IRON_BARS, 8); put(Material.FLOWER_POT, 4);
        put(Material.ITEM_FRAME, 10); put(Material.GLOW_ITEM_FRAME, 25); put(Material.PAINTING, 10);
        put(Material.ARMOR_STAND, 35); put(Material.JUKEBOX, 60); put(Material.BEEHIVE, 25);
        put(Material.BEE_NEST, 20); put(Material.CAULDRON, 20); put(Material.CHEST, 15);
        put(Material.TRAPPED_CHEST, 20); put(Material.ENDER_CHEST, 650); put(Material.BOOKSHELF, 20);
        put(Material.CHISELED_BOOKSHELF, 35); put(Material.ANVIL, 300); put(Material.CHIPPED_ANVIL, 230);
        put(Material.DAMAGED_ANVIL, 160); put(Material.BELL, 70); put(Material.SCAFFOLDING, 4);
        put(Material.CONDUIT, 1400); put(Material.TNT, 25);

        // --- Minecarts & rails ---
        put(Material.MINECART, 30); put(Material.CHEST_MINECART, 45); put(Material.FURNACE_MINECART, 45);
        put(Material.HOPPER_MINECART, 90); put(Material.TNT_MINECART, 55);
        put(Material.RAIL, 4); put(Material.POWERED_RAIL, 15); put(Material.DETECTOR_RAIL, 15);
        put(Material.ACTIVATOR_RAIL, 15);

        // --- Boats, including chest variants ---
        put(Material.OAK_BOAT, 20); put(Material.OAK_CHEST_BOAT, 35);
        put(Material.SPRUCE_BOAT, 20); put(Material.SPRUCE_CHEST_BOAT, 35);
        put(Material.BIRCH_BOAT, 20); put(Material.BIRCH_CHEST_BOAT, 35);
        put(Material.JUNGLE_BOAT, 20); put(Material.JUNGLE_CHEST_BOAT, 35);
        put(Material.ACACIA_BOAT, 20); put(Material.ACACIA_CHEST_BOAT, 35);
        put(Material.DARK_OAK_BOAT, 20); put(Material.DARK_OAK_CHEST_BOAT, 35);
        put(Material.MANGROVE_BOAT, 20); put(Material.MANGROVE_CHEST_BOAT, 35);
        put(Material.CHERRY_BOAT, 24); put(Material.CHERRY_CHEST_BOAT, 40);
        put(Material.BAMBOO_RAFT, 18); put(Material.BAMBOO_CHEST_RAFT, 32);

        // --- Horse armor - gear variety beyond player equipment ---
        put(Material.LEATHER_HORSE_ARMOR, 60); put(Material.IRON_HORSE_ARMOR, 220);
        put(Material.GOLDEN_HORSE_ARMOR, 400); put(Material.DIAMOND_HORSE_ARMOR, 1100);
        put(Material.TURTLE_HELMET, 300);

        // --- Deep dark & nature decor, round two ---
        put(Material.SCULK_VEIN, 10); put(Material.SCULK_CATALYST, 400); put(Material.SCULK_SHRIEKER, 500);
        put(Material.ECHO_SHARD, 280); put(Material.SPORE_BLOSSOM, 20); put(Material.HANGING_ROOTS, 8);
        put(Material.BIG_DRIPLEAF, 8); put(Material.SMALL_DRIPLEAF, 5);
        put(Material.VINE, 2); put(Material.TWISTING_VINES, 3); put(Material.WEEPING_VINES, 3);
        put(Material.LILY_PAD, 4);
        put(Material.COARSE_DIRT, 2); put(Material.ROOTED_DIRT, 3); put(Material.DIRT_PATH, 2);

        // --- End dimension decor ---
        put(Material.END_STONE, 6); put(Material.END_STONE_BRICKS, 7);
        put(Material.PURPUR_BLOCK, 25); put(Material.PURPUR_PILLAR, 26);
        // CHORUS_PLANT and CHORUS_FLOWER deliberately excluded - like CAVE_VINES,
        // they're structural/connector blocks with no corresponding item, so
        // `new ItemStack(material, amount)` throws for them at runtime even
        // though the Material constant itself compiles fine.

        // --- Loose-end single items ---
        put(Material.BOOK, 20); put(Material.WRITABLE_BOOK, 10); put(Material.PAPER, 3);
        put(Material.GLASS_BOTTLE, 2); put(Material.BUNDLE, 100); put(Material.RECOVERY_COMPASS, 650);
        put(Material.BRUSH, 60);

        // --- Armor trim smithing templates ---
        // Pulled from BuildingBlockShop's own list (itself scanned off
        // Material.values(), not hand-typed) rather than naming every
        // *_SMITHING_TEMPLATE constant here by hand - avoids a typo'd enum
        // constant breaking the build and automatically covers any new
        // trim a future game version adds. The netherite upgrade template
        // is priced steeper since it's the one that actually matters for
        // gearing up, not just cosmetics.
        for (Material trim : BuildingBlockShop.ARMOR_TRIMS) {
            if (!BASE_PRICES.containsKey(trim)) {
                put(trim, trim.name().equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE") ? 2500 : 350);
            }
        }

        // --- Ores & valuable storage blocks - "not just cobbled etc" ---
        // Also pulled from BuildingBlockShop's scanned list rather than
        // hand-typed. Priced by tier rather than per-block: an ore block
        // (mine it, get ~1 of the raw resource) is priced like one unit of
        // that resource; a storage/raw block (9 items compacted into one
        // block, same as the crafting recipe) is priced at roughly 9x that.
        Map<String, Long> oreTierUnitPrice = new LinkedHashMap<>();
        oreTierUnitPrice.put("NETHERITE", 5500L);
        oreTierUnitPrice.put("DIAMOND", 350L);
        oreTierUnitPrice.put("EMERALD", 220L);
        oreTierUnitPrice.put("GOLD", 60L);
        oreTierUnitPrice.put("AMETHYST", 35L);
        oreTierUnitPrice.put("IRON", 30L);
        oreTierUnitPrice.put("QUARTZ", 16L);
        oreTierUnitPrice.put("COAL", 18L);
        oreTierUnitPrice.put("COPPER", 14L);
        oreTierUnitPrice.put("REDSTONE", 10L);
        oreTierUnitPrice.put("LAPIS", 10L);
        for (Material ore : BuildingBlockShop.ORES_AND_VALUABLES) {
            if (BASE_PRICES.containsKey(ore)) continue;
            String name = ore.name();
            long unit = 20L; // fallback for anything that doesn't match a known tier
            for (Map.Entry<String, Long> tier : oreTierUnitPrice.entrySet()) {
                if (name.contains(tier.getKey())) { unit = tier.getValue(); break; }
            }
            boolean storageOrRawBlock = name.endsWith("_BLOCK");
            put(ore, storageOrRawBlock ? unit * 9 : unit);
        }

        // --- Everything else not banned and not already priced above ---
        // The long catch-all tail (tools, weapons, armor, food, potions,
        // wind charges, and anything else BASE_PRICES didn't already get a
        // hand-tuned entry for) gets a modest default so it's purchasable
        // without undercutting the hand-tuned prices above (which still win,
        // since put() here only adds materials with no existing entry).
        for (Material material : BuildingBlockShop.EVERYTHING_ELSE) {
            if (!BASE_PRICES.containsKey(material)) {
                put(material, 15L);
            }
        }
    }

    private static void put(Material material, long price) {
        if (BLOCKED.contains(material)) return; // never let a blocked material sneak into the pool
        BASE_PRICES.put(material, price);
    }

    private final PenguinMafiaMarket plugin;
    private final MarketManager market;
    private final Random random = new Random();

    public MarketBotManager(PenguinMafiaMarket plugin, MarketManager market) {
        this.plugin = plugin;
        this.market = market;
    }

    /**
     * Starts the restock timer - an immediate first stock, then every
     * REFRESH_INTERVAL after that. Also runs the same underpriced-listing
     * audit /bm restock prints in chat, every time, automatically - without
     * this, a stale/bugged listing only ever gets surfaced if an op happens
     * to notice a price looks wrong in the GUI and thinks to run /bm restock
     * themselves. Logged to console either way, and also pushed straight to
     * any op who's online, so it doesn't depend on anyone remembering to
     * check.
     */
    public static MarketBotManager start(PenguinMafiaMarket plugin, MarketManager market) {
        MarketBotManager manager = new MarketBotManager(plugin, market);
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.refresh();
                manager.reportUnderpricedListings();
            }
        }.runTaskTimer(plugin, 100L, REFRESH_INTERVAL_TICKS);
        return manager;
    }

    /**
     * Runs auditUnderpricedListings() and pushes whatever it finds to the
     * console (always) and to every online op (so it's seen without anyone
     * needing to run /bm restock by hand). Shares the exact same check
     * /bm restock's chat report uses, just fired automatically instead of
     * only on a manual trigger.
     */
    private void reportUnderpricedListings() {
        List<String> problems;
        try {
            problems = auditUnderpricedListings();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Black Market Dealer: automatic underpriced-listing audit failed.", e);
            return;
        }
        if (problems.isEmpty()) return;

        plugin.getLogger().warning("Black Market: " + problems.size() + " listing(s) still look underpriced:");
        for (String problem : problems) {
            plugin.getLogger().warning(" - " + problem);
        }

        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!op.hasPermission("penguinmafia.bm.admin")) continue;
            op.sendMessage(org.bukkit.ChatColor.YELLOW + "[Black Market] " + problems.size()
                    + " listing(s) still look underpriced - see console for the full list ("
                    + "dealer ones get auto-purged next restock; player ones need /bm admin setprice or refund).");
        }
    }

    /**
     * Walks every item in BASE_PRICES and creates fresh full-stack listings
     * for it until it has at least MIN_STOCK_STACKS of them - never wipes or
     * replaces what's already listed, and a material already at or above
     * that count (because players haven't bought much of it, say) is left
     * untouched rather than piled even higher. Also the handler behind the
     * op-only /bm restock command, so the exact same logic runs whether
     * it's the timer or a person firing it early.
     *
     * @return how many item types received new stock this round
     */
    public int refresh() {
        try {
            purgeSpawnEggListings();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Black Market Dealer: spawn egg purge failed, skipping it this round.", e);
        }

        try {
            purgeStaleUnderpricedListings();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Black Market Dealer: stale-listing purge failed, skipping it this round.", e);
        }

        double inflationMultiplier;
        try {
            inflationMultiplier = computeInflationMultiplier();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Black Market Dealer: inflation multiplier calculation failed, defaulting to 1.0x.", e);
            inflationMultiplier = 1.0;
        }

        Map<Material, List<Listing>> existingByMaterial = groupDealerListings();

        int restocked = 0;
        for (Map.Entry<Material, Long> entry : BASE_PRICES.entrySet()) {
            Material material = entry.getKey();
            if (BLOCKED.contains(material)) continue; // safety net, should never actually trigger

            try {
                long unitPrice = entry.getValue();
                // Capped at 99 too, belt-and-suspenders - every real vanilla max
                // stack size is 64 or less, but Paper's own item serialization
                // rejects any stack count outside 1-99 outright, so nothing this
                // class builds should ever be able to exceed that ceiling.
                int stackSize = Math.min(material.getMaxStackSize(), MAX_SERIALIZABLE_STACK);

                // Heavy Core/Mace have an explicit, non-negotiable price
                // floor (a stack of 64 must never go under 73M - see the
                // comment on their BASE_PRICES entry). The inflation
                // multiplier can go as low as 0.5x if the server's coin
                // supply ever shrinks below its baseline, which would let a
                // stack drop to ~36.7M - well under that floor. These two
                // items only ever get more expensive as the economy grows,
                // never cheaper, so that floor holds no matter what the
                // economy does.
                double effectiveMultiplier = (material == Material.HEAVY_CORE || material == Material.MACE)
                        ? Math.max(1.0, inflationMultiplier) : inflationMultiplier;

                List<Listing> existing = existingByMaterial.computeIfAbsent(material, m -> new ArrayList<>());
                boolean addedAny = false;
                while (existing.size() < MIN_STOCK_STACKS) {
                    // +/-15% variance so the dealer doesn't look like a flat, copy-pasted price list
                    double variance = 0.85 + random.nextDouble() * 0.30;
                    long price = Math.max(1, Math.round(unitPrice * stackSize * variance * effectiveMultiplier));

                    Listing created = market.createSystemListing(SELLER_ID, SELLER_NAME,
                            new ItemStack(material, stackSize), price);
                    existing.add(created);
                    addedAny = true;
                }
                if (addedAny) restocked++;
            } catch (Exception e) {
                // A handful of Material constants compile fine but have no
                // real item form (CAVE_VINES was the first one found this way,
                // the hard way, via a server log) - new ItemStack(material, n)
                // throws for those (IllegalArgumentException normally, but
                // caught broadly here since a future game version could throw
                // something else for the same underlying reason). Rather than
                // letting one bad material take down the whole restock task
                // (and every item type after it in this pass, and the /bm
                // restock command itself) until someone notices and
                // redeploys, skip just this one and keep going.
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Black Market Dealer couldn't stock " + material + " - skipping it.", e);
            }
        }

        try {
            restockEnchantedBooks(inflationMultiplier);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Black Market Dealer: enchanted book restock failed, skipping it this round.", e);
        }

        market.save();
        plugin.getLogger().info("Black Market Dealer added stock to " + restocked
                + " item type(s) (existing listings left in place, " + inflationMultiplier + "x inflation).");
        return restocked;
    }

    /** Clamp bounds for computeInflationMultiplier() - see its javadoc. */
    private static final double MIN_INFLATION_MULTIPLIER = 0.5;
    private static final double MAX_INFLATION_MULTIPLIER = 10.0;

    /**
     * Prices aren't meant to be fixed forever - as the server's total Frozen
     * Coin supply grows (job payouts, playtime rewards, AFK farms, selling
     * to other players...), a hardcoded price slowly becomes trivial to
     * afford, so every new listing this restock creates gets scaled by how
     * much the economy has grown since tracking started.
     *
     * The very first time this runs, today's total circulating coins becomes
     * the permanent 1.0x baseline (persisted in economy.yml, not reset by a
     * restart); every later restock compares the *current* total back to
     * that same baseline. A server that's since paid out twice as many
     * coins as it had at that baseline prices new stock at ~2x; one that's
     * barely grown stays near 1x. Clamped to [0.5x, 10x] so an early-game
     * near-zero economy or a runaway late-game one can't send prices to
     * absurd extremes in either direction.
     */
    private double computeInflationMultiplier() {
        Economy economy = market.getEconomy();
        long baseline = economy.getInflationBaseline();
        long currentTotal = economy.getTotalCirculatingCoins();

        if (baseline <= 0) {
            baseline = Math.max(currentTotal, 1000L);
            economy.setInflationBaseline(baseline);
        }

        double multiplier = currentTotal / (double) baseline;
        return Math.max(MIN_INFLATION_MULTIPLIER, Math.min(MAX_INFLATION_MULTIPLIER, multiplier));
    }

    /**
     * One-time cleanup, re-run (cheaply) on every refresh: deletes any
     * dealer-owned listing for a spawn egg. An earlier build briefly let the
     * "everything else" catch-all restock every mob's spawn egg before that
     * was caught and excluded - this clears out whatever it already placed,
     * so the villager egg (bought only through /shop) stays the only spawn
     * egg obtainable anywhere on the server. Harmless to run every time:
     * once they're gone, this is just an empty scan.
     */
    private void purgeSpawnEggListings() {
        int removed = 0;
        for (Listing listing : market.getListingsBy(SELLER_ID)) {
            if (listing.item.getType().name().endsWith("_SPAWN_EGG")) {
                market.removeListing(listing.id);
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Black Market Dealer: removed " + removed
                    + " spawn egg listing(s) - no spawn egg belongs on /bm.");
        }
    }

    /**
     * One-time-in-effect, safe-to-rerun-forever cleanup: deletes any
     * dealer-owned listing still sitting at (or near) a long-stale price for
     * its material - most importantly, the flat 15-coin "everything not
     * banned" catch-all price every ore, trim, valuable block, hoe/shovel
     * tier, and Heavy Core/Mace briefly got stocked at before this repricing
     * pass gave each of those a real, fair BASE_PRICES entry. Excluding
     * spawn eggs (purgeSpawnEggListings already strips those) and Enchanted
     * Books (priced per-preset, not from BASE_PRICES - restockEnchantedBooks
     * handles those directly), this compares every dealer listing's actual
     * per-item price against that material's current fair unit price and
     * removes anything well below what even the lowest legitimate roll could
     * produce (stack * unit * 0.85 variance * 0.5x inflation floor = 0.425x
     * fair - 0.3x gives real headroom below that without being so low it'd
     * ever let a genuine old bugged listing slip through). The normal
     * restock loop right after this immediately replaces whatever it
     * removes at the real price, so once every stale listing is gone this is
     * just an empty scan on every later restock.
     */
    /**
     * The lowest price a single restock roll could ever legitimately produce
     * for this material's current fair unit price - unitPrice * amount *
     * the lowest variance roll (0.85) * the lowest inflation multiplier that
     * can ever apply to it. For almost everything that's the global 0.5x
     * floor (see MIN_INFLATION_MULTIPLIER); Heavy Core/Mace use 1.0x instead
     * (see the effectiveMultiplier comment in refresh()'s stocking loop) so
     * their own explicit 73M-a-stack floor can't be undercut by this helper
     * thinking a lower price is still plausible.
     */
    private long minLegitimateStackPrice(Material type, long fairUnitPrice, int amount) {
        double minMultiplier = (type == Material.HEAVY_CORE || type == Material.MACE)
                ? 1.0 : MIN_INFLATION_MULTIPLIER;
        return Math.round(fairUnitPrice * amount * 0.85 * minMultiplier);
    }

    private void purgeStaleUnderpricedListings() {
        int removed = 0;
        for (Listing listing : market.getListingsBy(SELLER_ID)) {
            Material type = listing.item.getType();
            if (type == Material.ENCHANTED_BOOK) continue; // priced per-preset, not from BASE_PRICES

            Long fairUnitPrice = BASE_PRICES.get(type);
            if (fairUnitPrice == null) continue; // not something this class prices - leave it alone

            int amount = Math.max(1, listing.item.getAmount());
            // 0.9x of the true legitimate minimum: always below anything a
            // real restock roll could produce (so a genuine listing is
            // never falsely purged), while still well above what a bugged
            // flat-catch-all price (or the old Heavy Core exploit) actually
            // looks like.
            long perStackFloor = Math.round(minLegitimateStackPrice(type, fairUnitPrice, amount) * 0.9);

            if (listing.price < perStackFloor) { // well below fair - a leftover from a since-fixed price
                market.removeListing(listing.id);
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Black Market Dealer: removed " + removed
                    + " stale underpriced listing(s) left over from before the last repricing pass.");
        }
    }

    /**
     * Same "is this well below fair" check purgeStaleUnderpricedListings()
     * runs against the dealer's own stock, but reports instead of removing,
     * and runs against EVERY listing on the market - a player's own /bm sell
     * listing can be just as underpriced as a stale dealer one (MarketCommand
     * now blocks new player listings below this same floor, but that can't
     * retroactively fix one that was already live before that check shipped,
     * or one someone lists for a friend at a price this class doesn't know
     * about some other way). Called right after a restock so whoever
     * triggered it can see in chat, by exact Material name, listing id,
     * seller and price, anything still priced wrong RIGHT NOW - a GUI
     * tooltip's display name can't reliably be matched back to the
     * Material/pricing rule that produced it, so this reports the ground
     * truth the pricing code actually keyed off of. A flagged dealer listing
     * gets auto-purged and replaced next restock; a flagged player listing
     * needs an op to step in with /bm admin setprice <id> <price> (or
     * refund) since it's their item, not the dealer's stock.
     */
    public List<String> auditUnderpricedListings() {
        List<String> problems = new ArrayList<>();
        for (Listing listing : market.getAllListings()) {
            Material type = listing.item.getType();
            boolean isDealer = listing.seller.equals(SELLER_ID);

            if (type == Material.ENCHANTED_BOOK) {
                // Blank-book check only makes sense for the dealer's own
                // stock (restockEnchantedBooks should never create one) - a
                // player owning/selling a blank enchanted book isn't a bug.
                if (isDealer) {
                    if (!(listing.item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
                        problems.add("#" + listing.id + " ENCHANTED_BOOK (dealer) priced " + listing.price
                                + " - item meta isn't EnchantmentStorageMeta");
                    } else if (meta.getStoredEnchants().isEmpty()) {
                        problems.add("#" + listing.id + " ENCHANTED_BOOK (dealer) priced " + listing.price
                                + " - blank, no enchantment stored");
                    }
                }
                continue;
            }

            Long fairUnitPrice = BASE_PRICES.get(type);
            int amount = Math.max(1, listing.item.getAmount());

            if (fairUnitPrice == null) {
                // Not in BASE_PRICES at all. Normal for a player (they can
                // own all sorts of things this class never prices); only
                // worth flagging when it's sitting under the dealer's own
                // name, since this class should never have created it then.
                if (isDealer) {
                    problems.add("#" + listing.id + " " + type + " x" + amount + " priced " + listing.price
                            + " (dealer) - NOT in BASE_PRICES at all (this class never priced this material)");
                }
                continue;
            }

            long fairStackPrice = fairUnitPrice * amount;
            long floor = Math.round(minLegitimateStackPrice(type, fairUnitPrice, amount) * 0.9);
            if (listing.price < floor) {
                problems.add("#" + listing.id + " " + type + " x" + amount + " priced " + listing.price
                        + " by " + (isDealer ? "dealer" : listing.sellerName)
                        + " (base unit price " + fairUnitPrice + ", fair stack price ~" + fairStackPrice + ")");
            }
        }
        return problems;
    }

    // ================================================================
    // Enchanted books - sold with a real, fixed enchantment on them
    // instead of a blank, useless "Enchanted Book" that used to fall into
    // the flat 15-coin catch-all. Each preset keeps exactly one listing in
    // stock at a time (there's no "stack" of a specific enchanted book the
    // way there's a stack of cobblestone), re-created once bought.
    // ================================================================

    private record EnchantPreset(Enchantment enchantment, int level, long price) {}

    private static final List<EnchantPreset> ENCHANT_PRESETS = List.of(
            new EnchantPreset(Enchantment.MENDING, 1, 4500),
            new EnchantPreset(Enchantment.SILK_TOUCH, 1, 1200),
            new EnchantPreset(Enchantment.FORTUNE, 3, 1600),
            new EnchantPreset(Enchantment.LOOTING, 3, 1400),
            new EnchantPreset(Enchantment.SHARPNESS, 5, 1800),
            new EnchantPreset(Enchantment.PROTECTION, 4, 1600),
            new EnchantPreset(Enchantment.UNBREAKING, 3, 500),
            new EnchantPreset(Enchantment.EFFICIENCY, 5, 900),
            new EnchantPreset(Enchantment.POWER, 5, 1300),
            new EnchantPreset(Enchantment.INFINITY, 1, 2500),
            new EnchantPreset(Enchantment.FLAME, 1, 300),
            new EnchantPreset(Enchantment.PUNCH, 2, 250),
            new EnchantPreset(Enchantment.RESPIRATION, 3, 350),
            new EnchantPreset(Enchantment.AQUA_AFFINITY, 1, 250),
            new EnchantPreset(Enchantment.FEATHER_FALLING, 4, 500),
            new EnchantPreset(Enchantment.FIRE_PROTECTION, 4, 500),
            new EnchantPreset(Enchantment.THORNS, 3, 700),
            new EnchantPreset(Enchantment.SWEEPING_EDGE, 3, 600),
            new EnchantPreset(Enchantment.MULTISHOT, 1, 500),
            new EnchantPreset(Enchantment.QUICK_CHARGE, 3, 400),
            new EnchantPreset(Enchantment.PIERCING, 4, 400),
            new EnchantPreset(Enchantment.RIPTIDE, 3, 900),
            new EnchantPreset(Enchantment.LOYALTY, 3, 500),
            new EnchantPreset(Enchantment.CHANNELING, 1, 1500),
            new EnchantPreset(Enchantment.IMPALING, 5, 500),
            new EnchantPreset(Enchantment.DEPTH_STRIDER, 3, 400),
            new EnchantPreset(Enchantment.SOUL_SPEED, 3, 700),
            new EnchantPreset(Enchantment.SWIFT_SNEAK, 3, 700),
            new EnchantPreset(Enchantment.LUCK_OF_THE_SEA, 3, 350),
            new EnchantPreset(Enchantment.LURE, 3, 300),
            new EnchantPreset(Enchantment.BINDING_CURSE, 1, 200),
            new EnchantPreset(Enchantment.VANISHING_CURSE, 1, 150),
            new EnchantPreset(Enchantment.KNOCKBACK, 2, 150),
            new EnchantPreset(Enchantment.FIRE_ASPECT, 2, 350),
            new EnchantPreset(Enchantment.SMITE, 5, 350),
            new EnchantPreset(Enchantment.BANE_OF_ARTHROPODS, 5, 300),
            // 1.21 Mace enchantments - new and rare, priced accordingly
            new EnchantPreset(Enchantment.WIND_BURST, 3, 2200),
            new EnchantPreset(Enchantment.DENSITY, 5, 2000),
            new EnchantPreset(Enchantment.BREACH, 4, 2000)
    );

    /**
     * Makes sure each preset in ENCHANT_PRESETS has exactly one matching
     * listing in stock, creating one (at the given inflation-adjusted price)
     * whenever it's missing - whether because it's never been stocked
     * before, or because a player just bought the last copy.
     */
    private void restockEnchantedBooks(double inflationMultiplier) {
        List<Listing> existingBooks = new ArrayList<>();
        for (Listing listing : market.getListingsBy(SELLER_ID)) {
            if (listing.item.getType() == Material.ENCHANTED_BOOK) existingBooks.add(listing);
        }

        int restocked = 0;
        for (EnchantPreset preset : ENCHANT_PRESETS) {
            try {
                boolean inStock = existingBooks.stream().anyMatch(listing -> matchesPreset(listing.item, preset));
                if (inStock) continue;

                if (preset.enchantment() == null) {
                    // Defensive: a static Enchantment field resolving to null
                    // (e.g. a brand-new enchantment constant not yet backed
                    // by the server's registry) would otherwise NPE inside
                    // addStoredEnchant and, uncaught, take out every preset
                    // after it in the list along with the /bm restock command.
                    plugin.getLogger().warning("Black Market Dealer: an enchanted book preset has a null "
                            + "Enchantment (level " + preset.level() + ", price " + preset.price()
                            + ") - skipping it.");
                    continue;
                }

                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(preset.enchantment(), preset.level(), true);
                book.setItemMeta(meta);

                long price = Math.max(1, Math.round(preset.price() * inflationMultiplier));
                market.createSystemListing(SELLER_ID, SELLER_NAME, book, price);
                restocked++;
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Black Market Dealer couldn't stock an enchanted book preset (" + preset + ") - skipping it.", e);
            }
        }
        if (restocked > 0) {
            plugin.getLogger().info("Black Market Dealer restocked " + restocked + " enchanted book listing(s).");
        }
    }

    private boolean matchesPreset(ItemStack item, EnchantPreset preset) {
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return false;
        Map<Enchantment, Integer> stored = meta.getStoredEnchants();
        return stored.size() == 1 && preset.level() == stored.getOrDefault(preset.enchantment(), -1);
    }

    private Map<Material, List<Listing>> groupDealerListings() {
        Map<Material, List<Listing>> byMaterial = new HashMap<>();
        for (Listing listing : market.getListingsBy(SELLER_ID)) {
            Material material = listing.item.getType();
            int maxSafe = Math.min(material.getMaxStackSize(), MAX_SERIALIZABLE_STACK);
            if (listing.item.getAmount() > maxSafe) {
                plugin.getLogger().warning("Clamping oversized Black Market Dealer listing #" + listing.id
                        + " (" + material + " x" + listing.item.getAmount() + ") down to " + maxSafe
                        + " - item stacks can't serialize above that.");
                listing.item.setAmount(maxSafe);
            }
            byMaterial.computeIfAbsent(material, m -> new ArrayList<>()).add(listing);
        }
        return byMaterial;
    }
}
