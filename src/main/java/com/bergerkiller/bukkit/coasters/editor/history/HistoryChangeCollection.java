package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Base class that makes it easier to create and add new changes
 */
public abstract class HistoryChangeCollection {

    /**
     * Adds a change to be performed after this change is performed ({@link #redo()}).
     * When undoing, the child changes are performed in reverse order beforehand.
     * 
     * @param change to add
     * @return change added
     */
    public abstract HistoryChange addChange(HistoryChange change);

    public final HistoryChange addChangeGroup() {
        return addChange(new HistoryChangeGroup());
    }

    public final HistoryChange addChangeConnect(TrackConnection connection) {
        return addChangeConnect(connection.getNodeA(), connection.getNodeB());
    }

    public final HistoryChange addChangeConnect(TrackNode nodeA, TrackNode nodeB) {
        return addChange(new HistoryChangeConnect(nodeA, nodeB));
    }

    public final HistoryChange addChangeDisconnect(TrackConnection connection) {
        return addChangeDisconnect(connection.getNodeA(), connection.getNodeB());
    }

    public final HistoryChange addChangeDisconnect(TrackNode nodeA, TrackNode nodeB) {
        return addChange(new HistoryChangeDisconnect(nodeA, nodeB));
    }

    public final HistoryChange addChangePostMoveNode(TrackNode node, Vector startPosition, Vector startUp) {
        return addChange(new HistoryChangeMoveNode(node, startPosition, startUp, node.getPosition(), node.getOrientation()));
    }

    public final HistoryChange addChangeCreateNode(TrackNode node) {
        return addChange(new HistoryChangeCreateNode(node));
    }

    /**
     * Adds the changes required when deleting a node. Also adds all changes to do
     * with disconnecting (and re-connecting) neighbour connections
     * 
     * @param node
     * @return change
     */
    public final HistoryChange addChangeDeleteNode(TrackNode node) {
        List<TrackNode> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            return addChange(new HistoryChangeDeleteNode(node));
        }
        HistoryChange changes = this.addChangeGroup();
        for (TrackNode neighbour : neighbours) {
            changes.addChangeDisconnect(node, neighbour);
        }
        changes.addChange(new HistoryChangeDeleteNode(node));
        return changes;
    }
}
