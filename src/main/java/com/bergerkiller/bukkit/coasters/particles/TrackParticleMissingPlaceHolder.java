package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A placeholder class for particles that failed to be initialized.
 * Adding one to a world does not cause any additional resources
 * to be used up, and no packets will be sent for spawning it.
 * It acts mostly as a data/state holder.
 */
public final class TrackParticleMissingPlaceHolder extends TrackParticle {

    @Override
    protected void onAdded() {
    }

    @Override
    protected void onRemoved() {
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return 0;
    }

    @Override
    public void makeVisibleFor(Player viewer) {
    }

    @Override
    public void makeHiddenFor(Player viewer) {
    }

    @Override
    public boolean isUsingViewers() {
        return false;
    }

    @Override
    public void updateAppearance() {
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return false;
    }
}
