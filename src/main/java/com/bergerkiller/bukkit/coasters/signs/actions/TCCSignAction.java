package com.bergerkiller.bukkit.coasters.signs.actions;

import java.util.List;
import java.util.Locale;

import org.bukkit.Effect;
import org.bukkit.Location;

import com.bergerkiller.bukkit.coasters.CoasterRailType;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

public abstract class TCCSignAction extends SignAction {

    /**
     * Gets the text prefix for this sign action.
     * This is the word put right after tcc-.
     * Word should be all-lowercase characters.
     * 
     * @return prefix
     */
    public abstract String getPrefix();

    /**
     * Called when the sign is executed, with the track node at the sign already discovered
     * 
     * @param event
     * @param node
     */
    public abstract void executeTrack(SignActionEvent event, List<TrackNode> node);

    @Override
    public boolean match(SignActionEvent event) {
        String firstLine = event.getLine(1).toLowerCase(Locale.ENGLISH);
        if (firstLine.length() <= 3
                || firstLine.charAt(0) != 't'
                || firstLine.charAt(1) != 'c'
                || firstLine.charAt(2) != 'c')
        {
            return false;
        }

        int tOffset = 3;
        while (tOffset < firstLine.length()) {
            char c = firstLine.charAt(tOffset);
            if (c == '-' || c == ' ' || c == '_' || c == '.') {
                tOffset++;
            } else {
                break;
            }
        }
        return firstLine.startsWith(getPrefix(), tOffset);
    }

    @Override
    public void execute(SignActionEvent event) {
        // Check valid event
        if (event.isCartSign() && event.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            //TODO: Add something here or remove
        } else if (event.isTrainSign() && event.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            //TODO: Add something here or remove
        } else {
            return;
        }

        // Check powered
        if (!event.isPowered()) {
            return;
        }

        // Check has rails, and that the rails are of the correct type
        RailPiece railPiece = event.getRailPiece();
        if (railPiece == null || !(railPiece.type() instanceof CoasterRailType)) {
            failNoTrack(event);
            return;
        }

        // Discover all the track nodes that are active at this rail block
        List<TrackNode> nodes = ((CoasterRailType) railPiece.type()).getNodes(railPiece.block());
        if (nodes.isEmpty()) {
            failNoTrack(event);
            return;
        }

        // Execute!
        executeTrack(event, nodes);
    }

    // Plays a smoke and sound effect at the sign to indicate no track node is located here
    private void failNoTrack(SignActionEvent event) {
        Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
        WorldUtil.playSound(loc, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
    }
}
