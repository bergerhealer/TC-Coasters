package com.bergerkiller.bukkit.coasters.objects;

import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * The appearance and properties of an object. This type is immutable
 * and is assigned to Track Objects to setup their properties.
 * Implementations must implement the {@link #hashCode()} and {@link #equals(Object)}
 * methods for correct functioning inside maps.
 */
public interface TrackObjectType<P extends TrackParticle> {
    /**
     * Gets the width of the object on the tracks, changing the attachment points
     * to the track.
     * 
     * @return width
     */
    double getWidth();

    /**
     * Creates a copy of this track object type with the width updated
     * 
     * @param width
     * @return clone of this type with width changed
     */
    TrackObjectType<P> setWidth(double width);

    /**
     * Generates a name to easily identify this track object type.
     * It can include any details, such as material names.
     * This name is written to csv.
     * 
     * @return name
     */
    String generateName();

    /**
     * Spawns the particle appropriate for this track object type
     * 
     * @param point Position information of where to spawn
     * @return created particle
     */
    P createParticle(TrackConnection.PointOnPath point);

    /**
     * Refreshes the particle previously spawned using {@link #createParticle(point)}.
     * 
     * @param particle The particle to update
     * @param point The (new) position of the particle
     */
    void updateParticle(P particle, TrackConnection.PointOnPath point);
}
