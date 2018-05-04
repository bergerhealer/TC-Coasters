package com.bergerkiller.bukkit.coasters.tracks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Stores all the track groups and the special connections between track nodes.
 * There is one storage per world.
 */
public class TrackWorld extends CoasterWorldAccess.Component {
    private final List<TrackCoaster> _coasters;
    private final Set<TrackNode> _changedNodes;
    private boolean _is_loading;

    public TrackWorld(CoasterWorldAccess world) {
        super(world);
        this._coasters = new ArrayList<TrackCoaster>();
        this._changedNodes = new HashSet<TrackNode>();
        this._is_loading = false;
    }

    /**
     * Gets the folder in which coasters and other world-specific data is saved for this world.
     * This function also ensures that the folder itself exists.
     * 
     * @return world config folder
     */
    public File getConfigFolder() {
        World w = this.getWorld();
        File f = new File(this.getPlugin().getDataFolder(), w.getName() + "_" + w.getUID());
        f.mkdirs();
        return f;
    }

    /**
     * Gets a list of all coasters on this world
     * 
     * @return coasters
     */
    public List<TrackCoaster> getCoasters() {
        return this._coasters;
    }

    /**
     * Attempts to find the coaster by a given name
     * 
     * @param name
     * @return coaster
     */
    public TrackCoaster findCoaster(String name) {
        for (TrackCoaster coaster: this._coasters) {
            if (coaster.getName().equals(name)) {
                return coaster;
            }
        }
        return null;
    }

