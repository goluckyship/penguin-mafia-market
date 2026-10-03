package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /aura: a purely cosmetic particle ring that follows the player around,
 * toggled on/off, re-colored on the fly by naming a particle, or - for a
 * custom look - named colors (e.g. "/aura red black") rendered as a DUST
 * ring cycling through each color. Locked to one specific player by name
 * rather than an op check or permission node - this is a one-off personal
 * perk, not a staff tool, so even other ops shouldn't be able to flip it on
 * for themselves.
 */
public class AuraCommand implements CommandExecutor, TabCompleter {

    /** The only player allowed to use this command - case-insensitive. */
    private static final String ALLOWED_PLAYER = "Agolucky";

    private static final AuraManager.Style DEFAULT_STYLE = AuraManager.Style.of(Particle.END_ROD);

    /** A curated subset of particles that actually look good as a tight orbiting ring. */
    private static final List<String> SUGGESTED_PARTICLES = Arrays.asList(
            "end_rod", "heart", "flame", "soul_fire_flame", "witch", "portal",
            "enchant", "totem_of_undying", "snowflake", "glow", "happy_villager"
    );

    /**
     * Named colors for a custom DUST ring (e.g. "/aura red black"). DUST is
     * the one vanilla particle that takes an arbitrary RGB tint, so naming
     * one or more colors here builds a ring that cycles through each of
     * them in turn, rather than picking a single fixed vanilla particle.
     */
    private static final Map<String, Color> NAMED_COLORS = new LinkedHashMap<>();
    static {
        NAMED_COLORS.put("red", Color.fromRGB(220, 20, 20));
        NAMED_COLORS.put("black", Color.fromRGB(20, 20, 20));
        NAMED_COLORS.put("gold", Color.fromRGB(255, 193, 37));
        NAMED_COLORS.put("white", Color.fromRGB(255, 255, 255));
        NAMED_COLORS.put("gray", Color.fromRGB(128, 128, 128));
        NAMED_COLORS.put("grey", Color.fromRGB(128, 128, 128));
        NAMED_COLORS.put("blue", Color.fromRGB(40, 90, 230));
        NAMED_COLORS.put("green", Color.fromRGB(30, 180, 60));
        NAMED_COLORS.put("purple", Color.fromRGB(150, 40, 220));
        NAMED_COLORS.put("orange", Color.fromRGB(255, 140, 0));
        NAMED_COLORS.put("pink", Color.fromRGB(255, 105, 180));
        NAMED_COLORS.put("cyan", Color.fromRGB(0, 200, 200));
        NAMED_COLORS.put("yellow", Color.fromRGB(255, 220, 0));
        NAMED_COLORS.put("lime", Color.fromRGB(140, 255, 40));
    }

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
                manager.enable(player, DEFAULT_STYLE);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Aura turned on " + ChatColor.GRAY
                        + "(" + DEFAULT_STYLE.describe() + "). Run /aura <particle|colors> to change it, /aura off to stop.");
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            if (manager.disable(player)) {
                player.sendMessage(ChatColor.GRAY + "Aura turned off.");
            } else {
                player.sendMessage(ChatColor.GRAY + "Your aura isn't on.");
            }
            return true;
        }

        // If every argument names a color, build a custom DUST ring cycling
        // through them (e.g. "/aura red black" alternates red/black around
        // the ring). Otherwise fall back to treating a single argument as a
        // plain vanilla particle name.
        List<Color> colors = new ArrayList<>();
        boolean allColors = true;
        for (String arg : args) {
            Color color = NAMED_COLORS.get(arg.toLowerCase());
            if (color == null) {
                allColors = false;
                break;
            }
            colors.add(color);
        }

        if (allColors) {
            AuraManager.Style style = AuraManager.Style.ofPalette(colors);
            manager.enable(player, style);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Aura set to " + ChatColor.GRAY
                    + String.join(" & ", args) + ChatColor.LIGHT_PURPLE + "." + ChatColor.GRAY + " /aura off to stop.");
            return true;
        }

        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "\"" + args[args.length - 1] + "\" isn't a color I know. Try: "
                    + ChatColor.GRAY + String.join(", ", NAMED_COLORS.keySet()));
            return true;
        }

        Particle particle;
        try {
            particle = Particle.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown particle or color \"" + args[0] + "\". Particles: "
                    + ChatColor.GRAY + String.join(", ", SUGGESTED_PARTICLES)
                    + ChatColor.RED + " | Colors: " + ChatColor.GRAY + String.join(", ", NAMED_COLORS.keySet()));
            return true;
        }

        manager.enable(player, AuraManager.Style.of(particle));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Aura set to " + ChatColor.GRAY + particle.name().toLowerCase()
                + ChatColor.LIGHT_PURPLE + "." + ChatColor.GRAY + " /aura off to stop.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return new ArrayList<>();
        String last = args[args.length - 1].toLowerCase();
        List<String> options = new ArrayList<>(SUGGESTED_PARTICLES);
        options.addAll(NAMED_COLORS.keySet());
        if (args.length == 1) options.add("off");
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(last)) out.add(option);
        }
        return out;
    }
}
