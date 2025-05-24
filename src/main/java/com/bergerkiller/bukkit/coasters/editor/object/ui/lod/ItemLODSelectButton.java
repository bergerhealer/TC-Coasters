package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItem;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;

import java.util.function.Supplier;

/**
 * Button to configure item LOD's. Only works for item-driven track objects.
 */
public class ItemLODSelectButton extends MapWidgetButton {
    private final Supplier<PlayerEditState> stateSupplier;

    public ItemLODSelectButton(Supplier<PlayerEditState> stateSupplier) {
        this.stateSupplier = stateSupplier;
    }

    @Override
    public void onAttached() {
        updateEnabled();
    }

    public void updateEnabled() {
        setEnabled(stateSupplier.get().getObjects().getSelectedType() instanceof TrackObjectTypeItem);
    }

    @Override
    public void onActivate() {
        parent.addWidget(new ItemLODSelect(stateSupplier));
    }
}
