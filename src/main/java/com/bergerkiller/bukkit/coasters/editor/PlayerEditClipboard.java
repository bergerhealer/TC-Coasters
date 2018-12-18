package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Stores the nodes and connections between nodes copied to the clipboard.
 */
public class PlayerEditClipboard {
    private final PlayerEditState _state;
    private final Matrix4x4 _originalTransform = new Matrix4x4();
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
        this.getPlayerTransform(this._originalTransform);
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

        // Get old and new transformation
        Matrix4x4 transform_a = this._originalTransform.clone();
        Matrix4x4 transform_b = new Matrix4x4();
        this.getPlayerTransform(transform_b);

        // Create a transform from the old positions to the new positions
        transform_a.invert();
        transform_b.multiply(transform_a);
        Matrix4x4 transform = transform_b;

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

    private void getPlayerTransform(Matrix4x4 transform) {
        Location basePos = this._state.getPlayer().getEyeLocation();

        // Compute yaw rotation in steps of 90 degrees
        double yaw = (double) -basePos.getYaw();
        yaw = Math.round(yaw / 90.0);
        yaw *= 90.0;

        // Compute pitch as either -90.0, 0.0 or 90.0
        // Prefer 0.0 as that is the most common case
        double pitch = 0.0;
        if (basePos.getPitch() > 80.0f) {
            pitch = 90.0;
        } else if (basePos.getPitch() < -80.0f) {
            pitch = -90.0;
        }

        transform.setIdentity();
        transform.translate(basePos.getBlockX(), basePos.getBlockY(), basePos.getBlockZ());
        transform.rotateY(yaw);
        transform.rotateX(pitch);

        Vector v = new Vector();
        transform.transformPoint(v);
    }
}
