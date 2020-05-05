package com.bergerkiller.bukkit.coasters;

import static org.junit.Assert.*;

import java.util.Random;

import org.bukkit.util.Vector;
import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;
import com.bergerkiller.bukkit.common.utils.MathUtil;

public class TrackConnectionPathTest {

    // Creates a sufficiently complex path for testing purposes
    public TrackConnectionPath createTestPath() {
        return TrackConnectionPath.create(new Vector(20.0, 30.0, -502.0), new Vector(-0.4, 0.1, 1.0),
                                          new Vector(25.0, 25.0, -490.0), new Vector(-0.3, 0.3, 1.0));
    }

    @Test
    public void testTrackSingleEndedA() {
        TrackConnectionPath path = createTestPath();
        path.getEndA().initNormal();
        path.getEndB().initAuto();

        // Position vector
        assertVectorEquals(path.getPosition(0.0), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.25), 19.343208, 29.841932, -498.25255);
        assertVectorEquals(path.getPosition(0.5), 20.59674, 28.67894, -495.8356);
        assertVectorEquals(path.getPosition(0.75), 22.801903, 26.926477, -493.50085);
        assertVectorEquals(path.getPosition(1.0), 25.0, 25.0, -490.0);

        // Motion vector
        assertVectorEquals(path.getMotionVector(0.0), -0.369800, 0.0924500, 0.924500);
        assertVectorEquals(path.getMotionVector(0.25), 0.15269, -0.243214, 0.957879);
        assertVectorEquals(path.getMotionVector(0.5), 0.580247, -0.469009, 0.665841);
        assertVectorEquals(path.getMotionVector(0.75), 0.580247, -0.469009, 0.665841);
        assertVectorEquals(path.getMotionVector(1.0), 0.358979, -0.358979, 0.86155);

