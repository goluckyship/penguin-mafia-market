package net.penguinmafia.market;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed catalog for /shop: plain building and decoration blocks only -
 * wool, terracotta, concrete, glass, planks, stone and brick variants, and
 * the like - plus the one-off villager spawn egg special. Deliberately
 * leaves out anything rare or valuable (ores, ore/mineral storage blocks,
 * netherite, armor trims, and so on), so the shop is a convenience for
 * builders rather than a way to buy power; that "sell everything not
 * banned" tail belongs on the Black Market Dealer (/bm) instead, so it's
 * only computed here (ORES_AND_VALUABLES, ARMOR_TRIMS, EVERYTHING_ELSE) for
 * MarketBotManager to price and stock, never added to /shop's own CATEGORIES.
 */
public final class BuildingBlockShop {

    public static class Category {
        public final String name;
        public final List<Material> blocks;

        Category(String name, Material... blocks) {
            this.name = name;
            this.blocks = Arrays.asList(blocks);
        }
    }

    public static final List<Category> CATEGORIES = new ArrayList<>();
    public static final List<String> TAB_NAMES = new ArrayList<>();
    private static final Map<String, Material> BY_NAME = new HashMap<>();

    /**
     * Everything that isn't a plain building block or the villager egg
     * special, obtainable and not on the banned list - ores and their
     * storage blocks, armor trim smithing templates, and a long catch-all
     * tail of tools/weapons/armor/food/etc. Scanned off Material.values()
     * rather than hand-typed, so nothing is missed and nothing needs
     * updating when a future game version adds more. None of these three
     * lists are added to /shop's CATEGORIES - they exist purely so
     * MarketBotManager can stock and price them on the Black Market Dealer
     * (/bm) instead, per the server owner's call on where "sell everything"
     * belongs.
     */
    public static final List<Material> ORES_AND_VALUABLES = new ArrayList<>();
    public static final List<Material> ARMOR_TRIMS = new ArrayList<>();
    public static final List<Material> EVERYTHING_ELSE = new ArrayList<>();

    private static void add(String categoryName, Material... blocks) {
        CATEGORIES.add(new Category(categoryName, blocks));
        for (Material material : blocks) {
            String key = material.name().toLowerCase();
            BY_NAME.put(key, material);
            TAB_NAMES.add(key);
        }
    }

