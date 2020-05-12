package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.List;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.ItemDropTarget;

/**
 * Menu where the Track Object Type can be selected.
 * When pressing on it, the appearance of the type can be changed.
 */
public class TypeSelectMenu extends MapWidget implements ItemDropTarget {
    private final Supplier<PlayerEditState> _stateSupplier;
    private TrackObjectType<?> _displayedType = null;
    private MapTexture _displayedTypeTexture = null;
    private TypeSelectArrow _leftArrow;
    private TypeSelectArrow _rightArrow;

    public TypeSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this._stateSupplier = stateSupplier;
        this._leftArrow = addWidget(new TypeSelectArrow(stateSupplier.get().getPlugin(), false));
        this._rightArrow = addWidget(new TypeSelectArrow(stateSupplier.get().getPlugin(), true));
        this.setFocusable(true);
        this.setRetainChildWidgets(true);
    }

    /**
     * Sets the selected track object type displayed in this menu
     * 
     * @param selectedType
     */
    private void setDisplayedType(TrackObjectType<?> selectedType) {
        if (!LogicUtil.bothNullOrEqual(this._displayedType, selectedType)) {
            // if class or icon changes, re-draw
            if (selectedType.getClass() != this._displayedType.getClass() ||
                !selectedType.isSameImage(this._displayedType))
            {
                this._displayedTypeTexture = null;
                this.invalidate();
            }

            this._displayedType = selectedType;
        }
    }

    @Override
    public void onAttached() {
        this._displayedType = this._stateSupplier.get().getObjects().getSelectedType();
        this._displayedTypeTexture = null;
    }

    @Override
    public void onTick() {
        this.setDisplayedType(this._stateSupplier.get().getObjects().getSelectedType());
    }

    @Override
    public void onDraw() {
        // Set to valid value if unset
        if (this._displayedType == null) {
            this._displayedType = this._stateSupplier.get().getObjects().getSelectedType();
        }

        // Could be an expensive draw, so cache it
        if (this._displayedTypeTexture == null) {
            this._displayedTypeTexture = MapTexture.createEmpty(this.getWidth()-4, this.getHeight()-4);
            this._displayedType.drawImage(this._stateSupplier.get().getPlugin(), this._displayedTypeTexture);
        }

        // Draw static background of a button
        MapWidgetButton.fillBackground(view.getView(1, 1, getWidth() - 2, getHeight() - 2), true, false);

        int icon_x = (this.getWidth() - this._displayedTypeTexture.getWidth()) / 2;
        int icon_y = (this.getHeight() - this._displayedTypeTexture.getHeight()) / 2;
        if (this.isFocused()) {
            // Draw the icon with a little highlight
            this.view.draw(this._displayedTypeTexture, icon_x, icon_y);
            this.view.setBlendMode(MapBlendMode.ADD);
            this.view.fill(MapColorPalette.getColor(32, 32, 32));
            this.view.setBlendMode(MapBlendMode.OVERLAY);

            this.view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_RED);
            this.view.setAlignment(Alignment.MIDDLE);
            this.view.draw(MapFont.MINECRAFT, getWidth()/2, (getHeight()-7)/2, MapColorPalette.COLOR_WHITE, this._displayedType.getTitle());
            this.view.setAlignment(Alignment.LEFT);
        } else {
            this.view.draw(this._displayedTypeTexture, icon_x, icon_y);
            this.view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            this._leftArrow.setPressed(true);
            this._rightArrow.setPressed(false);
            switchType(-1);
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            this._leftArrow.setPressed(false);
            this._rightArrow.setPressed(true);
            switchType(1);
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            this._leftArrow.setPressed(false);
            this._rightArrow.setPressed(false);
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            this._leftArrow.setPressed(false);
            this._rightArrow.setPressed(false);
        } else {
            super.onKeyReleased(event);
        }
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        TrackObjectType<?> oldType = this._stateSupplier.get().getObjects().getSelectedType();
        TrackObjectType<?> newType = oldType.acceptItem(item);
        if (oldType != newType) {
            this._stateSupplier.get().getObjects().setSelectedType(newType);
            this.setDisplayedType(newType);
            this.display.playSound(CommonSounds.CLICK_WOOD);
            return true;
        }
        return false;
    }

    @Override
    public void onActivate() {
        this._stateSupplier.get().getObjects().getSelectedType().openMenu(this.getParent(), this._stateSupplier);
    }

    @Override
    public void onFocus() {
        _leftArrow.setVisible(true);
        _rightArrow.setVisible(true);
    }

    @Override
    public void onBlur() {
        _leftArrow.setVisible(false);
        _rightArrow.setVisible(false);
    }

    @Override
    public void onBoundsChanged() {
        _leftArrow.setPosition(-_leftArrow.getWidth(), (this.getHeight() - _leftArrow.getHeight()) / 2);
        _rightArrow.setPosition(this.getWidth(), (this.getHeight() - _rightArrow.getHeight()) / 2);
    }

    private void switchType(int offset) {
        List<TrackObjectType<?>> types = TrackCoasterCSV.getDefaultTrackObjectTypes();
        int currentIndex = (this._displayedType == null) ? 0 : types.indexOf(this._displayedType);
        if (currentIndex == -1) {
            for (int i = 0; i < types.size(); i++) {
                if (types.get(i).getClass() == this._displayedType.getClass()) {
                    currentIndex = i;
                    break;
                }
            }
        }
        currentIndex += offset;
        if (currentIndex >= 0 && currentIndex < types.size()) {
            TrackObjectType<?> type = types.get(currentIndex);
            this._stateSupplier.get().getObjects().setSelectedType(type);
            this.setDisplayedType(type);
            display.playSound(CommonSounds.CLICK);
        }
    }
}
