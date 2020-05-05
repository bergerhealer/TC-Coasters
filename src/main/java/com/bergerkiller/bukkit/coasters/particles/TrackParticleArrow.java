package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.VirtualArrowItem;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * A particle consisting of a floating arrow pointing into a certain direction.
 * Uses an armor stand with a lever item rotated into the direction. The direction
 * is along which is rotated, the up vector indicates the roll around this vector.
 */
public class TrackParticleArrow extends TrackParticle {
    private TrackParticleItemType itemType = TrackParticleItemType.LEVER;
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private boolean positionChanged = false;
    private boolean itemChanged = false;
    private int entityId = -1;

    protected TrackParticleArrow(Vector position, Quaternion orientation) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    public void setPosition(Vector position) {
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public void setDirection(Vector direction, Vector up) {
        this.setOrientation(Quaternion.fromLookDirection(direction, up));
    }

    public void setOrientation(Quaternion orientation) {
        if (!orientation.equals(this.orientation)) {
            this.orientation.setTo(orientation);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public Quaternion getOrientation() {
        return this.orientation;
    }

    public void setItemType(TrackParticleItemType itemType) {
        if (!this.itemType.equals(itemType)) {
            this.itemType = itemType;
            this.itemChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;
            VirtualArrowItem.create(this.entityId)
                .position(this.position, this.orientation)
                .move(getViewers());
        }
        if (this.itemChanged) {
            this.itemChanged = false;
            for (Player viewer : this.getViewers()) {
                VirtualArrowItem.create(this.entityId)
                    .item(this.itemType.getItem(this.getState(viewer)))
                    .updateItem(viewer);
            }
        }
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        TrackParticleState state = getState(viewer);
        VirtualArrowItem.create(this.entityId)
            .item(this.itemType.getItem(state))
            .glowing(state == TrackParticleState.SELECTED && getWorld().getPlugin().getGlowingSelections())
            .updateItem(viewer)
            .updateGlowing(viewer);
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        VirtualArrowItem.create(this.entityId).destroy(viewer);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        TrackParticleState state = getState(viewer);
        this.entityId = VirtualArrowItem.create(this.entityId)
            .glowing(state == TrackParticleState.SELECTED && getWorld().getPlugin().getGlowingSelections())
            .item(this.itemType.getItem(state))
            .position(this.position, this.orientation)
            .spawn(viewer);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }
}
