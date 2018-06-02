package com.bergerkiller.bukkit.coasters.editor;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Metadata about a single track node being edited
 */
public class PlayerEditNode {
    public final TrackNode node;
    public Vector dragPosition = null;

    public PlayerEditNode(TrackNode node) {
        this.node = node;
    }
}
