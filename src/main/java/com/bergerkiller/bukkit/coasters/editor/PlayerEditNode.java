package com.bergerkiller.bukkit.coasters.editor;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;

/**
 * Metadata about a single track node being edited
 */
public class PlayerEditNode {
    public final TrackNode node;
    public Vector dragPosition = null;
    public TrackNodeState startState = null;

    public PlayerEditNode(TrackNode node) {
        this.node = node;
    }

    public void moveBegin() {
        if (this.startState == null) {
            this.startState = this.node.getState();
        }
    }

    public void moveEnd() {
        this.startState = null;
    }
}
