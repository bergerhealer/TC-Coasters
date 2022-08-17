package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;

@CommandMethod("tccoasters|tcc")
class PlayerCommands {

    @CommandRequiresTCCPermission
    @CommandMethod("give")
    @CommandDescription("Gives the player the TCCoasters editor map")
    public void commandGiveEditorMap(
            final Player player,
            final TCCoasters plugin,
            final @Flag(value="stick", description="Give an editor stick instead, which acts as an off-hand editor map") boolean stick
    ) {
        if (stick) {
            player.sendMessage("Gave you a track editor stick!");
            ItemStack item = ItemUtil.createItem(MaterialUtil.getFirst("STICK", "LEGACY_STICK"), 1);
            ItemUtil.setDisplayName(item, "Track Editor (Stick)");
            ItemUtil.addLoreName(item, "TC-Coasters");
            item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
            tag.putValue("plugin", plugin.getName());
            tag.putValue("editorStick", true);
            tag.putValue("HideFlags", 1);
            player.getInventory().addItem(item);
        } else {
            player.sendMessage("Gave you a track editor map!");
            ItemStack item = MapDisplay.createMapItem(TCCoastersDisplay.class);
            ItemUtil.setDisplayName(item, "Track Editor");
            ItemUtil.addLoreName(item, "TC-Coasters");
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
            CommonTagCompound display = tag.createCompound("display");
            display.putValue("MapColor", 0x0000FF);
            player.getInventory().addItem(item);
        }
    }
}
