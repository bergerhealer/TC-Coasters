package com.bergerkiller.bukkit.coasters.tracks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectHolder;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLine;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.tracks.path.EndPoint;
import com.bergerkiller.bukkit.coasters.tracks.path.WidthSearcher;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * The connection between two track nodes
 */
public class TrackConnection implements Lockable, CoasterWorldComponent, TrackObjectHolder, TrackConnectionPath {
    protected static final TrackConnection[] EMPTY_ARR = new TrackConnection[0];
    protected NodeEndPoint _endA;
    protected NodeEndPoint _endB;
    private List<TrackParticleLine> lines = new ArrayList<TrackParticleLine>();
    private TrackObject[] objects = TrackObject.EMPTY;
    private double fullDistance = Double.NaN;

    protected TrackConnection(TrackNode nodeA, TrackNode nodeB) {
        this._endA = new NodeEndPoint(nodeA, nodeB);
        this._endB = new NodeEndPoint(nodeB, nodeA);
    }

    @Override
    public CoasterWorld getWorld() {
        return this._endA.node.getWorld();
    }

    /**
     * Gets node A making up this connection pair
     * 
     * @return node A
     */
    public TrackNode getNodeA() {
        return this._endA.node;
    }

    /**
     * Gets node B making up this connection pair
     * 
     * @return node B
     */
    public TrackNode getNodeB() {
        return this._endB.node;
    }

    @Override
    public EndPoint getEndA() {
        return this._endA;
    }

    @Override
    public EndPoint getEndB() {
        return this._endB;
    }

    /**
     * Gets the endpoint of node making up this connection pair
     * 
     * @param node to get the end point for
     * @return end point
     */
    public EndPoint getEndPoint(TrackNode node) {
        return (this._endA.node == node) ? this._endA : this._endB;
    }

    /**
     * Gets the other end of the connection, given a node
     * 
     * @param node
     * @return other node
     */
    public TrackNode getOtherNode(TrackNode node) {
        return (this._endA.node == node) ? this._endB.node : this._endA.node;
    }

    /**
     * Gets the full distance between the end points of this connection. This is not the linear
     * distance between the two points, but the full length of the path taken.
     * 
     * @return full distance
     */
    public double getFullDistance() {
        if (Double.isNaN(this.fullDistance)) {
            this.fullDistance = this.computeDistance(0.0, 1.0);
        }
        return this.fullDistance;
    }

    /**
     * Swaps the two ends of this connection, causing distances to objects
     * stored in this connection to be inverted. This should have no change
     * in appearance or behavior.
     */
    public void swapEnds() {
        // Swap endpoints
        {
            NodeEndPoint tmp = this._endA;
            this._endA = this._endB;
            this._endB = tmp;
        }

        // Compute total distance and invert all the objects's distances
        // This doesn't actually change the position of the object, so it can be done silently
        // Flipped is inverted also, because the motion vector on the path reverses direction
        for (TrackObject object : this.objects) {
            object.setDistanceFlippedSilently(this.getFullDistance() - object.getDistance(), !object.isFlipped());
        }

        //TODO: Technically we got to swap the lines also
        //      This is not a big deal, the next time this connection changes, it'll do a wrap-around
    }

    public boolean isConnected(TrackNode node) {
        return this._endA.node == node || this._endB.node == node;
    }

    public boolean isInterGroup() {
        return this._endA.node.getCoaster() != this._endB.node.getCoaster();
    }

    public boolean isZeroLength() {
        return this._endA.isZeroLength();
    }

    @Override
    public boolean isLocked() {
        return this._endA.node.isLocked() || this._endB.node.isLocked();
    }

    /**
     * Removes this connection, disconnecting the two nodes
     */
    public void remove() {
        this.getNodeA().getWorld().getTracks().disconnect(this);
    }

    /**
     * Gets the number of points needed to display this track connection in the world.
     * 
     * @return number of points, minimally 2
     */
    public int getPointCount() {
        int n = MathUtil.ceil(this._endA.node.getPosition().distance(this._endB.node.getPosition()) / 1.0);
        if (n < 2) {
            n = 2;
        }
        return n;
    }

