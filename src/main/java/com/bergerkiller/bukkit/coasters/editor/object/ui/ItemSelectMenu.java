package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeFallingBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItemStack;
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

                TrackObjectType<?> type = stateSupplier.get().getObjects().getSelectedType();
                if (type instanceof TrackObjectTypeItemStack) {
                    this.setSelectedItem(((TrackObjectTypeItemStack) type).getItem());
                } else if (type instanceof TrackObjectTypeFallingBlock) {
                    this.setSelectedItem(((TrackObjectTypeFallingBlock) type).getMaterial().createItem(1));
                }
            }

            @Override
            public void onSelectedItemChanged() {
                ItemStack item = this.getSelectedItem();
                if (item == null) {
                    return;
                }
                TrackObjectType<?> oldType = stateSupplier.get().getObjects().getSelectedType();
                TrackObjectType<?> newType = oldType.acceptItem(this.getSelectedItem());
                if (oldType != newType) {
                    stateSupplier.get().getObjects().setSelectedType(newType);
                }
            }
        });
        this.itemSelector.setPosition(7, 7);
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        return this.itemSelector.acceptItem(item);
    }
}
