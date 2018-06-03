package com.bergerkiller.bukkit.coasters.editor;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Metadata about a single track node being edited
 */
public class PlayerEditNode {
    public final TrackNode node;
    public Vector dragPosition = null;
    public Vector startPosition;
    public Vector startUp;

    public PlayerEditNode(TrackNode node) {
        this.node = node;
    }

    public void moveBegin() {
        if (this.startPosition == null) {
            this.startPosition = node.getPosition();
            this.startUp = node.getOrientation();
        }
    }

    public void moveEnd() {
        this.startPosition = null;
        this.startUp = null;
    }
}
