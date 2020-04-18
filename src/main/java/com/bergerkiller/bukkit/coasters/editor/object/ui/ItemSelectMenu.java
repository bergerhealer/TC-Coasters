package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

public class ItemSelectMenu extends MapWidgetMenu implements ItemDropTarget {
    private final MapWidgetItemSelector itemSelector;

    public ItemSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
        this.itemSelector = this.addWidget(new MapWidgetItemSelector() {
            @Override
            public void onAttached() {
                super.onAttached();
                this.setSelectedItem(stateSupplier.get().getObjects().getSelectedItem());
            }

            @Override
            public void onSelectedItemChanged() {
                stateSupplier.get().getObjects().setSelectedItem(this.getSelectedItem());
            }
        });
        this.itemSelector.setPosition(7, 7);
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        return this.itemSelector.acceptItem(item);
    }
}
