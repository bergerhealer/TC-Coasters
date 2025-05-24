package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import org.bukkit.Material;

import java.util.function.Supplier;

/**
 * Displays the different LODs set and has UI to modify them
 */
public class ItemLODSelect extends MapWidgetMenu {
    private final Supplier<PlayerEditState> stateSupplier;

    public ItemLODSelect(Supplier<PlayerEditState> stateSupplier) {
        this.stateSupplier = stateSupplier;
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        LODItemStack.List list = LODItemStack.createList(CommonItemStack.create(Material.RED_WOOL, 1).toBukkit());
        list = list.expandLOD(16, CommonItemStack.create(Material.GREEN_WOOL, 1).toBukkit());
        list = list.expandLOD(32, CommonItemStack.create(Material.BLUE_WOOL, 1).toBukkit());
        list = list.expandLOD(64, CommonItemStack.create(Material.YELLOW_WOOL, 1).toBukkit());
        list = list.expandLOD(80, CommonItemStack.create(Material.BLACK_WOOL, 1).toBukkit());

        this.addWidget(new ItemLODListWidget(stateSupplier.get().getPlugin(), list) {
            @Override
            public void onLODChanged(LODItemStack.List lodList) {
            }
        }).setPosition(5, 5);
    }
}
