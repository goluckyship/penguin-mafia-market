package net.penguinmafia.market;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Opens the Warehouse GUI and saves it back out the moment it's closed -
 * there's no "confirm" step, closing the inventory (however that happens:
 * pressing Escape, logging out, a plugin closing it) is what commits
 * whatever's currently sitting in it. Tracks which players currently have
 * their own Warehouse open so a close event only saves genuine Warehouse
 * sessions, never some other plugin's inventory.
 */
public class WarehouseGUIListener implements Listener {

    private final WarehouseManager warehouse;
    private final Set<UUID> openSessions = new HashSet<>();

    public WarehouseGUIListener(WarehouseManager warehouse) {
        this.warehouse = warehouse;
    }

    public void open(Player player) {
        Inventory inventory = warehouse.open(player);
        openSessions.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        HumanEntity entity = event.getPlayer();
        if (!openSessions.remove(entity.getUniqueId())) return;

        warehouse.storeContents(entity.getUniqueId(), event.getInventory().getContents());
    }
}
