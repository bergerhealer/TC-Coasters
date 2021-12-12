package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

@CommandMethod("tccoasters|tcc")
class PlayerCommands {

    @CommandRequiresTCCPermission
    @CommandMethod("give")
    @CommandDescription("Gives the player the TCCoasters editor map")
    public void commandGiveEditorMap(
            final Player player,
            final TCCoasters plugin
    ) {
        player.sendMessage("Gave you a track editor map!");
        ItemStack item = MapDisplay.createMapItem(TCCoastersDisplay.class);
        ItemUtil.setDisplayName(item, "Track Editor");
        CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
        CommonTagCompound display = tag.createCompound("display");
        display.putValue("MapColor", 0x0000FF);
        player.getInventory().addItem(item);
    }
}
