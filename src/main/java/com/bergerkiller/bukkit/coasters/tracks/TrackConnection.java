package com.bergerkiller.bukkit.coasters.tracks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.particles.TrackParticleLine;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * The connection between two track nodes
 */
public class TrackConnection implements Lockable {
    protected static final TrackConnection[] EMPTY_ARR = new TrackConnection[0];
    protected final EndPoint _endA;
    protected final EndPoint _endB;
    //private final TrackParticleLine _connParticleLine;
    private boolean _selected = false;
    private List<TrackParticleLine> lines = new ArrayList<TrackParticleLine>();

    protected TrackConnection(TrackNode nodeA, TrackNode nodeB) {
        this._endA = new EndPoint(nodeA, nodeB);
        this._endB = new EndPoint(nodeB, nodeA);
        //this._connParticleLine = nodeA.getCoaster().getParticles().addParticleLine(
        //        this._endA.node.getPosition(), this._endB.node.getPosition());
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

    /**
     * Gets the endpoint of node making up this connection pair
     * 
     * @param node to get the end point for
     * @return end point
     */
    public TrackConnectionPath.EndPoint getEndPoint(TrackNode node) {
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

    public boolean isConnected(TrackNode node) {
        return this._endA.node == node || this._endB.node == node;
    }

    public boolean isInterGroup() {
        return this._endA.node.getCoaster() != this._endB.node.getCoaster();
    }

    @Override
    public boolean isLocked() {
        return this._endA.node.isLocked() || this._endB.node.isLocked();
    }

    public void setSelected(boolean selected) {
        if (this._selected != selected) {
            this._selected = selected;
            //this._connParticleLine.setItemEnchanted(selected);
        }
    }

    /**
     * Removes this connection, disconnecting the two nodes
     */
    public void remove() {
        this.getNodeA().getWorld().getTracks().disconnect(this.getNodeA(), this.getNodeB());
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
            return this.getPath().getPosition(0.5);
        } else if (endNode == this.getNodeA()) {
            return this.getPath().getPosition((double) 1 / (double) (n-1));
        } else {
            return this.getPath().getPosition((double) (n-2) / (double) (n-1));
        }
    }

    /**
     * Called when the shape of the track connection has been changed.
     * This can happen as a result of position changes of the nodes themselves,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
        TrackConnectionPath path = this.getPath();

        //this._connParticleLine.setPositions(this._endA.node.getPosition(), this._endB.node.getPosition());

        // Initialize the 4 points of the De Casteljau's algorithm inputs
        // d1 and d2 are the diff between p1-p3 and p2-p4

        // Calculate the points forming the line
        int n = this.getPointCount();
        Vector[] points = new Vector[n];
        points[0] = this._endA.node.getPosition();
        points[n-1] = this._endB.node.getPosition();
        for (int i = 1; i < (n-1); i++) {
            double t = ((double) i / (double) (n-1));
            points[i] = path.getPosition(t);
        }

        if ((n - 1) != this.lines.size()) {
            for (int i = 0; i < this.lines.size(); i++) {
                this.lines.get(i).remove();
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

    }

    /**
     * Calculates all the discrete path points required to build the smooth path from t0 to t1.
     * 
     * @param builder
     * @param railsPos
     * @param smoothness
     * @param t0
     * @param t1
     */
    public void buildPath(List<RailPath.Point> points, IntVector3 railsPos, double smoothness, double t0, double t1) {
        TrackConnectionPath path = this.getPath();

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
                double error = path.getLinearError(curr_t0, curr_t1);
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
        Vector pos = getPath().getPosition(t);
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
        TrackConnectionPath path = this.getPath();
        RailPath.Position p = RailPath.Position.fromPosDir(path.getPosition(t), getOrientation(t));
        p.setMotion(path.getMotionVector(t));
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
        return Util.lerpOrientation(this._endA.node.getOrientation(), this._endB.node.getOrientation(), t);
    }

    /**
     * Gets the path of this track connection, from which spatial information
     * can be computed.
     * 
     * @return path
     */
    public TrackConnectionPath getPath() {
        return new TrackConnectionPath(this._endA, this._endB);
    }

    public void destroyParticles() {
        for (int i = 0; i < this.lines.size(); i++) {
            this.lines.get(i).remove();
        }
        this.lines.clear();
        //this._connParticleLine.remove();
    }

    public void markChanged() {
        this._endA.node.markChanged();
        this._endB.node.markChanged();
    }

    // metadata for a single endpoint
    protected static class EndPoint extends TrackConnectionPath.EndPoint {
        protected final TrackNode node;
        protected final TrackNode other;

        public EndPoint(TrackNode node, TrackNode other) {
            this.node = node;
            this.other = other;
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
        public Vector getOtherNodePosition() {
            return this.other.getPosition();
        }

        @Override
        public Vector getOtherNodeDirection() {
            return this.other.getDirection();
        }
    }
}
