package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.TCCoastersUtil.TargetedBlockInfo;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * The track editing state of a single player.
 * Note that this state is only ever valid for the World the player is on.
 * When the player changed world, any edited nodes are automatically cleared.
 */
public class PlayerEditState implements CoasterWorldAccess {
    private static final int EDIT_AUTO_TIMEOUT = 100;
    private final TCCoasters plugin;
    private final Player player;
    private final PlayerEditInput input;
    private final PlayerEditHistory history;
    private final PlayerEditClipboard clipboard;
    private final Map<TrackNode, PlayerEditNode> editedNodes = new LinkedHashMap<TrackNode, PlayerEditNode>();
    private CoasterWorldAccess cachedCoasterWorld = null;
    private TrackNode lastEdited = null;
    private long lastEditTime = System.currentTimeMillis();
    private Mode editMode = Mode.DISABLED;
    private Mode afterEditMode = null;
    private int heldDownTicks = 0;
    private boolean changed = false;
    private Matrix4x4 editStartTransform = null;
    private Vector editRotInfo = new Vector(); // virtual coordinate of the vector
    private Block targetedBlock = null;
    private BlockFace targetedBlockFace = BlockFace.UP;

    public PlayerEditState(TCCoasters plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.input = new PlayerEditInput(player);
        this.history = new PlayerEditHistory(player);
        this.clipboard = new PlayerEditClipboard(this);
    }

    public void load() {
        FileConfiguration config = this.plugin.getPlayerConfig(this.player);
        if (config.exists()) {
            config.load();

            this.editMode = config.get("mode", Mode.DISABLED);
            this.editedNodes.clear();
            List<String> editNodePositions = config.getList("editedNodes", String.class);
            if (editNodePositions != null && !editNodePositions.isEmpty()) {
                for (String nodeStr : editNodePositions) {
                    String[] coords = nodeStr.split("_");
                    if (coords.length == 3) {
                        try {
                            double x, y, z;
                            x = Double.parseDouble(coords[0]);
                            y = Double.parseDouble(coords[1]);
                            z = Double.parseDouble(coords[2]);
                            TrackNode node = getTracks().findNodeExact(new Vector(x, y, z));
                            if (node != null) {
                                this.editedNodes.put(node, new PlayerEditNode(node));
                            }
                        } catch (NumberFormatException ex) {}
                    }
                }
            }
        }
        this.changed = false;
    }

    public void save() {
        if (!this.changed) {
            return;
        }
        this.changed = false;
        FileConfiguration config = this.plugin.getPlayerConfig(this.player);
        config.set("mode", this.editMode);
        List<String> editedNodeNames = new ArrayList<String>(this.editedNodes.size());
        for (TrackNode node : this.getEditedNodes()) {
            Vector p = node.getPosition();
            editedNodeNames.add(p.getX() + "_" + p.getY() + "_" + p.getZ());
        }
        config.set("editedNodes", editedNodeNames);
        config.save();
    }

    public Player getPlayer() {
        return this.player;
    }

    public PlayerEditHistory getHistory() {
        return this.history;
    }

