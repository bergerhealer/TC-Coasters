package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Tracks information for performing node dragging manipulations.
 */
public final class NodeDragEvent {
    private final Matrix4x4 current;
    private final Matrix4x4 change;
    private final boolean isStart;

    public NodeDragEvent(Matrix4x4 current, Matrix4x4 change, boolean isStart) {
        this.current = current;
        this.change = change;
        this.isStart = isStart;
    }

    /**
     * Gets the current transform representing the player's current look direction
     *
     * @return current transform
     */
    public Matrix4x4 current() {
        return this.current;
    }

    /**
     * Gets the change transform representing the drag movement
     *
     * @return change transform
     */
    public Matrix4x4 change() {
        return this.change;
    }

    /**
     * Whether this is the very first drag event in the drag sequence
     *
     * @return true if starting drag event
     */
    public boolean isStart() {
        return isStart;
    }


}
