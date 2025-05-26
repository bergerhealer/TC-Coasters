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

public class ItemSelectMenu extends ItemSelectMenuBase {
    private final Supplier<PlayerEditState> stateSupplier;

    public ItemSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this.stateSupplier = stateSupplier;
    }

    @Override
    public ItemStack getInitialItem() {
        TrackObjectType<?> type = stateSupplier.get().getObjects().getSelectedType();
        if (type instanceof TrackObjectTypeItem) {
            return ((TrackObjectTypeItem<?>) type).getLODItems().getNearest().getItem();
        } else if (type instanceof TrackObjectTypeBlock) {
            return ((TrackObjectTypeBlock<?>) type).getBlockData().createItem(1);
        } else {
            return null;
        }
    }

    @Override
    public void onItemUpdated(ItemStack item) {
        stateSupplier.get().getObjects().transformSelectedType(type -> type.acceptItem(item));
    }
}
