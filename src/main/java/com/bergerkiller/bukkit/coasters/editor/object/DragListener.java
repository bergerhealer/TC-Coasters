package com.bergerkiller.bukkit.coasters.editor.object;

/**
 * Listener interface for handling when a player drags the right-click cursor
 */
public interface DragListener {
    /**
     * Called when a player right-click drags
     * 
     * @param delta Relative delta the player moved the cursor (0-1 per 90.0 degrees angle)
     */
    public void onDrag(double delta);
}
