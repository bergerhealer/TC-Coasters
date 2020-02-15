package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

/**
 * Creates a new node. Does not create or restore connections
 * with other nodes, which should be done using child history changes.
 */
public class HistoryChangeCreateNode extends HistoryChange {
    private final String coasterName;
    private final TrackNodeState state;

    public HistoryChangeCreateNode(TrackNode node) {
        this(node.getWorld(), node.getCoaster().getName(), node.getState());
    }

    public HistoryChangeCreateNode(CoasterWorld world, String coasterName, TrackNodeState state) {
        super(world);
        this.coasterName = coasterName;
        this.state = state;
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        if (undo) {
            TrackNode nodeToDelete = this.world.getTracks().findNodeExact(this.state.position);
            if (nodeToDelete != null && nodeToDelete.getCoaster().getName().equals(this.coasterName)) {
                if (nodeToDelete.isLocked()) {
                    throw new TrackLockedException();
                }
                nodeToDelete.remove();
            }
        } else {
            this.world.getTracks().createNew(this.coasterName, this.state);
        }
    }

}
