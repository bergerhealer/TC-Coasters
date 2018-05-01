package com.bergerkiller.bukkit.coasters.meta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for finding the shortest path from a node to another selected node
 */
public class TrackNodeSearchPath {
    public final HashSet<TrackNode> path = new HashSet<TrackNode>();
    public TrackNode current = null;
    public double cost = 0.0;
    public boolean foundSelection = false;
    public boolean foundEnd = false;

    public TrackNodeSearchPath(TrackNodeSearchPath base) {
        this.path.addAll(base.path);
        this.current = base.current;
        this.cost = base.cost;
        this.foundSelection = base.foundSelection;
        this.foundEnd = base.foundEnd;
    }

    public TrackNodeSearchPath(TrackNode startNode) {
        this.current = startNode;
        this.path.add(startNode);
    }

    public void next(TrackNode node) {
        this.path.add(node);
        this.cost += node.getPosition().distanceSquared(this.current.getPosition());
        this.current = node;
    }

    public static TrackNodeSearchPath findShortest(TrackNode startNode, Set<TrackNode> selected) {
        // Recursion using a list of paths (to avoid stack overflow)
        // 'Tick' each list once to move forwards
        // Drop/disable lists when their cost exceeds that of a path with a found other node
        // This avoids iterating more nodes than absolutely required
        List<TrackNodeSearchPath> paths = new ArrayList<TrackNodeSearchPath>();
        paths.add(new TrackNodeSearchPath(startNode));
        TrackNodeSearchPath bestPath = null;
        boolean finished = false;
        while (!finished) {
            finished = true;
            for (int i = 0; i < paths.size(); i++) {
                TrackNodeSearchPath path = paths.get(i);
                if (path.foundEnd) {
                    continue;
                }

                // Stop working on branches exceeding the best cost we've already found
                if (bestPath != null && path.cost >= bestPath.cost) {
                    path.foundEnd = true;
                    continue;
                }

                // Still got more to do - reset finished flag
                finished = false;

                // Find the next node in the path to look at
                TrackNode nextNode = null;
                for (TrackNode neighbour : path.current.getNeighbours()) {
                    if (path.path.contains(neighbour)) {
                        continue; // already traversed / we got from there
                    }

                    // Continue from next node. But if this is a splice into multiple paths,
                    // add additional node paths for all of those.
                    if (nextNode == null) {
                        nextNode = neighbour;
                    } else {
                        // Create a new branch, cloning this one as a base
                        TrackNodeSearchPath branch = new TrackNodeSearchPath(path);
                        branch.next(neighbour);
                        if (selected.contains(neighbour)) {
                            branch.foundEnd = branch.foundSelection = true;
                            if (bestPath == null || bestPath.cost > branch.cost) {
                                bestPath = branch;
                            }
                        }
                        paths.add(branch);
                    }
                }
                
                if (nextNode == null) {
                    path.foundEnd = true;
                } else {
                    path.next(nextNode);
                    if (selected.contains(nextNode)) {
                        path.foundEnd = path.foundSelection = true;
                        if (bestPath == null || bestPath.cost > path.cost) {
                            bestPath = path;
                        }
                    }
                }
            }
        }

        return bestPath;
    }
}
