package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MarketGUIListener implements Listener {

    private final MarketManager market;
    private final Economy economy;

    public MarketGUIListener(MarketManager market, Economy economy) {
        this.market = market;
        this.economy = economy;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MarketHolder)) return;

        // The GUI is view/click-only - never let items be physically taken from it.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Clicks in the player's own inventory (bottom half) while the GUI is open do nothing.
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        int slot = event.getRawSlot();
        MarketHolder marketHolder = (MarketHolder) holder;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == 45 && clicked.getType() == Material.ARROW) {
            MarketGUI.open(player, market, Math.max(0, marketHolder.getPage() - 1));
            return;
        }
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            MarketGUI.open(player, market, marketHolder.getPage() + 1);
            return;
        }
        if (slot == 49) return; // info item, no-op
        if (slot >= 45) return; // reserved bottom row

        Integer listingId = extractListingId(clicked);
        if (listingId == null) return;

        boolean success = market.buy(listingId, player);
        if (success) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[Black Market] " + ChatColor.GRAY + "Purchase complete.");
            MarketGUI.open(player, market, marketHolder.getPage()); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "That listing is no longer available, or you can't afford it / it's your own listing.");
            MarketGUI.open(player, market, marketHolder.getPage()); // refresh stale view
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        // Frozen Coins are pure currency - never let one actually be placed as a block.
        if (economy.isCoinItem(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Frozen Coins can't be placed - they're currency, not a block.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketHolder) {
            event.setCancelled(true);
        }
    }

    private Integer extractListingId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return null;
        List<String> lore = meta.getLore();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith("Listing #")) {
                try {
                    return Integer.parseInt(stripped.substring("Listing #".length()).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
