package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses a BlockFace of a Sign. Only supports the block faces and SELF
 */
public class SignBlockFaceParser implements ArgumentParser<CommandSender, BlockFace> {
    private final Map<String, BlockFace> byName = new HashMap<>();
    private final Map<String, Integer> byRelativeRotation = new HashMap<>();
    private final List<String> namesPlayer = new ArrayList<>();
    private final List<String> namesNonPlayer = new ArrayList<>();

    public SignBlockFaceParser() {
        byName.put("all", BlockFace.SELF);
        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            byName.put(face.name().toLowerCase(Locale.ENGLISH), face);
        }
        namesNonPlayer.addAll(byName.keySet());

        byRelativeRotation.put("left", -2);
        byRelativeRotation.put("right", 2);
        byRelativeRotation.put("front", 0);
        byRelativeRotation.put("back", 4);
        namesPlayer.addAll(namesNonPlayer);
        namesPlayer.addAll(byRelativeRotation.keySet());
    }

    @Override
    public ArgumentParseResult<BlockFace> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String faceName = inputQueue.peek().toLowerCase(Locale.ENGLISH);
        BlockFace face = byName.get(faceName);
        if (face != null) {
            inputQueue.poll();
            return ArgumentParseResult.success(face);
        }

        // Try relative directions
        Integer relative = byRelativeRotation.get(faceName);
        if (relative != null && commandContext.getSender() instanceof Player) {
            Player player = (Player) commandContext.getSender();
            face = FaceUtil.yawToFace(player.getEyeLocation().getYaw() - 90.0f, false);
            face = FaceUtil.rotate(face, relative.intValue());
            inputQueue.poll();
            return ArgumentParseResult.success(face);
        }

        // Not found
        return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                TCCoastersLocalization.INVALID_SIGN_FACE, faceName));
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return (commandContext.getSender() instanceof Player)
                ? namesPlayer : namesNonPlayer;
    }
}