    /**
     * Gets the motion vector at either end of this track connection
     * 
     * @param atNode
     * @return motion vector
     */
    public Vector getDirection(TrackNode atNode) {
        return this._endA.node == atNode ? this._endA.getDirection() : this._endB.getDirection();
    }

    /**
     * Gets the position near the end of the connection, where extra labels
     * can be displayed.
     * 
     * @param endNode
     * @return position
     */
    public Vector getNearEndPosition(TrackNode endNode) {
        int n = this.getPointCount();
        if (n <= 2) {
            return this.getPosition(0.5);
        } else if (endNode == this.getNodeA()) {
            return this.getPosition((double) 1 / (double) (n-1));
        } else {
            return this.getPosition((double) (n-2) / (double) (n-1));
        }
    }

    @Override
    public boolean hasObjects() {
        return this.objects.length > 0;
    }

    @Override
    public List<TrackObject> getObjects() {
        return LogicUtil.asImmutableList(this.objects);
    }

    /**
     * Adds a track object
     * 
     * @param object
     */
    public void addObject(TrackObject object) {
        this.objects = LogicUtil.appendArray(this.objects, object);
        this.markChanged();
        object.onAdded(this);
    }

    /**
     * Updates, removes and/or adds track objects to synchronize it with the objects
     * stored in a connection state. A clone of the objects is
     * created to guarantee the immutability of the track connection state.
     * If the track object occupies the exact same position and width, an attempt
     * is made to update the existing object instead of deleting and re-adding it.
     *
     * @param connectionObjects TrackConnectionState with objects to set
     */
    public void setAllObjects(TrackConnectionState connectionObjects) {
        this.clearObjects();
        this.addAllObjects(connectionObjects);
    }

    /**
     * Adds multiple track objects at once
     *
     * @param objects
     */
    public void addAllObjects(Collection<TrackObject> objects) {
        for (TrackObject object : objects) {
            this.addObject(object);
        }
    }

    /**
     * Adds multiple track objects at once
     * 
     * @param objects
     * @param filter Optional permission filter. Does not add the object if this predicate
     *               returns false.
     */
    public void addAllObjects(Collection<TrackObject> objects, AddObjectPredicate filter) {
        for (TrackObject object : objects) {
            if (filter.canAddObject(this, object)) {
                this.addObject(object);
            }
        }
    }

    /**
     * Adds all the objects stored in a connection state. A clone of the objects is
     * created to guarantee the immutability of the track connection state.
     *
     * @param connectionObjects
     */
    public void addAllObjects(TrackConnectionState connectionObjects) {
        addAllObjects(connectionObjects, (connection, object) -> true);
    }

    /**
     * Adds all the objects stored in a connection state. A clone of the objects is
     * created to guarantee the immutability of the track connection state.
     * 
     * @param connectionObjects
     * @param filter Optional permission filter. Does not add the object if this predicate
     *               returns false.
     */
    public void addAllObjects(TrackConnectionState connectionObjects, AddObjectPredicate filter) {
        if (connectionObjects.hasObjects()) {
            if (connectionObjects.isSameFlipped(this)) {
                for (TrackObject object : connectionObjects.getObjects()) {
                    TrackObject clone = object.cloneFlipEnds(this);
                    if (filter.canAddObject(this, clone)) {
                        this.addObject(clone);
                    }
                }
            } else {
                for (TrackObject object : connectionObjects.getObjects()) {
                    TrackObject clone = object.clone();
                    if (filter.canAddObject(this, clone)) {
                        this.addObject(clone);
                    }
                }
            }
        }
    }

