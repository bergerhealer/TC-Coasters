package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Changes the position and/or orientation of a node
 */
public class HistoryChangeMoveNode extends HistoryChange {
    private final Vector fromPosition;
    private final Vector fromOrientation;
    private final Vector toPosition;
    private final Vector toOrientation;

    public HistoryChangeMoveNode(CoasterWorldAccess world,
            Vector fromPosition, Vector fromOrientation,
            Vector toPosition, Vector toOrientation)
    {
        super(world);
        if (fromPosition == null) {
            throw new IllegalArgumentException("From position can not be null");
        }
        if (fromOrientation == null) {
            throw new IllegalArgumentException("From orientation can not be null");
        }
        if (toPosition == null) {
            throw new IllegalArgumentException("To position can not be null");
        }
        if (toOrientation == null) {
            throw new IllegalArgumentException("To orientation can not be null");
        }
        this.fromPosition = fromPosition;
        this.fromOrientation = fromOrientation;
        this.toPosition = toPosition;
        this.toOrientation = toOrientation;
    }

    @Override
    protected final void run(boolean undo) {
        if (undo) {
            TrackNode node = world.getTracks().findNodeExact(this.toPosition);
            if (node != null) {
                node.setPosition(this.fromPosition);
                node.setOrientation(this.fromOrientation);
            }
        } else {
            TrackNode node = world.getTracks().findNodeExact(this.fromPosition);
            if (node != null) {
                node.setPosition(this.toPosition);
                node.setOrientation(this.toOrientation);
            }
        }
    }

}