    public PlayerEditClipboard getClipboard() {
        return this.clipboard;
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
            ArrayList<TrackNode> oldNodes = new ArrayList<TrackNode>(this.getEditedNodes());
            this.editedNodes.clear();
            this.lastEdited = null;
            this.changed = true;
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

    public boolean isMode(Mode... modes) {
        return LogicUtil.contains(this.editMode, modes);
    }

    public Set<TrackNode> getEditedNodes() {
        return this.editedNodes.keySet();
    }

    /**
     * Gets a set of all the coasters of which a node is being edited
     * 
     * @return edited coasters
     */
    public Set<TrackCoaster> getEditedCoasters() {
        HashSet<TrackCoaster> coasters = new HashSet<TrackCoaster>();
        for (TrackNode node : getEditedNodes()) {
            coasters.add(node.getCoaster());
        }
        return coasters;
    }

    /**
     * Gets the last node that was clicked by the player to be edited
     * 
     * @return last edited node, null if no nodes are being edited.
     */
    public TrackNode getLastEditedNode() {
        TrackNode last = null;
        for (TrackNode node : this.editedNodes.keySet()) {
            last = node;
        }
        return last;
    }

    public boolean hasEditedNodes() {
        return !this.editedNodes.isEmpty();
    }

    /**
     * Deselects all nodes that we cannot actually edit, because the coaster they
     * are part of is locked.
     * 
     * @return True if nodes were deselected
     */
    public boolean deselectLockedNodes() {
        boolean hadLockedNodes = false;
        Iterator<TrackNode> iter = this.editedNodes.keySet().iterator();
        while (iter.hasNext()) {
            TrackNode node = iter.next();
            if (node.isLocked()) {
                iter.remove();
                hadLockedNodes = true;
                node.onStateUpdated(this.player);
                this.lastEdited = node;
            }
        }
        if (hadLockedNodes) {
            this.lastEditTime = System.currentTimeMillis();
            this.changed = true;
            this.player.sendMessage(ChatColor.RED + "This coaster is locked!");
        }
        return hadLockedNodes;
    }

    public void setMode(Mode mode) {
        this.afterEditMode = null;
        if (this.editMode != mode) {
            this.editMode = mode;
            this.changed = true;
            for (TrackNode node : this.getEditedNodes()) {
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
        if (node == null) {
            throw new IllegalArgumentException("Node can not be null");
        }
        boolean changed;
        if (editing) {
            if (this.editedNodes.containsKey(node)) {
                changed = false;
            } else {
                changed = true;
                this.editedNodes.put(node, new PlayerEditNode(node));
            }
        } else {
            changed = (this.editedNodes.remove(node) != null);
        }
        if (changed) {
            node.onStateUpdated(this.player);
            this.lastEdited = node;
            this.lastEditTime = System.currentTimeMillis();
            this.changed = true;
        }
    }

    public boolean isEditing(TrackNode node) {
        return this.editedNodes.containsKey(node);
    }

    /**
     * Gets whether the player is sneaking (holding shift), indicating special modifiers are active.
     * Also checks whether the player is holding down the same key while holding the editor map.
     * 
     * @return True if sneaking
     */
    public boolean isSneaking() {
        if (this.player.isSneaking()) {
            return true;
        }
        TCCoastersDisplay display = MapDisplay.getHeldDisplay(this.player, TCCoastersDisplay.class);
        if (display != null && display.getInput(this.player).isPressed(MapPlayerInput.Key.BACK)) {
            return true;
        }
        return false;
    }

    public boolean onLeftClick() {
        // When holding right-click and clicking left-click in position mode, create a new node and drag that
        if (this.getMode() == Mode.POSITION && this.isHoldingRightClick()) {
            Vector pos;
            if (this.getEditedNodes().size() == 1) {
                pos = this.getEditedNodes().iterator().next().getPosition();
            } else {
                pos = this.getNewNodePos();
            }
            this.createNewNode(pos, null, false);
            return true;
        }

        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(player.getEyeLocation());
        cameraTransform.invert();

        // Go by all track nodes on the server, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        TrackNode bestNode = null;
        TrackConnection bestJunction = null;
        double bestDistance = Double.MAX_VALUE;

        for (TrackCoaster coaster : getCoasterWorld().getTracks().getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                // Node itself
                {
                    double distance = node.getViewDistance(cameraTransform);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestNode = node;
                        bestJunction = null;
                    }
                }

                // If node has multiple junctions, check if the player is clicking on any of the junction nodes
                // This follows very precise rules
                if (node.getConnections().size() > 2) {
                    for (TrackConnection conn : node.getConnections()) {
                        double distance = node.getJunctionViewDistance(cameraTransform, conn);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestNode = node;
                            bestJunction = conn;
                        }
                    }
                }
            }
        }

        if (bestNode == null) {
            if (!this.isSneaking()) {
                clearEditedNodes();
            }
            return false;
        }

        // Switch junction when clicking on one
        if (bestJunction != null) {
            bestNode.switchJunction(bestJunction);
            return true;
        }

        long lastEditTime = getLastEditTime(bestNode);
        if (lastEditTime > 300) {
            // Single node selection mode
            if (!this.isSneaking()) {
                clearEditedNodes();
            }

            // Toggle edit state
            setEditing(bestNode, !isEditing(bestNode));

        } else if (isEditing(bestNode)) {
            // Mass-selection mode
            if (this.isSneaking()) {
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

    public void setTargetedBlock(Block clickedBlock, BlockFace clickedFace) {
        this.targetedBlock = clickedBlock;
        this.targetedBlockFace = clickedFace;
    }

    public boolean onRightClick() {
        this.input.click();
        return true;
    }

    public boolean isHoldingRightClick() {
        return this.input.hasInput() && this.plugin.isHoldingEditTool(this.player);
    }

    public void update() {
        if (this.isHoldingRightClick()) {
            this.input.update();
            this.changed = true;
            if (this.input.heldDuration() >= EDIT_AUTO_TIMEOUT) {
                if (this.heldDownTicks == 0 || this.editMode.autoActivate(this.heldDownTicks)) {
                    this.updateEditing();
                }
                this.heldDownTicks++;
            }
        } else {
            if (this.heldDownTicks > 0) {
                this.onEditingFinished();
                this.heldDownTicks = 0;
                this.targetedBlock = null;
            }
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
        } else if (this.editMode == Mode.RAILS) {
            // Set rails block to the block clicked
            setRailBlock();
        } else {
            // Position / Orientation logic
            changePositionOrientation();
        }
    }

    /**
     * Deletes all selected tracks and selects the track that is connected
     * to the now-deleted tracks. This allows repeated deletion to 'walk' and delete.
     */
    public void deleteTrack() {
        // Deselect nodes we cannot delete or modify
        this.deselectLockedNodes();

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // Defensive copy
        HashSet<TrackNode> toDelete = new HashSet<TrackNode>(this.getEditedNodes());

        // Disconnect nodes when adjacent nodes are selected
        boolean disconnectedNodes = false;
        for (TrackNode node : toDelete) {
            for (TrackNode neigh : node.getNeighbours()) {
                if (toDelete.contains(neigh)) {
                    node.getTracks().disconnect(node, neigh);
                    changes.addChangeDisconnect(node, neigh);
                    disconnectedNodes = true;
                }
            }
        }
        if (disconnectedNodes) {
            // Clean up empty nodes
            for (TrackNode node : toDelete) {
                if (node.getConnections().isEmpty()) {
                    this.setEditing(node, false);
                    changes.addChangeDeleteNode(node);
                    node.remove();
                }
            }
            return;
        }

        // Backup all nodes to delete, then select all unselected neighbours of those nodes
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
            changes.addChangeDeleteNode(node);
            node.remove();
        }
    }

    /**
     * Resets the rails blocks of all selected nodes, causing them to be
     * the same as the position of the rails node itself.
     */
    public void resetRailsBlocks() {
        setRailBlock(null);
    }

    /**
     * Sets the rails block to what the player last right-clicked,
     * or otherwise to where the player is looking.
     */
    public void setRailBlock() {
        // New rails block to use
        IntVector3 new_rail;
        if (this.targetedBlock != null) {
            new_rail = new IntVector3(this.targetedBlock);
        } else {
            Location eyeLoc = this.player.getEyeLocation();
            Vector pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(1.5));
            new_rail = new IntVector3(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        }
        setRailBlock(new_rail);
    }

    /**
     * Sets the rails block to the rail block coordinates specified
     * 
     * @param new_rail to set to, null to reset
     */
    public void setRailBlock(IntVector3 new_rail) {
        // Deselect nodes we cannot edit
        this.deselectLockedNodes();

        // Reset
        if (new_rail == null) {
            HistoryChange changes = null;
            for (TrackNode node : this.getEditedNodes()) {
                if (node.getRailBlock(false) != null) {
                    if (changes == null) {
                        changes = this.getHistory().addChangeGroup();
                    }
                    changes.addChangeSetRail(node, null);
                    node.setRailBlock(null);
                }
            }
            return;
        }

        TrackNode lastEdited = this.getLastEditedNode();
        if (lastEdited == null) {
            return;
        }

        // Simplified when its just one node
        if (this.editedNodes.size() == 1) {
            IntVector3 old_rail = lastEdited.getRailBlock(false);
            if (!LogicUtil.bothNullOrEqual(old_rail, new_rail)) {
                this.getHistory().addChangeSetRail(lastEdited, new_rail);
                lastEdited.setRailBlock(new_rail);
            }
            return;
        }

        // Use last selected node as a basis for the movement
        IntVector3 diff = new_rail.subtract(lastEdited.getRailBlock(true));
        if (diff.equals(IntVector3.ZERO)) {
            return; // No changes
        }

        // Offset all edited nodes by diff
        HistoryChange changes = this.getHistory().addChangeGroup();
        for (TrackNode node : this.getEditedNodes()) {
            IntVector3 node_new_rail = node.getRailBlock(true).add(diff);
            changes.addChangeSetRail(node, node_new_rail);
            node.setRailBlock(node_new_rail);
        }
    }

    /**
     * Sets the orientation for all selected nodes
     * 
     * @param orientation vector to set to
     */
    public void setOrientation(Vector orientation) {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        // Apply to all nodes
        HistoryChange changes = null;
        for (TrackNode node : this.getEditedNodes()) {
            TrackNodeState startState = node.getState();
            node.setOrientation(orientation);

            if (changes == null) {
                changes = this.getHistory().addChangeGroup();
            }
            changes.addChangePostMoveNode(node, startState);
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
        TrackWorld tracks = getTracks();
        Location eyeLoc = getPlayer().getEyeLocation();
        boolean dragAfterCreate = false;
        Vector pos = null;
        Vector ori = null;
        if (this.heldDownTicks == 0) {
            TrackNode lookingAt = findLookingAt();
            if (lookingAt != null) {
                this.clearEditedNodes();
                this.setEditing(lookingAt, true);
                pos = lookingAt.getPosition().clone();

                // Node: introduce a tiny offset to pos, otherwise
                // the node can not be properly looked up again during undo/redo
                pos.setY(pos.getY() + 1e-5);

                dragAfterCreate = true;
            }
        }

        // If targeting a block face, create a node on top of this face
        if (this.targetedBlock != null) {
            // If the targeted block is a rails block, connect to one end of it
            RailType clickedRailType = RailType.getType(this.targetedBlock);
            if (clickedRailType != RailType.NONE) {
                // Load the path for the rails clicked
                RailState state = new RailState();
                state.setRailBlock(this.targetedBlock);
                state.setRailType(clickedRailType);
                state.position().setLocation(clickedRailType.getSpawnLocation(this.targetedBlock, this.targetedBlockFace));
                state.position().setMotion(this.targetedBlockFace);
                state.initEnterDirection();
                RailPath path = state.loadRailLogic().getPath();

                // Select the best possible path end position
                RailPath.Position p1 = path.getStartPosition();
                RailPath.Position p2 = path.getEndPosition();
                p1.makeAbsolute(this.targetedBlock);
                p2.makeAbsolute(this.targetedBlock);
                RailPath.Position closest = (p1.distance(eyeLoc) < p2.distance(eyeLoc)) ? p1 : p2;
                pos = new Vector(closest.posX, closest.posY, closest.posZ);
            } else {
                // Find the spot on the block that was actually clicked
                TargetedBlockInfo info = TCCoastersUtil.rayTrace(this.player);
                if (info != null) {
                    // Find position (and correct clicked face if needed)
                    ori = FaceUtil.faceToVector(info.face);
                    pos = new Vector(info.block.getX() + info.position.getX(),
                                     info.block.getY() + info.position.getY(),
                                     info.block.getZ() + info.position.getZ());
                } else {
                    // Fallback
                    double f = 0.5 + TCCoastersUtil.OFFSET_TO_SIDE;
                    ori = FaceUtil.faceToVector(this.targetedBlockFace);
                    pos = new Vector(this.targetedBlock.getX() + 0.5 + f * this.targetedBlockFace.getModX(),
                                     this.targetedBlock.getY() + 0.5 + f * this.targetedBlockFace.getModY(),
                                     this.targetedBlock.getZ() + 0.5 + f * this.targetedBlockFace.getModZ());
                }
            }
        }

        // Create in front of the player by default
        boolean inAir = false;
        if (pos == null) {
            pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
            inAir = true;
        }

        // First check no track already exists at this position
        if (!dragAfterCreate && !tracks.findNodesNear(new ArrayList<TrackNode>(2), pos, 0.1).isEmpty()) {
            return;
        }

        // Create the node and set as editing
        createNewNode(pos, ori, inAir);

        // If drag mode, switch to POSITION mode and initiate the drag
        if (dragAfterCreate) {
            this.setMode(Mode.POSITION);
            this.editStartTransform = this.input.get().clone();
            this.editRotInfo = this.editStartTransform.toVector();
            this.setAfterEditMode(Mode.CREATE);
        }
    }

    private Vector getNewNodePos() {
        Location eyeLoc = getPlayer().getEyeLocation();
        return eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
    }

    private void createNewNode(Vector pos, Vector ori, boolean inAir) {
        TrackWorld tracks = getTracks();

        // Deselect nodes that are locked, to prevent modification
        this.deselectLockedNodes();

        // Not editing any new nodes, create a completely new coaster and node
        if (!this.hasEditedNodes()) {
            TrackNode newNode = tracks.createNew(pos).getNodes().get(0);
            if (ori != null) {
                newNode.setOrientation(ori);
            }
            this.getHistory().addChangeCreateNode(newNode);
            setEditing(newNode, true);
            return;
        }

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // First, find all track connections that have been selected thanks
        // to both nodes being selected for editing right now.
        HashSet<TrackConnection> selectedConnections = new HashSet<TrackConnection>();
        for (TrackNode node : getEditedNodes()) {
            for (TrackConnection conn : node.getConnections()) {
                if (this.getEditedNodes().contains(conn.getOtherNode(node))) {
                    selectedConnections.add(conn);
                }
            }
        }

        // Insert a new node in the middle of all selected connections
        // Select these created nodes
        List<TrackNode> createdConnNodes = new ArrayList<TrackNode>();
        for (TrackConnection conn : selectedConnections) {
            Vector newNodePos = conn.getPath().getPosition(0.5);
            Vector newNodeOri = conn.getOrientation(0.5);

            this.setEditing(conn.getNodeA(), false);
            this.setEditing(conn.getNodeB(), false);

            changes.addChangeDisconnect(conn);
            conn.remove();

            TrackNode newNode = conn.getNodeA().getCoaster().createNewNode(newNodePos, newNodeOri);
            changes.addChangeCreateNode(newNode);

            changes.addChangeConnect(tracks.connect(conn.getNodeA(), newNode));
            changes.addChangeConnect(tracks.connect(newNode, conn.getNodeB()));
            createdConnNodes.add(newNode);
        }

        // When clicking on a block or when only having one node selected, create a new node
        // When more than one node is selected and the air is clicked, simply connect the selected nodes together
        if (this.editedNodes.size() >= 2 && inAir) {
            // Connect all remaining edited nodes together in the most logical fashion
            // From previous logic we already know these nodes do not share connections among them
            // This means a single 'line' must be created that connects all nodes together
            // While we can figure this out using a clever algorithm, the nodes are already sorted in the
            // order the player clicked them. Let's give the player some control back, and connect them in the
            // same order.
            TrackNode prevNode = null;
            for (TrackNode node : getEditedNodes()) {
                if (prevNode != null) {
                    tracks.connect(prevNode, node);
                    changes.addChangeConnect(prevNode, node);
                }
                prevNode = node;
            }
        } else {
            // Add a new node connecting to existing selected nodes
            TrackNode newNode = null;
            for (TrackNode node : getEditedNodes()) {
                if (newNode == null) {
                    newNode = tracks.addNode(node, pos);
                    if (ori != null) {
                        newNode.setOrientation(ori);
                    }
                    changes.addChangeCreateNode(newNode);
                    changes.addChangeConnect(newNode, node);
                } else {
                    tracks.connect(node, newNode);
                    changes.addChangeConnect(newNode, node);
                }
            }
            clearEditedNodes();
            if (newNode != null) {
                setEditing(newNode, true);
            }
        }

        // Set all new nodes we created as editing, too.
        for (TrackNode node : createdConnNodes) {
            setEditing(node, true);
        }
    }

    public void changePositionOrientation() {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        if (!this.hasEditedNodes()) {
            return;
        }

        // Get current input
        Matrix4x4 current = this.input.get();

        // First click?
        if (this.heldDownTicks == 0) {
            this.editStartTransform = current.clone();
            this.editRotInfo = this.editStartTransform.toVector();

            // Store initial positions
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                editNode.dragPosition = editNode.node.getPosition().clone();
            }

            // Is used to properly alter the orientation of a node looked at
            TrackNode lookingAt = this.findLookingAt();
            if (lookingAt != null) {
                Vector forward = this.editStartTransform.getRotation().forwardVector();
                double distanceTo = lookingAt.getPosition().distance(this.editRotInfo);
                this.editRotInfo.add(forward.multiply(distanceTo));
            }
        }

        // Ensure moveBegin() is called once
        for (PlayerEditNode editNode : this.editedNodes.values()) {
            editNode.moveBegin();
        }

        // Calculate the transformation performed as a result of the player 'looking'
        Matrix4x4 changes = new Matrix4x4();
        changes.multiply(current);

        { // Divide
            Matrix4x4 m = this.editStartTransform.clone();
            m.invert();
            changes.multiply(m);
        }

        if (this.getMode() == Mode.ORIENTATION) {
            changes.transformPoint(this.editRotInfo);
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                editNode.node.setOrientation(this.editRotInfo.clone().subtract(editNode.node.getPosition()));
            }
        } else {
            Vector eyePos = this.player.getEyeLocation().toVector();
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                // Recover null
                if (editNode.dragPosition == null) {
                    editNode.dragPosition = editNode.node.getPosition().clone();
                }

                // Transform position
                changes.transformPoint(editNode.dragPosition);
                Vector position = editNode.dragPosition.clone();
                Vector orientation = editNode.startState.orientation.clone();

                // Snap position against the side of a block
                // Then, look for other rails blocks and attach to it
                // When sneaking, disable this functionality
                // When more than 1 node is selected, only do this for nodes with 1 or less connections
                // This is to avoid severe performance problems when moving a lot of track at once
                if (!this.isSneaking() && (this.editedNodes.size() == 1 || editNode.node.getConnections().size() <= 1)) {
                    TCCoastersUtil.snapToBlock(getWorld(), eyePos, position, orientation);

                    if (TCCoastersUtil.snapToCoasterRails(editNode.node, position, orientation)) {
                        // Play particle effects to indicate we are snapping to the coaster rails
                        PlayerUtil.spawnDustParticles(this.player, position, Color.RED);
                    } else if (TCCoastersUtil.snapToRails(getWorld(), editNode.node.getRailBlock(true), position, orientation)) {
                        // Play particle effects to indicate we are snapping to the rails
                        PlayerUtil.spawnDustParticles(this.player, position, Color.PURPLE);
                    }
                }

                // Apply to node
                editNode.node.setPosition(position);
                editNode.node.setOrientation(orientation);
            }
        }

        this.editStartTransform = current.clone();
    }

