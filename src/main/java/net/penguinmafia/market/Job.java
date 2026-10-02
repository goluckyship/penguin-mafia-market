package net.penguinmafia.market;

import org.bukkit.ChatColor;

/**
 * The five jobs a player can take up with /job join. Each one pays Frozen
 * Coins for a specific category of normal survival activity (mining,
 * farming, chopping trees, fighting hostile mobs, or fishing) - see
 * JobsListener for exactly which actions count and how much each pays.
 *
 * Jobs are a passive, grindable income source layered on top of the
 * player-to-player Black Market and Auction House: they reward just playing
 * the game normally, rather than requiring a sale to someone else, so a
 * player with no one to trade with still has a way to earn Frozen Coins.
 */
public enum Job {

    MINER("Miner", "Paid for breaking ores and stone-type blocks.", ChatColor.GRAY),
    FARMER("Farmer", "Paid for harvesting fully-grown crops.", ChatColor.GREEN),
    LUMBERJACK("Lumberjack", "Paid for chopping down logs.", ChatColor.GOLD),
    HUNTER("Hunter", "Paid for killing hostile mobs.", ChatColor.RED),
    FISHERMAN("Fisherman", "Paid for catching fish while fishing.", ChatColor.AQUA),
    BUILDER("Builder", "Paid for placing common building blocks.", ChatColor.YELLOW),
    ENCHANTER("Enchanter", "Paid for enchanting items at an enchanting table.", ChatColor.LIGHT_PURPLE);

    public final String displayName;
    public final String description;
    public final ChatColor color;

    Job(String displayName, String description, ChatColor color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    /** Case-insensitive lookup by name (or a couple of common aliases), null if nothing matches. */
    public static Job fromString(String text) {
        if (text == null) return null;
        String normalized = text.trim().toUpperCase();
        switch (normalized) {
            case "WOODCUTTER":
            case "LUMBERJACK":
                return LUMBERJACK;
            case "FISHER":
            case "FISHERMAN":
                return FISHERMAN;
            case "BUILD":
            case "BUILDER":
                return BUILDER;
            case "ENCHANT":
            case "ENCHANTER":
                return ENCHANTER;
            default:
                for (Job job : values()) {
                    if (job.name().equals(normalized)) return job;
                }
                return null;
        }
    }

    public String colored() {
        return color.toString() + displayName;
    }
}
