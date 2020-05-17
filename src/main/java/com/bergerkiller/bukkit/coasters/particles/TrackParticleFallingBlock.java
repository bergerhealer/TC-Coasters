package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.VirtualFallingBlock;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

public class TrackParticleFallingBlock extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED = (1<<2);
    protected static final int FLAG_MATERIAL_CHANGED = (1<<3);
    protected static final int FLAG_SMALL_CHANGES    = (1<<4);
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private BlockData material;
    private int holderEntityId = -1;
    private int entityId = -1;

    protected TrackParticleFallingBlock(Vector position, Quaternion orientation, BlockData material) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.material = material;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            if (Math.abs(position.getX() - this.position.getX()) < 0.05 &&
                Math.abs(position.getY() - this.position.getY()) < 0.05 &&
                Math.abs(position.getZ() - this.position.getZ()) < 0.05)
            {
                this.setFlag(FLAG_SMALL_CHANGES);
            }
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setMaterial(BlockData material) {
        if (this.material != material) {
            this.material = material;
            this.setFlag(FLAG_MATERIAL_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        VirtualFallingBlock.create(this.holderEntityId, this.entityId).destroy(viewer);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        TrackParticleState state = getState(viewer);

        VirtualFallingBlock block = VirtualFallingBlock.create(this.holderEntityId, this.entityId)
                .position(this.position)
                .smoothMovement(true)
                .material(this.material)
                .glowing(state == TrackParticleState.SELECTED)
                .spawn(viewer);

        this.holderEntityId = block.holderEntityId();
        this.entityId = block.entityId();
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        TrackParticleState state = getState(viewer);

        VirtualFallingBlock.create(this.holderEntityId, this.entityId)
            .glowing(state == TrackParticleState.SELECTED)
            .updateMetadata(viewer);
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_MATERIAL_CHANGED)) {
            this.clearFlag(FLAG_POSITION_CHANGED);

            for (Player viewer : this.getViewers()) {
                this.makeHiddenFor(viewer);
                this.makeVisibleFor(viewer);
            }
        }
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            VirtualFallingBlock block = VirtualFallingBlock.create(this.holderEntityId, this.entityId)
                    .position(this.position)
                    .smoothMovement(true)
                    .respawn(this.clearFlag(FLAG_SMALL_CHANGES))
                    .updatePosition(this.getViewers());

            this.holderEntityId = block.holderEntityId();
            this.entityId = block.entityId();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }
}