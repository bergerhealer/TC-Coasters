package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNode;
import org.bukkit.util.Vector;

/**
 * A dragged track node on a circular arc, tracking theta values
 * along the arc for position updates.
 */
class DraggedTrackNodeOnCircleArc extends DraggedTrackNode {
    /**
     * Initial theta value at the time dragging begun.
     * Normalized fraction along arc from first->last (can be <0 or >1)
     */
    public double initialTheta;
    /**
     * Calculated (new) theta value during dragging.
     * Normalized fraction along arc from first->last (can be <0 or >1).
     */
    public double theta;
    /**
     * The up-orientation of the node relative to the direction/normal vector on the circle
     */
    public Vector up = null;

    public DraggedTrackNodeOnCircleArc(DraggedTrackNode copy) {
        super(copy);
    }

    public void setInitialTheta(double initialTheta) {
        this.theta = this.initialTheta = initialTheta;
    }
}
