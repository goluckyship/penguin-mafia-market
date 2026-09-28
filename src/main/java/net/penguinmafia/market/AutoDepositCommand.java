package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /autodeposit (/ad): toggles automatic depositing of physical Frozen Coins
 * into the player's balance as soon as they land in their inventory. While
 * it's on, /bm withdraw is blocked for that player (see MarketCommand) -
 * withdrawing physical coins just to have them immediately swept back into
 * the balance would be pointless, and worse, confusing.
 */
public class AutoDepositCommand implements CommandExecutor {

    private final Economy economy;

    public AutoDepositCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /autodeposit.");
            return true;
        }
        Player player = (Player) sender;

        boolean enabled = economy.isAutoDepositEnabled(player);
        boolean newState = !enabled;
        economy.setAutoDepositEnabled(player, newState);

        if (newState) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Auto-Deposit enabled."
                    + ChatColor.RESET + ChatColor.GRAY + " Any Frozen Coins that land in your inventory will be"
                    + " deposited into your balance automatically.");
            player.sendMessage(ChatColor.RED + "While this is on, you can't use /bm withdraw"
                    + ChatColor.GRAY + " - turn Auto-Deposit off with /ad first.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Auto-Deposit disabled. "
                    + "Frozen Coins you pick up will stay as physical items, and /bm withdraw is available again.");
        }
        return true;
    }
}
