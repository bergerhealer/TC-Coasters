package com.bergerkiller.bukkit.coasters.tracks.path;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

/**
 * Quadratic bezier curve maths
 * 
 * https://pomax.github.io/bezierinfo/#decasteljau
 */
public class Bezier {
    public final EndPoint endA, endB;
    public double fpA, fpB, fdA, fdB;

    private Bezier(EndPoint endA, EndPoint endB) {
        this.endA = endA;
        this.endB = endB;
    }

    public static Bezier create(TrackConnectionPath path) {
        return new Bezier(path.getEndA(), path.getEndB());
    }

    public static Bezier create(EndPoint endA, EndPoint endB) {
        return new Bezier(endA, endB);
    }

    public Bezier setup_k0(double t) {
        double t2 = t*t;

        // fpB = -6t^2 + 6t
        // fpA = 6t^2 - 6t
        // fdB = -9t^2 + 6t
        // fdA = 9t^2 - 12t + 3

        fpB = 6.0 * (t - t2);
        fpA = -fpB;
        fdB = fpB - 3.0 * t2;
        fdA = -fdB - 6.0 * t + 3.0;
        return this;
    }

    public Bezier setup_k1(double t) {
        double t2 = t*t;
        double t3 = t2*t;

        // fpA = 2t^3 - 3t^2 + 1.0
        // fpB = -2t^3 + 3t^2
        // fdA = 3t^3 - 6t^2 + 3t
        // fdB = -3t^3 + 3t^2

        fpB = -2.0 * t3 + 3.0 * t2;
        fpA = -fpB + 1.0;
        fdB = fpB - t3;
        fdA = -fpB + t3 + 3.0 * (t - t2);
        return this;
    }

    public Bezier setup_k2(double t) {
        double t2 = t*t;
        double t3 = t2*t;
        double t4 = t2*t2;

        // fpB = -0.5t^4 + t^3
        // fpA = 0.5t^4 - t^3 + t
        // fdB = -0.75t^4 + t^3
        // fdA = 0.75t^4 - 2t^3 + 1.5t^2

        fpB = -0.5 * t4 + t3;
        fpA = -fpB + t;
        fdB = -0.75 * t4 + t3;
        fdA = -fdB - t3 + 1.5*t2;
        return this;
    }

    public Vector compute() {
        return compute(new Vector());
    }

    public Vector compute(Vector out_vec) {
        return compute(this.endA, this.endB, this.fpA, this.fpB, this.fdA, this.fdB, out_vec);
    }

    public static Vector compute(EndPoint endA, EndPoint endB, double fpA, double fpB, double fdA, double fdB, Vector out_vec) {
        double pfdA = fdA * endA.getStrength();
        double pfdB = fdB * endB.getStrength();
        Vector pA = endA.getPosition();
        Vector pB = endB.getPosition();
        Vector dA = endA.getDirection();
        Vector dB = endB.getDirection();
        out_vec.setX(fpA*pA.getX() + fpB*pB.getX() + pfdA*dA.getX() + pfdB*dB.getX());
        out_vec.setY(fpA*pA.getY() + fpB*pB.getY() + pfdA*dA.getY() + pfdB*dB.getY());
        out_vec.setZ(fpA*pA.getZ() + fpB*pB.getZ() + pfdA*dA.getZ() + pfdB*dB.getZ());
        return out_vec;
    }
}
