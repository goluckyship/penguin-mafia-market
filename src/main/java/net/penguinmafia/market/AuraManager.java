package net.penguinmafia.market;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the cosmetic /aura effect: a slowly-rotating ring of particles that
 * follows whichever player(s) have it turned on, purely visual - no potion
 * effect, no gameplay impact of any kind, just particles orbiting at their
 * feet. Unlike a vanilla area_effect_cloud (which is a stationary entity
 * that can't track a moving target), this recomputes each point's position
 * fresh every tick off the player's *current* location, so it genuinely
 * follows them around - through teleports, across worlds, however fast they
 * move.
 *
 * Not persisted to disk - it's a toggle for the current session only, same
 * as /afk and /gen start off rather than resuming after a restart.
 */
public class AuraManager implements Listener {

    private static final int POINTS_PER_RING = 10;
    private static final double RADIUS = 1.1;
    /** Radians the ring rotates per tick this task runs (every 2 ticks) - a slow, calm spin. */
    private static final double SPIN_SPEED = 0.12;

    private final Map<UUID, Particle> active = new ConcurrentHashMap<>();
    private double angle = 0;

    public static AuraManager start(PenguinMafiaMarket plugin) {
        AuraManager manager = new AuraManager();
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.tick();
            }
        }.runTaskTimer(plugin, 20L, 2L); // every 2 ticks - smooth without flooding packets
        return manager;
    }

    private void tick() {
        if (active.isEmpty()) return;
        angle += SPIN_SPEED;

        for (Map.Entry<UUID, Particle> entry : active.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            drawRing(player, entry.getValue());
        }
    }

    private void drawRing(Player player, Particle particle) {
        Location base = player.getLocation();
        for (int i = 0; i < POINTS_PER_RING; i++) {
            double offset = angle + (2 * Math.PI * i / POINTS_PER_RING);
            double x = base.getX() + RADIUS * Math.cos(offset);
            double z = base.getZ() + RADIUS * Math.sin(offset);
            // A touch of up-and-down bob per point (phase-shifted by its position
            // around the ring) so it reads as a living aura, not a flat hoop.
            double y = base.getY() + 0.15 + 0.1 * Math.sin(offset * 2);
            Location point = new Location(base.getWorld(), x, y, z);
            // count=0, all offsets/speed 0 - spawns exactly one particle at that
            // exact point rather than Bukkit's usual random-scatter cloud.
            player.getWorld().spawnParticle(particle, point, 0, 0, 0, 0, 0);
        }
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public Particle getParticle(Player player) {
        return active.get(player.getUniqueId());
    }

    /** Turns the aura on (or changes its particle type if already on) for this player. */
    public void enable(Player player, Particle particle) {
        active.put(player.getUniqueId(), particle);
    }

    /** @return true if the aura was on and is now turned off, false if it wasn't on to begin with */
    public boolean disable(Player player) {
        return active.remove(player.getUniqueId()) != null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Not strictly required (tick() already skips offline players), but
        // keeps the active set from quietly accumulating stale entries for
        // players who logged off with it still on.
        active.remove(event.getPlayer().getUniqueId());
    }
}
