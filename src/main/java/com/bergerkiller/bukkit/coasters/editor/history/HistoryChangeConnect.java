package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Connects or disconnects two nodes
 */
public class HistoryChangeConnect extends HistoryChange {
    private final Vector nodePosA;
    private final Vector nodePosB;

    public HistoryChangeConnect(TrackNode nodeA, TrackNode nodeB) {
        this(nodeA, nodeA.getPosition(), nodeB.getPosition());
    }

    public HistoryChangeConnect(CoasterWorldAccess world, Vector nodePosA, Vector nodePosB) {
        super(world);
        this.nodePosA = nodePosA;
        this.nodePosB = nodePosB;
    }

    @Override
    protected void run(boolean undo) {
        TrackNode nodeA = this.world.getTracks().findNodeExact(this.nodePosA);
        TrackNode nodeB = this.world.getTracks().findNodeExact(this.nodePosB);
        if (nodeA != null && nodeB != null) {
            if (undo) {
                this.world.getTracks().disconnect(nodeA, nodeB);
            } else {
                this.world.getTracks().connect(nodeA, nodeB);
            }
        }
    }
}
