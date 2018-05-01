package com.bergerkiller.bukkit.coasters.meta;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
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

    public TrackNode getNodeA() {
        return this._endA.node;
    }

    public TrackNode getNodeB() {
        return this._endB.node;
    }

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
     * Called when the shape of the track connection has been changed.
     * This can happen as a result of position changes of the nodes themselves,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
        //this._connParticleLine.setPositions(this._endA.node.getPosition(), this._endB.node.getPosition());

        // Initialize the 4 points of the De Casteljau's algorithm inputs
        // d1 and d2 are the diff between p1-p3 and p2-p4
        

        // Calculate the points forming the line
        int n = MathUtil.ceil(this._endA.node.getPosition().distance(this._endB.node.getPosition()) / 1.0);
        if (n < 2) {
            n = 2;
        }
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
     * Gets a rails path point at a particular t
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
        double tn2 = tn * tn;

        double fdA = 3.0 * tp * tn2;
        double fdB = 3.0 * tn * tp2;

        double fpA = fdA + tn * tn2;
        double fpB = fdB + tp * tp2;

        Vector p1 = this._endA.node.getPosition();
        Vector p2 = this._endB.node.getPosition();
        Vector d1 = this._endA.delta;
        Vector d2 = this._endB.delta;
        return new Vector(
                fpA*p1.getX() + fpB*p2.getX() + fdA*d1.getX() + fdB*d2.getX(),
                fpA*p1.getY() + fpB*p2.getY() + fdA*d1.getY() + fdB*d2.getY(),
                fpA*p1.getZ() + fpB*p2.getZ() + fdA*d1.getZ() + fdB*d2.getZ());
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
        protected Vector delta = new Vector();

        public EndPoint(TrackNode node, TrackNode other) {
            this.node = node;
            this.other = other;
        }

        public void initAuto() {
            double d = this.calcDistance();
            Vector p3_a = node.getPosition().clone().add(node.getDirection().clone().multiply(d));
            Vector p3_b = node.getPosition().clone().add(node.getDirection().clone().multiply(-d));
            if (p3_a.distanceSquared(other.getPosition()) < p3_b.distanceSquared(other.getPosition())) {
                this.delta = node.getDirection().clone().multiply(d);
            } else {
                this.delta = node.getDirection().clone().multiply(-d);
            }
        }

        public void initNormal() {
            this.delta = node.getDirection().clone().multiply(this.calcDistance());
        }

        public void initInverted() {
            this.delta = node.getDirection().clone().multiply(-this.calcDistance());
        }

        private double calcDistance() {
            return 0.5 * node.getPosition().distance(other.getPosition());
        }
    }
}
