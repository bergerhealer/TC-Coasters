package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * A single LOD itemstack being configured
 */
public class ItemLODEntry extends MapWidget {
    public LODItemStack.List lodList;
    public int index;

    public ItemLODEntry(LODItemStack.List lodList, int index) {

    }
}
