package com.bergerkiller.bukkit.coasters.tracks.path;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

/**
 * Basic implementation of {@link TrackConnectionPath} for use in a test environment
 */
public class TrackConnectionPathImpl implements TrackConnectionPath {
    private final EndPoint endA;
    private final EndPoint endB;

    public TrackConnectionPathImpl(EndPoint endA, EndPoint endB) {
        this.endA = endA;
        this.endB = endB;
    }

    @Override
    public EndPoint getEndA() {
        return this.endA;
    }

    @Override
    public EndPoint getEndB() {
        return this.endB;
    }
}
