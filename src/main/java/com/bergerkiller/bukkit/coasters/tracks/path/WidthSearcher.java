package com.bergerkiller.bukkit.coasters.tracks.path;

import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Helper class for searching the two points on a path equal distance from
 * a start node that have a requested width between them
 */
public class WidthSearcher {
    private static final double PRECISION = 1e-4;
    public static final WidthSearcher INSTANCE = new WidthSearcher();
    public final Point pointA = new Point();
    public final Point pointB = new Point();
    public TrackConnection connection;
    public double distance;

    public WidthSearcher init(TrackConnection connection, double distance) {
        this.connection = connection;
        this.distance = distance;
        pointA.init(connection, distance);
        pointB.init(connection, distance);
        return this;
    }

    public void search(double width) {
        // Moves the two points from 0.5*width to width (limit) to find width distance apart
        // Each time the points move half the difference in remaining distance apart
        // We do half, because both points are moving
        double d = 0.5 * width;
        do {
            pointA.setDistance(this.distance - d);
            pointB.setDistance(this.distance + d);
            double difference = 0.5 * (width - pointA.position.distance(pointB.position));
            if (difference < PRECISION) {
                break;
            }
            d += difference;
        } while (d < width);
    }

    public static final class Point {
        public TrackConnection connection;
        public final Discrete discrete = new Discrete();
        public final Vector position = new Vector();
        public boolean direction;
        public double distance;
        public double distanceOnPath;
        public double thetaOnPath;

        public void init(TrackConnection connection, double distance) {
            this.connection = connection;
            this.discrete.init(connection);
            this.direction = true;
            this.distance = distance;
            this.distanceOnPath = distance;
            this.thetaOnPath = 0.0; // may be illegal!
        }

        public Vector getOrientation() {
            return this.connection.getOrientation(this.thetaOnPath);
        }

        public TrackConnection.PointOnPath toPointOnPath() {
            Vector dir = connection.getMotionVector(thetaOnPath);
            Vector up = connection.getOrientation(thetaOnPath);
            Quaternion orientation = Quaternion.fromLookDirection(dir, up);
            return new TrackConnection.PointOnPath(connection, thetaOnPath, distanceOnPath, position, orientation);
        }

        public void setDistance(double newDistance) {
            if (newDistance == distance) {
                return;
            }

            boolean forward = (newDistance > distance);
            double difference = (newDistance - distance);

            do {
                double remaining = distanceOnPath;
                if (forward == direction) {
                    remaining = discrete.getTotalDistance() - remaining;
                }

                // Move as much distance on the current discrete path as possible
                if (Math.abs(difference) <= remaining) {
                    distanceOnPath += direction ? difference : (-difference);
                    distance += difference;
                    thetaOnPath = discrete.findTheta(distanceOnPath);
                    connection.getPosition(thetaOnPath, position);
                    return;
                }

                // Go to end of current connection
                if (forward == direction) {
                    distanceOnPath = discrete.getTotalDistance();
                    thetaOnPath = 1.0;
                } else {
                    distanceOnPath = 0.0;
                    thetaOnPath = 0.0;
                }

                // Update distance
                distance += forward ? remaining : (-remaining);
                difference = (newDistance - distance);

                // Go to next connection in the chain
            } while (next(forward));

            // Reached the end of the path, move linearly on the current path
            distanceOnPath += direction ? difference : (-difference);
            distance += difference;

            // Find position and direction on path at the current (end) theta
            EndPoint end = (thetaOnPath == 0.0) ? connection.getEndA() : connection.getEndB();
            double multiplier = (distanceOnPath <= 0.0) ? distanceOnPath : (discrete.getTotalDistance() - distanceOnPath);
            position.copy(end.getPosition());
            position.setX(position.getX() + multiplier * end.getDirection().getX());
            position.setY(position.getY() + multiplier * end.getDirection().getY());
            position.setZ(position.getZ() + multiplier * end.getDirection().getZ());
        }

        private boolean next(boolean forward) {
            TrackNode endNode = (direction == forward) ? connection.getNodeB() : connection.getNodeA();
            List<TrackConnection> endConn = endNode.getConnections();
            if (endConn.size() >= 2) {
                this.connection = (endConn.get(0) == this.connection) ? endConn.get(1) : endConn.get(0);
                this.discrete.init(this.connection);
                this.direction = (endNode == (forward ? this.connection.getNodeA() : this.connection.getNodeB()));
                this.distanceOnPath = (this.direction == forward) ? 0.0 : this.discrete.getTotalDistance();
                return true;
            }
            return false;
        }
    }
}