    /**
     * Finds the track node that exists precisely at a particular 3d position
     * 
     * @param position
     * @return node at the position, null if not found
     */
    public TrackNode findNodeExact(Vector position) {
        for (TrackCoaster coaster : this._coasters) {
            TrackNode node = coaster.findNodeExact(position);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Finds the track nodes that are approximately nearby a particular 3d position.
     * Is used when snapping two nodes together and combine them, and also to check a node
     * doesn't already exist at a particular location when creating new nodes.
     * 
     * @param result to add found nodes to
     * @param position
     * @param radius
     * @return result
     */
    public List<TrackNode> findNodesNear(List<TrackNode> result, Vector position, double radius) {
        for (TrackCoaster coaster : this._coasters) {
            coaster.findNodesNear(result, position, radius);
        }
        return result;
    }

    /**
     * Creates a very new coaster, with only a single track node.
     * It will have an auto-generated name.
     * 
     * @param firstNodePosition
     * @return new track coaster
     */
    public TrackCoaster createNew(Vector firstNodePosition) {
        TrackCoaster coaster = new TrackCoaster(this, this.getPlugin().generateNewCoasterName());
        coaster.createNewNode(firstNodePosition, new Vector(0.0, 1.0, 0.0));
        this._coasters.add(coaster);
        return coaster;
    }

    /**
     * Adds a new track node that is connected to another node, with the same up-vector as
     * the node connected to.
     * 
     * @param node
     * @param position
     * @return newly created node
     */
    public TrackNode addNode(TrackNode node, Vector position) {
        if (node.getWorld() != this.getWorld()) {
            throw new IllegalArgumentException("Input node is not on world " + this.getWorld().getName());
        }
        TrackNode newNode = node.getCoaster().createNewNode(position, node.getOrientation());
        this.addConnection(node, newNode);
        return newNode;
    }

    /**
     * Connects two track nodes together
     * 
     * @param nodeA
     * @param nodeB
     * @return connection
     */
    public TrackConnection connect(TrackNode nodeA, TrackNode nodeB) {
        // Let's not do this
        if (nodeA == nodeB) {
            throw new IllegalArgumentException("Input nodeA and nodeB are the same nodes!");
        }
        // Verify no such connection exists yet
        for (TrackConnection connection : nodeA._connections) {
            if (connection.isConnected(nodeB)) {
                return connection;
            }
        }
        // Safety!
        if (nodeA.getWorld() != this.getWorld()) {
            throw new IllegalArgumentException("Input nodeA is not on world " + this.getWorld().getName());
        }
        if (nodeB.getWorld() != this.getWorld()) {
            throw new IllegalArgumentException("Input nodeB is not on world " + this.getWorld().getName());
        }
        // Create new connection
        return addConnection(nodeA, nodeB);
    }

    /**
     * Removes all connections from/to a particular node
     * 
     * @param node
     */
    public void disconnectAll(TrackNode node) {
        // Store connections and clear for the node itself
        TrackConnection[] connections = node._connections;
        node._connections = new TrackConnection[0];

        // Schedule refresh of node
        scheduleNodeRefresh(node);

        // Also perform proper removal logic of the connections themselves
        for (TrackConnection conn : connections) {
            TrackNode other = conn.getOtherNode(node);

            // Remove all connections from the other node
            removeConnectionFromNode(other, conn);

            // Schedule refresh of other node
            scheduleNodeRefresh(other);

            // Destroy connection
            conn.destroyParticles();
            conn.markChanged();
        }
    }

    private final TrackConnection addConnection(TrackNode nodeA, TrackNode nodeB) {
        TrackConnection connection = new TrackConnection(nodeA, nodeB);
        addConnectionToNode(nodeA, connection);
        addConnectionToNode(nodeB, connection);
        scheduleNodeRefresh(nodeA);
        scheduleNodeRefresh(nodeB);
        connection.markChanged();
        return connection;
    }

    /**
     * Deletes all coasters from this world
     */
    public void clear() {
        // Mark loading to avoid slow tracking of changed nodes during clearing
        this._is_loading = true;

        // Perform clearing logic
        for (TrackCoaster coaster : this._coasters) {
            coaster.clear();
        }
        this._coasters.clear();
        this._changedNodes.clear();

        this.getRails().clear();

        // Done.
        this._is_loading = false;
    }

    /**
     * Resets and loads all coasters from file
     */
    public void load() {
        this.clear();

        // Mark loading to avoid slow tracking of changed nodes during load
        this._is_loading = true;

        // List all coasters saved on disk. List both .csv and .csv.tmp coasters.
        HashSet<String> coasterNames = new HashSet<String>();
        for (File coasterFile : this.getConfigFolder().listFiles()) {
            String name = coasterFile.getName().toLowerCase(Locale.ENGLISH);
            if (name.endsWith(".csv.tmp")) {
                coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 8)));
            } else if (name.endsWith(".csv")) {
                coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 4)));
            }
        }

        // Build and load all coasters
        for (String name : coasterNames) {
            TrackCoaster coaster = new TrackCoaster(this, name);
            this._coasters.add(coaster);
            coaster.load();
        }

        // Mark all coasters as unchanged
        for (TrackCoaster coaster : this._coasters) {
            coaster.markUnchanged();
        }

        // Force a refresh of all nodes contained
        this._is_loading = false;
        for (TrackCoaster coaster : this._coasters) {
            this._changedNodes.addAll(coaster.getNodes());
        }
    }

    /**
     * Called every tick to update any changed nodes
     */
    public void updateAll() {
        if (!this._changedNodes.isEmpty()) {
            // Refresh all the node's shape and track the connections that also changed
            HashSet<TrackConnection> changedConnections = new HashSet<TrackConnection>(this._changedNodes.size()+1);
            for (TrackNode changedNode : this._changedNodes) {
                changedNode.onShapeUpdated();
                changedConnections.addAll(changedNode.getConnections());
            }
            for (TrackConnection changedConnection : changedConnections) {
                changedConnection.onShapeUpdated();
            }

            // Purge all cached rail information for the changed nodes
            this.getRails().purge(this._changedNodes);

            // Re-create all the cached rail information for the changed nodes
            for (TrackNode changedNode : this._changedNodes) {
                this.getRails().store(changedNode);
            }
            this._changedNodes.clear();
        }
    }

    /**
     * Schedules a node for refreshing it's shape and path information in the world for the next tick
     * 
     * @param node
     */
    public void scheduleNodeRefresh(TrackNode node) {
        if (!this._is_loading) {
            this._changedNodes.add(node);
            for (TrackConnection conn_a : node._connections) {
                TrackNode other_a = conn_a.getOtherNode(node);
                this._changedNodes.add(other_a);
                for (TrackConnection conn_b : other_a._connections) {
                    if (conn_a != conn_b) {
                        TrackNode other_b = conn_b.getOtherNode(other_a);
                        this._changedNodes.add(other_b);
                    }
                }
            }
        }
    }

    /**
     * Cancels any refreshing planned for a node
     * 
     * @param node
     */
    public void cancelNodeRefresh(TrackNode node) {
        if (!this._is_loading) {
            this._changedNodes.remove(node);
        }
    }

    /**
     * Saves all coasters stored inside the world to disk
     * 
     * @param autosave whether to save only when changes occurred (true), or all the time (false)
     */
    public void save(boolean autosave) {
        Iterator<TrackCoaster> iter = this._coasters.iterator();
        while (iter.hasNext()) {
            TrackCoaster coaster = iter.next();
            if (coaster.getNodes().isEmpty()) {
                coaster.clear();
                iter.remove();

                // Deletes the physical saved files of the coasters
                String baseName = TCCoasters.escapeName(coaster.getName());
                File folder = getConfigFolder();
                File tmpFile = new File(folder, baseName + ".csv.tmp");
                File realFile = new File(folder, baseName + ".csv");
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                if (realFile.exists()) {
                    realFile.delete();
                }
            } else {
                coaster.save(autosave);
            }
        }
    }

    private static void addConnectionToNode(TrackNode node, TrackConnection connection) {
        node._connections = Arrays.copyOf(node._connections, node._connections.length + 1);
        node._connections[node._connections.length - 1] = connection;
    }

    private static void removeConnectionFromNode(TrackNode node, TrackConnection connection) {
        if (node._connections.length != 1) {
            for (int i = 0; i < node._connections.length; i++) {
                if (node._connections[i] == connection) {
                    TrackConnection[] new_connections = new TrackConnection[node._connections.length - 1];
                    System.arraycopy(node._connections, 0, new_connections, 0, i);
                    System.arraycopy(node._connections, i+1, new_connections, i, node._connections.length-i-1);
                    node._connections = new_connections;
                    break;
                }
            }
        } else if (node._connections[0] == connection) {
            node._connections = TrackConnection.EMPTY_ARR;
        }
    }
}
