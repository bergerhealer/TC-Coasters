package com.bergerkiller.bukkit.coasters.editor.object;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Metadata about a single track object being edited
 */
public class ObjectEditTrackObject {
    public TrackConnection connection;
    public final TrackObject object;
    /**
     * Distance of object from point clicked initially
     */
    public double dragDistance;
    /**
     * Whether this object is before (false) or beyond (true)
     * the point clicked (moving on clicked point connection node A -> B)
     */
    public boolean dragDirection;
    /**
     * Based on the sequence of objects on the track clicked by
     * the player, the object orientation is flipped.
     * Used when duplicating to preserve orientation.
     */
    public boolean alignmentFlipped;

    /*
     * State of the TrackObject prior to moving/duplicating it
     */
    public TrackConnection beforeDragConnection;
    public TrackObject beforeDragObject;

    /**
     * Based on comparing with the orientation to the player,
     * the object has orientation flipped.
     * Used when moving objects, to orient them how the player is looking.
     */
    public boolean beforeDragLookingAtFlipped;

    public ObjectEditTrackObject(TrackConnection connection, TrackObject object) {
        this.connection = connection;
        this.object = object;
        this.dragDistance = Double.NaN;
        this.dragDirection = false;
    }

    public void moveEnd() {
        this.beforeDragConnection = null;
        this.beforeDragObject = null;
        this.dragDistance = Double.NaN;
    }

    public double getDistancePosition() {
        return this.dragDirection ? this.dragDistance : -this.dragDistance;
    }

    public boolean isRemoved() {
        return !this.object.isAdded();
    }
}
