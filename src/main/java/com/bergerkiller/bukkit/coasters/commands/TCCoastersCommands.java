package com.bergerkiller.bukkit.coasters.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.commands.arguments.CommandInputPowerState;
import com.bergerkiller.bukkit.coasters.commands.arguments.TrackPositionAxis;
import com.bergerkiller.bukkit.coasters.commands.parsers.CommandInputPowerStateParser;
import com.bergerkiller.bukkit.coasters.commands.parsers.LocalizedParserException;
import com.bergerkiller.bukkit.coasters.commands.parsers.NamedPowerChannelParser;
import com.bergerkiller.bukkit.coasters.commands.parsers.SignBlockFaceParser;
import com.bergerkiller.bukkit.coasters.commands.parsers.TimeTicksParser;
import com.bergerkiller.bukkit.coasters.commands.parsers.TrackPositionAxisParser;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.common.cloud.CloudSimpleHandler;

/**
 * All TC-Coasters command logic
 */
public class TCCoastersCommands {
    private final CloudSimpleHandler cloud = new CloudSimpleHandler();

    public CloudSimpleHandler getHandler() {
        return cloud;
    }

    public void enable(TCCoasters plugin) {
        cloud.enable(plugin);

        // TCC Use permission handling
        cloud.getParser().registerBuilderModifier(CommandRequiresTCCPermission.class,
                (perm, builder) -> builder.permission(plugin::hasUsePermission));

        // Exception -> Message logic
        cloud.handle(LocalizedParserException.class, (sender, ex) -> {
            sender.sendMessage(ex.getMessage());
        });

        // Animation name suggestions
        cloud.suggest("animation_names", (context, input) -> {
            PlayerEditState state = context.inject(PlayerEditState.class).get();
            return new ArrayList<String>(state.getEditedNodes().stream()
                    .flatMap(n -> n.getAnimationStates().stream())
                    .map(s -> s.name)
                    .collect(Collectors.toSet()));
        });

        // Power channel name suggestions (for use assigning a sign to a channel)
        final NamedPowerChannelParser namedPowerChannelParser = new NamedPowerChannelParser(plugin);
        cloud.suggest("power_channels", namedPowerChannelParser::suggestions);
        cloud.parse("sign_block_face", p -> new SignBlockFaceParser());
        cloud.parse("time_duration_ticks", p -> new TimeTicksParser());

        // Makes PlayerEditState available as a command argument
        cloud.injector(PlayerEditState.class, (context, annotations) -> {
            if (context.getSender() instanceof Player) {
                Player p = (Player) context.getSender();
                return plugin.getEditState(p);
            } else {
                throw new LocalizedParserException(context, TCCoastersLocalization.PLAYER_ONLY);
            }
        });
        cloud.getParser().registerBuilderModifier(CommandRequiresSelectedNodes.class, (annot, builder) -> {
            return builder.prependHandler(context -> {
                PlayerEditState state = context.inject(PlayerEditState.class).get();
                state.deselectLockedNodes();
                if (!state.hasEditedNodes()) {
                    throw new LocalizedParserException(context, TCCoastersLocalization.NO_NODES_SELECTED);
                }
            });
        });
        cloud.parse(TrackPositionAxis.class, p -> new TrackPositionAxisParser());
        cloud.parse(NamedPowerChannel.class, p -> namedPowerChannelParser);
        cloud.parse(CommandInputPowerState.class, p -> new CommandInputPowerStateParser());

        // All commands that don't rely on an edit state, can be performed by non-players
        cloud.annotations(new GlobalCommands());

        // All commands that require a Player
        cloud.annotations(new PlayerCommands());

        // All commands that use a PlayerEditState (player info)
        cloud.annotations(new EditStateCommands());
        cloud.annotations(new EditStatePositionCommands());
        cloud.annotations(new EditStateOrientationCommands());
        cloud.annotations(new EditStateRailCommands());
        cloud.annotations(new EditStateAnimationCommands());
        cloud.annotations(new EditStateSignCommands());
        cloud.annotations(new EditStatePowerCommands());

        // Help menu
        cloud.helpCommand(Collections.singletonList("tccoasters"), "Shows information about all of TC-Coasters commands");
    }
}
