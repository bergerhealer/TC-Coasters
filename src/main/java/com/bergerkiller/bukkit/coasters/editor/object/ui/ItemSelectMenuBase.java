package com.bergerkiller.bukkit.coasters.editor.object.ui;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.item.MapWidgetItemSelector;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Base item select menu. Does not implement the methods for getting the current
 * item and setting a new one
 */
public abstract class ItemSelectMenuBase extends MapWidgetMenu {
    private final MapWidgetItemSelector itemSelector;

    public abstract ItemStack getInitialItem();

    public abstract void onItemUpdated(ItemStack item);

    public ItemSelectMenuBase() {
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);
        this.itemSelector = this.addWidget(new MapWidgetItemSelector() {
            @Override
            public void onAttached() {
                super.onAttached();

                ItemStack item = getInitialItem();
                if (item != null) {
                    setSelectedItem(item);
                }
            }

            @Override
            public void onSelectedItemChanged() {
                ItemStack item = this.getSelectedItem();
                if (item == null) {
                    return;
                }
                onItemUpdated(item);
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
