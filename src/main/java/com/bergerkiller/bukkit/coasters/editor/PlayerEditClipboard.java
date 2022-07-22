package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.events.CoasterCopyEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Stores the nodes and connections between nodes copied to the clipboard.
 */
public class PlayerEditClipboard {
    private final PlayerEditState _state;
    private final PlayerOrigin _origin = new PlayerOrigin();
    private final List<TrackNodeState> _nodes = new ArrayList<TrackNodeState>();
    private final Set<TrackConnectionState> _connections = new HashSet<TrackConnectionState>();
    private final Map<TrackNodeState, TrackNodeAnimationState[]> _animations = new HashMap<>();

    protected PlayerEditClipboard(PlayerEditState state) {
        this._state = state;
    }

    public Player getPlayer() {
        return _state.getPlayer();
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
        this._origin.setForPlayer(getPlayer());
        this._nodes.clear();
        this._connections.clear();
        this._animations.clear();

        HashSet<TrackNode> editedNodes = new HashSet<TrackNode>(this._state.getEditedNodes());
        if (CommonUtil.callEvent(new CoasterCopyEvent(getPlayer(), editedNodes, false)).isCancelled()) {
            return;
        }

        for (TrackNode node : editedNodes) {
            TrackNodeState nodeState = node.getState();
            this._nodes.add(nodeState);

            if (!node.getAnimationStates().isEmpty()) {
                List<TrackNodeAnimationState> node_anims = node.getAnimationStates();
                TrackNodeAnimationState[] animations = new TrackNodeAnimationState[node_anims.size()];
                for (int i = 0; i < animations.length; i++) {
                    animations[i] = node_anims.get(i).dereference();
                }
                this._animations.put(nodeState, animations);
            }

            for (TrackConnection connection : node.getConnections()) {
                if (editedNodes.contains(connection.getOtherNode(node))) {
                    this._connections.add(TrackConnectionState.createDereferenced(connection));
                }
            }
        }
    }

    /**
     * Creates a new coaster at the current position of the player using the
     * nodes last copied. If no nodes were copied, this function does nothing.
     */
    public void paste() throws ChangeCancelledException {
        if (!this.isFilled()) {
            return;
        }

        // Get origin transformation to apply
        Matrix4x4 transform = this._origin.getTransformTo(PlayerOrigin.getForPlayer(this._state.getPlayer()));

        // Create new coaster and all nodes on the clipboard
        HistoryChange history = this._state.getHistory().addChangeGroup();
        TrackWorld tracks = this._state.getWorld().getTracks();
        TrackCoaster coaster = tracks.createNewEmpty();

        try {
            // Create nodes
            for (TrackNodeState node_state : this._nodes) {
                TrackNodeAnimationState[] animations = this._animations.get(node_state);
                TrackNode node = coaster.createNewNode(node_state.transform(transform));
                history.addChangeCreateNode(getPlayer(), node);

                // Assign animations for this node
                if (animations != null) {
                    for (TrackNodeAnimationState anim : animations) {
                        TrackConnectionState[] connections = new TrackConnectionState[anim.connections.length];
                        for (int i = 0; i < connections.length; i++) {
                            connections[i] = anim.connections[i].transform(transform);
                        }
                        node.setAnimationState(anim.name, anim.state.transform(transform), connections);
                    }
                }

                // Perms!
                node.checkPowerPermissions(getPlayer());
            }

            // Create connections
            for (TrackConnectionState connectionState : this._connections) {
                TrackConnection connection = tracks.connect(connectionState.transform(transform), true);
                if (connection != null) {
                    history.addChangeAfterConnect(getPlayer(), connection);
                }
            }

            // Initialize coaster animation state connections
            coaster.refreshConnections();

            // Edit the newly created nodes
            this._state.clearEditedNodes();
            for (TrackNode node : coaster.getNodes()) {
                this._state.selectNode(node);
            }
        } catch (ChangeCancelledException ex) {
            // Roll back all changes and rethrow
            this._state.getHistory().removeChange(history);
            tracks.removeCoaster(coaster);
            throw ex;
        }
    }
}
