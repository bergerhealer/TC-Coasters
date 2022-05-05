package com.bergerkiller.bukkit.coasters.commands;

import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Quoted;

/**
 * Commands that alter or play track animations
 */
@CommandMethod("tccoasters|tcc animation")
class EditStateAnimationCommands {

    @CommandRequiresTCCPermission
    @CommandMethod("")
    @CommandDescription("Shows short command help and what animations are available and selected")
    public void commandShowAnimationDetails(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        String selected = state.getSelectedAnimation();
        if (selected == null) {
            sender.sendMessage(ChatColor.YELLOW + "No animation is selected.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Selected animation: " + selected);
        }

        String available = state.getEditedNodes().stream()
            .flatMap(n -> n.getAnimationStates().stream())
            .map(s -> s.name)
            .distinct()
            .collect(Collectors.joining(", "));
        if (available.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "The selected nodes contain no animations!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Available animations: " + available);
        }

        sender.sendMessage("");
        sender.sendMessage("Usage:");
        sender.sendMessage("/tcc animation [add/remove/select] [name]");
        sender.sendMessage("/tcc animation play [name] (duration)");
        sender.sendMessage("/tcc animation clear");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("add <name>")
    @CommandDescription("Adds an animation state of the current positions to all selected nodes")
    public void commandAddAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        for (TrackNode node : state.getEditedNodes()) {
            node.saveAnimationState(animName);
        }
        sender.sendMessage("Animation '" + animName + "' added to " + state.getEditedNodes().size() + " nodes!");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("remove <name>")
    @CommandDescription("Removes an animation state from all selected nodes")
    public void commandRemoveAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        int removedCount = 0;
        for (TrackNode node : state.getEditedNodes()) {
            if (node.removeAnimationState(animName)) {
                removedCount++;
            }
        }
        if (removedCount == 0) {
            sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
        } else {
            sender.sendMessage("Animation '" + animName + "' removed for " + removedCount + " nodes!");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("clear")
    @CommandDescription("Clears all animation state from all selected nodes")
    public void commandRemoveAnimation(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        for (TrackNode node : state.getEditedNodes()) {
            for (TrackNodeAnimationState anim_state : node.getAnimationStates()) {
                node.removeAnimationState(anim_state.name);
            }
        }
        sender.sendMessage("Animations cleared for " + state.getEditedNodes().size() + " nodes!");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("select <name>")
    @CommandDescription("Selects an animation state for editing")
    public void commandSelectAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        state.setSelectedAnimation(animName);
        sender.sendMessage(ChatColor.GREEN + "Selected track animation '" + animName + "'!");
        if (state.getSelectedAnimationNodes().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "None of your selected nodes contain this animation!");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("play <name>")
    @CommandDescription("Activates an animation state instantly")
    public void commandPlayAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        commandPlayAnimation(state, sender, animName, 0.0);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("play <name> <duration>")
    @CommandDescription("Activates an animation state over a duration in seconds")
    public void commandPlayAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName,
            final @Argument("duration") double duration
    ) {
        int playingCount = 0;
        for (TrackNode node : state.getEditedNodes()) {
            if (node.playAnimation(animName, duration)) {
                playingCount++;
            }
        }
        if (playingCount == 0) {
            sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
        } else {
            sender.sendMessage("Animation '" + animName + "' is now playing for " + playingCount + " nodes!");
        }
    }
}
