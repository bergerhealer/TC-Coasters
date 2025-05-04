package com.bergerkiller.bukkit.coasters.particles;

import com.bergerkiller.bukkit.coasters.util.QueuedTask;
import com.bergerkiller.bukkit.coasters.util.VirtualDisplayEntity;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Displays a Block or 3D model using a display entity
 */
public class TrackParticleDisplayBlock extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_TRANSFORM_CHANGED = (1<<3);
    protected static final int FLAG_BLOCK_CHANGED = (1<<4);
    protected static final int FLAG_CLIP_CHANGED      = (1<<5);
    protected static final int FLAG_BRIGHTNESS_CHANGED = (1<<6);
    protected static final int FLAG_LARGE_CHANGES     = (1<<7);

    private static final QueuedTask<TrackParticleDisplayBlock> DESPAWN_HOLDER_TASK = QueuedTask.create(
            100, TrackParticle::isAdded, TrackParticleDisplayBlock::destroyHolderEntity);

    private DoubleOctree.Entry<TrackParticle> position;
    private final Quaternion orientation;
    private double clip;
    private Brightness brightness;
    private final Vector size;
    private BlockData blockData;
    private int holderEntityId = -1;
    private int entityId = -1;

    protected TrackParticleDisplayBlock(Vector position, Quaternion orientation, double clip, Brightness brightness, Vector size, BlockData blockData) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.clip = clip;
        this.brightness = brightness;
        this.size = size.clone();
        this.blockData = blockData;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_TRANSFORM_CHANGED);
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            if (Math.abs(position.getX() - this.position.getX()) > 0.02 ||
                Math.abs(position.getY() - this.position.getY()) > 0.02 ||
                Math.abs(position.getZ() - this.position.getZ()) > 0.02)
            {
                setFlag(FLAG_LARGE_CHANGES);
            }
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setClip(double clip) {
        if (this.clip != clip) {
            this.clip = clip;
            this.setFlag(FLAG_CLIP_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setBrightness(Brightness brightness) {
        if (this.brightness != brightness) {
            this.brightness = brightness;
            this.setFlag(FLAG_BRIGHTNESS_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setSize(Vector size) {
        if (!this.size.equals(size)) {
            MathUtil.setVector(this.size, size);
            this.setFlag(FLAG_TRANSFORM_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setBlockData(BlockData blockData) {
        if (this.blockData != blockData) {
            this.blockData = blockData;
            this.setFlag(FLAG_BLOCK_CHANGED);
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
        VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId).destroy(viewer);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        TrackParticleState state = getState(viewer);

        VirtualDisplayEntity entity = VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                .position(this.position)
                .orientation(this.orientation)
                .clip(this.clip)
                .scale(this.size)
                .block(this.blockData)
                .brightness(this.brightness)
                .glowing(state == TrackParticleState.SELECTED)
                .spawn(viewer);
        this.holderEntityId = entity.holderEntityId();
        this.entityId = entity.entityId();
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        TrackParticleState state = getState(viewer);

        VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
            .glowing(state == TrackParticleState.SELECTED)
            .updateMetadata(viewer);
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            boolean large_changes = this.clearFlag(FLAG_LARGE_CHANGES);
            if (hasViewers()) {
                VirtualDisplayEntity entity = VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                        .position(this.position);
                if (large_changes) {
                    entity.spawnHolder(getViewers());
                    DESPAWN_HOLDER_TASK.schedule(this);
                }
                for (Player viewer : this.getViewers()) {
                    entity.updatePosition(viewer);
                }
                this.holderEntityId = entity.holderEntityId();
                this.entityId = entity.entityId();
            }
        }
        if (this.clearFlag(FLAG_TRANSFORM_CHANGED) && this.entityId != -1) {
            VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                .orientation(this.orientation)
                .scale(this.size)
                .updateMetadata(this.getViewers());
        }
        if (this.clearFlag(FLAG_BLOCK_CHANGED) && this.entityId != -1) {
            VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                .block(this.blockData)
                .updateMetadata(this.getViewers());
        }
        if (this.clearFlag(FLAG_CLIP_CHANGED) && this.entityId != -1) {
            VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                    .clip(this.clip)
                    .scale(this.size) // Needed for calculations
                    .updateMetadata(this.getViewers());
        }
        if (this.clearFlag(FLAG_BRIGHTNESS_CHANGED) && this.entityId != -1) {
            VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                    .brightness(this.brightness)
                    .updateMetadata(this.getViewers());
        }
    }

    private void destroyHolderEntity() {
        VirtualDisplayEntity entity = VirtualDisplayEntity.createBlock(this.holderEntityId, this.entityId)
                .position(this.position)
                .destroyHolder(getViewers());
        this.holderEntityId = entity.holderEntityId();
        this.entityId = entity.entityId();
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.holderEntityId == entityId || this.entityId == entityId;
    }
}
