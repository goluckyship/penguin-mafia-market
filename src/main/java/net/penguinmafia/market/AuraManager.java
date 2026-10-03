package net.penguinmafia.market;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
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
 * A style is either a single plain Particle (end_rod, heart, flame...) or a
 * multi-color DUST palette (red and black alternating, say) - DUST is the
 * one vanilla particle that takes an arbitrary RGB color, so that's what a
 * named-color request like "/aura red black" turns into under the hood.
 *
 * Not persisted to disk - it's a toggle for the current session only, same
 * as /afk and /gen start off rather than resuming after a restart.
 */
public class AuraManager implements Listener {

    private static final int POINTS_PER_RING = 10;
    private static final double RADIUS = 1.1;
    /** Radians the ring rotates per tick this task runs (every 2 ticks) - a slow, calm spin. */
    private static final double SPIN_SPEED = 0.12;
    private static final float DUST_SIZE = 1.3f;

    /** A ring's look: either a plain particle, or a list of colors cycled point-by-point as DUST. */
    public static final class Style {
        final Particle particle;
        final List<Color> palette;
        final List<RelativePoint> pattern;
        final String label;

        private Style(Particle particle, List<Color> palette, List<RelativePoint> pattern, String label) {
            this.particle = particle;
            this.palette = palette;
            this.pattern = pattern;
            this.label = label;
        }

        public static Style of(Particle particle) {
            return new Style(particle, null, null, null);
        }

        public static Style ofPalette(List<Color> colors) {
            return new Style(null, colors, null, null);
        }

        /** A fixed decorative shape (cracks, horns...) drawn relative to the player each tick - see RelativePoint. */
        public static Style ofPattern(List<RelativePoint> points, String label) {
            return new Style(null, null, points, label);
        }

        public String describe() {
            if (pattern != null) return label;
            return palette != null ? "custom colors" : particle.name().toLowerCase();
        }
    }

    /**
     * One point in a fixed decorative shape, in a coordinate space relative
     * to the player: x = sideways (positive = their right), y = straight up
     * from roughly chest height, z = forward(+)/backward(-) relative to the
     * way they're currently facing. Drawn fresh every tick by rotating this
     * offset to match the player's current yaw and adding it to their
     * current location, which is what makes a fixed shape like a pair of
     * horns or a set of lightning cracks "attach" to a moving, turning
     * player instead of only working while they stand still facing one way.
     */
    public static final class RelativePoint {
        final double x, y, z;
        final Color color;
        final float size;

        public RelativePoint(double x, double y, double z, Color color, float size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.size = size;
        }
    }

    private final Map<UUID, Style> active = new ConcurrentHashMap<>();
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

        for (Map.Entry<UUID, Style> entry : active.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            Style style = entry.getValue();
            if (style.pattern != null) {
                drawPattern(player, style.pattern);
            } else {
                drawRing(player, style);
            }
        }
    }

    /**
     * Draws a fixed decorative shape (RelativePoint list) attached to the
     * player: each point's local (sideways/up/forward) offset is rotated to
     * match the player's current facing direction, then added to their
     * current location - so the shape turns and moves with them every tick,
     * same as the ring does, just without the spin.
     */
    private void drawPattern(Player player, List<RelativePoint> points) {
        Location base = player.getLocation();
        double yawRad = Math.toRadians(base.getYaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double baseY = base.getY() + 1.1; // roughly chest/shoulder height, not feet

        for (RelativePoint p : points) {
            double worldX = base.getX() + rightX * p.x + forwardX * p.z;
            double worldZ = base.getZ() + rightZ * p.x + forwardZ * p.z;
            double worldY = baseY + p.y;
            Location point = new Location(base.getWorld(), worldX, worldY, worldZ);
            player.getWorld().spawnParticle(Particle.DUST, point, 0, 0, 0, 0, 0,
                    new Particle.DustOptions(p.color, p.size));
        }
    }

    private void drawRing(Player player, Style style) {
        Location base = player.getLocation();
        for (int i = 0; i < POINTS_PER_RING; i++) {
            double offset = angle + (2 * Math.PI * i / POINTS_PER_RING);
            double x = base.getX() + RADIUS * Math.cos(offset);
            double z = base.getZ() + RADIUS * Math.sin(offset);
            // A touch of up-and-down bob per point (phase-shifted by its position
            // around the ring) so it reads as a living aura, not a flat hoop.
            double y = base.getY() + 0.15 + 0.1 * Math.sin(offset * 2);
            Location point = new Location(base.getWorld(), x, y, z);

            if (style.palette != null) {
                Color color = style.palette.get(i % style.palette.size());
                player.getWorld().spawnParticle(Particle.DUST, point, 0, 0, 0, 0, 0,
                        new Particle.DustOptions(color, DUST_SIZE));
            } else {
                // count=0, all offsets/speed 0 - spawns exactly one particle at that
                // exact point rather than Bukkit's usual random-scatter cloud.
                player.getWorld().spawnParticle(style.particle, point, 0, 0, 0, 0, 0);
            }
        }
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public Style getStyle(Player player) {
        return active.get(player.getUniqueId());
    }

    /** Turns the aura on (or changes its look if already on) for this player. */
    public void enable(Player player, Style style) {
        active.put(player.getUniqueId(), style);
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
