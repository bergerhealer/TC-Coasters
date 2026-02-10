package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import org.bukkit.util.Vector;

/**
 * A manipulated track node on a circle, tracking angle values
 * around the circle for position updates. This node can be on any of the
 * 360 degrees of the circle and is not constrained by some bi-sector.
 */
class ManipulatedTrackNodeOnCircle extends ManipulatedTrackNode {
    /**
     * Calculated (new) radian angle applied to the node.
     * Angle is relative to the pinned orientation.
     */
    public double angle;
    /** The up-orientation vector of the node relative to the circle orientation */
    public Vector up;

    public ManipulatedTrackNodeOnCircle(ManipulatedTrackNode draggedNode) {
        super(draggedNode);
    }
}
