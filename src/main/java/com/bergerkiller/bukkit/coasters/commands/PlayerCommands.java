package com.bergerkiller.bukkit.coasters.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import org.bukkit.inventory.ItemFlag;

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
            player.getInventory().addItem(CommonItemStack.create(MaterialUtil.getFirst("STICK", "LEGACY_STICK"), 1)
                    .setCustomNameMessage("Track Editor (Stick)")
                    .addLoreMessage("TC-Coasters")
                    .addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1)
                    .updateCustomData(tag -> {
                        tag.putValue("plugin", plugin.getName());
                        tag.putValue("editorStick", true);
                    })
                    .addItemFlags(ItemFlag.HIDE_ENCHANTS)
                    .toBukkit());
            player.sendMessage("Gave you a track editor stick!");
        } else {
            player.getInventory().addItem(CommonItemStack.of(MapDisplay.createMapItem(TCCoastersDisplay.class))
                    .setCustomNameMessage("Track Editor")
                    .addLoreMessage("TC-Coasters")
                    .setFilledMapColor(0x0000FF)
                    .toBukkit());
            player.sendMessage("Gave you a track editor map!");
        }
    }
}
