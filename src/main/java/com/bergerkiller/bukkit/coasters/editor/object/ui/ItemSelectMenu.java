package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;

public class ItemSelectMenu extends MapWidgetMenu {
    private final MapWidgetItemSelector itemSelector;

    public ItemSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
        this.itemSelector = this.addWidget(new MapWidgetItemSelector() {
            @Override
            public void onAttached() {
                super.onAttached();

                TrackObjectType<?> type = stateSupplier.get().getObjects().getSelectedType();
                if (type instanceof TrackObjectTypeItem) {
                    this.setSelectedItem(((TrackObjectTypeItem<?>) type).getLODItems().getNearest().getItem());
                } else if (type instanceof TrackObjectTypeBlock) {
                    this.setSelectedItem(((TrackObjectTypeBlock<?>) type).getBlockData().createItem(1));
                }
            }

            @Override
            public void onSelectedItemChanged() {
                ItemStack item = this.getSelectedItem();
                if (item == null) {
                    return;
                }
                stateSupplier.get().getObjects().transformSelectedType(type -> type.acceptItem(this.getSelectedItem()));
            }
        });
        this.itemSelector.setPosition(7, 7);
    }

    @Override
    public boolean onItemDrop(Player player, ItemStack item) {
        return this.itemSelector.acceptItem(item);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        display.playSound(SoundEffect.PISTON_EXTEND);
    }

    @Override
    public void onDetached() {
        display.playSound(SoundEffect.PISTON_CONTRACT);
    }
}
