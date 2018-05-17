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
public class TrackConnection {
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

    public void setSelected(boolean selected) {
        if (this._selected != selected) {
            this._selected = selected;
            //this._connParticleLine.setItemEnchanted(selected);
        }
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
        return this._endA.node == atNode ? this._endA.direction : this._endB.direction;
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
            return getPosition((double) 1 / (double) (n-1));
        } else {
            return getPosition((double) (n-2) / (double) (n-1));
        }
    }

    /**
     * Called when the shape of the track connection has been changed.
     * This can happen as a result of position changes of the nodes themselves,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
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
            points[i] = this.getPosition(t);
        }

        if ((n - 1) != this.lines.size()) {
            for (int i = 0; i < this.lines.size(); i++) {
                this.lines.get(i).remove();
            }
            this.lines.clear();

            TrackParticleWorld pworld = this._endA.node.getCoaster().getParticles();
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
        return Util.lerpOrientation(this._endA.node.getOrientation(), this._endB.node.getOrientation(), t);
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return position at t
     */    
    public Vector getPosition(double t) {
        // https://pomax.github.io/bezierinfo/#decasteljau
        double tp = t;
        double tn = 1.0 - tp;
        double tp2 = tp * tp;

        double ff = 3.0 * (tp - tp2);
        double fdB = tp * ff;
        double fdA = tn * ff;

        double fpB = fdB + (tp * tp2);
        double fpA = -fpB + 1.0;

        double pfdA = fdA * this._endA.distance;
        double pfdB = fdB * this._endB.distance;
        Vector pA = this._endA.node.getPosition();
        Vector pB = this._endB.node.getPosition();
        Vector dA = this._endA.direction;
        Vector dB = this._endB.direction;
        return new Vector(
                fpA*pA.getX() + fpB*pB.getX() + pfdA*dA.getX() + pfdB*dB.getX(),
                fpA*pA.getY() + fpB*pB.getY() + pfdA*dA.getY() + pfdB*dB.getY(),
                fpA*pA.getZ() + fpB*pB.getZ() + pfdA*dA.getZ() + pfdB*dB.getZ());
    }

    /**
     * Calculates the motion vector along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return motion vector at t
     */
    public Vector getMotionVector(double t) {
        // Derivative of getPosition(t)
        double tp = t;
        double tn = 1.0 - tp;

        double tp2 = tp * tp;
        double tn2 = tn * tn;

        double fpA_dt = 6.0*(tp2-tp);
        double fpB_dt = -fpA_dt;

        double fdA_dt = fpA_dt + 3.0*tn2;
        double fdB_dt = fpB_dt - 3.0*tp2;

        double pfdA_dt = fdA_dt * this._endA.distance;
        double pfdB_dt = fdB_dt * this._endB.distance;
        Vector pA = this._endA.node.getPosition();
        Vector pB = this._endB.node.getPosition();
        Vector dA = this._endA.direction;
        Vector dB = this._endB.direction;
        return new Vector(
                fpA_dt*pA.getX() + fpB_dt*pB.getX() + pfdA_dt*dA.getX() + pfdB_dt*dB.getX(),
                fpA_dt*pA.getY() + fpB_dt*pB.getY() + pfdA_dt*dA.getY() + pfdB_dt*dB.getY(),
                fpA_dt*pA.getZ() + fpB_dt*pB.getZ() + pfdA_dt*dA.getZ() + pfdB_dt*dB.getZ()).normalize();
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
    protected static class EndPoint {
        protected final TrackNode node;
        protected final TrackNode other;
        protected Vector direction = new Vector();
        protected double distance = 0.0;

        public EndPoint(TrackNode node, TrackNode other) {
            this.node = node;
            this.other = other;
        }

        public void initAuto() {
            this.direction = other.getPosition().clone().subtract(node.getPosition()).normalize();
            this.updateDistance();
        }

        public void initNormal() {
            this.direction = node.getDirection();
            this.updateDistance();
        }

        public void initInverted() {
            this.direction = node.getDirection().clone().multiply(-1.0);
            this.updateDistance();
        }

        private final void updateDistance() {
            this.distance = 0.5 * node.getPosition().distance(other.getPosition());
        }

    }
}
