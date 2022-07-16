package com.bergerkiller.bukkit.coasters;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class TCCoastersLocalization extends LocalizationEnum {
    public static final TCCoastersLocalization PLAYER_ONLY = new TCCoastersLocalization("player.only", ChatColor.RED + "This command is only for players!");
    public static final TCCoastersLocalization NO_PERMISSION = new TCCoastersLocalization("use.noperm", ChatColor.RED + "You do not have permission to modify TC-Coasters track!");
    public static final TCCoastersLocalization SIGNS_NO_PERMISSION = new TCCoastersLocalization("signs.noperm", ChatColor.RED + "You do not have permission to add signs to TC-Coasters track!");
    public static final TCCoastersLocalization LOCKED = new TCCoastersLocalization("locked", ChatColor.RED + "This coaster is locked!");
    public static final TCCoastersLocalization PLOTSQUARED_NO_PERMISSION = new TCCoastersLocalization("plotsquared.use.noperm", ChatColor.RED + "You are not allowed to edit TC-Coasters track on this plot!");
    public static final TCCoastersLocalization ANIMATION_ADD = new TCCoastersLocalization("animation.add", ChatColor.GREEN + "Animation %0% added to %1% selected node(s)!");
    public static final TCCoastersLocalization ANIMATION_REMOVE = new TCCoastersLocalization("animation.remove", ChatColor.YELLOW + "Animation %0% removed from %1% selected node(s)!");
    public static final TCCoastersLocalization ANIMATION_RENAME = new TCCoastersLocalization("animation.rename", ChatColor.GREEN + "Animation %0% renamed to %1% for %2% selected node(s)!");
    public static final TCCoastersLocalization SIGN_REMOVE_FAILED = new TCCoastersLocalization("signs.remove.failed", ChatColor.RED + "Failed to remove the sign from this node!");
    public static final TCCoastersLocalization SIGN_REMOVE_SUCCESS = new TCCoastersLocalization("signs.remove.success", ChatColor.YELLOW + "Sign has been removed from this node!");
    public static final TCCoastersLocalization SIGN_ADD_FAILED = new TCCoastersLocalization("signs.add.failed", ChatColor.RED + "Failed to add sign to this node!");
    public static final TCCoastersLocalization SIGN_ADD_APPEND = new TCCoastersLocalization("signs.add.append", ChatColor.GREEN + "Extra sign added below the sign to extend the lines!");
    public static final TCCoastersLocalization SIGN_ADD_SUCCESS = new TCCoastersLocalization("signs.add.success", ChatColor.GREEN + "Sign has been added to this node!");
    public static final TCCoastersLocalization SIGN_MISSING = new TCCoastersLocalization("signs.missing", ChatColor.RED + "The selected nodes don't have any signs!");
    public static final TCCoastersLocalization SIGN_POWER_FAILED = new TCCoastersLocalization("signs.power.failed", ChatColor.RED + "Failed to update power channels!");
    public static final TCCoastersLocalization SIGN_POWER_ASSIGNED = new TCCoastersLocalization("signs.power.assigned",
            ChatColor.YELLOW + "Power channel '" + ChatColor.WHITE + "%0%" + ChatColor.YELLOW +
            "' assigned to " + ChatColor.BLUE + "%1%" + ChatColor.YELLOW +
            " of the last sign of " + ChatColor.WHITE + "%2%" + ChatColor.YELLOW + " nodes");
    public static final TCCoastersLocalization SIGN_POWER_REMOVED = new TCCoastersLocalization("signs.power.removed", ChatColor.YELLOW + "Power channel removed from sign!");
    public static final TCCoastersLocalization INVALID_AXIS = new TCCoastersLocalization("command.invalidaxis", ChatColor.RED + "Invalid axis: %0%");
    public static final TCCoastersLocalization INVALID_POWER_CHANNEL = new TCCoastersLocalization("command.power.invalidchannel", ChatColor.RED + "Invalid power channel: %0%");
    public static final TCCoastersLocalization INVALID_POWER_STATE = new TCCoastersLocalization("command.power.invalidstate", ChatColor.RED + "Invalid power state: %0%");
    public static final TCCoastersLocalization INVALID_SIGN_FACE = new TCCoastersLocalization("command.sign.invalidface", ChatColor.RED + "Invalid sign face: %0%");
    public static final TCCoastersLocalization NO_NODES_SELECTED = new TCCoastersLocalization("command.nodes.notselected", ChatColor.RED + "No track nodes are selected!");

    private TCCoastersLocalization(String name, String defValue) {
        super(name, defValue);
    }

    @Override
    public String get(String... arguments) {
        return JavaPlugin.getPlugin(TCCoasters.class).getLocale(this.getName(), arguments);
    }
}
