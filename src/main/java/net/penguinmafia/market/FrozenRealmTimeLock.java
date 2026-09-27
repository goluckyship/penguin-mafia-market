package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Keeps the Frozen Realm's sky time locked at midnight, without touching
 * gamerules (which are shared server-wide and would freeze the Overworld's
 * day/night cycle too) or the datapack's dimension type (a hand-authored
 * dimension type with a bad field once made the whole server refuse to
 * boot - not worth the risk for something this simple). Instead, a
 * repeating task just resets this one World's time periodically - the
 * plain Bukkit World#setTime(long) API already operates per-dimension, so
 * nowhere else on the server is affected.
 */
public class FrozenRealmTimeLock extends BukkitRunnable {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");
    private static final long MIDNIGHT = 18000L;

    /** Every 5 seconds is plenty - vanilla day/night drifts far too slowly for a longer gap to be visible. */
    private static final long PERIOD_TICKS = 100L;

    public static void start(PenguinMafiaMarket plugin) {
        new FrozenRealmTimeLock().runTaskTimer(plugin, 0L, PERIOD_TICKS);
    }

    @Override
    public void run() {
        World frozenRealm = FROZEN_REALM_KEY == null ? null : Bukkit.getWorld(FROZEN_REALM_KEY);
        if (frozenRealm == null) return;
        frozenRealm.setTime(MIDNIGHT);
    }
}
