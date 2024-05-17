package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannelRegistry;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Looks up a {@link NamedPowerChannel} by name. Suggestions only show those on the player/command block's
 * current world, but if none match, also resolves globally.
 */
public class NamedPowerChannelParser implements ArgumentParser<CommandSender, NamedPowerChannel>, BlockingSuggestionProvider.Strings<CommandSender> {
    private final TCCoasters plugin;

    public NamedPowerChannelParser(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public ArgumentParseResult<NamedPowerChannel> parse(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        // First find for the world the sender is on, if applicable
        String name = commandInput.readString();
        NamedPowerChannel powerState;
        NamedPowerChannelRegistry registry = findDefaultRegistry(commandContext);
        if (registry != null && (powerState = registry.findIfExists(name)) != null) {
            return ArgumentParseResult.success(powerState);
        }

        // Try global
        if ((powerState = plugin.findGlobalPowerState(name)) != null) {
            return ArgumentParseResult.success(powerState);
        }

        // Not found
        return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                TCCoastersLocalization.INVALID_POWER_CHANNEL, name));
    }

    @Override
    public Iterable<String> stringSuggestions(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        List<String> result = new ArrayList<>();

        // First list all names that match for the world the sender is on, if applicable
        NamedPowerChannelRegistry registry = findDefaultRegistry(commandContext);
        String input = commandInput.lastRemainingToken();
        if (registry != null) {
            for (String name : registry.getNames()) {
                if (name.startsWith(input)) {
                    result.add(name);
                }
            }
            if (!result.isEmpty()) {
                includeWorldPrefixes(result, input);
                return result;
            }
        }

        // See if the current input starts with a world name prefix
        // If so, return results for that world only
        CoasterWorld matchedWorld = plugin.findCoasterWorldByPrefix(input);
        if (matchedWorld != null) {
            String worldPrefix = matchedWorld.getBukkitWorld().getName() + ".";
            String inputWithoutWorld = input.substring(worldPrefix.length());
            for (String name : matchedWorld.getNamedPowerChannels().getNames()) {
                if (name.startsWith(inputWithoutWorld)) {
                    result.add(worldPrefix + name);
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
        includeWorldPrefixes(result, input);

        return result;
    }

    private void includeWorldPrefixes(List<String> result, String input) {
        for (CoasterWorld world : plugin.getCoasterWorlds()) {
            World loadedWorld = world.getBukkitWorld();
            if (loadedWorld != null && !world.getNamedPowerChannels().getNames().isEmpty()) {
                String prefix = loadedWorld.getName() + ".";
                if (prefix.startsWith(input)) {
                    result.add(prefix);
                }
            }
        }
    }

    private NamedPowerChannelRegistry findDefaultRegistry(CommandContext<CommandSender> context) {
        World world = BoundingRange.Axis.forSender(context.sender()).world;
        return (world == null) ? null : plugin.getCoasterWorld(world).getNamedPowerChannels();
    }
}
