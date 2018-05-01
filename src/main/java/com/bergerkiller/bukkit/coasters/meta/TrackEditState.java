package com.bergerkiller.bukkit.coasters.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * The track editing state of a single player
 */
public class TrackEditState {
    private static final int EDIT_AUTO_TIMEOUT = 5;
    private static final int EDIT_CANCEL_TIMEOUT = 8;
    private final TCCoasters plugin;
    private final Player player;
    private final Set<TrackNode> editedNodes = new HashSet<TrackNode>();
    private TrackNode lastEdited = null;
    private long lastEditTime = System.currentTimeMillis();
    private Mode editMode = Mode.DISABLED;
    private Mode afterEditMode = null;
    private int editTimeoutCtr = 0;
    private int heldDownTicks = 0;
    private Location editStartPos = null;
    private Vector editRotInfo = new Vector(); // virtual coordinate of the vector

    public TrackEditState(TCCoasters plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }

    public long getLastEditTime(TrackNode node) {
        if (this.lastEdited != node) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() - this.lastEditTime;
        }
    }

    public void clearEditedNodes() {
        if (!this.editedNodes.isEmpty()) {
            ArrayList<TrackNode> oldNodes = new ArrayList<TrackNode>(this.editedNodes);
            this.editedNodes.clear();
            this.lastEdited = null;
            for (TrackNode oldNode : oldNodes) {
                oldNode.onStateUpdated(this.player);
            }
        }
    }

    /**
     * Finds the track node the player is currently looking at exactly
     * 
     * @return node being looked at
     */
    public TrackNode findLookingAt() {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(this.player.getEyeLocation());
        cameraTransform.invert();

        // Go by all selected track nodes, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        TrackNode bestNode = null;
        double bestDistance = 0.3;
        for (TrackNode node : this.getEditedNodes()) {
            double distance = node.getViewDistance(cameraTransform);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNode = node;
            }
        }
        return bestNode;
    }

    public Mode getMode() {
        return this.editMode;
    }

    public Set<TrackNode> getEditedNodes() {
        return this.editedNodes;
    }

    public boolean hasEditedNodes() {
        return !this.editedNodes.isEmpty();
    }

    public void setMode(Mode mode) {
        this.afterEditMode = null;
        if (this.editMode != mode) {
            this.editMode = mode;
            for (TrackNode node : this.editedNodes) {
                node.onStateUpdated(this.player);
            }
        }
    }

    /**
     * Sets the mode to switch to after releasing right-click in the current mode
     * 
     * @param mode
     */
    public void setAfterEditMode(Mode mode) {
        this.afterEditMode = mode;
    }

    public void setEditing(TrackNode node, boolean editing) {
        if (LogicUtil.addOrRemove(this.editedNodes, node, editing)) {
            node.onStateUpdated(this.player);
            this.lastEdited = node;
            this.lastEditTime = System.currentTimeMillis();
        }
    }

    public boolean isEditing(TrackNode node) {
        return this.editedNodes.contains(node);
    }

    public boolean onLeftClick() {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(player.getEyeLocation());
        cameraTransform.invert();

        // Go by all track nodes on the server, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        TrackNode bestNode = null;
        double bestDistance = Double.MAX_VALUE;

        for (TrackCoaster coaster : plugin.getTracks(player.getWorld()).getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                double distance = node.getViewDistance(cameraTransform);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestNode = node;
                }
            }
        }

        if (bestNode == null) {
            return false;
        }

        long lastEditTime = getLastEditTime(bestNode);
        if (lastEditTime > 300) {
            // Single node selection mode
            if (!player.isSneaking()) {
                clearEditedNodes();
            }

            // Toggle edit state
            setEditing(bestNode, !isEditing(bestNode));

        } else if (isEditing(bestNode)) {
            // Mass-selection mode
            if (player.isSneaking()) {
                // Select all nodes between the clicked node and the nearest other selected node
                this.floodSelectNearest(bestNode);
            } else {
                // Flood-fill select all nodes connected from bestNode
                this.floodSelect(bestNode);
            }
        }

        return true;
    }

    /**
     * Selects a node and all nodes connected to it, recursively.
     * This will select an entire coaster or piece of track, for example.
     * Any previous selection is cleared.
     * 
     * @param startNode to flood select from
     */
    public void floodSelect(TrackNode startNode) {
        this.clearEditedNodes();

        List<TrackNode> pending = new ArrayList<TrackNode>(2);
        pending.add(startNode);

        while (!pending.isEmpty()) {
            TrackNode node = pending.remove(0);
            setEditing(node, true);
            for (TrackNode neighbour : node.getNeighbours()) {
                if (!isEditing(neighbour)) {
                    pending.add(neighbour);
                }
            }
        }
    }

    /**
     * Attempts to find the closest node that is also selected, but not the start node,
     * and selects the nodes between them. The selected nodes are added and the previous
     * selection is kept.
     * 
     * @param startNode
     */
    public void floodSelectNearest(TrackNode startNode) {
        // Find shortest path from startNode to any other nodes being edited
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(startNode, this.getEditedNodes());

        // Now do stuff with the found path, if found
        if (bestPath != null) {
            for (TrackNode node : bestPath.path) {
                this.setEditing(node, true);
            }
        }
    }

    public boolean onRightClick() {
        this.editTimeoutCtr = (EDIT_AUTO_TIMEOUT + EDIT_CANCEL_TIMEOUT);
        return true;
    }

    public void update() {
        if (this.editTimeoutCtr > 0 && this.plugin.isHoldingEditTool(this.player)) {
            this.editTimeoutCtr--;
            if (this.editTimeoutCtr >= EDIT_AUTO_TIMEOUT) {
                if (this.heldDownTicks == 0 || this.editMode.autoActivate(this.heldDownTicks)) {
                    this.updateEditing();
                }
                this.heldDownTicks++;
            }
        } else {
            if (this.heldDownTicks > 0) {
                this.onEditingFinished();
                this.heldDownTicks = 0;
            }
            this.editTimeoutCtr = 0;
            if (this.afterEditMode != null) {
                this.setMode(this.afterEditMode);
            }
        }
    }

    private void updateEditing() {
        if (this.editMode == Mode.CREATE) {
            // Create new tracks
            createTrack();
        } else if (this.editMode == Mode.DELETE) {
            // Delete tracks
            deleteTrack();
        } else {
            // Position / Orientation logic
            changePositionOrientation();
        }
    }

    // when player releases the right-click mouse button
    private void onEditingFinished() {
        // When drag-dropping a node onto a node, 'merge' the two
        // Do so by connecting all other neighbours of the dragged node to the node
        if (this.getMode() == Mode.POSITION && this.getEditedNodes().size() == 1) {
            TrackWorldStorage tracks = this.plugin.getTracks(this.player.getWorld());
            TrackNode draggedNode = this.getEditedNodes().iterator().next();
            TrackNode droppedNode = null;

            // Get all nodes nearby the position, sorted from close to far
            final Vector pos = draggedNode.getPosition();
            List<TrackNode> nearby = tracks.findNodesNear(new ArrayList<TrackNode>(), pos, 0.3);
            Collections.sort(nearby, new Comparator<TrackNode>() {
                @Override
                public int compare(TrackNode o1, TrackNode o2) {
                    return Double.compare(o1.getPosition().distanceSquared(pos),
                                          o2.getPosition().distanceSquared(pos));
                }
            });

            // Pick first (closest) node that is not the node dragged
            for (TrackNode nearNode : nearby) {
                if (nearNode != draggedNode) {
                    droppedNode = nearNode;
                    break;
                }
            }

            // Merge if found
            if (droppedNode != null) {
                List<TrackNode> connectedNodes = draggedNode.getNeighbours();

                // Check not already connected to the node
                if (!connectedNodes.contains(droppedNode)) {
                    draggedNode.remove();
                    for (TrackNode connected : connectedNodes) {
                        tracks.connect(droppedNode, connected);
                    }
                }
            }
        }
    }

    /**
     * Deletes all selected tracks and selects the track that is connected
     * to the now-deleted tracks. This allows repeated deletion to 'walk' and delete.
     */
    public void deleteTrack() {
        // Backup all nodes to delete, then select all unselected neighbours of those nodes
        HashSet<TrackNode> toDelete = new HashSet<TrackNode>(this.getEditedNodes());
        this.clearEditedNodes();
        for (TrackNode node : toDelete) {
            for (TrackNode neighbour : node.getNeighbours()) {
                if (!toDelete.contains(neighbour)) {
                    this.setEditing(neighbour, true);
                }
            }
        }

        // Delete all nodes to be deleted
        for (TrackNode node : toDelete) {
            node.remove();
        }
    }

    /**
     * Creates a new track node, connecting with already selected track nodes.
     * The created track node is selected to allow chaining create node calls.
     */
    public void createTrack() {
        // For the first click, check if the player is looking exactly at a particular connection or node
        // If so, create a node on this connection or node and switch mode to position adjustment
        // This allows for a kind of click-drag creation design
        // TODO: Find connection
        Location eyeLoc = getPlayer().getEyeLocation();
        TrackWorldStorage tracks = this.plugin.getTracks(this.player.getWorld());
        boolean dragAfterCreate = false;
        Vector pos = null;
        if (this.heldDownTicks == 0) {
            TrackNode lookingAt = findLookingAt();
            if (lookingAt != null) {
                this.clearEditedNodes();
                this.setEditing(lookingAt, true);
                pos = lookingAt.getPosition().clone();
                dragAfterCreate = true;
            }
        }

        if (pos == null) {
            pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
        }

        // First check no track already exists at this position
        if (!dragAfterCreate && !tracks.findNodesNear(new ArrayList<TrackNode>(2), pos, 0.1).isEmpty()) {
            return;
        }

        // Create the node and set as editing
        TrackNode newNode = null;
        if (hasEditedNodes()) {
            for (TrackNode node : getEditedNodes()) {
                if (newNode == null) {
                    newNode = tracks.addNode(node, pos);
                } else {
                    tracks.connect(node, newNode);
                }
            }
        } else {
            newNode = tracks.createNew(pos).getNodes().get(0);
        }
        clearEditedNodes();
        setEditing(newNode, true);

        // If drag mode, switch to POSITION mode and initiate the drag
        if (dragAfterCreate) {
            this.setMode(Mode.POSITION);
            this.editStartPos = eyeLoc;
            this.editRotInfo = this.editStartPos.toVector();
            this.setAfterEditMode(Mode.CREATE);
        }
    }

    public void changePositionOrientation() {
        if (!this.hasEditedNodes()) {
            return;
        }

        // First click?
        if (this.heldDownTicks == 0) {
            this.editStartPos = this.player.getEyeLocation();
            this.editRotInfo = this.editStartPos.toVector();

            TrackNode lookingAt = this.findLookingAt();
            if (lookingAt != null) {
                double distanceTo = lookingAt.getPosition().distance(this.editRotInfo);
                this.editRotInfo.add(this.editStartPos.getDirection().multiply(distanceTo));
            }
        }

        // Calculate the transformation performed as a result of the player 'looking'
        Location currPos = this.player.getEyeLocation();
        Matrix4x4 oldTransform = new Matrix4x4();
        Matrix4x4 newTransform = new Matrix4x4();
        oldTransform.translateRotate(this.editStartPos);
        newTransform.translateRotate(currPos);

        Matrix4x4 changes = new Matrix4x4();
        changes.multiply(newTransform);
        oldTransform.invert();
        changes.multiply(oldTransform);

        if (this.getMode() == Mode.ORIENTATION) {
            changes.transformPoint(this.editRotInfo);
            for (TrackNode node : this.getEditedNodes()) {
                node.setOrientation(this.editRotInfo.clone().subtract(node.getPosition()));
            }
        } else {
            for (TrackNode node : this.getEditedNodes()) {
                Vector v = node.getPosition().clone();
                changes.transformPoint(v);
                node.setPosition(v);
            }
        }

        this.editStartPos = currPos;
    }

    public static enum Mode {
        DISABLED("Disabled (hidden)", 0, 0),
        CREATE("Create Track", 10, 3),
        DELETE("Delete Track", 10, 6),
        POSITION("Change Position", 0, 1),
        ORIENTATION("Change Orientation", 0, 1);

        private final int _autoInterval;
        private final int _autoDelay;
        private final String _name;

        // name: displayed in the UI
        // autoDelay: how many ticks of holding right-click until continuously activating
        // autoInterval: tick interval of activation while holding right-click
        private Mode(String name, int autoDelay, int autoInterval) {
            this._name = name;
            this._autoDelay = autoDelay;
            this._autoInterval = autoInterval;
        }

        public boolean autoActivate(int tick) {
            tick -= this._autoDelay;
            return tick >= 0 && (tick % _autoInterval) == 0;
        }

        public String getName() {
            return this._name;
        }

        public static Mode fromName(String name) {
            for (Mode mode : values()) {
                if (mode.getName().equals(name)) {
                    return mode;
                }
            }
            return CREATE;
        }
    }
}
