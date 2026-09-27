package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Broad buckets used by the market's category filter button. */
public enum MarketCategory {
    ALL("All Items"),
    BLOCKS("Blocks"),
    ARMOR("Armor"),
    MATERIALS("Materials");

    public final String label;

    MarketCategory(String label) {
        this.label = label;
    }

    public MarketCategory next() {
        MarketCategory[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Classifies an item into one of the non-ALL buckets. */
    public static MarketCategory of(ItemStack item) {
        Material type = item.getType();
        String name = type.name();
        boolean armor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_HORSE_ARMOR") || type == Material.ELYTRA
                || type == Material.SHIELD || type == Material.TURTLE_HELMET;
        if (armor) return ARMOR;
        if (type.isBlock()) return BLOCKS;
        return MATERIALS;
    }
}
