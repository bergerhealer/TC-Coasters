package com.bergerkiller.bukkit.coasters.tracks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleArrow;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleText;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * A single node of track of a rollercoaster. Stores the 3D position
 * and the 'up' vector.
 */
public class TrackNode implements CoasterWorldAccess {
    private TrackCoaster _coaster;
    private Vector _pos, _up, _up_visual, _dir;
    //private TrackParticleItem _particle;
    private TrackParticleArrow _upParticleArrow;
    private List<TrackParticleText> _junctionParticles;
    // Connections are automatically updated when connecting/disconnecting
    protected TrackConnection[] _connections;

    protected TrackNode(TrackCoaster group, Vector pos, Vector up) {
        this._coaster = group;
        this._pos = pos;
        this._connections = TrackConnection.EMPTY_ARR;
        if (up.lengthSquared() < 1e-10) {
            this._up = new Vector(0.0, 0.0, 0.0);
        } else {
            this._up = up.clone().normalize();
        }
        this._up_visual = this._up.clone();
        this._dir = new Vector(0, 0, 1);

        /*
        this._particle = group.getParticles().addParticleItem(this._pos);
        this._particle.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(Player viewer) {
                TrackEditState editState = getPlugin().getEditState(viewer);
                return (editState.getMode() == TrackEditState.Mode.POSITION && editState.isEditing(TrackNode.this)) ? 
                        TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
            }
        });
        */

        this._upParticleArrow = group.getParticles().addParticleArrow(this._pos, this._dir, this._up_visual);
        this._upParticleArrow.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(Player viewer) {
                PlayerEditState editState = getPlugin().getEditState(viewer);
                return editState.isEditing(TrackNode.this) ? 
                        TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
            }
        });

        this._junctionParticles = Collections.emptyList();
    }

    public TrackCoaster getCoaster() {
        return this._coaster;
    }

    public void markChanged() {
        this._coaster.markChanged();
    }

    public final void remove() {
        getCoaster().removeNode(this);
    }

    public void setPosition(Vector position) {
        if (!this._pos.equals(position)) {
            this._pos = position.clone();
            //this._particle.setPosition(this._pos);
            this._upParticleArrow.setPosition(this._pos);
            this.scheduleRefresh();
            this.markChanged();
        }
    }

    public Vector getDirection() {
        return this._dir;
    }

    public Vector getPosition() {
        return this._pos;
    }

    /**
     * Gets the Bukkit world Location of where minecarts should be spawned.
     * Includes the direction vector in the orientation
     * 
     * @param orientation vector
     * @return spawn location
     */
    public Location getSpawnLocation(Vector orientation) {
        Vector dir = this.getDirection().clone();
        if (dir.dot(orientation) < 0.0) {
            dir.multiply(-1.0);
        }
        Vector pos = this.getPosition();
        return new Location(this.getWorld(),
                pos.getX(), pos.getY(), pos.getZ(),
                MathUtil.getLookAtYaw(dir),
                MathUtil.getLookAtPitch(dir.getX(),dir.getY(),dir.getZ()));
    }

    public Vector getUpPosition() {
        return this._pos.clone().add(this._up.clone().multiply(0.4));
    }

    public void setOrientation(Vector up) {
        // Assign up vector, normalize it to length 1
        double up_n = MathUtil.getNormalizationFactor(up);
        if (!Double.isInfinite(up_n)) {
            this._up = up.clone().multiply(up_n);
        }

        // Calculate what kind of up vector is used 'visually'
        // This is on a 90-degree angle with the track itself (dir)
        this._up_visual = this._dir.clone().crossProduct(this._up).crossProduct(this._dir);
        double n = MathUtil.getNormalizationFactor(this._up_visual);
        if (Double.isInfinite(n)) {
            // Fallback for up-vectors in the same orientation as dir
            this._up_visual = this._dir.clone().crossProduct(new Vector(1,1,1)).crossProduct(this._dir).normalize();
        } else {
            this._up_visual.multiply(n);
        }
        this._upParticleArrow.setDirection(this._dir, this._up_visual);
        this.markChanged();
    }

    public Vector getOrientation() {
        return this._up;
    }

    /**
     * Called when the shape of the track node has been changed.
     * This can happen as a result of position changes of the node itself,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
        // Sort before continueing
        this.sortConnections();

        // Refresh dir
        this._dir = new Vector();
        List<TrackConnection> connections = this.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            TrackConnection conn = connections.get(i);
            TrackNode neighbour = conn.getOtherNode(this);
            
            Vector v = neighbour.getPosition().clone().subtract(this.getPosition());
            double n = MathUtil.getNormalizationFactor(v);
            if (!Double.isInfinite(n)) {
                v.multiply(n);

                if (connections.size() > 2) {
                    // Best fit applies
                    if (this._dir.dot(v) > 0.0) {
                        this._dir.add(v);
                    } else {
                        this._dir.subtract(v);
                    }
                } else {
                    // Force direction from node to node at all times
                    // Add/subtract alternate based on index
                    if (i == 0) {
                        this._dir.add(v);
                    } else {
                        this._dir.subtract(v);
                    }
                }
            }
        }

        // Normalize
        double n = MathUtil.getNormalizationFactor(this._dir);
        if (Double.isInfinite(n)) {
            this._dir = new Vector(0, 0, 1);
        } else {
            this._dir.multiply(n);
        }

        // Recalculate the up-vector to ortho to dir
        this.setOrientation(this._up);

        // Refresh connections connected to this node, for they have changed
        for (int i = 0; i < connections.size(); i++) {
            TrackConnection conn = connections.get(i);

            // Recalculate the smoothening algorithm for all connections
            TrackConnection.EndPoint end;
            if (this == conn._endA.node) {
                end = conn._endA;
            } else {
                end = conn._endB;
            }
            if (connections.size() > 2) {
                end.initAuto();
            } else if (i == 0) {
                end.initNormal();
            } else {
                end.initInverted();
            }
        }

        // If more than 2 connections are added to this node, display junction labels
        if (connections.size() > 2) {
            // Initialize or shrink list of particles as required
            if (this._junctionParticles.isEmpty()) {
                this._junctionParticles = new ArrayList<TrackParticleText>(connections.size());
            } else {
                while (this._junctionParticles.size() > connections.size()) {
                    this._junctionParticles.remove(this._junctionParticles.size() - 1).remove();
                }
            }

            // Refresh the particles
            for (int i = 0; i < connections.size(); i++) {
                Vector pos = connections.get(i).getNearEndPosition(this);
                String text = TrackParticleText.getOrdinalText(i, Integer.toString(i+1));
                if (i >= this._junctionParticles.size()) {
                    this._junctionParticles.add(getParticles().addParticleText(pos, text));
                } else {
                    TrackParticleText particle = this._junctionParticles.get(i);
                    particle.setPosition(pos);
                    particle.setText(text);
                }
            }

        } else {
            for (TrackParticle particle : this._junctionParticles) {
                particle.remove();
            }
            this._junctionParticles = Collections.emptyList();
        }
    }

    private final void sortConnections() {
        // Sorting only required when count > 2
        if (this._connections.length <= 2) {
            return;
        }

        // Sort connections as required on the horizontal plane
        // Use the first connection as a base angle
        ArrayList<TrackConnection> tmp = new ArrayList<TrackConnection>(this.getConnections());
        int baseIndex = 0; //TODO: Compute for T-splits

        // Perform the sorting logic based on yaw
        TrackConnection base = tmp.remove(baseIndex);
        Vector bp = base.getOtherNode(this).getPosition();
        final Vector p = this.getPosition();
        final float base_yaw = MathUtil.getLookAtYaw(bp.getX()-p.getX(), bp.getZ()-p.getZ());
        Collections.sort(tmp, new Comparator<TrackConnection>() {
            @Override
            public int compare(TrackConnection o1, TrackConnection o2) {
                Vector p1 = o1.getOtherNode(TrackNode.this).getPosition();
                Vector p2 = o2.getOtherNode(TrackNode.this).getPosition();
                float y1 = MathUtil.getLookAtYaw(p1.getX()-p.getX(), p1.getZ()-p.getZ()) - base_yaw;
                float y2 = MathUtil.getLookAtYaw(p2.getX()-p.getX(), p2.getZ()-p.getZ()) - base_yaw;
                while (y1 < 0.0f) y1 += 360.0f;
                while (y2 < 0.0f) y2 += 360.0f;
                return Float.compare(y1, y2);
            }
        });
        tmp.add(baseIndex, base);

        // Apply
        for (int i = 0; i < tmp.size(); i++) {
            this._connections[i] = tmp.get(i);
        }
    }
    
    public void onStateUpdated(Player viewer) {
        //this._particle.onStateUpdated(viewer);
        this._upParticleArrow.onStateUpdated(viewer);
        for (TrackParticle juncParticle : this._junctionParticles) {
            juncParticle.onStateUpdated(viewer);
        }
    }

    public List<TrackConnection> getConnections() {
        return Arrays.asList(this._connections);
    }

    public List<TrackNode> getNeighbours() {
        TrackNode[] result = new TrackNode[this._connections.length];
        for (int i = 0; i < this._connections.length; i++) {
            result[i] = this._connections[i].getOtherNode(this);
        }
        return Arrays.asList(result);
    }

    public double getViewDistance(Matrix4x4 cameraTransform) {
        return Math.min(getViewDistance(cameraTransform, this.getPosition()),
                        getViewDistance(cameraTransform, this.getUpPosition()));
    }

    private static double getViewDistance(Matrix4x4 cameraTransform, Vector pos) {
        pos = pos.clone();
        cameraTransform.transformPoint(pos);

        // Behind the player
        if (pos.getZ() <= 1e-6) {
            return Double.MAX_VALUE;
        }

        // Calculate limit based on depth (z) and filter based on it
        double lim = Math.max(1.0, pos.getZ() * Math.pow(4.0, -pos.getZ()));
        if (Math.abs(pos.getX()) > lim || Math.abs(pos.getY()) > lim) {
            return Double.MAX_VALUE;
        }

        // Calculate 2d distance
        return Math.sqrt(pos.getX() * pos.getX() + pos.getY() * pos.getY()) / lim;
    }

    public void destroyParticles() {
        this._upParticleArrow.remove();
        for (TrackParticle particle : this._junctionParticles) {
            particle.remove();
        }
        this._junctionParticles = Collections.emptyList();
    }

    public IntVector3 getRailsBlock() {
        return new IntVector3(getPosition().getX(), getPosition().getY(), getPosition().getZ());
    }

    /**
     * Builds a rail path for this track node, covering half of the connections connecting to this node.
     * 
     * @return rail path
     */
    public RailPath buildPath() {
        List<TrackConnection> connections = this.getConnections();
        if (connections.isEmpty()) {
            return RailPath.EMPTY;
        }

        // Minimalist.
        /*
        IntVector3 railsPos = getRailsBlock();
        RailPath.Builder builder = new RailPath.Builder();
        TrackConnection first = connections.get(0);
        if (first.getNodeA() == this) {
            builder.add(first.getPathPoint(railsPos, 0.5));
            builder.add(first.getPathPoint(railsPos, 0.0));
        } else {
            builder.add(first.getPathPoint(railsPos, 0.5));
            builder.add(first.getPathPoint(railsPos, 1.0));
        }
        if (connections.size() >= 2) {
            TrackConnection second = connections.get(1);
            builder.add(second.getPathPoint(railsPos, 0.5));
        }
        return builder.build();
        */

        //TODO: We can use less or more iterations here based on how much is really needed
        // This would help performance a lot!
        IntVector3 railsPos = getRailsBlock();
        RailPath.Builder builder = new RailPath.Builder();
        TrackConnection first = connections.get(0);
        if (first.getNodeA() == this) {
            for (int n = 50; n >= 0; --n) {
                double t = 0.01 * n;
                builder.add(first.getPathPoint(railsPos, t));
            }
        } else {
            for (int n = 50; n <= 100; n++) {
                double t = 0.01 * n;
                builder.add(first.getPathPoint(railsPos, t));
            }
        }
        if (connections.size() >= 2) {
            TrackConnection second = connections.get(1);
            if (second.getNodeA() == this) {
                for (int n = 1; n <= 50; n++) {
                    double t = 0.01 * n;
                    builder.add(second.getPathPoint(railsPos, t));
                }
            } else {
                for (int n = 100-1; n >= 50; --n) {
                    double t = 0.01 * n;
                    builder.add(second.getPathPoint(railsPos, t));
                }
            }
        }
        return builder.build();
    }

    private void scheduleRefresh() {
        this.getTracks().scheduleNodeRefresh(this);
    }

    // CoasterWorldAccess

    @Override
    public TCCoasters getPlugin() {
        return this._coaster.getPlugin();
    }

    @Override
    public World getWorld() {
        return this._coaster.getWorld();
    }

    @Override
    public TrackWorld getTracks() {
        return this._coaster.getTracks();
    }

    @Override
    public TrackParticleWorld getParticles() {
        return this._coaster.getParticles();
    }

    @Override
    public TrackRailsWorld getRails() {
        return this._coaster.getRails();
    }

}
