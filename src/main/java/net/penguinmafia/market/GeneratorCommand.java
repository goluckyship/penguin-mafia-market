package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /gen (op-only): each op can have one item generator running at a time.
 * Run it with args to start one at your current location - it spawns a
 * chosen item there, including real Frozen Coins, on a fixed interval,
 * forever (persists across restarts). Run /gen again, from anywhere, with
 * no need to be standing at it, to stop your own.
 */
public class GeneratorCommand implements CommandExecutor, TabCompleter {

    private final GeneratorManager manager;

    public GeneratorCommand(GeneratorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /gen.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only ops can use /gen.");
            return true;
        }

        if (manager.isActive(player)) {
            manager.stop(player);
            player.sendMessage(ChatColor.GRAY + "Your generator has been stopped.");
            return true;
        }

        if (args.length != 3) {
            sendUsage(player);
            return true;
        }

        boolean coin = isCoinAlias(args[0]);
        Material material = null;
        if (!coin) {
            material = Material.matchMaterial(args[0]);
            if (material == null || !material.isItem()) {
                player.sendMessage(ChatColor.RED + "Unknown item: " + args[0]
                        + ChatColor.GRAY + " - use a material id like diamond, iron_ingot, or \"coin\" for Frozen Coins.");
                return true;
            }
        }

        int delaySeconds;
        int amount;
        try {
            delaySeconds = Integer.parseInt(args[1]);
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendUsage(player);
            return true;
        }
        if (delaySeconds < 1 || delaySeconds > 86400) {
            player.sendMessage(ChatColor.RED + "Delay must be between 1 and 86400 seconds.");
            return true;
        }
        if (amount < 1 || amount > 6400) {
            player.sendMessage(ChatColor.RED + "Amount must be between 1 and 6400 per cycle.");
            return true;
        }

        String materialName = coin ? null : material.name();
        manager.start(player, coin, materialName, delaySeconds, amount);

        String itemLabel = coin ? "Frozen Coins" : amount + "x " + material.name().toLowerCase().replace('_', ' ');
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Generator started."
                + ChatColor.RESET + ChatColor.GRAY + " Dropping " + itemLabel + " right here every " + delaySeconds
                + "s, forever (survives restarts).");
        player.sendMessage(ChatColor.GRAY + "Run /gen again, from anywhere, to stop it.");
        return true;
    }

    private boolean isCoinAlias(String s) {
        return s.equalsIgnoreCase("coin") || s.equalsIgnoreCase("coins")
                || s.equalsIgnoreCase("frozencoin") || s.equalsIgnoreCase("frozencoins");
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- /gen ---");
        player.sendMessage(ChatColor.GRAY + "/gen <item> <delay-secs> <amount> " + ChatColor.WHITE
                + "- start a generator at your feet (you can only have one running at a time)");
        player.sendMessage(ChatColor.GRAY + "  <item> " + ChatColor.WHITE + "- a material id (diamond, iron_ingot, ...) or \"coin\" for Frozen Coins");
        player.sendMessage(ChatColor.GRAY + "/gen " + ChatColor.WHITE + "- with no args, stops your active generator from anywhere");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("coin"));
            for (Material m : Material.values()) {
                if (m.isItem()) options.add(m.name().toLowerCase());
            }
            return filter(options, args[0]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String o : options) {
            if (o.startsWith(lower)) out.add(o);
        }
        return out;
    }
}
