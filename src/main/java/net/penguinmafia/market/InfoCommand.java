package net.penguinmafia.market;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /info: reopens the same how-to-play menu that's shown automatically when
 * a player joins.
 */
public class InfoCommand implements CommandExecutor {

    private final InfoGUI infoGUI;

    public InfoCommand(InfoGUI infoGUI) {
        this.infoGUI = infoGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /info.");
            return true;
        }
        infoGUI.open((Player) sender);
        return true;
    }
}
