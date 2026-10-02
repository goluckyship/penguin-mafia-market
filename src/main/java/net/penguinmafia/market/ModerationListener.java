package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Enforces the live effects ModerationManager tracks: a frozen player can
 * look around but can't walk anywhere, a muted player's chat messages never
 * go out, and a tempbanned player is turned away at login with a message
 * showing how much longer it lasts. Warnings have no live enforcement -
 * they're a record for staff to act on, not an automatic penalty.
 */
public class ModerationListener implements Listener {

    private final ModerationManager moderation;

    public ModerationListener(ModerationManager moderation) {
        this.moderation = moderation;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        java.util.UUID id = event.getPlayer().getUniqueId();
        if (!moderation.isBanned(id)) return;

        String remaining = moderation.banTimeRemaining(id);
        String reason = moderation.getBanReason(id);
        String message = ChatColor.RED + "You are temporarily banned"
                + (remaining.isEmpty() ? "" : " for another " + remaining) + ".\n"
                + ChatColor.GRAY + "Reason: " + reason;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return; // just looking around - always allowed, even while frozen
        }
        Player player = event.getPlayer();
        if (moderation.isFrozen(player.getUniqueId())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!moderation.isMuted(player.getUniqueId())) return;

        event.setCancelled(true);
        String remaining = moderation.muteTimeRemaining(player.getUniqueId());
        player.sendMessage(ChatColor.RED + "You're muted" + (remaining.isEmpty() ? "" : " for another " + remaining) + " and can't chat right now.");
    }

    /**
     * A freeze is meant to hold someone in place for an active AFK-check or
     * dispute, not to be a silent standing punishment that outlives the
     * session - so it's lifted automatically the moment they disconnect.
     * Mutes and warnings are unaffected and persist across logins.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moderation.unfreeze(event.getPlayer().getUniqueId());
    }
}