    /**
     * Removes an object from this connecion, if it was added
     * 
     * @param object
     * @return True if the object was removed, False if not
     */
    public boolean removeObject(TrackObject object) {
        for (int i = 0; i < this.objects.length; i++) {
            TrackObject old_object = this.objects[i];
            if (old_object.equals(object)) {
                this.objects = TrackObject.removeArrayElement(this.objects, i);
                this.markChanged();
                old_object.onRemoved(this);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all objects that exist for this connection
     */
    public void clearObjects() {
        TrackObject[] objects = this.objects;
        if (objects.length > 0) {
            this.objects = TrackObject.EMPTY;
            this.markChanged();
            for (TrackObject object : objects) {
                object.onRemoved(this);
            }
        }
    }

    /**
     * Saves all the track objects stored on this connection, to a matching
     * connection saved as an animation state of the given name.
     * 
     * @param name Name of the animation state to update, null to update all of them
     */
    public void saveObjectsToAnimationStates(String name) {
        this.getNodeA().updateAnimationStates(name, state -> state.updateTrackObjects(TrackConnection.this));
        this.getNodeB().updateAnimationStates(name, state -> state.updateTrackObjects(TrackConnection.this));
    }

    /**
     * Moves an existing object to a new connection and/or distance. If the new connection
     * is the same, it is not removed from this connection. It is made sure the object orientation
     * stays the same, changing the flipped property if needed.
     * 
     * @param object
     * @param newConnection
     * @param newDistance
     * @param rightDirection The direction the player moving it is looking to align the objects
     * @return True if the object was found and moved
     */
    public boolean moveObject(TrackObject object, TrackConnection newConnection, double newDistance, Vector rightDirection) {
        if (newConnection != this) {
            for (int i = 0; i < this.objects.length; i++) {
                if (this.objects[i].equals(object)) {
                    this.objects = TrackObject.removeArrayElement(this.objects, i);
                    this.markChanged();

                    newConnection.objects = LogicUtil.appendArray(newConnection.objects, object);
                    newConnection.markChanged();
                    object.setDistanceComputeFlipped(newConnection, newDistance, rightDirection);
                    return true;
                }
            }
            return false;
        } else if (object.getDistance() == newDistance) {
            return true; // no changes
        } else {
            object.setDistanceComputeFlipped(this, newDistance, rightDirection);
            return true;
        }
    }

    /**
     * Called when the shape of the track connection has been changed.
     * This can happen as a result of position changes of the nodes themselves,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
        // Reset (is lazy initialized again if needed)
        this.fullDistance = Double.NaN;

        // Ensure orientation of A and B have an aligned forward vector
        this._endB.alignOrientationForward(this._endA.getOrientation());

        // Initialize the 4 points of the De Casteljau's algorithm inputs
        // d1 and d2 are the diff between p1-p3 and p2-p4

        // Calculate the points forming the line
        int n = this.getPointCount();
        Vector[] points = new Vector[n];
        points[0] = this._endA.node.getPosition();
        points[n-1] = this._endB.node.getPosition();
        for (int i = 1; i < (n-1); i++) {
            double t = ((double) i / (double) (n-1));
            points[i] = getPosition(t);
        }

        if ((n - 1) != this.lines.size()) {
            for (TrackParticleLine line : this.lines) {
                line.remove();
            }
            this.lines.clear();

            TrackParticleWorld pworld = this._endA.node.getWorld().getParticles();
            for (int i = 0; i < (n-1); i++) {
                this.lines.add(pworld.addParticleLine(points[i], points[i + 1]));
            }
        } else {
            for (int i = 0; i < this.lines.size(); i++) {
                this.lines.get(i).setPositions(points[i], points[i+1]);
            }
        }

        for (TrackObject object : this.objects) {
            object.onShapeUpdated(this);
        }
    }

    /**
     * Calculates all the discrete path points required to build the smooth path from t0 to t1.
     * 
     * @param points Points list to add points of the path to
     * @param railsPos Rail block
     * @param smoothness Configured smoothness value
     * @param t0 Start theta [0 .. 1]
     * @param t1 End theta [0 .. 1]
     */
    public void buildPath(List<RailPath.Point> points, IntVector3 railsPos, double smoothness, double t0, double t1) {
        // If this is a zero-length connection, only add a point at t1
        if (isZeroLength()) {
            points.add(getPathPoint(railsPos,  t1));
            return;
        }

        // Initial point
        points.add(getPathPoint(railsPos,  t0));

        // If smoothness is too rough, just create a linear line
        if (smoothness <= 1e-4) {
            points.add(getPathPoint(railsPos,  t1));
            return;
        }

        // Stop if already same
        if (t0 == t1) {
            return;
        }

        final double epsilon = 1.1102230246251565E-16;
        double threshold = 1.0 / smoothness;
        double curr_t0 = t0;
        while (true) {
            double curr_t1 = t1;
            do {
                // When error is small enough, stop
                double error = getLinearError(curr_t0, curr_t1);
                if (error <= threshold) {
                    //System.out.println("ADD SEGMENT DELTA=" + (curr_t1 - curr_t0) + " ERROR " + error);
                    break;
                }

                // Error too large, choose halfway between curr_t0 and curr_t1 and try again
                // If the delta is so small curr_t0 equals curr_t1 this loop breaks.
                //System.out.println("ERROR TOO BIG DELTA=" + (curr_t1 - curr_t0) + " ERROR " + error);
                curr_t1 = 0.5 * (curr_t1 + curr_t0);
            } while (Math.abs(curr_t1 - curr_t0) > epsilon);

            if (Math.abs(t1 - curr_t1) > epsilon) {
                // Different, add point and loop again
                points.add(getPathPoint(railsPos, curr_t1));
                curr_t0 = curr_t1;
            } else {
                // Same, add t1 and stop
                points.add(getPathPoint(railsPos, t1));
                break;
            }
        }

        // System.out.println("ADDED " + points.size() + " POINTS");
    }

    /*
    public void buildPath(List<RailPath.Point> points, IntVector3 railsPos, double t0, double t1) {
        double curr_t0 = t0;
        Vector position_t0 = getPosition(curr_t0);
        Vector absition_t0 = getAbsition(curr_t0);

        // Initial point
        points.add(getPathPoint(railsPos, position_t0, curr_t0));

        while (curr_t0 != t1) {
            double curr_t1 = t1;
            Vector absition_t1;
            Vector position_t1;
            do {
                absition_t1 = getAbsition(curr_t1);
                position_t1 = getPosition(curr_t1);

                // Attempt to create a linear line from curr_t0 to curr_t1
                // The line is between position_t0 and position_t1
                double dt = (curr_t1 - curr_t0);
                double dt_inv = 1.0 / dt;

                double mx = dt_inv * (position_t1.getX() - position_t0.getX());
                double my = dt_inv * (position_t1.getY() - position_t0.getY());
                double mz = dt_inv * (position_t1.getZ() - position_t0.getZ());

                double bx = position_t0.getX() - curr_t0 * mx;
                double by = position_t0.getY() - curr_t0 * my;
                double bz = position_t0.getZ() - curr_t0 * mz;

                // line position:
                //   y = mx * t + bx;
                // line absition:
                //   yP = 0.5 * mx * t^2 + bx * t

                // Compute line absition error value between curr_t1 and curr_t0
                double half_sq_dt = 0.5 * (curr_t1*curr_t1 - curr_t0*curr_t0);
                double absition_err_x = (half_sq_dt * mx + dt * bx) - absition_t1.getX() + absition_t0.getX();
                double absition_err_y = (half_sq_dt * my + dt * by) - absition_t1.getY() + absition_t0.getY();
                double absition_err_z = (half_sq_dt * mz + dt * bz) - absition_t1.getZ() + absition_t0.getZ();

                // When error is small enough, stop
                final double ERROR_THRESHOLD = 0.5;
                if (absition_err_x >= -ERROR_THRESHOLD && absition_err_x <= ERROR_THRESHOLD &&
                    absition_err_y >= -ERROR_THRESHOLD && absition_err_y <= ERROR_THRESHOLD &&
                    absition_err_z >= -ERROR_THRESHOLD && absition_err_z <= ERROR_THRESHOLD)
                {
                    System.out.println("ADD SEGMENT DELTA=" + dt + " ERROR " + absition_err_x + "/" + absition_err_y + "/" + absition_err_z);
                    break;
                }

                // Error too large, choose halfway between curr_t0 and curr_t1 and try again
                // If the delta is so small curr_t0 equals curr_t1 this loop breaks.
                System.out.println("ERROR TOO BIG DELTA=" + dt + " ERROR " + absition_err_x + "/" + absition_err_y + "/" + absition_err_z);
                curr_t1 = 0.5 * (curr_t1 + curr_t0);
            } while (curr_t1 != curr_t0);

            // Next curr_t0
            curr_t0 = curr_t1;
            absition_t0 = absition_t1;
            position_t0 = position_t1;

            points.add(getPathPoint(railsPos, position_t0, curr_t0));
        }
    }
    */

    /**
     * Gets a rails path point at a particular t.
     * This only includes position and orientation information and is faster
     * to calculate.
     * 
     * @param railsPos
     * @param position at t
     * @param t [0 ... 1]
     * @return point at t
     */
    public RailPath.Point getPathPoint(IntVector3 railsPos, Vector position, double t) {
        Vector pos = position.clone();
        pos.setX(pos.getX() - railsPos.x);
        pos.setY(pos.getY() - railsPos.y);
        pos.setZ(pos.getZ() - railsPos.z);
        return new RailPath.Point(pos, getOrientation(t));
    }

    /**
     * Gets a rails path point at a particular t.
     * This only includes position and orientation information and is faster
     * to calculate.
     * 
     * @param railsPos
     * @param t [0 ... 1]
     * @return point at t
     */
    public RailPath.Point getPathPoint(IntVector3 railsPos, double t) {
        Vector pos = getPosition(t);
        pos.setX(pos.getX() - railsPos.x);
        pos.setY(pos.getY() - railsPos.y);
        pos.setZ(pos.getZ() - railsPos.z);
        return new RailPath.Point(pos, getOrientation(t));
    }

    /**
     * Gets the position information on the rails for a particular theta.
     * The result will contain the exact position, direction and up-vector information.
     * 
     * @param from node, used for the direction of the position
     * @param t theta on the connection [ 0.0 ... 1.0 ]
     * @return path position
     */
    public RailPath.Position getPathPosition(TrackNode from, double t) {
        RailPath.Position p = RailPath.Position.fromPosDir(getPosition(t), getOrientation(t));
        p.setMotion(getMotionVector(t));
        if (from == this._endB.node) {
            p.invertMotion();
        }
        return p;
    }

    /**
     * Calculates the up-vector orientation at a particular t
     * 
     * @param t [0 ... 1]
     * @return orientation at t
     */
    public Vector getOrientation(double t) {
        return Quaternion.slerp(this._endA.getOrientation(), this._endB.getOrientation(), t).upVector();
    }

    /**
     * Walks this path from the start a distance towards the end, obtaining a point
     * on the path found a distance away.
     * 
     * @param distance The distance to walk from the start
     * @return point on the path at distance
     */
    public TrackConnection.PointOnPath findPointAtDistance(double distance) {
        double theta = findPointThetaAtDistance(distance);
        Vector position = getPosition(theta);
        Vector motionVector = getMotionVector(theta);
        Quaternion orientation = Quaternion.fromLookDirection(motionVector, this.getOrientation(theta));
        return new TrackConnection.PointOnPath(this, theta, distance, position, orientation);
    }

    /**
     * Walks this path from the start a distance towards the end, obtaining a point
     * on the path found a distance away. The width can be set to non-zero to find
     * the middle between two points on the path width apart.
     * 
     * @param distance The distance to walk from the start
     * @param width The width between two points on the path between which to find a point
     * @return point on the path at distance
     */
    public TrackConnection.PointOnPath findPointAtDistance(double distance, double width) {
        if (width <= 0.0) {
            return findPointAtDistance(distance);
        }

        WidthSearcher searcher = WidthSearcher.INSTANCE.init(this, distance);
        searcher.search(width);

        Vector mid_direction = searcher.pointB.position.clone().subtract(searcher.pointA.position);
        double lengthSquared = mid_direction.lengthSquared();
        if (lengthSquared <= 1e-10) {
            return searcher.pointA.toPointOnPath();
        }

        Vector mid_position = new Vector(searcher.pointA.position.getX() + 0.5 * mid_direction.getX(),
                                         searcher.pointA.position.getY() + 0.5 * mid_direction.getY(),
                                         searcher.pointA.position.getZ() + 0.5 * mid_direction.getZ());
        mid_direction.multiply(MathUtil.getNormalizationFactorLS(lengthSquared));
        Vector mid_up = Util.lerpOrientation(searcher.pointA.getOrientation(), searcher.pointB.getOrientation(), 0.5);
        Quaternion mid_orientation = Quaternion.fromLookDirection(mid_direction, mid_up);
        return new PointOnPath(this, 0.5, distance, mid_position, mid_orientation);
    }

    public void onRemoved() {
        for (int i = 0; i < this.lines.size(); i++) {
            this.lines.get(i).remove();
        }
        this.lines.clear();
        for (TrackObject object : this.objects) {
            object.onRemoved(this);
        }
    }

    public void markChanged() {
        this._endA.node.markChanged();
        this._endB.node.markChanged();
    }

    // metadata for a single endpoint
    protected static class NodeEndPoint extends EndPoint {
        protected final TrackNode node;
        protected final TrackNode other;

        public NodeEndPoint(TrackNode node, TrackNode other) {
            this.node = node;
            this.other = other;

            // Positions of nodes are fairly well known. We need the strength to know whether
            // this connection is a zero-length one. For that reason, calculate it earlier.
            // A second calculation is done later during onShapeUpdated()
            this.computeStrengthUsingPositions();
        }

        @Override
        public Vector getNodePosition() {
            return this.node.getPosition();
        }

        @Override
        public Vector getNodeDirection() {
            return this.node.getDirection();
        }

        @Override
        public Vector getNodeUp() {
            return this.node.getOrientation();
        }

        @Override
        public Vector getOtherNodePosition() {
            return this.other.getPosition();
        }

        @Override
        public Vector getOtherNodeDirection() {
            return this.other.getDirection();
        }
    }

    /**
     * A point on the track connection path
     */
    public static final class PointOnPath {
        public final TrackConnection connection;
        public final double theta;
        public final double distance;
        public final Vector position;
        public final Quaternion orientation;

        public PointOnPath(TrackConnection connection, double theta, double distance, Vector position, Quaternion orientation) {
            this.connection = connection;
            this.theta = theta;
            this.distance = distance;
            this.position = position;
            this.orientation = orientation;
        }

        public CoasterWorld getWorld() {
            return this.connection.getWorld();
        }

        /**
         * Transforms the position and orientation of this point using a transformation matrix.
         * If the input transform is null, this same point is returned (identity).
         * 
         * @param transform The transformation matrix, null for identity
         * @return transformed point
         */
        public PointOnPath transform(Matrix4x4 transform) {
            if (transform == null) {
                return this;
            }

            Matrix4x4 pos = new Matrix4x4();
            pos.translate(this.position);
            pos.rotate(this.orientation);
            pos.multiply(transform);
            return new PointOnPath(this.connection, this.theta, this.distance, pos.toVector(), pos.getRotation());
        }

        @Override
        public String toString() {
            return "{x=" + position.getX() + ", y=" + position.getY() + ", z=" + position.getZ() + ", distance=" + distance + "}";
        }
    }

    @FunctionalInterface
    public interface AddObjectPredicate {
        boolean canAddObject(TrackConnection connection, TrackObject object);
    }
}
