package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /aura: a purely cosmetic particle ring that follows the player around,
 * toggled on/off (or re-colored on the fly by naming a particle). Locked to
 * one specific player by name rather than an op check or permission node -
 * this is a one-off personal perk, not a staff tool, so even other ops
 * shouldn't be able to flip it on for themselves.
 */
public class AuraCommand implements CommandExecutor, TabCompleter {

    /** The only player allowed to use this command - case-insensitive. */
    private static final String ALLOWED_PLAYER = "Agolucky";

    private static final Particle DEFAULT_PARTICLE = Particle.END_ROD;

    /** A curated subset of particles that actually look good as a tight orbiting ring. */
    private static final List<String> SUGGESTED_PARTICLES = Arrays.asList(
            "end_rod", "heart", "flame", "soul_fire_flame", "witch", "portal",
            "enchant", "totem_of_undying", "dust", "snowflake", "glow", "happy_villager"
    );

    private final AuraManager manager;

    public AuraCommand(AuraManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /aura.");
            return true;
        }
        if (!player.getName().equalsIgnoreCase(ALLOWED_PLAYER)) {
            player.sendMessage(ChatColor.RED + "Only " + ALLOWED_PLAYER + " can use /aura.");
            return true;
        }

        if (args.length == 0) {
            if (manager.isActive(player)) {
                manager.disable(player);
                player.sendMessage(ChatColor.GRAY + "Aura turned off.");
            } else {
                manager.enable(player, DEFAULT_PARTICLE);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Aura turned on " + ChatColor.GRAY
                        + "(" + DEFAULT_PARTICLE.name().toLowerCase() + "). Run /aura <particle> to change it, /aura off to stop.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            if (manager.disable(player)) {
                player.sendMessage(ChatColor.GRAY + "Aura turned off.");
            } else {
                player.sendMessage(ChatColor.GRAY + "Your aura isn't on.");
            }
            return true;
        }

        Particle particle;
        try {
            particle = Particle.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown particle \"" + args[0] + "\". Try one of: "
                    + ChatColor.GRAY + String.join(", ", SUGGESTED_PARTICLES));
            return true;
        }

        manager.enable(player, particle);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Aura set to " + ChatColor.GRAY + particle.name().toLowerCase()
                + ChatColor.LIGHT_PURPLE + "." + ChatColor.GRAY + " /aura off to stop.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>(SUGGESTED_PARTICLES);
        options.add("off");
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(args[0].toLowerCase())) out.add(option);
        }
        return out;
    }
}
