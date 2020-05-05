package com.bergerkiller.bukkit.coasters.objects;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A single object on a connection
 */
public class TrackObject implements Cloneable, TrackParticleState.Source {
    public static final TrackObject[] EMPTY = new TrackObject[0];
    private TrackObjectType<?> type;
    private TrackParticle particle;
    private double distance;
    private boolean flipped;

    public TrackObject(TrackObjectType<?> type, double distance, boolean flipped) {
        if (type == null) {
            throw new IllegalArgumentException("Track object type can not be null");
        }
        this.type = type;
        this.particle = null;
        this.distance = distance;
        this.flipped = flipped;
    }

    /**
     * Gets the type of object displayed
     * 
     * @return object type
     */
    public TrackObjectType<?> getType() {
        return this.type;
    }

    /**
     * Sets the type of object displayed
     * 
     * @param connection Connection on which it is displayed (used for internal calculations)
     * @param type The object type displayed
     */
    public void setType(TrackConnection connection, TrackObjectType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Track object type can not be null");
        }

        this.type = type;
        connection.markChanged();

        // Update particle (if already spawned)
        if (this.particle != null) {
            TrackConnection.PointOnPath point = findPointOnPath(connection);
            if (this.type.getClass() == type.getClass()) {
                // Same type, different properties, only an update of the particle is needed
                this.type.updateParticle(CommonUtil.unsafeCast(this.particle), point);
            } else {
                // Recreate (different type)
                this.particle.remove();
                this.particle = this.type.createParticle(point);
                this.particle.setStateSource(this);
            }
        }
    }

    public double getDistance() {
        return this.distance;
    }

    /**
     * Whether the track object front is facing along the direction from connection node A to B (false),
     * or flipped from node B to A (true)
     * 
     * @return True if flipped
     */
    public boolean isFlipped() {
        return this.flipped;
    }

    /**
     * Sets the distance of the track object, while also computing whether the object orientation should be
     * flipped 180 degrees based on the 'right' direction vector. This vector should be set to the direction
     * the player is looking. This method always refreshes the position of the object, even if distance
     * is unchanged.
     * 
     * @param connection
     * @param distance
     * @param rightDirection
     */
    public void setDistanceComputeFlipped(TrackConnection connection, double distance, Vector rightDirection) {
        this.distance = distance;
        this.flipped = false;
        TrackConnection.PointOnPath point = this.findPointOnPath(connection);
        this.flipped = (point.orientation.rightVector().dot(rightDirection) < 0.0);
        connection.markChanged();

        if (this.particle != null) {
            if (this.flipped) {
                point.orientation.rotateYFlip();
            }
            this.type.updateParticle(CommonUtil.unsafeCast(this.particle), point);
        }
    }

    /**
     * Sets the distance and flipped properties, without any sort of special computation
     * 
     * @param connection
     * @param distance
     * @param flipped
     */
    public void setDistanceFlipped(TrackConnection connection, double distance, boolean flipped) {
        if (this.distance != distance || this.flipped != flipped) {
            this.distance = distance;
            this.flipped = flipped;
            connection.markChanged();
            this.onShapeUpdated(connection);
        }
    }

    public void setDistanceFlippedSilently(double distance, boolean flipped) {
        this.distance = distance;
        this.flipped = flipped;
    }

    public void onAdded(TrackConnection connection) {
        TrackConnection.PointOnPath point = findPointOnPath(connection);
        this.particle = this.type.createParticle(point);
        this.particle.setStateSource(this);
    }

    private TrackConnection.PointOnPath findPointOnPath(TrackConnection connection) {
        TrackConnection.PointOnPath point = connection.findPointAtDistance(this.distance, this.type.getWidth());
        if (this.flipped) {
            point.orientation.rotateYFlip();
        }
        return point;
    }

    public void onRemoved(TrackConnection connection) {
        if (this.particle != null) {
            connection.getWorld().getParticles().removeParticle(this.particle);
            this.particle = null;
        }
    }

    public void onShapeUpdated(TrackConnection connection) {
        if (this.particle != null) {
            TrackConnection.PointOnPath point = findPointOnPath(connection);
            this.type.updateParticle(CommonUtil.unsafeCast(this.particle), point);
        }
    }

    public void onStateUpdated(Player viewer) {
        if (this.particle != null) {
            this.particle.onStateUpdated(viewer);
        }
    }

    @Override
    public TrackObject clone() {
        return new TrackObject(this.type, this.distance, this.flipped);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObject) {
            TrackObject other = (TrackObject) o;
            return this.distance == other.distance &&
                   this.type.equals(other.type) &&
                   this.flipped == other.flipped;
        } else {
            return false;
        }
    }

    @Override
    public TrackParticleState getState(PlayerEditState viewer) {
        return viewer.getMode() == PlayerEditMode.OBJECT && viewer.getObjects().isEditing(this) ? 
                TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
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
