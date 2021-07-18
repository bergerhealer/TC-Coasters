package com.bergerkiller.bukkit.coasters;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class TCCoastersLocalization extends LocalizationEnum {
    public static final TCCoastersLocalization NO_PERMISSION = new TCCoastersLocalization("use.noperm", ChatColor.RED + "You do not have permission to modify TC-Coasters track!");
    public static final TCCoastersLocalization LOCKED = new TCCoastersLocalization("locked", ChatColor.RED + "This coaster is locked!");
    public static final TCCoastersLocalization PLOTSQUARED_NO_PERMISSION = new TCCoastersLocalization("plotsquared.use.noperm", ChatColor.RED + "You are not allowed to edit TC-Coasters track on this plot!");
    public static final TCCoastersLocalization ANIMATION_ADD = new TCCoastersLocalization("animation.add", ChatColor.GREEN + "Animation %0% added to %1% selected node(s)!");
    public static final TCCoastersLocalization ANIMATION_REMOVE = new TCCoastersLocalization("animation.remove", ChatColor.YELLOW + "Animation %0% removed from %1% selected node(s)!");
    public static final TCCoastersLocalization ANIMATION_RENAME = new TCCoastersLocalization("animation.rename", ChatColor.GREEN + "Animation %0% renamed to %1% for %2% selected node(s)!");

    private TCCoastersLocalization(String name, String defValue) {
        super(name, defValue);
    }

    @Override
    public String get(String... arguments) {
        return JavaPlugin.getPlugin(TCCoasters.class).getLocale(this.getName(), arguments);
    }
}
