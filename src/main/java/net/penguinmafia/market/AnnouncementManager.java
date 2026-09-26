package net.penguinmafia.market;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Penguin Announcements - a looping broadcaster.
 * Op-managed list of messages that get broadcast to the whole server one at a
 * time, in order, on a repeating timer (default every 5 minutes / 300s).
 * Persisted to plugins/PenguinMafiaMarket/announcements.yml so it survives restarts.
 *
 * Messages support a clickable-link syntax: [[display text=>https://example.com]]
 * turns "display text" into a clickable, hoverable link to that URL, e.g.
 * "&bJoin our Discord! [[click here=>https://discord.gg/yourcode]]"
 */
public class AnnouncementManager {

    private static final String DEFAULT_PREFIX = "&b&lPenguin Announcements &r&7» &f";

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final List<String> messages = new ArrayList<>();
    private int intervalSeconds = 300;
    private boolean enabled = true;
    private int index = 0;

    private BukkitTask task;

    public AnnouncementManager(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "announcements.yml");
        load();
        start();
    }

    private void load() {
        if (!file.exists()) {
            messages.add("&aWelcome to the server! Check out /bm for the Penguin Mafia Black Market.");
            intervalSeconds = 300;
            enabled = true;
            save();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        intervalSeconds = config.getInt("interval-seconds", 300);
        enabled = config.getBoolean("enabled", true);
        List<String> loaded = config.getStringList("messages");
        messages.clear();
        messages.addAll(loaded);
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        config.set("interval-seconds", intervalSeconds);
        config.set("enabled", enabled);
        config.set("messages", messages);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save announcements.yml", e);
        }
    }

    private void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        long ticks = Math.max(20L, intervalSeconds * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, ticks, ticks);
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[(.+?)=>(.+?)\\]\\]");

    private void broadcastNext() {
        if (!enabled || messages.isEmpty()) return;
        if (index >= messages.size()) index = 0;
        String raw = messages.get(index);
        index++;

        String full = ChatColor.translateAlternateColorCodes('&', DEFAULT_PREFIX + raw);
        Matcher matcher = LINK_PATTERN.matcher(full);

        if (!matcher.find()) {
            // No clickable link in this message - plain broadcast is enough.
            Bukkit.broadcastMessage(full);
            return;
        }

        // Build a component message: plain text segments plus a clickable/hoverable link segment.
        ComponentBuilder builder = new ComponentBuilder("");
        int last = 0;
        matcher.reset();
        while (matcher.find()) {
            String before = full.substring(last, matcher.start());
            if (!before.isEmpty()) {
                builder.append(TextComponent.fromLegacyText(before));
            }
            String displayText = matcher.group(1);
            String url = matcher.group(2);
            TextComponent link = new TextComponent(TextComponent.fromLegacyText(
                    ChatColor.translateAlternateColorCodes('&', "&b&n" + displayText)));
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to open: " + url)));
            builder.append(link);
            last = matcher.end();
        }
        if (last < full.length()) {
            builder.append(TextComponent.fromLegacyText(full.substring(last)));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(builder.create());
        }
        // Consoles/logs don't render click events - log a readable plain version too.
        Bukkit.getConsoleSender().sendMessage(matcher.replaceAll(m -> m.group(1)));
    }

    public void add(String message) {
        messages.add(message);
        save();
    }

    /** 1-based index as shown by /pa list. Returns the removed message, or null if out of range. */
    public String remove(int oneBasedIndex) {
        int i = oneBasedIndex - 1;
        if (i < 0 || i >= messages.size()) return null;
        String removed = messages.remove(i);
        if (index > i) index--;
        save();
        return removed;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setInterval(int seconds) {
        this.intervalSeconds = Math.max(5, seconds);
        save();
        start();
    }

    public int getInterval() {
        return intervalSeconds;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
