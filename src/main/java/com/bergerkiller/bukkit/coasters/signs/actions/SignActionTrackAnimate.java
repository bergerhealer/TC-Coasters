package com.bergerkiller.bukkit.coasters.signs.actions;

import java.util.List;

import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.animation.TrackAnimation;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionTrackAnimate extends TCCSignNodeAction implements TrackAnimationListener {

    @Override
    public String getPrefix() {
        return "animate";
    }

    @Override
    public void executeTrack(SignActionEvent event, List<TrackNode> nodes) {
        String animName = event.getLine(2);
        double duration = ParseUtil.parseDouble(event.getLine(3), 0.0);
        for (TrackNode node : nodes) {
            node.playAnimation(animName, duration);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(TCCoastersPermissions.BUILD_ANIMATOR)
                .setName("track animator")
                .setDescription("start coaster track animations")
                .handle(event.getPlayer());
    }

    @Override
    public void onTrackAnimationBegin(SignActionEvent event, String animationName, List<TrackAnimation> states) {
        if (!event.getLine(2).equals(animationName)) {
            event.setLevers(false);
        }
    }

    @Override
    public void onTrackAnimationEnd(SignActionEvent event, String animationName, List<TrackAnimation> states) {
        if (event.getLine(2).equals(animationName)) {
            event.setLevers(true);
        }
    }
}
