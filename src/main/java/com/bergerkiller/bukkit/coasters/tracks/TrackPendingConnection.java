package com.bergerkiller.bukkit.coasters.tracks;

import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectHolder;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A connection stored inside an animation state. This connection is created
 * when the animation state is activated.
 */
public class TrackPendingConnection extends TrackNodeReference implements TrackObjectHolder {
    public static final TrackPendingConnection[] EMPTY_ARR = new TrackPendingConnection[0];
    public TrackObject[] objects;

    public TrackPendingConnection(TrackWorld world, Vector position) {
        super(world, position);
        this.objects = TrackObject.EMPTY;
    }

    public TrackPendingConnection(TrackNode node, List<TrackObject> objects) {
        super(node);
        if (objects.isEmpty()) {
            // No objects
            this.objects = TrackObject.EMPTY;
        } else {
            // Clone the objects so changes to them don't cause changes here
            this.objects = new TrackObject[objects.size()];
            for (int i = 0; i < this.objects.length; i++) {
                this.objects[i] = objects.get(i).clone();
            }
        }
    }

    public TrackPendingConnection(TrackNode node) {
        super(node);
        this.objects = TrackObject.EMPTY;
    }

    @Override
    public TrackPendingConnection dereference() {
        return new TrackPendingConnection(getWorld(), getPosition());
    }

    @Override
    public TrackPendingConnection transform(TrackWorld world, Matrix4x4 transform) {
        Vector new_position = this.getPosition().clone();
        transform.transformPoint(new_position);
        return new TrackPendingConnection(world, new_position);
    }

    @Override
    public void addObject(TrackObject object) {
        this.objects = LogicUtil.appendArray(this.objects, object);
    }
}
