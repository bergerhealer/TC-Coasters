package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.attachments.control.light.LightAPIController;

/**
 * A particle consisting of a floating item
 */
public class TrackParticleLight extends TrackParticle {
    private LightAPIController controller;
    private IntVector3 position;
    private LightType type;
    private int level;

    public TrackParticleLight(Vector position, LightType type, int level) {
        this.controller = null;
        this.position = new IntVector3(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        this.type = type;
        this.level = level;
    }

    @Override
    public final boolean isUsingViewers() {
        return false;
    }

    @Override
    protected void onAdded() {
        addWithoutViewers();
        addLight();
    }

    @Override
    protected void onRemoved() {
        removeWithoutViewers();
        removeLight();
    }

    public void setPosition(Vector position) {
        IntVector3 new_blockpos = new IntVector3(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        if (!new_blockpos.equals(this.position)) {
            this.removeLight();
            this.position = new_blockpos;
            this.addLight();
        }
    }

    public void setType(LightType type) {
        if (this.type != type) {
            this.removeLight();
            this.type = type;
            this.controller = null; // reset
            this.addLight();
        }
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.removeLight();
            this.level = level;
            this.addLight();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        double dx = this.position.midX() - viewerPosition.getX();
        double dy = this.position.midY() - viewerPosition.getY();
        double dz = this.position.midZ() - viewerPosition.getZ();
        return (dx*dx) + (dy*dy) + (dz*dz);
    }

    @Override
    public void updateAppearance() {
        // No viewers, unused
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        // No viewers, unused
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        // No viewers, unused
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return false;
    }

    private void addLight() {
        if (this.controller == null) {
            this.controller = LightAPIController.get(this.getWorld().getBukkitWorld(),
                    this.type == LightType.SKY);
        }

        this.controller.add(this.position, this.level);
    }

    private void removeLight() {
        if (this.controller == null) {
            return;
        }

        this.controller.remove(this.position, this.level);
    }

    /**
     * Type of light emitted
     */
    public static enum LightType {
        SKY, BLOCK
    }
}
