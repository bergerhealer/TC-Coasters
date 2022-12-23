package com.bergerkiller.bukkit.coasters.tracks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.util.FileUtil;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV;
import com.bergerkiller.bukkit.coasters.csv.TrackCSVReader;
import com.bergerkiller.bukkit.coasters.csv.TrackCSVWriter;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;

/**
 * All the nodes belonging to a single coaster.
 * Properties applied to all the nodes of the coaster are stored here.
 */
public class TrackCoaster implements CoasterWorldComponent, Lockable {
    private final CoasterWorld _world;
    private String _name;
    private List<TrackNode> _nodes;
    private boolean _changed = false;
    private boolean _locked = false;

    protected TrackCoaster(CoasterWorld world, String name) {
        this._world = world;
        this._name = name;
        this._nodes = new ArrayList<TrackNode>();
        this._changed = false;
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
    }

    /**
     * Finds the track node that exists precisely at a particular 3d position.<br>
     * <br>
     * If multiple nodes exist at the same position (=zero distance neighbours),
     * then it will select the orphan node if it has zero connections to other nodes.
     * This makes sure that when creating links, the 'straightened' effect of the
     * node is preferred instead of creating random broken junctions.
     * 
     * @param position
     * @return node at the position, null if not found
     */
    public TrackNode findNodeExact(Vector position) {
        final double MAX_DIFF = 1e-6;
        for (TrackNode node : this._nodes) {
            Vector npos = node.getPosition();
            double dx = Math.abs(npos.getX() - position.getX());
            double dy = Math.abs(npos.getY() - position.getY());
            double dz = Math.abs(npos.getZ() - position.getZ());
            if (dx < MAX_DIFF && dy < MAX_DIFF && dz < MAX_DIFF) {
                return node.selectZeroDistanceOrphan();
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
        // Minor
        double rq = (radius*radius);
        for (TrackNode node : this._nodes) {
            if (node.getPosition().distanceSquared(position) < rq) {
                result.add(node);
            }
        }
        return result;
    }

    public void removeNode(TrackNode node) {
        if (this._nodes.remove(node)) {
            this.getWorld().getTracks().disconnectAll(node, true);
            this.getWorld().getTracks().cancelNodeRefresh(node);
            this.getWorld().getRails().purge(node);
            node.onRemoved();
            this.getPlugin().forAllEditStates(editState -> editState.setEditing(node, false));
            this.markChanged();
        }
    }

    public List<TrackNode> getNodes() {
        return this._nodes;
    }

    public String getName() {
        return this._name;
    }

    public TrackNode createNewNode(TrackNodeState state) {
        TrackNode node = new TrackNode(this, state);
        this._nodes.add(node);
        return node;
    }

    public TrackNode createNewNode(Vector position, Vector up) {
        return createNewNode(TrackNodeState.create(position, up, null));
    }

    @Override
    public boolean isLocked() {
        return this._locked;
    }

    /**
     * Sets whether this coaster is locked.
     * A locked coaster cannot be modified by players until unlocked.
     * 
     * @param locked
     */
    public void setLocked(boolean locked) {
        if (this._locked != locked) {
            this._locked = locked;
            this.markChanged();
        }
    }

    /**
     * Internal use only. Mark the coaster as unchanged so it is NOT saved.
     */
    protected void markUnchanged() {
        this._changed = false;
    }

    /**
     * Called from all over the place to indicate that the coaster has been changed.
     * Autosave will kick in at a later time to save this coaster to file again.
     * 
     * @param changed
     */
    public void markChanged() {
        this._changed = true;
    }

    /**
     * Removes this coaster from the world, clearing all nodes contained within
     */
    public void remove() {
        getWorld().getTracks().removeCoaster(this);
    }

    /**
     * Removes all nodes and connections of this coaster
     */
    public void clear() {
        getWorld().getRails().purge(this._nodes);
        for (TrackNode node : this._nodes) {
            this.getWorld().getTracks().disconnectAll(node, true);
            node.onRemoved();
        }
        this._nodes.clear();
    }

    /**
     * Refreshes connections made between nodes
     */
    public void refreshConnections() {
        TrackWorld tracks = this.getWorld().getTracks();
        for (TrackNode node : getNodes()) {
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                TrackConnectionState[] new_connections = state.connections;
                boolean changed = false;
                for (int i = 0; i < new_connections.length; i++) {
                    TrackConnectionState referenced = new_connections[i].reference(tracks);
                    if (referenced != new_connections[i]) {
                        if (!changed) {
                            changed = true;
                            new_connections = new_connections.clone();
                        }
                        new_connections[i] = referenced;
                    }
                }
                if (changed) {
                    node.setAnimationState(state.name, state.state, new_connections);
                }
            }
        }
    }

    /**
     * Loads this coaster's nodes from file and makes all connections contained therein
     */
    public void load() {
        // Load the save file. If the save file is not found, but a .tmp file version of it does exist,
        // this indicates saving failed previously inbetween deleting and renaming the .tmp to .csv.
        // We must load the .tmp file instead, then, but also log a warning about this!
        String baseName = TCCoasters.escapeName(this.getName());
        File folder = this.getWorld().getConfigFolder(false);
        File tmpFile = new File(folder, baseName + ".csv.tmp");
        File realFile = new File(folder, baseName + ".csv");
        if (!realFile.exists()) {
            if (tmpFile.exists()) {
                this.getPlugin().getLogger().log(Level.WARNING,
                        "Coaster " + this.getName() + " was restored from a temporary csv file, indicating prior save failure");
                realFile = tmpFile;
            } else {
                this.getPlugin().getLogger().log(Level.SEVERE,
                        "Coaster " + this.getName() + " could not be loaded: missing file");
                return;
            }
        }

        // Load from file
        try {
            this.loadFromStream(new FileInputStream(realFile));
        } catch (CoasterLoadException e) {
            // Log the message
            this.getPlugin().getLogger().log(Level.SEVERE, e.getMessage());

            // Move the file to a backup place
            File backupFile = new File(folder, baseName + ".csv.corrupt." + System.currentTimeMillis());
            if (realFile.renameTo(backupFile)) {
                // Log it so people know
                this.getPlugin().getLogger().log(Level.WARNING,
                        "A backup of this corrupted coaster file can be found at " + backupFile.toString());

                // Re-save the coaster right away, to make sure details are still there
                this.save(false);
            }
        } catch (FileNotFoundException e) {
            this.getPlugin().getLogger().log(Level.SEVERE,
                    "Failed to find file trying to load coaster " + this.getName());
        }

        // Coaster loaded. Any post-ops?
        this.markUnchanged();
    }

    /**
     * Loads this coaster by reading CSV data from a reader
     * 
     * @param inputStream to read from
     */
    public void loadFromStream(InputStream inputStream) throws CoasterLoadException {
        this.loadFromStream(inputStream, null);
    }

    /**
     * Loads this coaster by reading CSV data from a reader
     * 
     * @param inputStream to read from
     * @param origin relative to which to place the coaster
     */
    public void loadFromStream(InputStream inputStream, PlayerOrigin origin) throws CoasterLoadException {
        try (TrackCSVReader reader = new TrackCSVReader(inputStream)) {
            reader.setOrigin(origin);
            reader.create(this);

            // Note: on failure not all nodes may be loaded, but at least some are.
        } catch (SyntaxException ex) {
            throw new CoasterLoadException("Syntax error while loading coaster " + this.getName() + " " + ex.getMessage());
        } catch (IOException ex) {
            throw new CoasterLoadException("An I/O Error occurred while loading coaster " + this.getName(), ex);
        } catch (Throwable t) {
            getPlugin().getLogger().log(Level.SEVERE, "An unexpected error occurred while loading coaster " + this.getName(), t);
            throw new CoasterLoadException("An unexpected error occurred while loading coaster " + this.getName(), t);
        }
    }

    /**
     * Renames this coaster, renaming the save file and changing {@link #getname()}
     *
     * @param newName New name
     */
    public void renameTo(String newName) {
        File folder = this.getWorld().getConfigFolder(true);
        File oldRealFile = new File(folder, TCCoasters.escapeName(this.getName()) + ".csv");
        File newRealFile = new File(folder, TCCoasters.escapeName(newName) + ".csv");
        oldRealFile.renameTo(newRealFile);
        this._name = newName;
    }

    /**
     * Saves the contents of this coaster to .csv file
     * 
     * @param autosave whether to save only when changes occurred (true), or all the time (false)
     */
    public void save(boolean autosave) {
        if (autosave && !this._changed) {
            return;
        }
        this._changed = false;

        // Save coaster information to a tmp file first
        boolean success = false;
        String baseName = TCCoasters.escapeName(this.getName());
        File folder = this.getWorld().getConfigFolder(true);
        File tmpFile = new File(folder, baseName + ".csv.tmp");
        File realFile = new File(folder, baseName + ".csv");
        try (TrackCSVWriter writer = new TrackCSVWriter(new FileOutputStream(tmpFile, false))) {
            if (this.isLocked()) {
                writer.write(new TrackCSV.LockCoasterEntry());
            }
            writer.writeAll(this.getNodes());
            success = true;
        } catch (IOException ex) {
            this.getPlugin().getLogger().log(Level.SEVERE,
                    "An I/O Error occurred while saving coaster " + this.getName(), ex);
        } catch (Throwable t) {
            this.getPlugin().getLogger().log(Level.SEVERE,
                    "An unexpected error occurred while saving coaster " + this.getName(), t);
        }

        // If successful, attempt deleting the original save file
        if (success && (!realFile.delete() && realFile.exists())) {
            this.getPlugin().getLogger().log(Level.SEVERE,
                    "Failed to save coaster " + this.getName() + ": Old file could not be overwritten");
            success = false;
        }

        // Check for success to decide whether to keep or discard the tmpFile
        if (!success) {
            tmpFile.delete(); // Ignore failure to delete again...
            return;
        }

        // Attempt moving. If that fails, attempt a copy + delete.
        // Note that copy + delete is more dangerous, as it is not atomic.
        if (!tmpFile.renameTo(realFile)) {
            if (FileUtil.copy(tmpFile, realFile)) {
                tmpFile.delete();
            } else {
                this.getPlugin().getLogger().log(Level.SEVERE,
                        "Failed to save coaster " + this.getName() + ": Failed to move or copy file");
            }
        }
    }

    /**
     * Exception thrown when a coaster cannot be loaded
     */
    public static class CoasterLoadException extends Exception {
        private static final long serialVersionUID = 7975001382148776707L;

        public CoasterLoadException(String message) {
            super(message);
        }

        public CoasterLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
