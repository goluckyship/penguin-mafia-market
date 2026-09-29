package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Backs /autodeposit (/ad): once a player turns it on, any physical Frozen
 * Coin item that ends up in their inventory - picked up off the ground from
 * a Frozen Reaver kill, handed to them by another player, given by an op,
 * anything - is automatically swept into their balance instead of sitting
 * in their inventory. Implemented as a periodic sweep rather than hooking
 * every possible "item entered inventory" event (pickup, chest transfer,
 * trade, /give, dragging in a GUI...) individually, so no source is ever
 * missed. Only online players who have it enabled are scanned, so the cost
 * is negligible.
 */
public class AutoDepositManager extends BukkitRunnable {

    private final Economy economy;

    public AutoDepositManager(Economy economy) {
        this.economy = economy;
    }

    public static void start(PenguinMafiaMarket plugin, Economy economy) {
        new AutoDepositManager(economy).runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!economy.isAutoDepositEnabled(player)) continue;
            sweep(player);
        }
    }

    private void sweep(Player player) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;

        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (economy.isCoinItem(item)) {
                total += item.getAmount();
                inventory.setItem(i, null);
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (economy.isCoinItem(offhand)) {
            total += offhand.getAmount();
            inventory.setItemInOffHand(null);
        }

        if (total <= 0) return;

        economy.addBalance(player, total);
        if (!economy.isChatQuiet(player)) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "[Auto-Deposit] "
                    + ChatColor.RESET + ChatColor.GRAY + "Deposited " + total + " Frozen Coins. New balance: "
                    + ChatColor.AQUA + economy.getBalance(player));
        }
    }
}
