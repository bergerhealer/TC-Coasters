package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Node drag manipulator that alters the position of the edited nodes
 * based on where the player is dragging.
 */
public class NodeDragManipulatorPosition extends NodeDragManipulatorBase {
    @Override
    public void onStarted(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) {
        for (PlayerEditNode node : editedNodes) {
            node.dragPosition = node.node.getPosition().clone();
        }
    }

    @Override
    public void onUpdate(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) {
        // Check whether the player is moving only a single node or not
        // Count two zero-connected nodes as one node
        final boolean isSingleNode = state.isEditingSingleNode();

        Player player = state.getPlayer();
        Vector eyePos = player.getEyeLocation().toVector();
        for (PlayerEditNode editNode : editedNodes) {
            // Recover null
            if (editNode.dragPosition == null) {
                editNode.dragPosition = editNode.node.getPosition().clone();
            }

            // Transform position and compute direction using player view position relative to the node
            event.change().transformPoint(editNode.dragPosition);
            Vector position = editNode.dragPosition.clone();
            Vector orientation = editNode.startState.orientation.clone();
            Vector direction = position.clone().subtract(player.getEyeLocation().toVector()).normalize();
            if (Double.isNaN(direction.getX())) {
                direction = player.getEyeLocation().getDirection();
            }

            // Snap position against the side of a block
            // Then, look for other rails blocks and attach to it
            // When sneaking, disable this functionality
            // When more than 1 node is selected, only do this for nodes with 1 or less connections
            // This is to avoid severe performance problems when moving a lot of track at once
            if (!state.isSneaking() && (isSingleNode || editNode.node.getConnections().size() <= 1)) {
                TCCoastersUtil.snapToBlock(state.getBukkitWorld(), eyePos, position, orientation);

                if (TCCoastersUtil.snapToCoasterRails(editNode.node, position, orientation, n -> !state.isEditing(n))) {
                    // Play particle effects to indicate we are snapping to the coaster rails
                    PlayerUtil.spawnDustParticles(player, position, Color.RED);
                } else if (TCCoastersUtil.snapToRails(state.getBukkitWorld(), editNode.node.getRailBlock(true), position, direction, orientation)) {
                    // Play particle effects to indicate we are snapping to the rails
                    PlayerUtil.spawnDustParticles(player, position, Color.PURPLE);
                }
            }

            // Apply to node
            editNode.node.setPosition(position);
            editNode.node.setOrientation(orientation);
        }
    }

    @Override
    public void onFinished(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) throws ChangeCancelledException {
        if (tryMergeSingleNode(state, history, editedNodes)) {
            return;
        }

        recordEditedNodesInHistory(state, history, editedNodes);
    }
}
