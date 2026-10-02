package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /job - join/leave jobs, check progress, and see who's grinding the hardest. */
public class JobsCommand implements CommandExecutor, TabCompleter {

    private final JobsManager jobs;

    public JobsCommand(JobsManager jobs) {
        this.jobs = jobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                sendStats(player, player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join":
                return handleJoin(sender, args);
            case "leave":
                return handleLeave(sender, args);
            case "list":
                return handleList(sender);
            case "stats":
                return handleStats(sender, args);
            case "top":
                return handleTop(sender, args);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can join a job.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /job join <job>");
            return true;
        }
        Job job = Job.fromString(args[1]);
        if (job == null) {
            player.sendMessage(ChatColor.RED + "Unknown job. " + jobNameList());
            return true;
        }
        if (jobs.hasJoined(player, job)) {
            player.sendMessage(ChatColor.RED + "You've already joined " + job.colored() + ChatColor.RED + ".");
            return true;
        }
        if (jobs.getJoinedCount(player) >= JobsManager.MAX_CONCURRENT_JOBS) {
            player.sendMessage(ChatColor.RED + "You can only hold " + JobsManager.MAX_CONCURRENT_JOBS
                    + " jobs at once - /job leave <job> to make room.");
            return true;
        }
        jobs.join(player, job);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "[Jobs] " + ChatColor.GRAY + "Joined "
                + job.colored() + ChatColor.GRAY + " - " + job.description);
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can leave a job.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /job leave <job>");
            return true;
        }
        Job job = Job.fromString(args[1]);
        if (job == null) {
            player.sendMessage(ChatColor.RED + "Unknown job. " + jobNameList());
            return true;
        }
        if (!jobs.leave(player, job)) {
            player.sendMessage(ChatColor.RED + "You haven't joined " + job.colored() + ChatColor.RED + ".");
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "Left " + job.colored() + ChatColor.GRAY
                + " - its progress has been reset.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "--- Jobs (" + JobsManager.MAX_CONCURRENT_JOBS + " at a time) ---");
        for (Job job : Job.values()) {
            sender.sendMessage(job.colored() + ChatColor.GRAY + " - " + job.description);
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length >= 2) {
            target = Bukkit.getOfflinePlayer(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /job stats <player>");
            return true;
        }
        sendStats(sender, target);
        return true;
    }

    private void sendStats(CommandSender viewer, OfflinePlayer target) {
        java.util.Set<Job> joined = jobs.getJoinedJobs(target);
        String name = target.getName() != null ? target.getName() : "that player";
        if (joined.isEmpty()) {
            viewer.sendMessage(ChatColor.GRAY + name + " hasn't joined any jobs. /job join <job> to start one.");
            return;
        }

        viewer.sendMessage(ChatColor.LIGHT_PURPLE + "--- " + name + "'s jobs ---");
        for (Job job : joined) {
            long xp = jobs.getXp(target, job);
            int level = jobs.levelForXp(xp);
            long intoLevel = jobs.xpIntoCurrentLevel(xp);
            long forNext = level < JobsManager.MAX_LEVEL ? jobs.xpForNextLevel(level) : 0;
            String progress = level >= JobsManager.MAX_LEVEL
                    ? ChatColor.GOLD + "MAX LEVEL"
                    : intoLevel + " / " + forNext + " xp to next level";
            viewer.sendMessage(job.colored() + ChatColor.GRAY + " - level " + ChatColor.WHITE + level
                    + ChatColor.GRAY + " (" + progress + ChatColor.GRAY + ")");
        }
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /job top <job>");
            return true;
        }
        Job job = Job.fromString(args[1]);
        if (job == null) {
            sender.sendMessage(ChatColor.RED + "Unknown job. " + jobNameList());
            return true;
        }

        List<Map.Entry<UUID, Long>> top = jobs.topByJob(job, 10);
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody has earned any " + job.colored() + ChatColor.GRAY
                    + " xp yet.");
            return true;
        }

        sender.sendMessage(ChatColor.LIGHT_PURPLE + "--- Top " + job.displayName + "s ---");
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            int level = jobs.levelForXp(entry.getValue());
            String name = player.getName() != null ? player.getName() : entry.getKey().toString();
            sender.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - level " + level);
            rank++;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "--- Jobs ---");
        sender.sendMessage(ChatColor.GRAY + "/job " + ChatColor.WHITE + "- see your own job levels and progress");
        sender.sendMessage(ChatColor.GRAY + "/job list " + ChatColor.WHITE + "- see every job and what it pays for");
        sender.sendMessage(ChatColor.GRAY + "/job join <job> " + ChatColor.WHITE + "- join a job (up to "
                + JobsManager.MAX_CONCURRENT_JOBS + " at once)");
        sender.sendMessage(ChatColor.GRAY + "/job leave <job> " + ChatColor.WHITE + "- leave a job (resets its progress)");
        sender.sendMessage(ChatColor.GRAY + "/job stats [player] " + ChatColor.WHITE + "- check your or someone else's job levels");
        sender.sendMessage(ChatColor.GRAY + "/job top <job> " + ChatColor.WHITE + "- the top 10 players in a job");
    }

    private String jobNameList() {
        StringBuilder sb = new StringBuilder(ChatColor.GRAY + "Jobs: ");
        for (Job job : Job.values()) {
            sb.append(job.displayName).append(", ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("join", "leave", "list", "stats", "top", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("leave")
                || args[0].equalsIgnoreCase("top"))) {
            List<String> names = new ArrayList<>();
            for (Job job : Job.values()) names.add(job.name().toLowerCase());
            return filter(names, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }
}
