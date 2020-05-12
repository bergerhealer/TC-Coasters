package com.bergerkiller.bukkit.coasters.particles;

import java.util.Collections;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.VirtualArrowItem;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * A particle consisting of two sticks that measure out the width of
 * a track object. Is only displayed when the object is selected.
 */
public class TrackParticleWidthMarker extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    private static final ItemStack MARKER_ITEM = new ItemStack(MaterialUtil.getFirst("REDSTONE_TORCH", "LEGACY_REDSTONE_TORCH_ON"));
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private double width;
    private int markerA_entityId = -1;
    private int markerB_entityId = -1;

    public TrackParticleWidthMarker(Vector position, Quaternion orientation, double width) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.width = width;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setWidth(double width) {
        if (this.width != width) {
            this.width = width;
            this.setFlag(FLAG_POSITION_CHANGED);
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
    public boolean isVisible(Player viewer) {
        return this.getState(viewer) == TrackParticleState.SELECTED;
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.markerA_entityId != -1) {
            VirtualArrowItem.create(this.markerA_entityId).destroy(viewer);
        }
        if (this.markerB_entityId != -1) {
            VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);
        }
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        // Spawn 1 or 2 marker entities to denote the position on the path
        if (this.width <= 0.0) {
            this.markerA_entityId = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position, this.orientation)
                    .spawn(viewer);
        } else {
            Vector dir = this.orientation.forwardVector().multiply(0.5 * this.width);
            this.markerA_entityId = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().subtract(dir), this.orientation)
                    .spawn(viewer);
            this.markerB_entityId = VirtualArrowItem.create(this.markerB_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().add(dir), this.orientation)
                    .spawn(viewer);
        }
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            for (Player viewer : this.getViewers()) {
                moveMarkers(viewer);
            }

            if (this.width <= 0.0) {
                this.markerB_entityId = -1;
            }
        }
    }

    private void moveMarkers(Player viewer) {
        // Move 1 or 2 marker entities to denote the position on the path
        if (this.width <= 0.0) {
            // Move first item
            VirtualArrowItem.create(this.markerA_entityId)
                    .position(this.position, this.orientation)
                    .move(Collections.singleton(viewer));

            // Despawn second item if a previous width was set
            if (this.markerB_entityId != -1) {
                VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);
            }
        } else {
            // Move both items
            Vector dir = this.orientation.forwardVector().multiply(0.5 * this.width);
            VirtualArrowItem itemA = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().subtract(dir), this.orientation);
            VirtualArrowItem itemB = VirtualArrowItem.create(this.markerB_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().add(dir), this.orientation);

            if (itemA.hasEntityId()) {
                itemA.move(Collections.singleton(viewer));
            } else {
                this.markerA_entityId = itemA.spawn(viewer);
            }
            if (itemB.hasEntityId()) {
                itemB.move(Collections.singleton(viewer));
            } else {
                this.markerB_entityId = itemB.spawn(viewer);
            }
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.markerA_entityId == entityId || this.markerB_entityId == entityId;
    }
}
