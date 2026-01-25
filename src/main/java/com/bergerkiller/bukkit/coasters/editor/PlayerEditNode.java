package com.bergerkiller.bukkit.coasters.editor;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector2;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Metadata about a single track node being edited
 */
public class PlayerEditNode {
    public final TrackNode node;

    /** Tracks the position while dragging */
    public Vector dragPosition = null;
    /** Tracks the direction of this node from the circle fit center while dragging */
    public Quaternion dragCircleFitDirection = null;
    /** Tracks the initial state before dragging begun */
    public TrackNodeState startState = null;

    public PlayerEditNode(TrackNode node) {
        this.node = node;
    }

    public boolean hasMoveBegun() {
        return this.startState != null;
    }

    public boolean moveBegin(Player player) {
        if (this.startState != null) {
            return true;
        }

        if (CommonUtil.callEvent(new CoasterBeforeChangeNodeEvent(player, node)).isCancelled()) {
            return false;
        }
        this.startState = this.node.getState();
        return true;
    }

    public void moveEnd() {
        this.startState = null;
    }
}
