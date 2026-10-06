package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * /home opens a 3-row menu: 9 homes on the top row, a light-blue glass
 * divider, 9 homes on the bottom row (18 total). A soul torch is a saved
 * home; an empty slot shows as gray dye. /sethome saves the next free slot,
 * or says there's none left. /delhome <n> removes one.
 *
 * In the menu: left-click a home to teleport, right-click an empty slot to
 * set a home there, shift-click a saved home to delete it.
 */
public class HomeGUI implements Listener, CommandExecutor {

    private static final String TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Your Homes";

    private static final class HomeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final HomeManager homes;

    public HomeGUI(HomeManager homes) {
        this.homes = homes;
    }

    /** Slots 0-8 = homes 1-9, 18-26 = homes 10-18, 9-17 = divider. Returns 1-based home number or -1. */
    private static int homeAt(int slot) {
        if (slot >= 0 && slot < 9) return slot + 1;
        if (slot >= 18 && slot < 27) return slot - 18 + 10;
        return -1;
    }

    private static int slotOf(int home) {
        return home <= 9 ? home - 1 : home - 10 + 18;
    }

    public void open(Player player) {
        UUID id = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(new HomeHolder(), 27, TITLE);
        ItemStack divider = named(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 18; i++) inv.setItem(i, divider);
        for (int n = 1; n <= HomeManager.MAX_HOMES; n++) {
            String where = homes.describe(id, n);
            if (where != null) {
                inv.setItem(slotOf(n), named(Material.SOUL_TORCH, ChatColor.AQUA + "" + ChatColor.BOLD + "Home " + n,
                        ChatColor.GRAY + where, "",
                        ChatColor.YELLOW + "Left-click: teleport",
                        ChatColor.RED + "Shift-click: delete"));
            } else {
                inv.setItem(slotOf(n), named(Material.GRAY_DYE, ChatColor.GRAY + "Home " + n + " (empty)",
                        ChatColor.YELLOW + "Right-click: set home here"));
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HomeHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int n = homeAt(event.getSlot());
        if (n < 0) return;
        UUID id = player.getUniqueId();
        boolean set = homes.has(id, n);

        if (!set) {
            if (event.getClick().isRightClick()) {
                homes.set(id, n, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Home " + n + " set.");
                open(player);
            }
            return;
        }
        if (event.getClick().isShiftClick()) {
            homes.delete(id, n);
            player.sendMessage(ChatColor.GRAY + "Home " + n + " deleted.");
            open(player);
        } else if (event.getClick().isLeftClick()) {
            Location loc = homes.get(id, n);
            player.closeInventory();
            if (loc == null) {
                player.sendMessage(ChatColor.RED + "That home's world isn't loaded.");
                return;
            }
            player.teleport(loc);
            player.sendMessage(ChatColor.AQUA + "Teleported to home " + n + ".");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomeHolder) event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have homes.");
            return true;
        }
        UUID id = player.getUniqueId();
        switch (command.getName().toLowerCase()) {
            case "sethome": {
                int slot = homes.nextFree(id);
                if (slot < 0) {
                    player.sendMessage(ChatColor.RED + "You have no available homes - all " + HomeManager.MAX_HOMES
                            + " are used. Delete one with /delhome <number> or in /home.");
                    return true;
                }
                homes.set(id, slot, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Home " + slot + " set. (" + homes.describe(id, slot) + ")");
                return true;
            }
            case "delhome": {
                if (args.length != 1) {
                    player.sendMessage(ChatColor.RED + "Usage: /delhome <1-" + HomeManager.MAX_HOMES + ">");
                    return true;
                }
                int n;
                try {
                    n = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    n = -1;
                }
                if (n < 1 || n > HomeManager.MAX_HOMES || !homes.has(id, n)) {
                    player.sendMessage(ChatColor.RED + "You don't have a home " + args[0] + ".");
                    return true;
                }
                homes.delete(id, n);
                player.sendMessage(ChatColor.GRAY + "Home " + n + " deleted.");
                return true;
            }
            default:
                open(player);
                return true;
        }
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
