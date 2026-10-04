package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /mod (alias /staff) - the moderation toolbox: freeze, mute, and warn, plus
 * a history lookup. Every subcommand is op-only. Modeled on the other
 * command classes' structure (JobsCommand, AuctionCommand) for consistency.
 */
public class ModerationCommand implements CommandExecutor, TabCompleter {

    private final ModerationManager moderation;

    public ModerationCommand(ModerationManager moderation) {
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("penguinmafia.mod")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use /mod.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "freeze":
                return handleFreeze(sender, args);
            case "unfreeze":
                return handleUnfreeze(sender, args);
            case "mute":
                return handleMute(sender, args);
            case "unmute":
                return handleUnmute(sender, args);
            case "warn":
                return handleWarn(sender, args);
            case "tempban":
                return handleTempban(sender, args);
            case "unban":
                return handleUnban(sender, args);
            case "unwarn":
                return handleUnwarn(sender, args);
            case "history":
                return handleHistory(sender, args);
            case "list":
                return handleList(sender);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod freeze <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + args[1] + " isn't online.");
            return true;
        }
        if (!moderation.freeze(target.getUniqueId())) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + " is already frozen.");
            return true;
        }

        // Visual-only lightning bolt - no damage, no block/fire ignition, just the flash and
        // crack sound, so it reads as a dramatic "you've been caught" moment rather than an attack.
        target.getWorld().strikeLightningEffect(target.getLocation());

        // Blindness + Darkness together black out the screen almost completely (Darkness alone
        // still lets some ambient light through) for as long as the freeze lasts - removed again
        // the moment they're unfrozen. No particles/icon so it doesn't clutter their HUD further.
        int oneHourTicks = 20 * 60 * 60;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, oneHourTicks, 0, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, oneHourTicks, 0, false, false, false));

        target.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "FROZEN",
                ChatColor.GRAY + "A staff member has frozen you in place", 10, 70, 20);

        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Froze " + target.getName() + " in place.");
        target.sendMessage(ChatColor.RED + "You've been frozen in place by a staff member.");
        return true;
    }

    private boolean handleUnfreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod unfreeze <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + args[1] + " isn't online.");
            return true;
        }
        if (!moderation.unfreeze(target.getUniqueId())) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + " wasn't frozen.");
            return true;
        }

        target.removePotionEffect(PotionEffectType.BLINDNESS);
        target.removePotionEffect(PotionEffectType.DARKNESS);
        target.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "UNFROZEN", ChatColor.GRAY + "You can move again", 10, 50, 20);

        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Unfroze " + target.getName() + ".");
        target.sendMessage(ChatColor.GRAY + "You've been unfrozen - you can move again.");
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod mute <player> <minutes> [reason]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        long minutes;
        try {
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Minutes must be a whole number.");
            return true;
        }
        if (minutes <= 0) {
            sender.sendMessage(ChatColor.RED + "Minutes must be greater than 0.");
            return true;
        }
        moderation.mute(target.getUniqueId(), minutes);

        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Muted "
                + (target.getName() != null ? target.getName() : args[1]) + " for " + minutes + " minute(s)"
                + (reason != null ? " - " + reason : "") + ".");

        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(ChatColor.RED + "You've been muted for " + minutes + " minute(s)"
                    + (reason != null ? ": " + reason : "") + ".");
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod unmute <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!moderation.unmute(target.getUniqueId())) {
            sender.sendMessage(ChatColor.GRAY + (target.getName() != null ? target.getName() : args[1]) + " wasn't muted.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Unmuted " + (target.getName() != null ? target.getName() : args[1]) + ".");
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(ChatColor.GRAY + "You've been unmuted.");
        }
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod warn <player> <reason>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String issuerName = sender instanceof Player ? sender.getName() : "Console";

        moderation.warn(target, issuerName, reason);
        int total = moderation.getWarningCount(target.getUniqueId());

        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Warned "
                + (target.getName() != null ? target.getName() : args[1]) + " (" + total + " total warning"
                + (total == 1 ? "" : "s") + ") - " + reason);

        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(ChatColor.RED + "You've been warned by staff: " + ChatColor.WHITE + reason);
        }
        return true;
    }

    private boolean handleTempban(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod tempban <player> <minutes> [reason]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        long minutes;
        try {
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Minutes must be a whole number.");
            return true;
        }
        if (minutes <= 0) {
            sender.sendMessage(ChatColor.RED + "Minutes must be greater than 0.");
            return true;
        }
        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        moderation.tempban(target.getUniqueId(), minutes, reason);

        String name = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Tempbanned " + name + " for " + minutes
                + " minute(s)" + (reason != null ? " - " + reason : "") + ".");

        Player online = target.getPlayer();
        if (online != null) {
            online.kickPlayer(ChatColor.RED + "You've been temporarily banned for " + minutes + " minute(s)."
                    + (reason != null ? "\n" + ChatColor.GRAY + "Reason: " + reason : ""));
        }
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod unban <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String name = target.getName() != null ? target.getName() : args[1];
        if (!moderation.unban(target.getUniqueId())) {
            sender.sendMessage(ChatColor.GRAY + name + " isn't tempbanned.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Unbanned " + name + ".");
        return true;
    }

    private boolean handleUnwarn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod unwarn <player> <#> " + ChatColor.GRAY
                    + "(see /mod history <player> for the numbers)");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String name = target.getName() != null ? target.getName() : args[1];

        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Warning number must be a whole number.");
            return true;
        }

        if (!moderation.removeWarning(target.getUniqueId(), index)) {
            sender.sendMessage(ChatColor.RED + "No warning #" + index + " found for " + name + ".");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "[Mod] " + ChatColor.GRAY + "Removed warning #" + index + " from " + name + ".");
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mod history <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String name = target.getName() != null ? target.getName() : args[1];
        List<ModerationManager.Warning> list = moderation.getWarnings(target.getUniqueId());

        if (list.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + name + " has no warnings on record.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "--- " + name + "'s warning history (" + list.size() + ") ---");
        for (int i = 0; i < list.size(); i++) {
            ModerationManager.Warning warning = list.get(i);
            sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + " " + ChatColor.GRAY + "["
                    + ModerationManager.formatTimestamp(warning.timestampMillis) + " UTC] "
                    + ChatColor.WHITE + warning.issuerName + ChatColor.GRAY + ": " + warning.reason);
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<UUID> frozen = moderation.getFrozenPlayers();
        sender.sendMessage(ChatColor.GOLD + "--- Frozen players (" + frozen.size() + ") ---");
        if (frozen.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody is frozen right now.");
        } else {
            for (UUID id : frozen) {
                sender.sendMessage(ChatColor.WHITE + "- " + ModerationManager.nameOf(id));
            }
        }

        List<UUID> muted = moderation.getMutedPlayers();
        sender.sendMessage(ChatColor.GOLD + "--- Muted players (" + muted.size() + ") ---");
        if (muted.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody is muted right now.");
        } else {
            for (UUID id : muted) {
                sender.sendMessage(ChatColor.WHITE + "- " + ModerationManager.nameOf(id)
                        + ChatColor.GRAY + " (" + moderation.muteTimeRemaining(id) + " left)");
            }
        }

        List<UUID> banned = moderation.getBannedPlayers();
        sender.sendMessage(ChatColor.GOLD + "--- Tempbanned players (" + banned.size() + ") ---");
        if (banned.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody is tempbanned right now.");
        } else {
            for (UUID id : banned) {
                sender.sendMessage(ChatColor.WHITE + "- " + ModerationManager.nameOf(id)
                        + ChatColor.GRAY + " (" + moderation.banTimeRemaining(id) + " left)");
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Moderation ---");
        sender.sendMessage(ChatColor.GRAY + "/mod freeze <player> " + ChatColor.WHITE + "- lock a player in place with a lightning strike, a black screen, and a FROZEN title (lifts automatically on disconnect)");
        sender.sendMessage(ChatColor.GRAY + "/mod unfreeze <player> " + ChatColor.WHITE + "- let them move again");
        sender.sendMessage(ChatColor.GRAY + "/mod mute <player> <minutes> [reason] " + ChatColor.WHITE + "- block their chat for a while");
        sender.sendMessage(ChatColor.GRAY + "/mod unmute <player> " + ChatColor.WHITE + "- lift a mute early");
        sender.sendMessage(ChatColor.GRAY + "/mod warn <player> <reason> " + ChatColor.WHITE + "- add a permanent warning to their record");
        sender.sendMessage(ChatColor.GRAY + "/mod tempban <player> <minutes> [reason] " + ChatColor.WHITE + "- ban them, auto-lifted after the time's up");
        sender.sendMessage(ChatColor.GRAY + "/mod unban <player> " + ChatColor.WHITE + "- lift a tempban early");
        sender.sendMessage(ChatColor.GRAY + "/mod history <player> " + ChatColor.WHITE + "- see everyone's warnings for a player (numbered)");
        sender.sendMessage(ChatColor.GRAY + "/mod unwarn <player> <#> " + ChatColor.WHITE + "- remove a specific warning by its number");
        sender.sendMessage(ChatColor.GRAY + "/mod list " + ChatColor.WHITE + "- see who's currently frozen, muted, or tempbanned");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("freeze", "unfreeze", "mute", "unmute", "warn", "tempban", "unban", "history", "unwarn", "list", "help"), args[0]);
        }
        if (args.length == 2 && Arrays.asList("freeze", "unfreeze", "mute", "unmute", "warn", "tempban", "unban", "history", "unwarn").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }
}
