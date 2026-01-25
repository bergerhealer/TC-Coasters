package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

/**
 * Represents a circle positions on a bisector of two points.
 */
class Circle2DBiSector extends Circle2D {
    /** The bisector line */
    public final BiSector2D bisector;
    /** Side indicator: +1 = CCW, -1 = CW, 0 = undefined */
    public final int side;
    /** Vector pointing towards the side of the circle center from the bisector middle, X coordinate */
    public final double sideX;
    /** Vector pointing towards the side of the circle center from the bisector middle, Y coordinate */
    public final double sideY;
    /** Minor (short) arc (true) or major (long) arc (false) */
    public final boolean minorArc;

    public Circle2DBiSector(double cx, double cy, double r, BiSector2D bisector, int side, double sideX, double sideY, boolean minorArc) {
        super(cx, cy, r);
        this.bisector = bisector;
        this.side = side;
        this.sideX = sideX;
        this.sideY = sideY;
        this.minorArc = minorArc;
    }
}
