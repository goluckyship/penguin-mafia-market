package net.penguinmafia.market;

import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the fixed-shape /aura looks - right now just one: a pair of black
 * devil horns above the head plus a full pair of red demonic wings - a solid
 * black backing with jagged red lightning-crack veins over it - running all
 * the way down past the torso to the lower body, not just a small patch
 * behind the shoulders. No Particle.FLAME anywhere in this - every point is
 * a colored DUST particle, so the colors are exact, not whatever fixed tint
 * a vanilla particle happens to be.
 *
 * Computed once (a fixed seed, so the shape is consistent across restarts
 * rather than re-rolling into a different-looking pattern every time) and
 * cached - AuraManager rotates/translates these same relative points onto
 * the player's current position and facing every tick, same as it does for
 * the simple ring.
 */
final class AuraPatterns {

    private AuraPatterns() {}

    private static final Color RED_BRIGHT = Color.fromRGB(255, 25, 20);
    private static final Color RED_DARK = Color.fromRGB(60, 5, 5);
    private static final Color HORN_BLACK = Color.fromRGB(12, 12, 14);
    private static final Color CLOUD_DARK = Color.fromRGB(35, 35, 40);
    private static final Color CLOUD_LIGHT = Color.fromRGB(70, 70, 78);

    private static List<AuraManager.RelativePoint> cachedDevil;
    private static List<AuraManager.RelativePoint> cachedOm;

    static List<AuraManager.RelativePoint> devilAura() {
        if (cachedDevil == null) {
            List<AuraManager.RelativePoint> points = new ArrayList<>();
            Random rand = new Random(133742); // fixed seed - same shape every time
            // Two horns, mirrored left/right.
            points.addAll(horn(false));
            points.addAll(horn(true));
            // A dense black backing behind where the red cracks run, spanning
            // from the shoulders all the way down past the torso to the
            // lower body/legs - a full wing, not just a patch near the head.
            // Without this the gaps between the red veins would just be
            // empty air (showing straight through to whatever's behind the
            // player), which reads as missing rather than part of the design.
            points.addAll(wingFill(false, rand));
            points.addAll(wingFill(true, rand));
            // Several red lightning-crack trunks per side, branching as they
            // go and running the same long shoulder-to-lower-body span as the
            // black backing - mirrored so the whole thing reads as symmetric.
            for (int i = 0; i < 4; i++) {
                double startX = 0.12 + i * 0.04;
                double startY = 0.35 - i * 0.1;
                double startZ = -0.1 - i * 0.05;
                growCrack(points, rand, startX, startY, startZ, 0.1, -1.0 + i * 0.05, -0.3, 2.0, 0, false);
                growCrack(points, rand, -startX, startY, startZ, -0.1, -1.0 + i * 0.05, -0.3, 2.0, 0, true);
            }
            cachedDevil = points;
        }
        return cachedDevil;
    }

    /**
     * "om": a flattened dark storm cloud hovering just above the player's
     * head, built from a dense scatter of dark-gray-to-black dust packed
     * into a lumpy, puffy blob (a handful of overlapping bulges rather than
     * a perfect sphere, so it reads as a cloud and not a ball) - no vanilla
     * CLOUD particle involved, since that one's a fixed light-gray/white and
     * can't be darkened.
     */
    static List<AuraManager.RelativePoint> omCloud() {
        if (cachedOm == null) {
            List<AuraManager.RelativePoint> points = new ArrayList<>();
            Random rand = new Random(90210); // fixed seed - same cloud shape every time
            // A handful of overlapping puff centers, each with its own scatter
            // of points around it, so the outline is lumpy instead of a sphere.
            double[][] puffs = {
                    {0.0, 0.0, 0.0, 0.55}, {0.35, 0.05, 0.1, 0.4}, {-0.35, 0.03, -0.05, 0.4},
                    {0.15, 0.12, -0.3, 0.35}, {-0.15, 0.1, 0.3, 0.35}, {0.0, -0.1, 0.0, 0.3}
            };
            for (double[] puff : puffs) {
                int count = 40;
                for (int i = 0; i < count; i++) {
                    double radius = puff[3] * Math.cbrt(rand.nextDouble());
                    double theta = rand.nextDouble() * Math.PI * 2;
                    double phi = Math.acos(2 * rand.nextDouble() - 1);
                    double x = puff[0] + radius * Math.sin(phi) * Math.cos(theta);
                    double y = puff[1] + radius * Math.cos(phi) * 0.6; // flattened vertically
                    double z = puff[2] + radius * Math.sin(phi) * Math.sin(theta);
                    Color color = lerp(CLOUD_LIGHT, CLOUD_DARK, rand.nextDouble());
                    float size = 1.0f + (float) rand.nextDouble() * 0.6f;
                    points.add(new AuraManager.RelativePoint(x, 1.75 + y, z, color, size));
                }
            }
            cachedOm = points;
        }
        return cachedOm;
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
     * A dense scatter of black dust filling a wing-shaped area behind the
     * player that runs from the shoulders all the way down past the torso
     * to the lower body/legs (not just a patch up near the head) - widest
     * around the middle, tapering to nothing at the top and bottom, so it
     * reads as a full solid black wing with the red cracks running over it.
     */
    private static List<AuraManager.RelativePoint> wingFill(boolean rightSide, Random rand) {
        List<AuraManager.RelativePoint> points = new ArrayList<>();
        double side = rightSide ? 1 : -1;
        int count = 220;
        for (int i = 0; i < count; i++) {
            double t = rand.nextDouble(); // 0 = shoulder (top), 1 = lower body/legs (bottom)
            double widthFactor = Math.sin(t * Math.PI); // 0 at both ends, widest around the middle
            double spread = (rand.nextDouble() * 2 - 1) * (0.12 + widthFactor * 0.55);
            double outward = side * (0.12 + widthFactor * 0.9 + spread * 0.5);
            double up = 0.35 - t * 2.1; // from shoulder height down past the feet
            double back = -0.08 - widthFactor * 0.55;
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
