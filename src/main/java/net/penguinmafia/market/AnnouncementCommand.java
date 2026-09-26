package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /pa (Penguin Announcements) - op-only management of the looping broadcast messages.
 * /pa add <message>       - add a new message to the rotation
 * /pa remove <#>          - remove a message by its number in /pa list
 * /pa list                - show all messages, their numbers, and current settings
 * /pa interval <seconds>  - change how often messages loop (default 300 = 5 min)
 * /pa on | off            - enable/disable the broadcaster without clearing messages
 */
public class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final AnnouncementManager manager;

    public AnnouncementCommand(AnnouncementManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only ops can manage Penguin Announcements.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add": {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pa add <message>");
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                manager.add(message);
                sender.sendMessage(ChatColor.AQUA + "Added announcement #" + manager.getMessages().size() + ": "
                        + ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }
            case "remove": {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pa remove <#>");
                    return true;
                }
                int num;
                try {
                    num = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "That's not a number.");
                    return true;
                }
                String removed = manager.remove(num);
                if (removed == null) {
                    sender.sendMessage(ChatColor.RED + "No announcement #" + num + ". Use /pa list to see numbers.");
                } else {
                    sender.sendMessage(ChatColor.AQUA + "Removed: " + ChatColor.translateAlternateColorCodes('&', removed));
                }
                return true;
            }
            case "list": {
                List<String> messages = manager.getMessages();
                sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Penguin Announcements "
                        + ChatColor.RESET + ChatColor.GRAY + "(every " + manager.getInterval() + "s, "
                        + (manager.isEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF") + ChatColor.GRAY + ")");
                if (messages.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  (no announcements yet - use /pa add <message>)");
                } else {
                    for (int i = 0; i < messages.size(); i++) {
                        sender.sendMessage(ChatColor.GRAY + " " + (i + 1) + ". "
                                + ChatColor.translateAlternateColorCodes('&', messages.get(i)));
                    }
                }
                return true;
            }
            case "interval": {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pa interval <seconds>");
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "That's not a number.");
                    return true;
                }
                manager.setInterval(seconds);
                sender.sendMessage(ChatColor.AQUA + "Announcements now loop every " + manager.getInterval() + " seconds.");
                return true;
            }
            case "on": {
                manager.setEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Penguin Announcements enabled.");
                return true;
            }
            case "off": {
                manager.setEnabled(false);
                sender.sendMessage(ChatColor.RED + "Penguin Announcements disabled.");
                return true;
            }
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Penguin Announcements");
        sender.sendMessage(ChatColor.GRAY + "/pa add <message> " + ChatColor.DARK_GRAY + "- add a message to the loop (supports & color codes)");
        sender.sendMessage(ChatColor.GRAY + "  Clickable link: " + ChatColor.DARK_GRAY + "[[display text=>https://url]]");
        sender.sendMessage(ChatColor.GRAY + "/pa remove <#> " + ChatColor.DARK_GRAY + "- remove a message (see /pa list for numbers)");
        sender.sendMessage(ChatColor.GRAY + "/pa list " + ChatColor.DARK_GRAY + "- show all messages and settings");
        sender.sendMessage(ChatColor.GRAY + "/pa interval <seconds> " + ChatColor.DARK_GRAY + "- change loop speed (default 300 = 5 min)");
        sender.sendMessage(ChatColor.GRAY + "/pa on|off " + ChatColor.DARK_GRAY + "- toggle the broadcaster");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "interval", "on", "off").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Arrays.asList();
    }
}
