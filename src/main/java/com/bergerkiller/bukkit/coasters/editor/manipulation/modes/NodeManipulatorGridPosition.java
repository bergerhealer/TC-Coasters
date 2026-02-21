package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNodeSpacingEqualizer;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulatorBase;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.generated.net.minecraft.world.phys.AxisAlignedBBHandle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Node drag manipulator that alters the position of the edited nodes
 * based on where the player is dragging. The positions are snapped to a half-block grid.
 * When the player is sneaking, the positions along the connection direction are not snapped,
 * allowing for finer control of the node spacing on straight tracks.
 */
public class NodeManipulatorGridPosition extends NodeManipulatorBase<ManipulatedTrackNode> {
    public static final Initializer INITIALIZER = NodeManipulatorGridPosition::new;

    public NodeManipulatorGridPosition(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes);
        for (ManipulatedTrackNode node : manipulatedNodes) {
            node.dragPosition = node.node.getPosition().clone();
        }
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
    }

    @Override
    public void onDragUpdate(NodeDragEvent event) {
        // Check whether the player is moving only a single node or not
        // Count two zero-connected nodes as one node
        final boolean isSingleNode = state.isEditingSingleNode();

        for (ManipulatedTrackNode manipulatedNode : manipulatedNodes) {
            // Get raw drag result (position + orientation) and flags
            NodeDragPosition result = handleDrag(manipulatedNode, event, isSingleNode);

            // If snapping to rails, use the exact position returned (rails take over)
            Vector snapped;
            if (result.snappedToRails) {
                snapped = result.position.clone();
            } else {
                // Snap to half-block grid (nearest 0.5)
                snapped = result.position.clone();
                snapped.setX(Math.round(snapped.getX() * 2.0) / 2.0);
                snapped.setY(Math.round(snapped.getY() * 2.0) / 2.0);
                snapped.setZ(Math.round(snapped.getZ() * 2.0) / 2.0);

                if (result.snappedToBlock) {
                    // If snapping to a block face, preserve the axis component along the
                    // returned orientation vector (so the node can be placed flush to the wall/floor)

                    // orientation is expected to be a face normal (components -1/0/1)
                    if (Math.abs(result.orientation.getX()) > 0.5) {
                        snapped.setX(result.position.getX());
                    }
                    if (Math.abs(result.orientation.getY()) > 0.5) {
                        snapped.setY(result.position.getY());
                    }
                    if (Math.abs(result.orientation.getZ()) > 0.5) {
                        snapped.setZ(result.position.getZ());
                    }
                } else if (manipulatedNodes.size() < 64 /* Avoid huge lag */) {
                    // If not snapped to a block already, see if the position is too close to a solid block
                    // If so, snap to the block face (if not already snapped to rails, which take precedence over blocks)
                    // Positions are axis aligned so the check is actually pretty simple.

                    // Find all solid blocks near to the snapped position (axis-aligned less than a block away)
                    // This is done by computing a 3x3x3 cube of points around the snapped position
                    final Vector snappedFinal = snapped;
                    List<ObstructingBlock> obstructingBlocks = IntStream.rangeClosed(-1, 1).boxed()
                            .flatMap(x -> IntStream.rangeClosed(-1, 1).boxed()
                            .flatMap(y -> IntStream.rangeClosed(-1, 1)
                            .mapToObj(z -> new Vector(x * 0.99, y * 0.99, z * 0.99).add(snappedFinal))))
                            .map(IntVector3::blockOf)
                            .distinct()
                            .map(b -> b.toBlock(state.getBukkitWorld()))
                            .map(ObstructingBlock::of)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!obstructingBlocks.isEmpty()) {
                        // Go by all 6 axis directions and find a path through the obstructing blocks that has the least displacement
                        ObstructingRayResult bestRayResult = Stream.of(FaceUtil.BLOCK_SIDES)
                                .map(f -> rayTraceObstructingBlocks(snappedFinal, f, obstructingBlocks))
                                .filter(Objects::nonNull)
                                .min(Comparator.comparingDouble(a -> a.displacement))
                                .orElse(null);

                        if (bestRayResult != null) {
                            result = new NodeDragPosition(
                                    bestRayResult.position,
                                    FaceUtil.faceToVector(bestRayResult.face),
                                    true,
                                    false
                            );
                            snapped = result.position.clone();
                        }
                    }
                }
            }

            // Apply to node(s)
            manipulatedNode.setPosition(snapped);
            manipulatedNode.setOrientation(result.orientation);
        }
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        if (tryMergeSingleNode(history)) {
            return;
        }

        recordEditedNodesInHistory(history);
    }

    @Override
    public void equalizeNodeSpacing(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.equalizeSpacing(state, history);
    }

    @Override
    public void makeFiner(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeFiner(state, history);
    }

    @Override
    public void makeCourser(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeCourser(state, history);
    }

    private static ObstructingRayResult rayTraceObstructingBlocks(Vector position, BlockFace direction, List<ObstructingBlock> blocks) {
        Vector movingPos = position.clone();
        boolean foundObstructingBlockThisStep;
        boolean foundObstructingBlock = false;
        do {
            foundObstructingBlockThisStep = false;
            for (ObstructingBlock block : blocks) {
                if (!block.containsPoint(movingPos)) {
                    continue;
                }

                // Move into axis direction beyond min/max bounds of the block.
                block.displace(movingPos, direction);
                foundObstructingBlockThisStep = true;
                foundObstructingBlock = true;
                break;
            }
        } while (foundObstructingBlockThisStep);

        if (foundObstructingBlock) {
            double displacement = position.distance(movingPos);
            return new ObstructingRayResult(direction, displacement, movingPos);
        } else {
            return null;
        }
    }

    private static class ObstructingBlock {
        public final Block block;
        public final AxisAlignedBBHandle bbox;

        private ObstructingBlock(Block block, AxisAlignedBBHandle bbox) {
            this.block = block;
            this.bbox = bbox;
        }

        public boolean containsPoint(Vector point) {
            double px = point.getX() - block.getX();
            double py = point.getY() - block.getY();
            double pz = point.getZ() - block.getZ();
            return px > bbox.getMinX() && px < bbox.getMaxX()
                    && py > bbox.getMinY() && py < bbox.getMaxY()
                    && pz > bbox.getMinZ() && pz < bbox.getMaxZ();
        }

        public void displace(Vector position, BlockFace direction) {
            switch (direction) {
                case WEST:
                    position.setX(block.getX() + bbox.getMinX());
                    break;
                case EAST:
                    position.setX(block.getX() + bbox.getMaxX());
                    break;
                case NORTH:
                    position.setZ(block.getZ() + bbox.getMinZ());
                    break;
                case SOUTH:
                    position.setZ(block.getZ() + bbox.getMaxZ());
                    break;
                case DOWN:
                    position.setY(block.getY() + bbox.getMinY());
                    break;
                case UP:
                    position.setY(block.getY() + bbox.getMaxY());
                    break;
            }
        }

        public static ObstructingBlock of(Block block) {
            AxisAlignedBBHandle bbox = BlockUtil.getBoundingBox(block);
            if (bbox == null) {
                return null;
            } else {
                // Include the minimal spacing between block and node that is desired
                bbox = bbox.growUniform(TCCoastersUtil.OFFSET_TO_SIDE);
                return new ObstructingBlock(block, bbox);
            }
        }
    }

    private static class ObstructingRayResult {
        public final BlockFace face;
        public final double displacement;
        public final Vector position;

        public ObstructingRayResult(BlockFace face, double displacement, Vector position) {
            this.face = face;
            this.displacement = displacement;
            this.position = position;
        }
    }
}
