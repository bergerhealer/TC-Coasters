package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import org.bukkit.util.Vector;

/**
 * A manipulated track node on a circular arc, tracking theta values
 * along the arc for position updates.
 */
class ManipulatedTrackNodeOnCircleArc extends ManipulatedTrackNode {
    /**
     * Initial theta value at the time manipulation begun.
     * Normalized fraction along arc from first->last (can be <0 or >1)
     */
    public double initialTheta;
    /**
     * Calculated (new) theta value during manipulation/dragging.
     * Normalized fraction along arc from first->last (can be <0 or >1).
     */
    public double theta;
    /**
     * The up-orientation of the node relative to the direction/normal vector on the circle
     */
    public Vector up = null;

    public ManipulatedTrackNodeOnCircleArc(ManipulatedTrackNode copy) {
        super(copy);
    }

    public void setInitialTheta(double initialTheta) {
        this.theta = this.initialTheta = initialTheta;
    }
}
