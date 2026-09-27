package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketGUIListener implements Listener {

    private final PenguinMafiaMarket plugin;
    private final MarketManager market;
    private final Economy economy;

    /** Players who just clicked "Search" and whose next chat line should be used as the query. */
    private final Map<UUID, Boolean> awaitingSearch = new ConcurrentHashMap<>();

    public MarketGUIListener(PenguinMafiaMarket plugin, MarketManager market, Economy economy) {
        this.plugin = plugin;
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

        if (slot == 45 && clicked.getType() == Material.ARROW && marketHolder.getPage() > 0) {
            reopen(player, marketHolder, marketHolder.getPage() - 1);
            return;
        }
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            reopen(player, marketHolder, marketHolder.getPage() + 1);
            return;
        }
        if (slot == 46) {
            if (marketHolder.getMode() == MarketHolder.Mode.BROWSE) {
                MarketGUI.openMyListings(player, market, 0);
            } else {
                MarketGUI.open(player, market, 0);
            }
            return;
        }
        if (slot == 47 && marketHolder.getMode() == MarketHolder.Mode.BROWSE) {
            player.closeInventory();
            awaitingSearch.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.AQUA + "[Black Market] " + ChatColor.GRAY
                    + "Type what you're searching for in chat (item or seller name), or type "
                    + ChatColor.WHITE + "cancel" + ChatColor.GRAY + " to back out.");
            return;
        }
        if (slot == 48 && marketHolder.getMode() == MarketHolder.Mode.BROWSE
                && marketHolder.getFilter() != null && !marketHolder.getFilter().isBlank()
                && clicked.getType() == Material.BARRIER) {
            MarketGUI.open(player, market, 0);
            return;
        }
        if (slot >= 45) return; // remaining bottom row: info tile, page indicator, filler

        Integer listingId = extractListingId(clicked);
        if (listingId == null) return;

        if (marketHolder.getMode() == MarketHolder.Mode.MY_LISTINGS) {
            boolean cancelled = market.cancelListing(listingId, player);
            if (cancelled) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "[Black Market] " + ChatColor.GRAY
                        + "Listing #" + listingId + " cancelled - item returned to you.");
            } else {
                player.sendMessage(ChatColor.RED + "That listing is already gone.");
            }
            MarketGUI.openMyListings(player, market, marketHolder.getPage());
            return;
        }

        boolean success = market.buy(listingId, player);
        if (success) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[Black Market] " + ChatColor.GRAY + "Purchase complete.");
        } else {
            player.sendMessage(ChatColor.RED + "That listing is no longer available, or you can't afford it / it's your own listing.");
        }
        MarketGUI.open(player, market, marketHolder.getPage(), marketHolder.getFilter());
    }

    private void reopen(Player player, MarketHolder holder, int page) {
        if (holder.getMode() == MarketHolder.Mode.MY_LISTINGS) {
            MarketGUI.openMyListings(player, market, page);
        } else {
            MarketGUI.open(player, market, page, holder.getFilter());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!awaitingSearch.remove(id, true)) return;

        event.setCancelled(true);
        String query = event.getMessage().trim();
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + "Search cancelled.");
                MarketGUI.open(player, market, 0);
                return;
            }
            MarketGUI.open(player, market, 0, query);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingSearch.remove(event.getPlayer().getUniqueId());
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
