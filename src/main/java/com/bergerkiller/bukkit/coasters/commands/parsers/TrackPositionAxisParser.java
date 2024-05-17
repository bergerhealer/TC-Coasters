package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.Arrays;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.arguments.TrackPositionAxis;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Parses a direction from string
 */
public class TrackPositionAxisParser implements ArgumentParser<CommandSender, TrackPositionAxis>, BlockingSuggestionProvider.Strings<CommandSender> {

    public TrackPositionAxisParser() {
    }

    @Override
    public ArgumentParseResult<TrackPositionAxis> parse(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        String name = commandInput.readString();
        if (name.equals("~") && commandContext.sender() instanceof Player) {
            return ArgumentParseResult.success(TrackPositionAxis.eye(
                    (Player) commandContext.sender()));
        } else if (name.equalsIgnoreCase("x")) {
            return ArgumentParseResult.success(TrackPositionAxis.X);
        } else if (name.equalsIgnoreCase("y")) {
            return ArgumentParseResult.success(TrackPositionAxis.Y);
        } else if (name.equalsIgnoreCase("z")) {
            return ArgumentParseResult.success(TrackPositionAxis.Z);
        }

        return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                TCCoastersLocalization.INVALID_AXIS, name));
    }

    @Override
    public Iterable<String> stringSuggestions(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        return Arrays.asList("x", "y", "z", "~");
    }
}
