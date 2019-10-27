package com.bergerkiller.bukkit.coasters.editor;

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
    public Vector dragPosition = null;
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
