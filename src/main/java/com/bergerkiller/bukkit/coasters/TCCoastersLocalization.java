package com.bergerkiller.bukkit.coasters;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class TCCoastersLocalization extends LocalizationEnum {
    public static final TCCoastersLocalization NO_PERMISSION = new TCCoastersLocalization("use.noperm", ChatColor.RED + "You do not have permission to modify TC-Coasters track!");
    public static final TCCoastersLocalization PLOTSQUARED_NO_PERMISSION = new TCCoastersLocalization("plotsquared.use.noperm", ChatColor.RED + "This is not inside your plot! You only have permission to modify TC-Coasters track inside your own plots!");

    private TCCoastersLocalization(String name, String defValue) {
        super(name, defValue);
    }

    @Override
    public String get(String... arguments) {
        return JavaPlugin.getPlugin(TCCoasters.class).getLocale(this.getName(), arguments);
    }
}
