package net.penguinmafia.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Tags a GUI Inventory as ours and remembers what it's showing (page, mode, active search filter). */
public class MarketHolder implements InventoryHolder {

    public enum Mode { BROWSE, MY_LISTINGS }

    private final int page;
    private final Mode mode;
    private final String filter;
    private Inventory inventory;

    public MarketHolder(int page, Mode mode, String filter) {
        this.page = page;
        this.mode = mode;
        this.filter = filter;
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

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
