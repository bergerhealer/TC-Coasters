package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.particles.TrackParticleArrow;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleText;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A single state of a track node, which can be the target state for animations
 */
public class TrackNodeAnimationState {
    protected static final TrackNodeAnimationState[] EMPTY_ARR = new TrackNodeAnimationState[0];
    public final String name;
    public final TrackNodeState state;
    public final TrackConnectionState[] connections;
    private TrackParticleArrow _upParticleArrow;
    private TrackParticleText _nameLabelParticleText;

    private TrackNodeAnimationState(String name, TrackNodeState state, TrackConnectionState[] connections) {
        this.name = name;
        this.state = state;
        this.connections = connections;
        this._upParticleArrow = null;
        this._nameLabelParticleText = null;
    }

    /**
     * Breaks the reference to nodes, storing position instead
     * 
     * @return altered animation state that is dereferenced
     */
    public TrackNodeAnimationState dereference() {
        TrackConnectionState[] dereferenced_connections = new TrackConnectionState[connections.length];
        for (int i = 0; i < dereferenced_connections.length; i++) {
            dereferenced_connections[i] = connections[i].dereference();
        }
        return new TrackNodeAnimationState(this.name, this.state, dereferenced_connections);
    }

    /**
     * Creates a new track connection state with a connection added or updated, if it already existed.
     * 
     * @param connection
     * @return updated track node animation state
     */
    public TrackNodeAnimationState updateConnection(TrackConnectionState connection) {
        // Check if already exists
        for (int i = 0; i < this.connections.length; i++) {
            TrackConnectionState prev_connection = this.connections[i];
            if (prev_connection.node_a.isReference(connection.node_a)) {
                if (!prev_connection.node_b.isReference(connection.node_b)) {
                    continue;
                }
            } else if (prev_connection.node_a.isReference(connection.node_b)) {
                if (!prev_connection.node_b.isReference(connection.node_a)) {
                    continue;
                }
            } else {
                continue;
            }

            // Same connection! Update existing in-place
            TrackConnectionState[] new_connections = this.connections.clone();
            new_connections[i] = connection;
            return new TrackNodeAnimationState(this.name, this.state, new_connections);
        }

        // Append new connection
        TrackConnectionState[] new_connections = LogicUtil.appendArray(this.connections, connection);
        return new TrackNodeAnimationState(this.name, this.state, new_connections);
    }

    /**
     * Updates the track objects of a connection, if the connection matches one stored
     * in this animation state. If not found, the same track node animation state is returned.
     * 
     * @param connection
     * @return updated track animation state
     */
    public TrackNodeAnimationState updateTrackObjects(TrackConnection connection) {
        for (int i = 0; i < this.connections.length; i++) {
            TrackConnectionState old_connection = this.connections[i];
            if (old_connection.isSame(connection) || old_connection.isSameFlipped(connection)) {
                TrackConnectionState[] new_connections = this.connections.clone();
                new_connections[i] = TrackConnectionState.create(connection);
                return new TrackNodeAnimationState(this.name, this.state, new_connections);
            }
        }
        return this;
    }

    /**
     * Removes track connections from this state's connections that are connected to the node specified.
     * A new updated animation state is returned if connections were removed. The same animation state
     * instance is returned if there were no changes.
     * 
     * @param connectedTo The node reference to which connections should be removed
     * @return updated track node animation state
     */
    public TrackNodeAnimationState removeConnectionWith(TrackNodeReference connectedTo) {
        for (int i = 0; i < this.connections.length; i++) {
            if (this.connections[i].isConnected(connectedTo)) {
                TrackConnectionState[] new_connections = LogicUtil.removeArrayElement(this.connections, i);
                return new TrackNodeAnimationState(this.name, this.state, new_connections);
            }
        }
        return this;
    }

    /**
     * Clears all connections of this animation state, if any were specified
     * 
     * @return updated animation state
     */
    public TrackNodeAnimationState clearConnections() {
        if (this.connections.length > 0) {
            return new TrackNodeAnimationState(this.name, this.state, TrackConnectionState.EMPTY);
        } else {
            return this;
        }
    }

    public void updateIndex(int new_index) {
        this._nameLabelParticleText.setText(TrackParticleText.getOrdinalText(new_index, this.name));
    }

    /**
     * Spawns the particles displayed for this animation state, for a node owner
     * 
     * @param owner
     * @param index
     */
    public void spawnParticles(TrackNode owner, int index) {
        this.destroyParticles();

        this._upParticleArrow = owner.getWorld().getParticles().addParticleArrow(state.position, owner.getDirection(), state.orientation);

        String text = TrackParticleText.getOrdinalText(index, this.name);
        Vector textPos = state.position.clone();
        textPos.add(this._upParticleArrow.getOrientation().upVector().multiply(0.6));
        this._nameLabelParticleText = owner.getWorld().getParticles().addParticleTextNoItem(textPos, text);
    }

    /**
     * Destroys previously spawned particles, if spawned
     */
    public void destroyParticles() {
        if (this._upParticleArrow != null) {
            this._upParticleArrow.remove();
            this._upParticleArrow = null;
        }
        if (this._nameLabelParticleText != null) {
            this._nameLabelParticleText.remove();
            this._nameLabelParticleText = null;
        }
    }

    public static TrackNodeAnimationState create(String name, TrackNodeState state, TrackConnectionState[] connections) {
        return new TrackNodeAnimationState(name, state, connections);
    }
}
