package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemSelectMenuBase;

abstract class ItemLODItemSelectMenu extends ItemSelectMenuBase {
    public ItemLODItemSelectMenu() {
        this.setPositionAbsolute(true);
        this.setPosition((128 - getWidth()) / 2, 23);
    }
}
