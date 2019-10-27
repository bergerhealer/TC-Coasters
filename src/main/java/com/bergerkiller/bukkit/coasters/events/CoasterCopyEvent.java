package com.bergerkiller.bukkit.coasters.events;

import java.util.Set;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Event fired when a player attempts to copy one or more selected nodes to
 * the clipboard or to export them as a hastebin paste. This event can be handled
 * to restrict what coasters a player can duplicate or export.
 */
public class CoasterCopyEvent extends CoasterEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Set<TrackNode> _nodes;
    private final boolean _exportingPaste;

    /**
     * Initializes a new CoasterCopyEvent
     * 
     * @param who             The player that is making the copy
     * @param nodes           The nodes that the player wishes to copy
     * @param exportingPaste  Whether this copy is for exporting the nodes as a hastebin paste
     */
    public CoasterCopyEvent(Player who, Set<TrackNode> nodes, boolean exportingPaste) {
        super(who);
        _nodes = nodes;
        _exportingPaste = exportingPaste;
    }

    /**
     * Gets the nodes being copied. The set is mutable, and nodes removed (or added)
     * to this set will not be copied / copied as well.
     * 
     * @return nodes being copied
     */
    public Set<TrackNode> getNodes() {
        return _nodes;
    }

    /**
     * Gets whether this copy is for exporting the nodes as a hastebin paste.
     * If this true, then the player already has permission to perform this paste.
     * In that case, this event can be used to restrict what nodes can be exported as a paste,
     * or if the paste is allowed at all.
     * 
     * @return True if this event is about exporting a hastebin paste
     */
    public boolean isExportingPaste() {
        return _exportingPaste;
    }

    @Override
    public World getWorld() {
        return getPlayer().getWorld();
    }

    @Override
    public final HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
