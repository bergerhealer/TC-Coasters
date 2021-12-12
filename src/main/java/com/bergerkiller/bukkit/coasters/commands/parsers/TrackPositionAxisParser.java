package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.arguments.TrackPositionAxis;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses a direction from string
 */
public class TrackPositionAxisParser implements ArgumentParser<CommandSender, TrackPositionAxis> {

    public TrackPositionAxisParser() {
    }

    @Override
    public ArgumentParseResult<TrackPositionAxis> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String name = inputQueue.peek();
        if (name.equals("~") && commandContext.getSender() instanceof Player) {
            inputQueue.poll();
            return ArgumentParseResult.success(TrackPositionAxis.eye(
                    (Player) commandContext.getSender()));
        } else if (name.equalsIgnoreCase("x")) {
            inputQueue.poll();
            return ArgumentParseResult.success(TrackPositionAxis.X);
        } else if (name.equalsIgnoreCase("y")) {
            inputQueue.poll();
            return ArgumentParseResult.success(TrackPositionAxis.Y);
        } else if (name.equalsIgnoreCase("z")) {
            inputQueue.poll();
            return ArgumentParseResult.success(TrackPositionAxis.Z);
        }

        return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                TCCoastersLocalization.INVALID_AXIS, name));
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return Arrays.asList("x", "y", "z", "~");
    }
}
