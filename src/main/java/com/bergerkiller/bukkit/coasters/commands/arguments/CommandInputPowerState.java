package com.bergerkiller.bukkit.coasters.commands.arguments;

import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;

/**
 * Simple ON/OFF power state. For use with the power command.
 */
public enum CommandInputPowerState {
    ON, OFF, TOGGLE;

    public boolean getState(NamedPowerChannel channel) {
        switch (this) {
        case ON: return true;
        case OFF: return false;
        case TOGGLE: return !channel.isPowered();
        default:
            return false; // Meh
        }
    }
}
