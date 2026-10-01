package net.penguinmafia.market;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /shop: opens the Penguin Mafia Shop GUI - a paged catalog of building and
 * decoration blocks. See ShopGUI for the browsing/buying logic.
 */
public class ShopCommand implements CommandExecutor {

    private final ShopGUI shopGUI;

    public ShopCommand(ShopGUI shopGUI) {
        this.shopGUI = shopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /shop.");
            return true;
        }
        shopGUI.openCatalog((Player) sender, 0);
        return true;
    }
}
