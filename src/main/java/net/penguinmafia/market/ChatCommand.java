package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /chat: toggles whether Auto-Deposit and AFK Farm print a line every single
 * time they credit your balance. With a farm running, that can be once a
 * second - enough to bury the rest of the chat. Turning this off doesn't
 * stop the deposits themselves, just the message; check /bal (or /bm
 * balance) whenever you actually want to see the number.
 */
public class ChatCommand implements CommandExecutor {

    private final Economy economy;

    public ChatCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /chat.");
            return true;
        }
        Player player = (Player) sender;

        boolean quiet = !economy.isChatQuiet(player);
        economy.setChatQuiet(player, quiet);

        if (quiet) {
            player.sendMessage(ChatColor.GRAY + "Balance-update messages are now " + ChatColor.RED + "off"
                    + ChatColor.GRAY + ". Auto-Deposit and AFK Farm will keep crediting you, just quietly - check "
                    + ChatColor.WHITE + "/bal" + ChatColor.GRAY + " whenever you want to see it.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Balance-update messages are now " + ChatColor.GREEN + "on"
                    + ChatColor.GRAY + ". Auto-Deposit and AFK Farm will print a line every time they credit you.");
        }
        return true;
    }
}
