package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.commands.arguments.CommandInputPowerState;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;

@Command("tccoasters|tcc power")
class EditStatePowerCommands {

    @CommandRequiresTCCPermission
    @Command("<channel>")
    @CommandDescription("Reads the current power state of a named sign power channel")
    public void commandReadChannelPower(
            final CommandSender sender,
            final @Argument("channel") NamedPowerChannel channel
    ) {
        sendChannelMessage(sender, channel, "is currently " + stringifyPower(channel.isPowered()));
        if (channel.hasPulse()) {
            sender.sendMessage(ChatColor.YELLOW + "It will turn " +
                    stringifyPower(!channel.isPowered()) +
                    ChatColor.YELLOW + " after " +
                    ChatColor.WHITE + channel.getPulseDelay() +
                    ChatColor.YELLOW + " ticks");
        }
    }

    @CommandRequiresTCCPermission
    @Command("<channel> <state>")
    @CommandDescription("Updates the power state of a named sign power channel")
    public void commandWriteChannelPower(
            final CommandSender sender,
            final @Argument("channel") NamedPowerChannel channel,
            final @Argument("state") CommandInputPowerState state,
            final @Flag(value="pulse",
                        parserName="time_duration_ticks",
                        description="Sends a power state change pulse") Integer pulseDelay
    ) {
        if (!channel.checkPermission(sender)) {
            return;
        }

        boolean powered = state.getState(channel);
        if (pulseDelay != null) {
            channel.pulsePowered(powered, pulseDelay.intValue());
            sendChannelMessage(sender, channel, "pulsed " + stringifyPower(powered) +
                    ChatColor.YELLOW + " for " + ChatColor.WHITE + pulseDelay.intValue() +
                    ChatColor.YELLOW + " ticks");
        } else {
            channel.setPowered(powered);
            sendChannelMessage(sender, channel, "set to " + stringifyPower(powered));
        }
    }

    // Just an alias. Easier typing to quickly trigger a sign.
    @CommandRequiresTCCPermission
    @Command("<channel> pulse [delay]")
    @CommandDescription("Updates the power state of a named sign power channel")
    public void commandWriteChannelPulsePower(
            final CommandSender sender,
            final @Argument("channel") NamedPowerChannel channel,
            final @Argument(value="delay",
                            parserName="time_duration_ticks",
                            description="Sends a power state change pulse") Integer delay
    ) {
        commandWriteChannelPower(sender, channel, CommandInputPowerState.ON,
                delay == null ? 0 : delay);
    }

    private void sendChannelMessage(CommandSender sender, NamedPowerChannel channel, String message) {
        sender.sendMessage(ChatColor.YELLOW + "Power channel '" +
                ChatColor.WHITE + channel.getName() +
                ChatColor.YELLOW + "' " + message);
    }

    private String stringifyPower(boolean powered) {
        return powered ? (ChatColor.GREEN + "ON") : (ChatColor.RED + "OFF");
    }
}
