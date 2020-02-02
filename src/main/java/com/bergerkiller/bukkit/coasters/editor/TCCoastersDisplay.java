package com.bergerkiller.bukkit.coasters.editor;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class TCCoastersDisplay extends MapDisplay {
    private boolean _hasPermission;

    private final MapWidgetTabView tabView = new MapWidgetTabView() {
        @Override
        public void onAttached() {
            PlayerEditState state = getState();
            for (PlayerEditMode mode : PlayerEditMode.values()) {
                mode.createView(this.addTab(), state);
            }
            this.setSelectedIndex(state.getMode().ordinal());
            this.setBounds(5, 20, 128-10, 90);
        }
    };

    @Override
    public void onTick() {
        super.onTick();

        // Detect changes in permission to revoke/return permissions
        if (TCCoastersPermissions.USE.has(this.getPlayer()) != this._hasPermission) {
            this.setRunning(false);
            this.setRunning(true);
        }
    }

    @Override
    public void onAttached() {
        getLayer().draw(this.loadTexture("com/bergerkiller/bukkit/coasters/resources/coaster_bg.png"), 0, 0);

        // Don't do onTick when not viewing for better performance
        this.setUpdateWithoutViewers(false);

        // When no permission, simply show nothing on the map
        this._hasPermission = TCCoastersPermissions.USE.has(this.getPlayer());
        if (!this._hasPermission) {
            getLayer(1).draw(MapFont.MINECRAFT, 5, 5, MapColorPalette.COLOR_RED, "No Permission");
            return;
        }

        this.setReceiveInputWhenHolding(true);

        this.addWidget(this.tabView);

        this.addWidget(new MapWidgetSelectionBox() {
            @Override
            public void onAttached() {
                for (PlayerEditMode mode : PlayerEditMode.values()) {
                    this.addItem(mode.getName());
                }
                this.setSelectedItem(getState().getMode().getName());
                this.setBounds(5, 5, 128-10, 11);
                super.onAttached();
            }

            @Override
            public void onSelectedItemChanged() {
                getState().setMode(PlayerEditMode.fromName(this.getSelectedItem()));
                tabView.setSelectedIndex(getState().getMode().ordinal());
            }
        });
    }

    @Override
    public void onDetached() {
        this.clearWidgets();
        this.setReceiveInputWhenHolding(false);
    }

    public PlayerEditState getState() {
        return getPlugin().getEditState(getPlayer());
    }
    
    public Player getPlayer() {
        return this.getOwners().get(0);
    }

    public CoasterWorldAccess getCoasterWorld() {
        return getState().getCoasterWorld();
    }

    @Override
    public TCCoasters getPlugin() {
        return (TCCoasters) super.getPlugin();
    }
}
