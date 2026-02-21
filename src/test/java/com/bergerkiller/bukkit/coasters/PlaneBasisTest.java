package com.bergerkiller.bukkit.coasters;

import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class PlaneBasisTest {

    @Test
    public void testFromPointsFlatX() {
        // Create points flat on the Y-plane (all y=1) but with different x and z values
        List<Vector> points = Arrays.asList(
                new Vector(1.0, 0.0, 0.5),
                new Vector(1.0, 0.5, 0.0),
                new Vector(1.0, 2.5, -0.5),
                new Vector(1.0, 0.0, -3.0)
        );
        PlaneBasis basis = PlaneBasis.estimateFromPoints(points, new Vector(0.0, 1.0, 0.0));

        assertEquals(new Vector(1.0, 0.75, -0.75), basis.centroid);
        assertEquals(new Vector(1.0, 0.0, 0.0), basis.ex);
    }

    @Test
    public void testFromPointsFlatY() {
        // Create points flat on the Y-plane (all y=1) but with different x and z values
        List<Vector> points = Arrays.asList(
                new Vector(0.0, 1.0, 0.5),
                new Vector(0.5, 1.0, 0.0),
                new Vector(2.5, 1.0, -0.5),
                new Vector(0.0, 1.0, -3.0)
        );
        PlaneBasis basis = PlaneBasis.estimateFromPoints(points, new Vector(0.0, 1.0, 0.0));

        assertEquals(new Vector(0.75, 1.0, -0.75), basis.centroid);
        assertEquals(new Vector(0.0, 1.0, 0.0), basis.ey);
    }

    @Test
    public void testFromPointsOnLine() {
        // Create points that are all on a single line (flat Y)
        List<Vector> pointsSource = Arrays.asList(
                new Vector(4.0, 0.0, 0.0),
                new Vector(4.0, 0.0, 3.0),
                new Vector(4.0, 0.0, 6.0),
                new Vector(4.0, 0.0, 22.0)
        );

        // Test at many different rotation and introduce a small amount of random deviation
        final double RANDOM_NOISE_AMOUNT = 0.01;
        Random r = new Random();
        for (final boolean randomNoise : new boolean[] {false, true}) {
            for (double yawAngle = 5.0; yawAngle <= 360.0; yawAngle += 15.0) {
                Quaternion q = Quaternion.fromYawPitchRoll(0.0, yawAngle, 0.0);
                List<Vector> points = pointsSource.stream()
                        .map(p -> {
                            p = p.clone();
                            if (randomNoise) {
                                // Add small random deviation
                                MathUtil.addToVector(p,
                                        r.nextDouble(RANDOM_NOISE_AMOUNT),
                                        r.nextDouble(RANDOM_NOISE_AMOUNT),
                                        r.nextDouble(RANDOM_NOISE_AMOUNT));
                            }
                            q.transformPoint(p);
                            return p;
                        })
                        .collect(Collectors.toList());
                PlaneBasis basis = PlaneBasis.estimateFromPoints(points, new Vector(0.0, 1.0, 0.0));

                assertEquals(0.0, basis.ex.getY(), RANDOM_NOISE_AMOUNT);
                assertEquals(0.0, basis.ey.getY(), RANDOM_NOISE_AMOUNT);
                assertEquals(0.0, basis.normal.getX(), RANDOM_NOISE_AMOUNT);
                assertEquals(1.0, basis.normal.getY(), RANDOM_NOISE_AMOUNT);
                assertEquals(0.0, basis.normal.getZ(), RANDOM_NOISE_AMOUNT);
            }
        }
    }

    @Test
    public void testPlanePointTransform() {
        // Create points flat on the Y-plane (all y=1) but with different x and z values
        List<Vector> points = Arrays.asList(
                new Vector(0.0, 1.0, 0.5),
                new Vector(0.5, 1.0, 0.0),
                new Vector(2.5, 1.0, -0.5),
                new Vector(0.0, 1.0, -3.0)
        );
        PlaneBasis basis = PlaneBasis.estimateFromPoints(points, new Vector(0.0, 1.0, 0.0));

        // Test position
        {
            Vector test = new Vector(20.5, 100.5, -1005.2);
            Vector conv = basis.toPlane(test);

            assertEquals(conv.getY(), test.getY(), 1e-8); // Flat, so this should be identical

            Vector reverse = basis.fromPlane(conv);
            assertEquals(reverse, test);
        }

        // Test unit vector (should stay unit vector)
        {
            Vector test = new Vector(0.5, 1.0, -7.8).normalize();
            Vector conv = basis.toPlane(test);

            assertEquals(1.0, conv.length(), 1e-8); // Should still be a unit vector

            Vector reverse = basis.fromPlane(conv);
            assertEquals(test, reverse);
        }
    }
}
