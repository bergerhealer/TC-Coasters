package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Creates a new node. Does not create or restore connections
 * with other nodes, which should be done using child history changes.
 */
public class HistoryChangeCreateNode extends HistoryChange {
    private final String coasterName;
    private final Vector position;
    private final Vector up;

    public HistoryChangeCreateNode(TrackNode node) {
        this(node, node.getCoaster().getName(), node.getPosition(), node.getOrientation());
    }

    public HistoryChangeCreateNode(CoasterWorldAccess world, String coasterName, Vector position, Vector up) {
        super(world);
        this.coasterName = coasterName;
        this.position = position;
        this.up = up;
    }

    @Override
    protected void run(boolean undo) {
        if (undo) {
            TrackNode nodeToDelete = this.world.getTracks().findNodeExact(this.position);
            if (nodeToDelete != null && nodeToDelete.getCoaster().getName().equals(this.coasterName)) {
                nodeToDelete.remove();
            }
        } else {
            this.world.getTracks().createNew(this.coasterName, this.position, this.up);
        }
    }

}
