package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.QueuedTask;
import com.bergerkiller.bukkit.coasters.util.VirtualArmorStandItem;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Displays an item or 3D model using the head of an armorstand
 */
public class TrackParticleArmorStandItem extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_POSE_CHANGED      = (1<<3);
    protected static final int FLAG_ITEM_CHANGED      = (1<<4);
    protected static final int FLAG_SMALL_CHANGES     = (1<<5);

    private static final QueuedTask<TrackParticleArmorStandItem> DESPAWN_HOLDER_TASK = QueuedTask.create(
            100, TrackParticle::isAdded, TrackParticleArmorStandItem::destroyHolderEntity);

    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private ItemStack item;
    private int holderEntityId = -1;
    private int entityId = -1;

    protected TrackParticleArmorStandItem(Vector position, Quaternion orientation, ItemStack item) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.item = item;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_POSE_CHANGED);
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            if (Math.abs(position.getX() - this.position.getX()) < 0.05 &&
                Math.abs(position.getY() - this.position.getY()) < 0.05 &&
                Math.abs(position.getZ() - this.position.getZ()) < 0.05 &&
                setFlag(FLAG_SMALL_CHANGES))
            {
                DESPAWN_HOLDER_TASK.schedule(this);
            }
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setItem(ItemStack item) {
        if (this.item != item && !this.item.equals(item)) {
            this.item = item;
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
        TrackParticleState state = getState(viewer);

        VirtualArmorStandItem entity = VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                .position(this.position)
                .orientation(this.orientation)
                .item(this.item)
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
            VirtualArmorStandItem entity = VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                    .position(this.position)
                    .respawn(this.clearFlag(FLAG_SMALL_CHANGES));

            for (Player viewer : this.getViewers()) {
                entity.glowing(this.getState(viewer) == TrackParticleState.SELECTED)
                    .updatePosition(viewer);
            }

            this.holderEntityId = entity.holderEntityId();
            this.entityId = entity.entityId();
        }
        if (this.clearFlag(FLAG_POSE_CHANGED) && this.entityId != -1) {
            VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                .orientation(this.orientation)
                .updateOrientation(this.getViewers());
        }
        if (this.clearFlag(FLAG_ITEM_CHANGED) && this.entityId != -1) {
            VirtualArmorStandItem.create(this.holderEntityId, this.entityId)
                .item(this.item)
                .updateItem(this.getViewers());
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
        return this.entityId == entityId;
    }
}
