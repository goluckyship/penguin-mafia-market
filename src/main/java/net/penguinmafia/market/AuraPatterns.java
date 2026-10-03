package net.penguinmafia.market;

import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the fixed-shape /aura looks - right now just one: a pair of black
 * devil horns above the head with jagged red lightning-crack veins
 * branching out behind, based on a reference image of two mirrored
 * fractal/crack shapes converging into horn points. No Particle.FLAME
 * anywhere in this - every point is a colored DUST particle, so the red and
 * black are exact, not whatever fixed tint a vanilla particle happens to be.
 *
 * Computed once (a fixed seed, so the shape is consistent across restarts
 * rather than re-rolling into a different-looking crack pattern each time)
 * and cached - AuraManager rotates/translates these same relative points
 * onto the player's current position and facing every tick, same as it does
 * for the simple ring.
 */
final class AuraPatterns {

    private AuraPatterns() {}

    private static final Color RED_BRIGHT = Color.fromRGB(255, 25, 20);
    private static final Color RED_DARK = Color.fromRGB(60, 5, 5);
    private static final Color HORN_BLACK = Color.fromRGB(12, 12, 14);

    private static List<AuraManager.RelativePoint> cachedDevil;

    static List<AuraManager.RelativePoint> devilAura() {
        if (cachedDevil == null) {
            List<AuraManager.RelativePoint> points = new ArrayList<>();
            Random rand = new Random(133742); // fixed seed - same shape every time
            // Two horns, mirrored left/right.
            points.addAll(horn(false));
            points.addAll(horn(true));
            // A dense black backing behind where the cracks run - in the
            // reference image the space around the red veins is just the
            // picture's white background, but in-game that would be empty
            // air (showing straight through to the sky/whatever's behind
            // the player), which reads as a gap rather than part of the
            // design. This fills that same wing-shaped area solid black so
            // the red cracks look like they're running over a black wing,
            // not floating on nothing.
            points.addAll(wingFill(false, rand));
            points.addAll(wingFill(true, rand));
            // Several lightning-crack trunks per side, branching as they go,
            // trailing up and out from around shoulder height - mirrored so
            // the whole thing reads as symmetric, same as the reference image.
            for (int i = 0; i < 3; i++) {
                double startX = 0.12 + i * 0.05;
                double startY = 0.1 + i * 0.25;
                double startZ = -0.1 - i * 0.05;
                growCrack(points, rand, startX, startY, startZ, 0.15, 0.9 - i * 0.1, -0.35, 1.0, 0, false);
                growCrack(points, rand, -startX, startY, startZ, -0.15, 0.9 - i * 0.1, -0.35, 1.0, 0, true);
            }
            cachedDevil = points;
        }
        return cachedDevil;
    }

    /** One horn: a curved, tapering line of black dust rising from the side of the head and curling forward at the tip. */
    private static List<AuraManager.RelativePoint> horn(boolean rightSide) {
        List<AuraManager.RelativePoint> points = new ArrayList<>();
        int steps = 9;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double side = (rightSide ? 1 : -1);
            double outward = side * (0.22 + t * 0.30);
            double up = 0.55 + t * 0.85;
            double forward = 0.05 + 0.22 * Math.sin(t * Math.PI * 0.9); // curls forward then tip bends back a touch
            float size = (float) (1.5 - t * 0.9); // thick base, pointed tip
            points.add(new AuraManager.RelativePoint(outward, up, forward, HORN_BLACK, Math.max(0.45f, size)));
        }
        return points;
    }

    /**
     * A dense scatter of black dust filling a leaf/wing-shaped area behind
     * the player, in roughly the same envelope the crack trunks grow
     * through (same starting offsets and direction, see devilAura()) -
     * widest in the middle, tapering to nothing at the root and the tip, so
     * it reads as a solid black wing with the red cracks running over it.
     */
    private static List<AuraManager.RelativePoint> wingFill(boolean rightSide, Random rand) {
        List<AuraManager.RelativePoint> points = new ArrayList<>();
        double side = rightSide ? 1 : -1;
        int count = 110;
        for (int i = 0; i < count; i++) {
            double t = rand.nextDouble(); // 0 = near the shoulder (root), 1 = far tip
            double widthFactor = Math.sin(t * Math.PI); // 0 at both ends, widest at the middle
            double spread = (rand.nextDouble() * 2 - 1) * (0.12 + widthFactor * 0.38);
            double outward = side * (0.12 + t * 1.05 + spread * 0.5);
            double up = 0.05 + t * 1.55 + spread * 0.35;
            double back = -0.08 - t * 0.85;
            float size = 0.75f + (float) rand.nextDouble() * 0.5f;
            points.add(new AuraManager.RelativePoint(outward, up, back, HORN_BLACK, size));
        }
        return points;
    }

    /**
     * Recursively grows one jagged lightning-crack branch from (x,y,z) in
     * direction (dx,dy,dz), occasionally splitting into a secondary branch,
     * fading from bright red near the trunk to near-black at the tips -
     * matching the reference image's veiny, tapering red cracks.
     */
    private static void growCrack(List<AuraManager.RelativePoint> points, Random rand,
                                   double x, double y, double z,
                                   double dx, double dy, double dz,
                                   double length, int depth, boolean mirror) {
        if (depth > 3 || length < 0.08) return;
        int steps = 4 + rand.nextInt(3);
        double stepLen = length / steps;
        double cx = x, cy = y, cz = z;
        double vx = dx, vy = dy, vz = dz;
        for (int i = 0; i < steps; i++) {
            // Jagged wiggle each step - what makes it read as a crack/lightning vein rather than a smooth curve.
            vx += (rand.nextDouble() - 0.5) * 0.25;
            vy += (rand.nextDouble() - 0.3) * 0.15;
            vz += (rand.nextDouble() - 0.5) * 0.20;
            double mag = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (mag < 0.001) mag = 1;
            vx /= mag; vy /= mag; vz /= mag;

            cx += vx * stepLen;
            cy += vy * stepLen;
            cz += vz * stepLen;

            double fade = (depth / 4.0) + (i / (double) steps) * 0.25;
            Color color = lerp(RED_BRIGHT, RED_DARK, Math.min(1.0, fade));
            float size = (float) Math.max(0.3, 1.1 - fade * 0.8);
            points.add(new AuraManager.RelativePoint(cx, cy, cz, color, size));

            if (depth < 3 && rand.nextDouble() < 0.4) {
                double bdx = vx + (rand.nextDouble() - 0.5) * 0.9;
                double bdy = vy + rand.nextDouble() * 0.4;
                double bdz = vz + (rand.nextDouble() - 0.5) * 0.9;
                growCrack(points, rand, cx, cy, cz, bdx, bdy, bdz, length * 0.55, depth + 1, mirror);
            }
        }
    }

    private static Color lerp(Color a, Color b, double t) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return Color.fromRGB(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
