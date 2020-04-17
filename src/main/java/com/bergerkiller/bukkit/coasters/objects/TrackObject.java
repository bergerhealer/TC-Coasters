package com.bergerkiller.bukkit.coasters.objects;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleObject;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A single object on a connection
 */
public class TrackObject implements Cloneable {
    public static final TrackObject[] EMPTY = new TrackObject[0];
    private double distance;
    private TrackParticleObject particle = null;
    private ItemStack item;

    public TrackObject(double distance, ItemStack item) {
        this.distance = distance;
        this.item = item;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public double getDistance() {
        return this.distance;
    }

    public void setDistance(TrackConnection connection, double distance) {
        if (this.distance != distance) {
            this.distance = distance;
            connection.markChanged();
            this.onShapeUpdated(connection);
        }
    }

    public void setDistanceSilently(double distance) {
        this.distance = distance;
    }

    public void onAdded(TrackConnection connection, TrackConnectionPath path) {
        TrackConnection.PointOnPath point = connection.findPointAtDistance(this.distance);
        this.particle = connection.getWorld().getParticles().addParticleObject(point.position, point.orientation, item);
        this.particle.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(PlayerEditState viewer) {
                return viewer.getMode() == PlayerEditMode.OBJECT && viewer.isEditingTrackObject(TrackObject.this) ? 
                        TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
            }
        });
    }

    public void onRemoved(TrackConnection connection) {
        if (this.particle != null) {
            connection.getWorld().getParticles().removeParticle(this.particle);
            this.particle = null;
        }
    }

    public void onShapeUpdated(TrackConnection connection) {
        if (this.particle != null) {
            TrackConnection.PointOnPath point = connection.findPointAtDistance(this.distance);
            this.particle.setPositionOrientation(point.position, point.orientation);
        }
    }

    public void onStateUpdated(Player viewer) {
        if (this.particle != null) {
            this.particle.onStateUpdated(viewer);
        }
    }

    @Override
    public TrackObject clone() {
        return new TrackObject(this.distance, this.item);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObject) {
            TrackObject other = (TrackObject) o;
            return this.distance == other.distance &&
                   this.item.equals(other.item);
        } else {
            return false;
        }
    }

    /**
     * Converts a list of track objects to a baked array of objects, taking up less space in memory.
     * The track object elements can be cloned individually when clone == true.
     * 
     * @param objects List of track objects
     * @param clone Whether to clone the track objects
     * @return Array of track objects
     */
    public static TrackObject[] listToArray(List<TrackObject> objects, boolean clone) {
        if (objects == null || objects.isEmpty()) {
            return TrackObject.EMPTY;
        } else {
            TrackObject[] objects_clone = new TrackObject[objects.size()];
            if (clone) {
                for (int i = 0; i < objects.size(); i++) {
                    objects_clone[i] = objects.get(i).clone();
                }
            } else {
                for (int i = 0; i < objects.size(); i++) {
                    objects_clone[i] = objects.get(i);
                }
            }
            return objects_clone;
        }
    }

    /**
     * Removes an item from an array of track objects, making sure to return the EMPTY
     * constant when empty.
     * 
     * @param objects
     * @param index
     * @return updated objects array
     */
    public static TrackObject[] removeArrayElement(TrackObject[] objects, int index) {
        if (index == 0 && objects.length == 1) {
            return EMPTY;
        } else {
            return LogicUtil.removeArrayElement(objects, index);
        }
    }
}
