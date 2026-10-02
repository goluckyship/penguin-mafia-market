package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /warehouse (alias /wh) - paid personal storage, see WarehouseManager for the design. */
public class WarehouseCommand implements CommandExecutor, TabCompleter {

    private final WarehouseManager warehouse;
    private final WarehouseGUIListener gui;

    public WarehouseCommand(WarehouseManager warehouse, WarehouseGUIListener gui) {
        this.warehouse = warehouse;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the Warehouse.");
            return true;
        }

        if (args.length == 0) {
            gui.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("upgrade")) {
            return handleUpgrade(player);
        }
        if (args[0].equalsIgnoreCase("info")) {
            return handleInfo(player);
        }

        sendHelp(player);
        return true;
    }

    private boolean handleUpgrade(Player player) {
        int rowsBefore = warehouse.getRows(player.getUniqueId());
        if (rowsBefore >= WarehouseManager.MAX_ROWS) {
            player.sendMessage(ChatColor.GRAY + "Your Warehouse is already at the maximum size (" + WarehouseManager.MAX_ROWS + " rows).");
            return true;
        }

        long cost = WarehouseManager.upgradeCost(rowsBefore);
        WarehouseManager.UpgradeResult result = warehouse.upgrade(player);
        switch (result) {
            case OK:
                player.sendMessage(ChatColor.DARK_PURPLE + "[Warehouse] " + ChatColor.GRAY + "Upgraded to "
                        + (rowsBefore + 1) + " rows for " + ChatColor.AQUA + CoinFormat.format(cost)
                        + ChatColor.GRAY + " coins.");
                return true;
            case CANT_AFFORD:
                player.sendMessage(ChatColor.RED + "You need " + CoinFormat.format(cost)
                        + " coins to upgrade to " + (rowsBefore + 1) + " rows.");
                return true;
            case MAXED:
                player.sendMessage(ChatColor.GRAY + "Your Warehouse is already at the maximum size.");
                return true;
            default:
                return true;
        }
    }

    private boolean handleInfo(Player player) {
        int rows = warehouse.getRows(player.getUniqueId());
        player.sendMessage(ChatColor.DARK_PURPLE + "--- Your Warehouse ---");
        player.sendMessage(ChatColor.GRAY + "Size: " + ChatColor.WHITE + rows + " row(s) (" + (rows * 9) + " slots)");
        if (rows < WarehouseManager.MAX_ROWS) {
            player.sendMessage(ChatColor.GRAY + "Next upgrade: " + ChatColor.AQUA
                    + CoinFormat.format(WarehouseManager.upgradeCost(rows)) + ChatColor.GRAY + " coins for another row.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Already at the maximum size.");
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "--- Warehouse ---");
        player.sendMessage(ChatColor.GRAY + "/warehouse " + ChatColor.WHITE + "- open your personal storage");
        player.sendMessage(ChatColor.GRAY + "/warehouse info " + ChatColor.WHITE + "- see your current size and next upgrade cost");
        player.sendMessage(ChatColor.GRAY + "/warehouse upgrade " + ChatColor.WHITE + "- spend coins to add another row (up to "
                + WarehouseManager.MAX_ROWS + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : Arrays.asList("upgrade", "info")) {
                if (option.startsWith(args[0].toLowerCase())) out.add(option);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
