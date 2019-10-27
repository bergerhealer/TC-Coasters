package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.List;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.events.CoasterAfterChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterDeleteConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterDeleteNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

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

    /**
     * Removes a change from this collection of changes, making it as if the change
     * never happened.
     * 
     * @param change to remove
     */
    public abstract void removeChange(HistoryChange change);

    public final HistoryChange addChangeGroup() {
        return addChange(new HistoryChangeGroup());
    }

    private <T extends CoasterEvent> T handleEvent(T event) throws ChangeCancelledException {
        CommonUtil.callEvent(event);
        if (event.isCancelled()) {
            throw new ChangeCancelledException();
        }
        return event;
    }

    public final HistoryChange addChangeConnect(Player who, TrackNode nodeA, TrackNode nodeB) throws ChangeCancelledException {
        for (TrackConnection connection : nodeA.getConnections()) {
            if (connection.getOtherNode(nodeA) == nodeB) {
                return addChangeConnect(who, connection);
            }
        }
        throw new IllegalStateException("Trying to handle a connect change for a connection that does not exist");
    }

    public final HistoryChange addChangeConnect(Player who, TrackConnection connection) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterCreateConnectionEvent(who, connection));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            connection.remove();
            throw ex;
        }
        return addChange(new HistoryChangeConnect(connection.getNodeA(), connection.getNodeB()));
    }

    public final HistoryChange addChangeDisconnect(Player who, TrackConnection connection) throws ChangeCancelledException {
        handleEvent(new CoasterDeleteConnectionEvent(who, connection));
        return addChange(new HistoryChangeDisconnect(connection.getNodeA(), connection.getNodeB()));
    }

    public final HistoryChange addChangeAfterChangingNode(Player who, TrackNode node, TrackNodeState startState) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterAfterChangeNodeEvent(who, node, startState));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            node.setPosition(startState.position);
            node.setOrientation(startState.orientation);
            node.setRailBlock(startState.railBlock);
            throw ex;
        }

        return addChange(new HistoryChangeNode(node, startState, node.getState()));
    }

    public final HistoryChange addChangeCreateNode(Player who, TrackNode node) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterCreateNodeEvent(who, node));
        } catch (ChangeCancelledException ex) {
            node.remove();
            throw ex;
        }
        return addChange(new HistoryChangeCreateNode(node));
    }

    public final HistoryChange addChangeBeforeSetRail(Player who, TrackNode node, IntVector3 new_rail) throws ChangeCancelledException {
        handleEvent(new CoasterBeforeChangeNodeEvent(who, node));
        TrackNodeState old_state = node.getState();
        TrackNodeState new_state = old_state.changeRail(new_rail);
        return addChange(new HistoryChangeNode(node, old_state, new_state));
    }

    public void handleChangeBefore(Player who, TrackNode node) throws ChangeCancelledException {
        handleEvent(new CoasterBeforeChangeNodeEvent(who, node));
    }

    public void handleChangeAfterSetRail(Player who, TrackNode node, IntVector3 old_rail) throws ChangeCancelledException {
        TrackNodeState old_state = node.getState().changeRail(old_rail);
        handleEvent(new CoasterAfterChangeNodeEvent(who, node, old_state));
    }

    /**
     * Adds the changes required when deleting a node. Also adds all changes to do
     * with disconnecting (and re-connecting) neighbour connections
     * 
     * @param who   The player that wants to delete a node
     * @param node  The node being deleted
     * @return change
     */
    public final HistoryChange addChangeDeleteNode(Player who, TrackNode node) throws ChangeCancelledException {
        handleEvent(new CoasterDeleteNodeEvent(who, node));

        List<TrackConnection> connections = node.getConnections();
        if (connections.isEmpty()) {
            return addChange(new HistoryChangeDeleteNode(node));
        } else {
            HistoryChange changes = this.addChangeGroup();
            for (TrackConnection connection : connections) {
                changes.addChangeDisconnect(who, connection);
            }
            changes.addChange(new HistoryChangeDeleteNode(node));
            return changes;
        }
    }
}
