package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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
 * /gen (op-only): drops a standing item generator at the op's current
 * location that spawns a chosen item - including real Frozen Coins - on a
 * fixed interval, forever (persists across restarts) until an op stands
 * within 2 blocks of it and runs /gen again to remove it.
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

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            return handleList(player);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return handleRemove(player, args[1]);
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

        Location location = player.getLocation();
        String materialName = coin ? null : material.name();
        GeneratorManager.Generator result = manager.toggle(location, coin, materialName, delaySeconds, amount);

        if (result == null) {
            player.sendMessage(ChatColor.GRAY + "Removed the generator near you.");
        } else {
            String itemLabel = coin ? "Frozen Coins" : amount + "x " + material.name().toLowerCase().replace('_', ' ');
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Generator #" + result.id + " created."
                    + ChatColor.RESET + ChatColor.GRAY + " Dropping " + itemLabel + " right here every " + delaySeconds
                    + "s, forever (survives restarts).");
            player.sendMessage(ChatColor.GRAY + "Stand within 2 blocks and run /gen again to remove it,"
                    + " or /gen remove " + result.id + " from anywhere.");
        }
        return true;
    }

    private boolean isCoinAlias(String s) {
        return s.equalsIgnoreCase("coin") || s.equalsIgnoreCase("coins")
                || s.equalsIgnoreCase("frozencoin") || s.equalsIgnoreCase("frozencoins");
    }

    private boolean handleList(Player player) {
        List<GeneratorManager.Generator> all = manager.list();
        if (all.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No generators are active.");
            return true;
        }
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- Active Generators ---");
        for (GeneratorManager.Generator gen : all) {
            String itemLabel = gen.coin ? "Frozen Coins" : gen.materialName.toLowerCase().replace('_', ' ');
            player.sendMessage(ChatColor.GRAY + "#" + gen.id + " " + ChatColor.WHITE + gen.amount + "x " + itemLabel
                    + ChatColor.GRAY + " every " + gen.delaySeconds + "s @ " + gen.world + " "
                    + (int) gen.x + ", " + (int) gen.y + ", " + (int) gen.z);
        }
        return true;
    }

    private boolean handleRemove(Player player, String idArg) {
        int id;
        try {
            id = Integer.parseInt(idArg);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Usage: /gen remove <id>");
            return true;
        }
        if (manager.remove(id)) {
            player.sendMessage(ChatColor.GRAY + "Removed generator #" + id + ".");
        } else {
            player.sendMessage(ChatColor.RED + "No generator #" + id + " found. Check /gen list.");
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- /gen ---");
        player.sendMessage(ChatColor.GRAY + "/gen <item> <delay-secs> <amount> " + ChatColor.WHITE
                + "- drop a generator at your feet (run again within 2 blocks to remove)");
        player.sendMessage(ChatColor.GRAY + "  <item> " + ChatColor.WHITE + "- a material id (diamond, iron_ingot, ...) or \"coin\" for Frozen Coins");
        player.sendMessage(ChatColor.GRAY + "/gen list " + ChatColor.WHITE + "- show all active generators");
        player.sendMessage(ChatColor.GRAY + "/gen remove <id> " + ChatColor.WHITE + "- remove a generator from anywhere");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("coin", "list", "remove"));
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
