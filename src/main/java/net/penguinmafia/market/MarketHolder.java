package net.penguinmafia.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Tags a GUI Inventory as ours and remembers what it's showing (page, mode, search filter, sort, category). */
public class MarketHolder implements InventoryHolder {

    public enum Mode { BROWSE, MY_LISTINGS }

    private final int page;
    private final Mode mode;
    private final String filter;
    private final boolean sortDescending;
    private final MarketCategory category;
    private Inventory inventory;

    public MarketHolder(int page, Mode mode, String filter, boolean sortDescending, MarketCategory category) {
        this.page = page;
        this.mode = mode;
        this.filter = filter;
        this.sortDescending = sortDescending;
        this.category = category;
    }

    public int getPage() {
        return page;
    }

    public Mode getMode() {
        return mode;
    }

    public String getFilter() {
        return filter;
    }

    public boolean isSortDescending() {
        return sortDescending;
    }

    public MarketCategory getCategory() {
        return category;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
