package com.bergerkiller.bukkit.coasters.tracks;

import java.util.UUID;

/**
 * The unique key to which TC-Coasters signs are mapped
 */
public final class TrackNodeSignKey {
    private final UUID uuid;

    private TrackNodeSignKey(UUID uuid) {
        this.uuid = uuid;
    }

    public static TrackNodeSignKey of(UUID uuid) {
        return new TrackNodeSignKey(uuid);
    }

    public static TrackNodeSignKey random() {
        return new TrackNodeSignKey(UUID.randomUUID());
    }

    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TrackNodeSignKey) {
            return uuid.equals(((TrackNodeSignKey) o).uuid);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "TCCSignKey{" + uuid + "}";
    }
}