    // when player releases the right-click mouse button
    private void onEditingFinished() {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        // When drag-dropping a node onto a node, 'merge' the two
        // Do so by connecting all other neighbours of the dragged node to the node
        if (this.getMode() == Mode.POSITION && this.getEditedNodes().size() == 1) {
            TrackWorld tracks = this.getTracks();
            PlayerEditNode draggedNode = this.editedNodes.values().iterator().next();
            TrackNode droppedNode = null;

            // Get all nodes nearby the position, sorted from close to far
            final Vector pos = draggedNode.node.getPosition();
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
                if (nearNode != draggedNode.node) {
                    droppedNode = nearNode;
                    break;
                }
            }

            // Merge if found
            if (droppedNode != null) {
                List<TrackNode> connectedNodes = draggedNode.node.getNeighbours();

                // Track all the changes we are doing down below.
                HistoryChange changes = this.getHistory().addChangePostMoveNode(
                        draggedNode.node, draggedNode.startState);

                // Delete dragged node
                changes.addChangeDeleteNode(draggedNode.node);
                draggedNode.node.remove();

                // Connect all that was connected to it, with the one dropped on
                for (TrackNode connected : connectedNodes) {
                    if (connected != droppedNode) {
                        changes.addChangeConnect(droppedNode, connected);
                        tracks.connect(droppedNode, connected);
                    }
                }

                // Do not do the standard position/orientation change saving down below
                draggedNode.moveEnd();
                return;
            }
        }

