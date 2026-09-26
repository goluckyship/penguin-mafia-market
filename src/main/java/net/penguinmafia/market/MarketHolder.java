package net.penguinmafia.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Tags a GUI Inventory as ours and remembers which page it's showing. */
public class MarketHolder implements InventoryHolder {
    private final int page;
    private Inventory inventory;

    public MarketHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
