package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * /coinflip <amount> - bet some Frozen Coins on a 50/50 coin flip.
 * Win: get your bet back plus that much again (net +amount).
 * Lose: the bet is gone (net -amount).
 */
public class CoinflipCommand implements CommandExecutor {

    private final Economy economy;

    public CoinflipCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can gamble.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /coinflip <amount>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return true;
        }

        if (!economy.removeBalance(player, amount)) {
            player.sendMessage(ChatColor.RED + "You don't have " + amount + " Frozen Coins to bet. Balance: "
                    + economy.getBalance(player));
            return true;
        }

        boolean win = ThreadLocalRandom.current().nextBoolean();
        if (win) {
            economy.addBalance(player, amount * 2);
            player.sendMessage(ChatColor.GREEN + "Heads! You won " + amount + " Frozen Coins."
                    + ChatColor.GRAY + " New balance: " + economy.getBalance(player));
        } else {
            player.sendMessage(ChatColor.RED + "Tails! You lost " + amount + " Frozen Coins."
                    + ChatColor.GRAY + " New balance: " + economy.getBalance(player));
        }

        return true;
    }
}