        // For position/orientation, store the changes
        if (this.isMode(Mode.POSITION, Mode.ORIENTATION)) {
            HistoryChange changes = this.getHistory().addChangeGroup();
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                changes.addChangePostMoveNode(editNode.node, editNode.startState);
                editNode.moveEnd();
            }
        }
    }

    public static enum Mode {
        DISABLED("Disabled (hidden)", 0, 1),
        CREATE("Create Track", 20, 4),
        POSITION("Change Position", 0, 1),
        ORIENTATION("Change Orientation", 0, 1),
        RAILS("Change Rails Block", 0, 1),
        DELETE("Delete Track", 10, 3);

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
            return tick >= 0 && (_autoInterval <= 0 || (tick % _autoInterval) == 0);
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

    // CoasterWorldAccess

    /**
     * Gets the coaster world the player is currently on
     * 
     * @return coaster world
     */
    public CoasterWorldAccess getCoasterWorld() {
        World bukkitWorld = this.player.getWorld();
        if (this.cachedCoasterWorld == null || this.cachedCoasterWorld.getWorld() != bukkitWorld) {
            this.cachedCoasterWorld = this.plugin.getCoasterWorld(bukkitWorld);
        }
        return this.cachedCoasterWorld;
    }

    @Override
    public TCCoasters getPlugin() {
        return this.plugin;
    }

    @Override
    public World getWorld() {
        return this.player.getWorld();
    }

    @Override
    public TrackWorld getTracks() {
        return this.getCoasterWorld().getTracks();
    }

    @Override
    public TrackParticleWorld getParticles() {
        return this.getCoasterWorld().getParticles();
    }

    @Override
    public TrackRailsWorld getRails() {
        return this.getCoasterWorld().getRails();
    }
}