        // Some error expected, less error in range 0.5-1.0 (Auto End B causes linearity)
        assertEquals(1.503, path.getLinearError(0.0, 0.5), 1e-3);
        assertEquals(0.163, path.getLinearError(0.5, 1.0), 1e-3);
        assertEquals(2.504, path.getLinearError(0.0, 1.0), 1e-3);
        assertEquals(2.504, path.getLinearError(1.0, 0.0), 1e-3);
    }

    @Test
    public void testTrackSingleEndedB() {
        TrackConnectionPath path = createTestPath();
        path.getEndA().initAuto();
        path.getEndB().initInverted();

        // Position vector
        assertVectorEquals(path.getPosition(0.0), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.25), 22.106404, 27.893596, -498.495305);
        assertVectorEquals(path.getPosition(0.5), 24.158744, 25.841256, -496.154148);
        assertVectorEquals(path.getPosition(0.75), 25.381712, 24.618288, -493.735916);
        assertVectorEquals(path.getPosition(1.0), 25.0, 25.0, -490.0);

        // Motion vector
        assertVectorEquals(path.getMotionVector(0.0), 0.358979, -0.358979, 0.86155);
        assertVectorEquals(path.getMotionVector(0.25), 0.533575, -0.533575, 0.656198);
        assertVectorEquals(path.getMotionVector(0.5), 0.533575, -0.533575, 0.656198);
        assertVectorEquals(path.getMotionVector(0.75), 0.184899, -0.184899, 0.965207);
        assertVectorEquals(path.getMotionVector(1.0), -0.276172, 0.276172, 0.920575);

        // Some error expected, less error in range 0.0-0.5 (Auto End A causes linearity)
        assertEquals(0.170, path.getLinearError(0.0, 0.5), 1e-3);
        assertEquals(1.645, path.getLinearError(0.5, 1.0), 1e-3);
        assertEquals(2.686, path.getLinearError(0.0, 1.0), 1e-3);
        assertEquals(2.686, path.getLinearError(1.0, 0.0), 1e-3);
    }

    @Test
    public void testTrackDoubleEnded() {
        TrackConnectionPath path = createTestPath();
        path.getEndA().initNormal();
        path.getEndB().initInverted();

        // Position vector
        assertVectorEquals(path.getPosition(0.0), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.25), 19.965237, 29.219903, -498.310355);
        assertVectorEquals(path.getPosition(0.5), 22.255484, 27.020196, -495.989748);
        assertVectorEquals(path.getPosition(0.75), 24.667990, 25.060390, -493.674266);
        assertVectorEquals(path.getPosition(1.0), 25.0, 25.0, -490.0);

        // Motion vector
        assertVectorEquals(path.getMotionVector(0.0), -0.369800, 0.0924500, 0.924500);
        assertVectorEquals(path.getMotionVector(0.25), 0.413469, -0.488597, 0.768320);
        assertVectorEquals(path.getMotionVector(0.5), 0.653332, -0.566295, 0.502460);
        assertVectorEquals(path.getMotionVector(0.75), 0.495144, -0.366275, 0.787829);
        assertVectorEquals(path.getMotionVector(1.0), -0.276172, 0.276172, 0.920575);

        // Some error expected across the board
        assertEquals(2.510, path.getLinearError(0.0, 0.5), 1e-3);
        assertEquals(2.637, path.getLinearError(0.5, 1.0), 1e-3);
        assertEquals(1.426, path.getLinearError(0.0, 1.0), 1e-3);
        assertEquals(1.426, path.getLinearError(1.0, 0.0), 1e-3);
    }

    @Test
    public void testTrackStraight() {
        TrackConnectionPath path = createTestPath();
        path.getEndA().initAuto();
        path.getEndB().initAuto();

        // Position vector
        assertVectorEquals(path.getPosition(0.0), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.25), 21.484375, 28.515625, -498.4375);
        assertVectorEquals(path.getPosition(0.5), 22.5, 27.5, -496.0);
        assertVectorEquals(path.getPosition(0.75), 23.515625, 26.484375, -493.5625);
        assertVectorEquals(path.getPosition(1.0), 25.0, 25.0, -490.0);

        // Motion vector
        assertVectorEquals(path.getMotionVector(0.0), 0.358979, -0.358979, 0.86155);
        assertVectorEquals(path.getMotionVector(0.25), 0.358979, -0.358979, 0.86155);
        assertVectorEquals(path.getMotionVector(0.5), 0.358979, -0.358979, 0.86155);
        assertVectorEquals(path.getMotionVector(0.75), 0.358979, -0.358979, 0.86155);
        assertVectorEquals(path.getMotionVector(1.0), 0.358979, -0.358979, 0.86155);

        // We expect (near) 0 error because this bezier is a linear line
        assertEquals(0.0, path.getLinearError(0.0, 0.5), 1e-10);
        assertEquals(0.0, path.getLinearError(0.5, 1.0), 1e-10);
        assertEquals(0.0, path.getLinearError(0.0, 1.0), 1e-10);
        assertEquals(0.0, path.getLinearError(1.0, 0.0), 1e-10);
    }

    @Test
    public void testTrackSamePoints() {
        TrackConnectionPath path = TrackConnectionPath.create(new Vector(20.0, 30.0, -502.0), new Vector(-0.4, 0.1, 1.0),
                                                              new Vector(20.0, 30.0, -502.0), new Vector(-0.3, 0.3, 1.0));
        path.getEndA().initAuto();
        path.getEndB().initAuto();

        // Position vector
        assertVectorEquals(path.getPosition(0.0), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.25), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.5), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(0.75), 20.0, 30.0, -502.0);
        assertVectorEquals(path.getPosition(1.0), 20.0, 30.0, -502.0);

        // Motion vector
        assertVectorEquals(path.getMotionVector(0.0), 0.0, 0.0, 0.0);
        assertVectorEquals(path.getMotionVector(0.25), 0.0, 0.0, 0.0);
        assertVectorEquals(path.getMotionVector(0.5), 0.0, 0.0, 0.0);
        assertVectorEquals(path.getMotionVector(0.75), 0.0, 0.0, 0.0);
        assertVectorEquals(path.getMotionVector(1.0), 0.0, 0.0, 0.0);

        // We expect (near) 0 error because this bezier is a linear line
        assertEquals(0.0, path.getLinearError(0.0, 0.5), 1e-20);
        assertEquals(0.0, path.getLinearError(0.5, 1.0), 1e-20);
        assertEquals(0.0, path.getLinearError(0.0, 1.0), 1e-20);
        assertEquals(0.0, path.getLinearError(1.0, 0.0), 1e-20);
    }

    @Ignore
    @Test
    public void testTrackPathBuildEpsilon() {
        Random r = new Random();
        int n = 0;
        final double epsilon = 1.1102230246251565E-16;
        while (n < 10000000) {
            double input_t0 = r.nextDouble();
            double input_t1 = r.nextDouble();
            double curr_t0 = input_t0;
            double curr_t1 = input_t1;
            int ctr = 0;
            do {
                // Error too large, choose halfway between curr_t0 and curr_t1 and try again
                // If the delta is so small curr_t0 equals curr_t1 this loop breaks.
                //System.out.println("ERROR TOO BIG DELTA=" + (curr_t1 - curr_t0) + " ERROR " + error);
                curr_t1 = 0.5 * (curr_t1 + curr_t0);
                if(++ctr >= 100000000) {
                    fail("FAILED AT [" + n + "] " + input_t0 + "/" + input_t1 + "  ->  " + (curr_t1-curr_t0));
                }
            } while (Math.abs(curr_t1-curr_t0) > epsilon);
            
            n++;
        }
    }

    @Test
    public void testComputeDistanceAtSCurve() {
        // Creates a path that forms a perfect S-curve, where the middle is at theta=0.5
        // This would cause problems with a simple binary algorithm
        TrackConnectionPath path = TrackConnectionPath.create(new Vector(-20.0, 30.0, -502.0), new Vector(0.0, 0.0, 1.0),
                                                              new Vector(20.0, 30.0, -502.0), new Vector(0.0, 0.0, 1.0));
        path.getEndA().initNormal();
        path.getEndB().initInverted();

        double distance = path.computeDistance(0.0, 1.0);
        double distanceEval = computeDistanceEval(path, 0.0, 1.0);
        assertEquals(distance, distanceEval, 2e-4);
    }

    @Test
    public void testComputeDistance() {
        // Pick some random values, can feed a seed into it, raise iterations, etc.
        // Mostly for testing the proof of concept, there might be edge cases that fall through
        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            TrackConnectionPath path = TrackConnectionPath.create(new Vector(20.0, 30.0, -502.0), randDirection(rand),
                                                                  new Vector(25.0, 25.0, -490.0), randDirection(rand));
            path.getEndA().initNormal();
            path.getEndB().initInverted();

            double distance = path.computeDistance(0.0, 1.0);
            double distanceEval = computeDistanceEval(path, 0.0, 1.0);

            // System.out.println("dist=" + distance + "\texp=" + distanceEval);

            // Difference between the two should be below 2e-4, which is about the resolution of
            // Minecraft's entity position network synchronization.
            assertEquals(distance, distanceEval, 2e-4);
        }
    }

    // Computes distance very slowly but guarantees accuracy to within a precision
    private double computeDistanceEval(TrackConnectionPath path, double t0, double t1) {
        double dt = 1e-6;
        Vector p0 = path.getPosition(t0);
        Vector p1 = new Vector();
        double distance = 0.0;
        while (true) {
            t0 += dt;
            if (t0 > t1) {
                break;
            }
            path.getPosition(t0, p1);
            distance += p0.distance(p1);
            p0.copy(p1);
        }
        return distance;
    }

    @Ignore
    @Test
    public void benchmarkComputeDistance() {
        Random rand = new Random(423523532);
        long total_a = 0;
        long total_b = 0;
        int num_paths = 100;
        int num_iterations = 10000;
        for (int i = 0; i < num_paths; i++) {
            TrackConnectionPath path = TrackConnectionPath.create(new Vector(20.0, 30.0, -502.0), randDirection(rand),
                                                                  new Vector(25.0, 25.0, -490.0), randDirection(rand));
            path.getEndA().initNormal();
            path.getEndB().initInverted();

            long time_a = System.nanoTime();
            for (int n = 0; n < num_iterations; n++) {
                /* == Old == */
                path.computeDistance(0.0, 1.0);
                /* ========= */
            }
            long time_b = System.nanoTime();
            for (int n = 0; n < num_iterations; n++) {
                /* == New == */
                path.computeDistance(0.0, 1.0);
                /* ========= */
            }
            long time_c = System.nanoTime();
            total_a += (time_b - time_a);
            total_b += (time_c - time_b);
        }
        System.out.println("Performance: old took " + (total_a / 1000000000.0) + "s VS new took " + (total_b / 1000000000.0) + "s");
    }

    private Vector randDirection(Random rand) {
        while (true) {
            Vector v = new Vector(rand.nextDouble() - 0.5, rand.nextDouble() - 0.5, rand.nextDouble() - 0.5);
            double len = v.length();
            if (len < 1e-10) {
                continue;
            }
            return v.multiply(1.0 / len);
        }
    }

    private void assertVectorEquals(Vector v, double x, double y, double z) {
        try {
            assertEquals(x, v.getX(), 1e-6);
            assertEquals(y, v.getY(), 1e-6);
            assertEquals(z, v.getZ(), 1e-6);
        } catch (java.lang.AssertionError e) {
            System.out.println("Vector not equal:  " +
                MathUtil.round(v.getX(), 6) + ", " +
                MathUtil.round(v.getY(), 6) + ", " +
                MathUtil.round(v.getZ(), 6));
            throw e;
        }
    }
}
