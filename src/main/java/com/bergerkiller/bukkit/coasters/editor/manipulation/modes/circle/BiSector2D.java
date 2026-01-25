package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.common.math.Vector2;

/**
 * Represents a bisector between two points in 2D space.
 * This represents the line that is equidistant from both points on a circle.
 */
final class BiSector2D {
    /** Middle of the bisector, X coordinate */
    public final double mx;
    /** Middle of the bisector, Y coordinate */
    public final double my;
    /** Perpendicular unit vector X component */
    public final double perpUnitX;
    /** Perpendicular unit vector Y component */
    public final double perpUnitY;
    /** Length of the bisector from the original points */
    public final double length;

    public BiSector2D(double mx, double my, double perpUnitX, double perpUnitY, double length) {
        this.mx = mx;
        this.my = my;
        this.perpUnitX = perpUnitX;
        this.perpUnitY = perpUnitY;
        this.length = length;
    }

    /**
     * Gets a point along this bisector at distance (theta) t from the midpoint
     *
     * @param t Theta
     * @return Point at distance t along the bisector
     */
    public Vector2 getPoint(double t) {
        return new Vector2(
                this.mx + this.perpUnitX * t,
                this.my + this.perpUnitY * t
        );
    }

    /**
     * Creates a bisector from two points
     * @param p1 First point
     * @param p2 Second point
     * @return Bisector between the two points
     */
    public static BiSector2D fromPoints(Vector2 p1, Vector2 p2) {
        // chord, midpoint and perp direction
        double mx = (p1.x + p2.x) * 0.5;
        double my = (p1.y + p2.y) * 0.5;
        double cx = p2.x - p1.x;
        double cy = p2.y - p1.y;
        double perpX = -cy;
        double perpY = cx;
        double perpLen = Math.hypot(perpX, perpY);

        // normalize perp
        if (perpLen < 1e-12) {
            // degenerate: pinned points coincident or extremely close -> no constrained solution
            perpX = 0.0;
            perpY = 1.0;
        } else {
            perpX /= perpLen;
            perpY /= perpLen;
        }

        return new BiSector2D(mx, my, perpX, perpY, perpLen);
    }
}
