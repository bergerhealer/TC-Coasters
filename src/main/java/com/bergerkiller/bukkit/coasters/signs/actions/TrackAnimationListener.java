package com.bergerkiller.bukkit.coasters.signs.actions;

import com.bergerkiller.bukkit.coasters.animation.TrackAnimation;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.List;

/**
 * SignActions that implement this interface are notified when track animations for nodes
 * that have this sign action begin or end.
 */
public interface TrackAnimationListener {
    /**
     * Called when a new animation is started for rails that have this SignAction as a sign on it
     *
     * @param event SignActionEvent
     * @param animationName Name of the animation that begun for (some of) the nodes of these rails.
     *                      Can be an empty String if no animation name was provided.
     * @param states All node animations being played (with this same name)
     */
    void onTrackAnimationBegin(SignActionEvent event, String animationName, List<TrackAnimation> states);

    /**
     * Called once an animation finished playing for rails that have this SignAction as a sign on it
     *
     * @param event SignActionEvent
     * @param animationName Name of the animation that finished for (some of) the nodes of these rails.
     *      *               Can be an empty String if no animation name was provided.
     * @param states All node animations being played (with this same name)
     */
    void onTrackAnimationEnd(SignActionEvent event, String animationName, List<TrackAnimation> states);
}
