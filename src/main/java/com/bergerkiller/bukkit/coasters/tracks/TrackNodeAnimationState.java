package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.particles.TrackParticleArrow;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleText;

/**
 * A single state of a track node, which can be the target state for animations
 */
public class TrackNodeAnimationState {
    protected static final TrackNodeAnimationState[] EMPTY_ARR = new TrackNodeAnimationState[0];
    public final String name;
    public final TrackNodeState state;
    public final TrackPendingConnection[] connections;
    private TrackParticleArrow _upParticleArrow;
    private TrackParticleText _nameLabelParticleText;

    // For dereference()
    private TrackNodeAnimationState(TrackNodeAnimationState original, TrackPendingConnection[] dereferenced_connections) {
        this.name = original.name;
        this.state = original.state;
        this.connections = dereferenced_connections;
        this._upParticleArrow = null;
        this._nameLabelParticleText = null;
    }

    private TrackNodeAnimationState(String name, TrackNode node, TrackNodeState state, TrackPendingConnection[] connections, int index) {
        this.name = name;
        this.state = state;
        this.connections = connections;
        this._upParticleArrow = node.getWorld().getParticles().addParticleArrow(state.position, node.getDirection(), state.orientation);

        String text = TrackParticleText.getOrdinalText(index, this.name);
        Vector textPos = state.position.clone();
        textPos.add(this._upParticleArrow.getOrientation().upVector().multiply(0.6));
        this._nameLabelParticleText = node.getWorld().getParticles().addParticleTextNoItem(textPos, text);
    }

    /**
     * Breaks the reference to nodes, storing position instead
     * 
     * @return altered animation state that is dereferenced
     */
    public TrackNodeAnimationState dereference() {
        TrackPendingConnection[] dereferenced_connections = new TrackPendingConnection[connections.length];
        for (int i = 0; i < dereferenced_connections.length; i++) {
            dereferenced_connections[i] = dereferenced_connections[i].dereference();
        }
        return new TrackNodeAnimationState(this, dereferenced_connections);
    }

    public void updateIndex(int new_index) {
        this._nameLabelParticleText.setText(TrackParticleText.getOrdinalText(new_index, this.name));
    }

    public void destroyParticles() {
        this._upParticleArrow.remove();
        this._nameLabelParticleText.remove();
    }

    public static TrackNodeAnimationState create(String name, TrackNode node, TrackNodeState state, TrackPendingConnection[] connections, int index) {
        return new TrackNodeAnimationState(name, node, state, connections, index);
    }
}
