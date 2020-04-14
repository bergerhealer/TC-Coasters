package com.bergerkiller.bukkit.coasters.objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleObject;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

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
}
