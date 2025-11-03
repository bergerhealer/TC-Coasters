package com.bergerkiller.bukkit.coasters.tracks;

import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

/**
 * Stores a binding of something to a TrackNode:
 * <ul>
 *     <li>None: Not bound, see {@link #NONE}</li>
 *     <li>Active: Bound to a TrackNode, see {@link #forNode(TrackNode)}</li>
 *     <li>Animations: Bound to a TrackNode's animation states list, see
 *     {@link #forNodeAnimationStates(TrackNode)}</li>
 * </ul>
 * This binding can represent
 * the object is not bound ({@link #NONE}), is bound to a node and active,
 * or is bound to the track node but only stored as part of the node's animations.
 */
public interface TrackNodeBinding {
    TrackNodeBinding NONE = new TrackNodeBinding() {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public TrackNode node() {
            return null;
        }

        @Override
        public CoasterWorld world() {
            return null;
        }

        @Override
        public boolean isAddedAsAnimation() {
            return false;
        }

        @Override
        public void assertActive(String what) {
            throw new IllegalStateException("This " + what + " has no binding with a track node");
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public String toString() {
            return "TrackNodeBinding{NONE}";
        }
    };

    /**
     * Creates a binding for a node that is currently active
     *
     * @param node TrackNode, cannot be null
     * @return TrackNodeBinding
     */
    static TrackNodeBinding forNode(TrackNode node) {
        return new ValidBinding(node, false);
    }

    /**
     * Creates a binding for a node that is part of the node's
     * added animation states. It is not otherwise active.
     *
     * @param node TrackNode, cannot be null
     * @return TrackNodeBinding
     */
    static TrackNodeBinding forNodeAnimationStates(TrackNode node) {
        return new ValidBinding(node, true);
    }

    /**
     * Whether this binding is for an active object. This means the object
     * is displayed / handles events, and {@link #node()} is available and non-null.
     * It also means this is not {@link #isAddedAsAnimation() added as an animation} only.
     *
     * @return True if this binding is active
     */
    boolean isActive();

    /**
     * Gets the node bound to, or <i>null</i> if not bound to a node
     *
     * @return owning TrackNode, <i>null</i> if not owned by a node
     */
    TrackNode node();

    /**
     * Gets the world of the node bound to, or <u>null</u> if not bound to a node
     *
     * @return CoasterWorld, <i>null</i> if not owned by a node
     */
    CoasterWorld world();

    /**
     * Whether this binding is part of one of the node's animation states.
     *
     * @return True if added to a node's animation state list
     */
    boolean isAddedAsAnimation();

    /**
     * Asserts that this binding is active, meaning it is assigned to a node and not
     * as an animation. Throws an informative error if this is not the case.
     *
     * @param what What type of object is being asserted (e.g. "sign")
     */
    void assertActive(String what);

    /**
     * Implementation of a valid non-null binding
     */
    final class ValidBinding implements TrackNodeBinding {
        private final TrackNode node;
        private final boolean addedAsAnimation;

        private ValidBinding(TrackNode node, boolean addedAsAnimation) {
            if (node == null) {
                throw new IllegalArgumentException("TrackNode cannot be null");
            }
            this.node = node;
            this.addedAsAnimation = addedAsAnimation;
        }

        @Override
        public boolean isActive() {
            return !addedAsAnimation;
        }

        @Override
        public TrackNode node() {
            return node;
        }

        @Override
        public CoasterWorld world() {
            return node.getWorld();
        }

        @Override
        public boolean isAddedAsAnimation() {
            return addedAsAnimation;
        }

        @Override
        public void assertActive(String what) {
            if (addedAsAnimation) {
                throw new IllegalStateException("This " + what + " has a binding with an animation state of the node at "
                        + node.getPosition() + " that is inactive");
            }
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof ValidBinding) {
                ValidBinding other = (ValidBinding) o;
                return node == other.node && addedAsAnimation == other.addedAsAnimation;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "TrackNodeBinding{" + (addedAsAnimation ? "ANIMATION " : "NODE ") + node + "}";
        }
    }
}
