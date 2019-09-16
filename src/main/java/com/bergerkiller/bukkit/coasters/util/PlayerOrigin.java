package com.bergerkiller.bukkit.coasters.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * The origin information used when placing coasters relative to the player.
 * The position is restricted to full block coordinates, and the orientation
 * is only full 90-degree angles.
 */
public class PlayerOrigin extends TrackCoasterCSV.CSVEntry implements PlayerOriginHolder {
    private IntVector3 position;
    private IntVector3 orientation;

    public IntVector3 getPosition() {
        return this.position;
    }

    public IntVector3 getOrientation() {
        return this.orientation;
    }

    public void setForNode(Vector position) {
        this.position = new IntVector3(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        this.orientation = IntVector3.ZERO;
    }

    public void setForPlayer(Player player) {
        Location basePos = player.getEyeLocation();

        // Compute yaw rotation in steps of 90 degrees
        double yaw = (double) -basePos.getYaw();
        yaw = Math.round(yaw / 90.0);
        yaw *= 90.0;

        // Compute pitch as either -90.0, 0.0 or 90.0
        // Prefer 0.0 as that is the most common case
        double pitch = 0.0;
        if (basePos.getPitch() > 80.0f) {
            pitch = 90.0;
        } else if (basePos.getPitch() < -80.0f) {
            pitch = -90.0;
        }

        // Update fields
        this.position = new IntVector3(basePos);
        this.orientation = new IntVector3(pitch, yaw, 0.0);
    }

    public Matrix4x4 getTransform() {
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(this.position.midX(), this.position.midY(), this.position.midZ());
        transform.rotateY((double) orientation.y);
        transform.rotateX((double) orientation.x);
        transform.rotateZ((double) orientation.z);
        return transform;
    }

    /**
     * Gets the transformation required to convert from this origin to another
     * 
     * @param newOrigin
     * @return transformation matrix for the change
     */
    public Matrix4x4 getTransformTo(PlayerOrigin newOrigin) {
        // Get old and new transformation
        Matrix4x4 transform_old = this.getTransform();
        Matrix4x4 transform_new = newOrigin.getTransform();

        // Create a transform from the old positions to the new positions
        transform_old.invert();
        transform_new.multiply(transform_old);
        return transform_new;
    }

    @Override
    public PlayerOrigin getOrigin() {
        return this;
    }

    @Override
    public boolean detect(StringArrayBuffer buffer) {
        return buffer.get(0).equals("ORIGIN");
    }

    @Override
    public void read(StringArrayBuffer buffer) throws SyntaxException {
        buffer.skipNext(1);
        this.position = buffer.nextIntVector3();
        this.orientation = new IntVector3( 90 * (buffer.nextInt() / 90),
                                           90 * (buffer.nextInt() / 90),
                                           90 * (buffer.nextInt() / 90) );
    }

    @Override
    public void write(StringArrayBuffer buffer) {
        buffer.put("ORIGIN");
        buffer.putIntVector3(this.position);
        buffer.putIntVector3(this.orientation);
    }

    public static PlayerOrigin getForNode(Vector position) {
        PlayerOrigin origin = new PlayerOrigin();
        origin.setForNode(position);
        return origin;
    }

    public static PlayerOrigin getForPlayer(Player player) {
        PlayerOrigin origin = new PlayerOrigin();
        origin.setForPlayer(player);
        return origin;
    }
}
