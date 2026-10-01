package net.penguinmafia.market;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed catalog for /shop: plain building and decoration blocks only -
 * wool, terracotta, concrete, glass, planks, stone and brick variants, and
 * the like. Deliberately leaves out anything rare or valuable (ore/mineral
 * storage blocks, netherite, and so on), so the shop is a convenience for
 * builders rather than a way to buy power.
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
    }

    private BuildingBlockShop() {
    }

    public static Material lookup(String name) {
        if (name == null) return null;
        return BY_NAME.get(name.toLowerCase());
    }
}
