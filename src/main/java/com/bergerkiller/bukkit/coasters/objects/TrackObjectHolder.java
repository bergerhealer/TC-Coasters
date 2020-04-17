package com.bergerkiller.bukkit.coasters.objects;

import java.util.List;

/**
 * Interface for a class that can store Track Objects
 */
public interface TrackObjectHolder {
    /**
     * Gets all added track objects
     * 
     * @return list of track objects
     */
    List<TrackObject> getObjects();

    /**
     * Gets whether track objects are added
     * 
     * @return True if track objects are added
     */
    boolean hasObjects();
}
