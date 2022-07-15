package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannelRegistry;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Looks up a {@link NamedPowerChannel} by name. Suggestions only show those on the player/command block's
 * current world, but if none match, also resolves globally.
 */
public class NamedPowerChannelParser implements ArgumentParser<CommandSender, NamedPowerChannel> {
    private final TCCoasters plugin;

    public NamedPowerChannelParser(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public ArgumentParseResult<NamedPowerChannel> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        // First find for the world the sender is on, if applicable
        String name = inputQueue.peek();
        NamedPowerChannel powerState;
        NamedPowerChannelRegistry registry = findDefaultRegistry(commandContext);
        if (registry != null && (powerState = registry.findIfExists(name)) != null) {
            inputQueue.poll();
            return ArgumentParseResult.success(powerState);
        }

        // Try global
        if ((powerState = plugin.findGlobalPowerState(name)) != null) {
            inputQueue.poll();
            return ArgumentParseResult.success(powerState);
        }

        // Not found
        return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                TCCoastersLocalization.INVALID_POWER_CHANNEL, name));
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        List<String> result = new ArrayList<>();

        // First list all names that match for the world the sender is on, if applicable
        NamedPowerChannelRegistry registry = findDefaultRegistry(commandContext);
        if (registry != null) {
            for (String name : registry.getNames()) {
                if (name.startsWith(input)) {
                    result.add(name);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        // List all power states everywhere on all worlds
        for (CoasterWorld world : plugin.getCoasterWorlds()) {
            if (world.getNamedPowerChannels() != registry) {
                for (String name : world.getNamedPowerChannels().getNames()) {
                    if (name.startsWith(input)) {
                        result.add(name);
                    }
                }
            }
        }

        return result;
    }

    private NamedPowerChannelRegistry findDefaultRegistry(CommandContext<CommandSender> context) {
        World world = BoundingRange.Axis.forSender(context.getSender()).world;
        return (world == null) ? null : plugin.getCoasterWorld(world).getNamedPowerChannels();
    }
}
