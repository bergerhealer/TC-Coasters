package com.bergerkiller.bukkit.coasters.particles;

import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.QueuedTask;
import com.bergerkiller.bukkit.coasters.util.VirtualArmorStandItem;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;

import java.util.Collections;

/**
 * Displays an item or 3D model using the head of an armorstand
 */
public class TrackParticleArmorStandItem extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_POSE_CHANGED      = (1<<3);
    protected static final int FLAG_ITEM_CHANGED      = (1<<4);
    protected static final int FLAG_LARGE_CHANGES     = (1<<5);

    private static final QueuedTask<TrackParticleArmorStandItem> DESPAWN_HOLDER_TASK = QueuedTask.create(
            100, TrackParticle::isAdded, TrackParticleArmorStandItem::destroyHolderEntity);

    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private LODItemStack.List lodList;
    private int holderEntityId = -1;
    private int entityId = -1;

    protected TrackParticleArmorStandItem(Vector position, Quaternion orientation, LODItemStack.List lodList) {
        if (lodList == null) {
            throw new IllegalArgumentException("LOD Item List cannot be null");
        }
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.lodList = lodList;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_POSE_CHANGED);
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

    public void setItem(ItemStack item) {
        setLODItems(LODItemStack.createList(item));
    }

    public void setLODItems(LODItemStack.List lodList) {
        if (lodList == null) {
            throw new IllegalArgumentException("LOD Item List cannot be null");
        }
        if (!this.lodList.equals(lodList)) {
            this.lodList = lodList;
            this.setFlag(FLAG_ITEM_CHANGED);
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
        VirtualArmorStandItem.create(this.holderEntityId, this.entityId).destroy(viewer);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        makeVisibleFor(viewer, this.lodList.getNearest().getItem());
    }

    @Override
    public TrackParticleLifecycle getLifecycle(State state) {
        if (lodList.isSingleLOD()) {
            return this;
        } else {
            return new LODLifecycle(lodList, lodList.getForDistance(state.getViewDistance()));
        }
    }

    @Override
    public boolean isLifecycleValid(State state) {
        return lodList.isSingleLOD();
    }

    private VirtualArmorStandItem createArmorStandItem() {
        return VirtualArmorStandItem.create(this.holderEntityId, this.entityId);
    }

    private void makeVisibleFor(Player viewer, ItemStack item) {
        TrackParticleState state = getState(viewer);

        VirtualArmorStandItem entity = createArmorStandItem()
                .position(this.position)
                .orientation(this.orientation)
                .item(item)
                .glowing(state == TrackParticleState.SELECTED)
                .spawn(viewer);
        this.holderEntityId = entity.holderEntityId();
        this.entityId = entity.entityId();
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        TrackParticleState state = getState(viewer);

        VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
            .glowing(state == TrackParticleState.SELECTED)
            .position(this.position)
            .updateMetadata(viewer);
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            boolean large_changes = this.clearFlag(FLAG_LARGE_CHANGES);
            if (hasViewers()) {
                VirtualArmorStandItem entity = VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                        .position(this.position);

                if (large_changes) {
                    for (Player viewer : this.getViewers()) {
                        entity.glowing(this.getState(viewer) == TrackParticleState.SELECTED)
                                .updatePosition(viewer);
                    }
                } else {
                    // For small changes don't move the entity but destroy and re-spawn the holder
                    // This disables interpolation so these changes are more accurate for positioning
                    entity.destroyHolderKeepEntityId(getViewers());
                    for (Player viewer : this.getViewers()) {
                        entity.glowing(this.getState(viewer) == TrackParticleState.SELECTED)
                                .spawnHolder(viewer);
                    }
                    DESPAWN_HOLDER_TASK.schedule(this);
                }

                this.holderEntityId = entity.holderEntityId();
                this.entityId = entity.entityId();
            }
        }
        if (this.clearFlag(FLAG_POSE_CHANGED) && this.entityId != -1) {
            VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                .orientation(this.orientation)
                .updateOrientation(this.getViewers());
        }
        if (this.clearFlag(FLAG_ITEM_CHANGED) && this.entityId != -1) {
            if (lodList.isSingleLOD()) {
                // Update directly
                VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                        .item(this.lodList.getNearest().getItem())
                        .updateItem(this.getViewers());
            } else {
                // Must force players in view to refresh LOD
                for (Player viewer : getViewers()) {
                    getWorld().scheduleViewerUpdate(viewer);
                }
            }
        }
    }

    private void destroyHolderEntity() {
        VirtualArmorStandItem entity = VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
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

    /**
     * Used for different LODs, if configured that way
     */
    private class LODLifecycle implements TrackParticleLifecycle {
        private final LODItemStack.List lodListUsed; // For detecting changes in item
        private final LODItemStack lod;

        public LODLifecycle(LODItemStack.List lodList, LODItemStack lod) {
            this.lodListUsed = lodList;
            this.lod = lod;
        }

        @Override
        public void makeVisibleFor(Player viewer) {
            TrackParticleArmorStandItem.this.makeVisibleFor(viewer, lod.getItem());
        }

        @Override
        public void makeHiddenFor(Player viewer) {
            TrackParticleArmorStandItem.this.makeHiddenFor(viewer);
        }

        @Override
        public boolean isLifecycleValid(State state) {
            return lodList == lodListUsed && lod.isForDistance(state.getViewDistance());
        }

        @Override
        public void switchFromLifecycle(TrackParticleLifecycle oldLifecycle, Player viewer) {
            if (oldLifecycle instanceof LODLifecycle) {
                // Only have to update the displayed item
                createArmorStandItem()
                        .item(this.lod.getItem())
                        .updateItem(Collections.singletonList(viewer));
            } else {
                TrackParticleLifecycle.super.switchFromLifecycle(oldLifecycle, viewer);
            }
        }
    }
}
