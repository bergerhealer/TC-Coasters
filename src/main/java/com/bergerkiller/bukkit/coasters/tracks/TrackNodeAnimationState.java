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
    private TrackParticleArrow _upParticleArrow;
    private TrackParticleText _nameLabelParticleText;

    private TrackNodeAnimationState(String name, TrackNode node, TrackNodeState state, int index) {
        this.name = name;
        this.state = state;
        this._upParticleArrow = node.getParticles().addParticleArrow(state.position, node.getDirection(), state.orientation);

        String text = TrackParticleText.getOrdinalText(index, this.name);
        Vector textPos = state.position.clone();
        textPos.add(this._upParticleArrow.getOrientation().upVector().multiply(0.6));
        this._nameLabelParticleText = node.getParticles().addParticleTextNoItem(textPos, text);
    }

    public void updateIndex(int new_index) {
        this._nameLabelParticleText.setText(TrackParticleText.getOrdinalText(new_index, this.name));
    }

    public void destroyParticles() {
        this._upParticleArrow.remove();
        this._nameLabelParticleText.remove();
    }

    public static TrackNodeAnimationState create(String name, TrackNode node, TrackNodeState state, int index) {
        return new TrackNodeAnimationState(name, node, state, index);
    }
}
