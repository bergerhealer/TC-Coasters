package com.bergerkiller.bukkit.coasters.objects;

import java.util.Collection;

/**
 * Interface for a class that can store Track Objects
 */
public interface TrackObjectHolder {
    /**
     * Adds a track object
     * 
     * @param object
     */
    void addObject(TrackObject object);

    /**
     * Adds multiple track objects at once
     * 
     * @param objects
     */
    default void addAllObjects(Collection<TrackObject> objects) {
        for (TrackObject object : objects) {
            this.addObject(object);
        }
    }
}