    static {
        add("Wool",
                Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
                Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
                Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
                Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL);

        add("Terracotta",
                Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
                Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
                Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
                Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
                Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA,
                Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA);

        add("Glazed Terracotta",
                Material.WHITE_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA,
                Material.MAGENTA_GLAZED_TERRACOTTA, Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
                Material.YELLOW_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
                Material.PINK_GLAZED_TERRACOTTA, Material.GRAY_GLAZED_TERRACOTTA,
                Material.LIGHT_GRAY_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA,
                Material.PURPLE_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA,
                Material.BROWN_GLAZED_TERRACOTTA, Material.GREEN_GLAZED_TERRACOTTA,
                Material.RED_GLAZED_TERRACOTTA, Material.BLACK_GLAZED_TERRACOTTA);

        add("Concrete",
                Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
                Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
                Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
                Material.BLACK_CONCRETE);

        add("Concrete Powder",
                Material.WHITE_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER,
                Material.MAGENTA_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER,
                Material.YELLOW_CONCRETE_POWDER, Material.LIME_CONCRETE_POWDER,
                Material.PINK_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER,
                Material.LIGHT_GRAY_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER,
                Material.PURPLE_CONCRETE_POWDER, Material.BLUE_CONCRETE_POWDER,
                Material.BROWN_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER,
                Material.RED_CONCRETE_POWDER, Material.BLACK_CONCRETE_POWDER);

        add("Glass",
                Material.GLASS, Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS,
                Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
                Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS);

        add("Planks",
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
                Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS,
                Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS, Material.CRIMSON_PLANKS,
                Material.WARPED_PLANKS);

        add("Logs",
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
                Material.CRIMSON_STEM, Material.WARPED_STEM);

        add("Stone & Bricks",
                Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.SMOOTH_STONE,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
                Material.CHISELED_STONE_BRICKS, Material.BRICKS, Material.MUD_BRICKS,
                Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
                Material.CRACKED_DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.COBBLED_DEEPSLATE,
                Material.CHISELED_DEEPSLATE, Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS,
                Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.ANDESITE, Material.POLISHED_ANDESITE,
                Material.DIORITE, Material.POLISHED_DIORITE, Material.GRANITE, Material.POLISHED_GRANITE);

        add("Sandstone",
                Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE,
                Material.SMOOTH_SANDSTONE, Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE,
                Material.CUT_RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE);

        add("Quartz",
                Material.QUARTZ_BLOCK, Material.CHISELED_QUARTZ_BLOCK, Material.QUARTZ_PILLAR,
                Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ);

        add("Prismarine",
                Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE);

        add("Nether",
                Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
                Material.CRACKED_NETHER_BRICKS, Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK,
                Material.SOUL_SAND, Material.SOUL_SOIL, Material.BASALT, Material.POLISHED_BASALT,
                Material.SMOOTH_BASALT, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE,
                Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                Material.CHISELED_POLISHED_BLACKSTONE);

        add("End",
                Material.END_STONE, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK,
                Material.PURPUR_PILLAR);

        add("Misc",
                Material.GLOWSTONE, Material.SEA_LANTERN, Material.HONEYCOMB_BLOCK, Material.OBSIDIAN,
                Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.CLAY,
                Material.HAY_BLOCK, Material.BONE_BLOCK, Material.MUD, Material.PACKED_MUD,
                Material.MOSS_BLOCK, Material.COPPER_BLOCK, Material.CUT_COPPER);

        // Not a building block - a one-off expensive special sold per-item
        // rather than per-stack (see ShopGUI.CUSTOM_UNIT_PRICE).
        add("Special", Material.VILLAGER_SPAWN_EGG);

        // --- Everything below is NOT added to /shop. It's computed here so
        // MarketBotManager can stock it on the Black Market Dealer instead. ---

        // Materials that must never be sold anywhere no matter what - the
        // same creative-only/admin-only/structurally-special set the Black
        // Market Dealer's BLOCKED already refuses, plus a couple more that
        // only matter once "sell literally everything else" is on the table.
        Set<Material> banned = EnumSet.of(
                Material.DRAGON_EGG, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
                Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
                Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.BARRIER,
                Material.DEBUG_STICK, Material.JIGSAW, Material.LIGHT, Material.BEDROCK,
                Material.END_PORTAL_FRAME, Material.SPAWNER, Material.TRIAL_SPAWNER,
                Material.VAULT, Material.REINFORCED_DEEPSLATE, Material.PETRIFIED_OAK_SLAB,
                Material.KNOWLEDGE_BOOK
        );

        // Everything already in /shop (by Material, not by category) so
        // nothing gets stocked in both places.
        Set<Material> alreadyInShop = new HashSet<>(BY_NAME.values());

        for (Material material : Material.values()) {
            if (!material.isItem() || material.isLegacy()) continue;
            if (banned.contains(material)) continue;
            if (alreadyInShop.contains(material)) continue;
            // Spawn eggs are never for sale here, no matter the mob - the
            // villager egg (already in /shop, handled above) is the only
            // spawn egg members can buy anywhere on the server.
            if (material.name().endsWith("_SPAWN_EGG")) continue;

            String name = material.name();
            boolean oreLike = name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")
                    || name.equals("RAW_IRON_BLOCK") || name.equals("RAW_GOLD_BLOCK")
                    || name.equals("RAW_COPPER_BLOCK")
                    || (name.endsWith("_BLOCK") && (name.contains("DIAMOND") || name.contains("EMERALD")
                        || name.contains("GOLD") || name.contains("IRON") || name.contains("LAPIS")
                        || name.contains("REDSTONE") || name.contains("NETHERITE") || name.contains("COAL")
                        || name.contains("AMETHYST") || name.contains("COPPER")));

            if (oreLike) {
                ORES_AND_VALUABLES.add(material);
            } else if (name.endsWith("_SMITHING_TEMPLATE")) {
                ARMOR_TRIMS.add(material);
            } else {
                EVERYTHING_ELSE.add(material);
            }
        }

        ORES_AND_VALUABLES.sort(Comparator.comparing(Enum::name));
        ARMOR_TRIMS.sort(Comparator.comparing(Enum::name));
        EVERYTHING_ELSE.sort(Comparator.comparing(Enum::name));
    }

    private BuildingBlockShop() {
    }

    public static Material lookup(String name) {
        if (name == null) return null;
        return BY_NAME.get(name.toLowerCase());
    }
}
