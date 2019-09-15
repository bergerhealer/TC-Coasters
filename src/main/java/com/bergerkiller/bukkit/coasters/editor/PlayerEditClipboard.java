package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Stores the nodes and connections between nodes copied to the clipboard.
 */
public class PlayerEditClipboard {
    private final PlayerEditState _state;
    private final PlayerOrigin _origin = new PlayerOrigin();
    private final List<TrackNodeState> _nodes = new ArrayList<TrackNodeState>();
    private final Set<TrackConnectionState> _connections = new HashSet<TrackConnectionState>();

    protected PlayerEditClipboard(PlayerEditState state) {
        this._state = state;
    }

    /**
     * Gets whether contents are copied into this clipboard
     * 
     * @return True if filled
     */
    public boolean isFilled() {
        return !this._nodes.isEmpty();
    }

    /**
     * Gets the number of nodes on the clipboard
     * 
     * @return node count
     */
    public int getNodeCount() {
        return this._nodes.size();
    }

    /**
     * Copies all nodes and connections between them that are selected by the player
     */
    public void copy() {
        this._origin.setForPlayer(this._state.getPlayer());
        this._nodes.clear();
        this._connections.clear();
        for (TrackNode node : this._state.getEditedNodes()) {
            this._nodes.add(node.getState());
            for (TrackConnection connection : node.getConnections()) {
                if (this._state.isEditing(connection.getOtherNode(node))) {
                    this._connections.add(TrackConnectionState.create(connection));
                }
            }
        }
    }

    /**
     * Creates a new coaster at the current position of the player using the
     * nodes last copied. If no nodes were copied, this function does nothing.
     */
    public void paste() {
        if (!this.isFilled()) {
            return;
        }

        // Get origin transformation to apply
        Matrix4x4 transform = this._origin.getTransformTo(PlayerOrigin.getForPlayer(this._state.getPlayer()));

        // Create new coaster and all nodes on the clipboard
        HistoryChange history = this._state.getHistory().addChangeGroup();
        TrackWorld tracks = this._state.getTracks();
        TrackCoaster coaster = tracks.createNew(this._nodes.get(0).transform(transform));
        for (int i = 1; i < this._nodes.size(); i++) {
            coaster.createNewNode(this._nodes.get(i).transform(transform));
        }
        for (TrackNode node : coaster.getNodes()) {
            history.addChangeCreateNode(node);
        }

        // Create connections
        for (TrackConnectionState connectionState : this._connections) {
            TrackConnection connection = tracks.connect(connectionState.transform(transform));
            if (connection != null) {
                history.addChangeConnect(connection);
            }
        }

        // Edit the newly created nodes
        this._state.clearEditedNodes();
        for (TrackNode node : coaster.getNodes()) {
            this._state.setEditing(node, true);
        }
    }
}
