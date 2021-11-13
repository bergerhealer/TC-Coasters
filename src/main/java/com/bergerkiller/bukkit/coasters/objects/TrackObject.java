package com.bergerkiller.bukkit.coasters.objects;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleMissingPlaceHolder;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWidthMarker;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A single object on a connection
 */
public class TrackObject implements Cloneable {
    public static final TrackObject[] EMPTY = new TrackObject[0];
    private TrackObjectType<?> type;
    private TrackParticle particle;
    private TrackParticleWidthMarker particleWidthMarker;
    private double distance;
    private boolean flipped;
    private final TrackParticleState.Source source_selected = viewer -> {
        return viewer.getMode() == PlayerEditMode.OBJECT && viewer.getObjects().isEditing(this) ? 
                TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
    };
    private final TrackParticleState.Source source_selected_blink = viewer -> {
        return (  viewer.getMode() == PlayerEditMode.OBJECT &&
                  viewer.getObjects().isBlink() &&
                  viewer.getObjects().isEditing(this)  )
                ?
                  TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
    };

    public TrackObject(TrackObjectType<?> type, double distance, boolean flipped) {
        if (type == null) {
            throw new IllegalArgumentException("Track object type can not be null");
        }
        this.type = type;
        this.particle = null;
        this.particleWidthMarker = null;
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

        boolean typeClassChanged = (this.type.getClass() != type.getClass());
        this.type = type;
        connection.markChanged();

        // Update particle (if already spawned)
        if (isAdded()) {
            TrackConnection.PointOnPath point = findPointOnPath(connection);
            if (typeClassChanged) {
                // Recreate (different type)
                this.particle.remove();
                initParticle(point);
            } else {
                // Same type, different properties, only an update of the particle is needed
                updateParticle(point);
            }
        }
        if (this.particleWidthMarker != null) {
            this.particleWidthMarker.setWidth(this.type.getWidth());
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

        if (isAdded()) {
            if (this.flipped) {
                point.orientation.rotateYFlip();
            }
            updateParticle(point);
        }
        if (this.particleWidthMarker != null) {
            this.particleWidthMarker.setPositionOrientation(point.position, point.orientation);
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

    private TrackConnection.PointOnPath findPointOnPath(TrackConnection connection) {
        TrackConnection.PointOnPath point = connection.findPointAtDistance(this.distance, this.type.getWidth());
        if (this.flipped) {
            point.orientation.rotateYFlip();
        }
        return point;
    }

    public boolean isAdded() {
        return this.particle != null;
    }

    public void onAdded(TrackConnection connection) {
        if (isAdded()) {
            throw new IllegalStateException("Object was already added to a connection");
        } else {
            initParticle(findPointOnPath(connection));
        }
    }

    public void onRemoved(TrackConnection connection) {
        if (!isAdded()) {
            throw new IllegalStateException("Object was never added to a connection");
        }
        if (this.particleWidthMarker != null) {
            this.particleWidthMarker.remove();
            this.particleWidthMarker = null;
        }
        this.particle.remove();
        this.particle = null; // isAdded() -> false

        // Remove from player edit states to prevent trouble
        connection.getPlugin().forAllEditStates(state -> state.getObjects().deselectTrackObject(connection, TrackObject.this));
    }

    public void onShapeUpdated(TrackConnection connection) {
        if (!isAdded()) {
            throw new IllegalStateException("Updating shape of a removed track object");
        }

        TrackConnection.PointOnPath point = findPointOnPath(connection);
        if (this.particleWidthMarker != null) {
            this.particleWidthMarker.setPositionOrientation(point.position, point.orientation);
        }
        updateParticle(point);
    }

    /**
     * Updates the visual appearance of the track object. For example, the object was selected
     * or deselected by a player.<br>
     * <br>
     * Throws an {@link IllegalStateException} if this object was removed from the world
     * 
     * @param connection
     * @param editState
     */
    public void onStateUpdated(TrackConnection connection, PlayerEditState editState) {
        if (!isAdded()) {
            throw new IllegalStateException("Updating state of a removed track object");
        }

        this.particle.onStateUpdated(editState.getPlayer());

        boolean isViewerEditing = (this.source_selected.getState(editState) == TrackParticleState.SELECTED);
        if (isViewerEditing) {
            // Someone is definitely viewing this marker! Spawn it if we have not.
            if (this.particleWidthMarker == null) {
                TrackConnection.PointOnPath point = findPointOnPath(connection);
                this.particleWidthMarker = point.getWorld().getParticles().addParticleWidthMarker(
                        point.position, point.orientation, this.type.getWidth());
                this.particleWidthMarker.setStateSource(this.source_selected);
            }
        } else if (this.particleWidthMarker != null) {
            // Check if anybody is still viewing this marker. If not, get rid of it.
            final AtomicBoolean hasOtherEditStateViewing = new AtomicBoolean(false);
            connection.getPlugin().forAllEditStates(otherEditState -> {
                if (otherEditState != editState && this.source_selected.getState(otherEditState) == TrackParticleState.SELECTED) {
                    hasOtherEditStateViewing.set(true);
                }
            });
            if (!hasOtherEditStateViewing.get()) {
                this.particleWidthMarker.remove();
                this.particleWidthMarker = null;
            }
        }
    }

    private void initParticle(TrackConnection.PointOnPath point) {
        try {
            this.particle = this.type.createParticle(point.transform(this.type.getTransform()));
            this.particle.setStateSource(this.source_selected_blink);
        } catch (Throwable t) {
            point.connection.getPlugin().getLogger().log(Level.SEVERE, "Failed to create particle for track object "
                    + this.type.getTitle(), t);
            this.particle = point.connection.getWorld().getParticles().addParticle(new TrackParticleMissingPlaceHolder());
        }
    }

    private void updateParticle(TrackConnection.PointOnPath point) {
        if (!(this.particle instanceof TrackParticleMissingPlaceHolder)) {
            this.type.updateParticle(CommonUtil.unsafeCast(this.particle), point.transform(this.type.getTransform()));
        }
    }

    @Override
    public TrackObject clone() {
        return new TrackObject(this.type, this.distance, this.flipped);
    }

    /**
     * Clones this track object, but with the A/B node ends of a connection flipped.
     * 
     * @param connection
     * @return flipped track object
     */
    public TrackObject cloneFlipEnds(TrackConnection connection) {
        return new TrackObject(this.type, connection.getFullDistance() - this.distance, !this.flipped);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObject) {
            TrackObject other = (TrackObject) o;
            return Math.abs(this.distance - other.distance) <= 1e-20 &&
                   this.type.equals(other.type) &&
                   this.flipped == other.flipped;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{distance=" + this.distance + ", flipped=" + this.flipped + ", type=" + this.type + "}";
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
