package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles clicks inside the /shop catalog and quantity-picker GUIs. Both are
 * view/click-only - items can never be taken out or shift-clicked in from
 * the player's own inventory.
 */
public class ShopGUIListener implements Listener {

    private final ShopGUI shopGUI;

    public ShopGUIListener(ShopGUI shopGUI) {
        this.shopGUI = shopGUI;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();

        if (rawHolder instanceof ShopGUI.CatalogHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            if (event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof ShopGUI.CatalogHolder)) {
                return;
            }
            handleCatalogClick((Player) event.getWhoClicked(), (ShopGUI.CatalogHolder) rawHolder, event);

        } else if (rawHolder instanceof ShopGUI.QuantityHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            if (event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof ShopGUI.QuantityHolder)) {
                return;
            }
            handleQuantityClick((Player) event.getWhoClicked(), (ShopGUI.QuantityHolder) rawHolder, event);
        }
    }

    private void handleCatalogClick(Player player, ShopGUI.CatalogHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 45 && holder.page > 0) {
            shopGUI.openCatalog(player, holder.page - 1);
            return;
        }
        if (slot == 53 && holder.page < shopGUI.getTotalPages() - 1) {
            shopGUI.openCatalog(player, holder.page + 1);
            return;
        }
        if (slot == 48) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= holder.slotMaterials.length) return;

        Material material = holder.slotMaterials[slot];
        if (material == null) return;

        if (event.isRightClick()) {
            shopGUI.openQuantityMenu(player, material, holder.page);
        } else if (event.isLeftClick()) {
            shopGUI.purchase(player, material, ShopGUI.STACK_SIZE);
            shopGUI.openCatalog(player, holder.page); // refresh so the balance line updates
        }
    }

    private void handleQuantityClick(Player player, ShopGUI.QuantityHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 18) {
            shopGUI.openCatalog(player, holder.originPage);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= holder.slotAmounts.length) return;

        long amount = holder.slotAmounts[slot];
        if (amount == 0) return; // not a buy button (filler, or an unused slot)

        shopGUI.purchase(player, holder.material, amount);
        shopGUI.openCatalog(player, holder.originPage);
    }
}
