package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Changes the rails block of a single node
 */
public class HistoryChangeSetRail extends HistoryChange {
    private final Vector node_pos;
    private final IntVector3 rail_old;
    private final IntVector3 rail_new;

    public HistoryChangeSetRail(CoasterWorldAccess world, Vector nodePos, IntVector3 rail_old, IntVector3 rail_new) {
        super(world);
        if (nodePos == null) {
            throw new IllegalArgumentException("Node position can not be null");
        }
        this.node_pos = nodePos;
        this.rail_old = rail_old;
        this.rail_new = rail_new;
    }

    @Override
    protected void run(boolean undo) {
        TrackNode node = world.getTracks().findNodeExact(this.node_pos);
        if (node != null) {
            node.setRailBlock(undo ? rail_old : rail_new);
        }
    }

}
