package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A list of LODItemStack entries in a List of LODs.
 * Has methods for adding new LODs, removing them and
 * modifying them.
 */
public abstract class ItemLODListWidget extends MapWidgetScroller {
    private static final int LIST_WIDTH = 108;
    private static final int LIST_HEIGHT = 103;
    private static final int ITEM_HEIGHT = 16;
    private static final int REMOVE_BUTTON_SIZE = 11;
    private final TCCoasters plugin;
    private LODItemStack.List lodList;

    public ItemLODListWidget(TCCoasters plugin, LODItemStack.List lodList) {
        this.plugin = plugin;
        this.lodList = lodList;
        this.setSize(LIST_WIDTH, LIST_HEIGHT);

        for (int i = 0; i < lodList.size(); i++) {
            this.addContainerWidget(new LODItemWidget(i));
        }
    }

    private boolean isLODItemAt(int lodIndex, int expectedThreshold, ItemStack expectedItem) {
        LODItemStack item = lodList.getItem(lodIndex);
        return item.getDistanceThreshold() == expectedThreshold &&
                item.getItem() == expectedItem;
    }

    private LODItemWidget getItemWidget(int lodIndex) {
        return (LODItemWidget) getContainer().getWidget(lodIndex);
    }

    /**
     * Called when the LOD list is re-configured
     *
     * @param lodList Updated List of LOD ItemStacks
     */
    public abstract void onLODChanged(LODItemStack.List lodList);

    private class LODItemWidget extends MapWidget {
        private int index;
        private int buttonIdx = 0;
        private boolean isEditingDistance = false;
        private int liveDistance = -1;
        private boolean liveDistanceChanged = false;
        private boolean focusFromEditingDistance = false;
        private final MapTexture itemIcon;

        public LODItemWidget(int index) {
            this.index = index;
            this.setBounds(0, index * ITEM_HEIGHT, LIST_WIDTH, ITEM_HEIGHT);
            this.setFocusable(true);
            this.itemIcon = MapTexture.createEmpty(ITEM_HEIGHT, ITEM_HEIGHT);
            updateItemImage();
        }

        public void updateItemImage() {
            ItemStack item = lodList.getItem(this.index).getItem();
            this.itemIcon.clear();
            if (item != null) {
                this.itemIcon.fillItem(plugin.getResourcePack(), item);
            }
            this.invalidate();
        }

        public void applyLiveDistance() {
            if (liveDistanceChanged) {
                liveDistanceChanged = false;
            } else {
                return;
            }

            // For focusing the right row later
            ItemStack focusItem = lodList.getItem(index).getItem();
            int focusThreshold = liveDistance;

            // Update distances. This could result in items being re-ordered.
            LODItemStack.List oldList = lodList;
            LODItemStack.List newList = lodList.updateDistanceThreshold(index, liveDistance);
            lodList = newList; // Ensures update functions work properly

            // Re-sync all widget in case order / items have changed
            for (int i = 0; i < oldList.size(); i++) {
                LODItemStack oldLODItem = oldList.getItem(i);
                LODItemStack newLODItem = newList.getItem(i);
                if (!oldLODItem.equals(newLODItem)) {
                    LODItemWidget widget = getItemWidget(i);
                    if (!Objects.equals(oldLODItem.getItem(), newLODItem.getItem())) {
                        widget.updateItemImage();
                    }
                    widget.liveDistance = newLODItem.getDistanceThreshold();
                    widget.buttonIdx = 0;
                    widget.isEditingDistance = false;
                    widget.invalidate();
                }
            }

            // Look up the item this widget is now at. Current index has preference.
            // Then focus this one and ensure editing of the distance is resumed (in case it was moved)
            if (isFocused()) {
                LODItemWidget itemWidget = null;
                if (isLODItemAt(index, focusThreshold, focusItem)) {
                    itemWidget = getItemWidget(index);
                } else {
                    for (int i = 0; i < lodList.size(); i++) {
                        if (i != index && isLODItemAt(i, focusThreshold, focusItem)) {
                            itemWidget = getItemWidget(i);
                            break;
                        }
                    }
                }
                if (itemWidget != null) {
                    itemWidget.buttonIdx = 1;
                    itemWidget.isEditingDistance = true;
                    itemWidget.invalidate();

                    if (itemWidget != this) {
                        itemWidget.focusFromEditingDistance = true;
                        itemWidget.focus();
                    }
                }
            }
        }

        @Override
        public void onFocus() {
            super.onFocus();

            // When a new item is selected (focused) we don't want the editing state to reset
            if (focusFromEditingDistance) {
                focusFromEditingDistance = false;
            } else {
                buttonIdx = 0;
                isEditingDistance = false;
            }
        }

        @Override
        public void onBlur() {
            super.onBlur();
            applyLiveDistance();
        }

        @Override
        public void onActivate() {
            if (buttonIdx == 0) {
                // Show item configuration dialog
            } else if (buttonIdx == 1) {
                // Toggle editing the distance
                isEditingDistance = !isEditingDistance;
                liveDistance = lodList.getItem(index).getDistanceThreshold();
                invalidate();
            } else if (buttonIdx == 2) {
                // Removal
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (this.isFocused()) {
                if (event.getKey() == MapPlayerInput.Key.LEFT) {
                    buttonIdx = Math.max(0, buttonIdx - 1);
                    isEditingDistance = false;
                    invalidate();
                    return;
                } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                    buttonIdx = Math.min(2, buttonIdx + 1);
                    isEditingDistance = false;
                    invalidate();
                    return;
                } else if (this.isEditingDistance) {
                    if (event.getKey() == MapPlayerInput.Key.UP) {
                        liveDistance = Math.min(10000, liveDistance + 1);
                        liveDistanceChanged = true;
                        invalidate();
                        return;
                    } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                        liveDistance = Math.max(0, liveDistance - 1);
                        liveDistanceChanged = true;
                        invalidate();
                        return;
                    }
                }
            }

            super.onKeyPressed(event);
        }

        @Override
        public void onKeyReleased(MapKeyEvent event) {
            applyLiveDistance();
            super.onKeyReleased(event);
        }

        @Override
        public void onDraw() {
            int selButtonIdx = -1;
            if (this.isFocused()) {
                view.fill(MapColorPalette.getColor(64, 64, 64));
                selButtonIdx = this.buttonIdx;
            } else {
                view.fill(MapColorPalette.getColor(32, 32, 32));
            }

            // Item icon
            if (selButtonIdx == 0) {
                this.view.draw(this.itemIcon, 0, 0);
                this.view.setBlendMode(MapBlendMode.ADD);
                this.view.fill(MapColorPalette.getColor(32, 32, 32));
                this.view.setBlendMode(MapBlendMode.OVERLAY);
                this.view.drawRectangle(0, 0, ITEM_HEIGHT, ITEM_HEIGHT, MapColorPalette.COLOR_WHITE);
            } else {
                this.view.draw(this.itemIcon, 0, 0);
            }

            // View distance threshold
            {
                byte textColor;
                if (isEditingDistance) {
                    textColor = MapColorPalette.COLOR_YELLOW;
                } else if (selButtonIdx == 1) {
                    textColor = MapColorPalette.COLOR_WHITE;
                } else {
                    textColor = MapColorPalette.getColor(158, 158, 158);
                }
                int viewThreshold = isEditingDistance
                        ? liveDistance
                        : lodList.getItem(index).getDistanceThreshold();
                int viewThresholdX = ITEM_HEIGHT + 4;
                int viewThresholdY = 4;
                String text = Integer.toString(viewThreshold);
                view.draw(MapFont.MINECRAFT, viewThresholdX, viewThresholdY, textColor, text);
                if (isEditingDistance) {
                    int textW = (int) view.calcFontSize(MapFont.MINECRAFT, text).getWidth();
                    view.drawLine(viewThresholdX, viewThresholdY + 8,
                            viewThresholdX + textW, viewThresholdY + 8, textColor);
                }
            }

            // Remove button
            {
                byte removeButtonColorBg, removeButtonColorIcon;
                if (lodList.size() == 1) {
                    // Can't remove
                    removeButtonColorBg = MapColorPalette.getColor(64, 64, 64);
                    removeButtonColorIcon = MapColorPalette.getColor(90, 90, 90);
                } else if (selButtonIdx == 2) {
                    removeButtonColorBg = MapColorPalette.getColor(128, 0, 0);
                    removeButtonColorIcon = MapColorPalette.getColor(200, 0, 0);
                } else {
                    removeButtonColorBg = MapColorPalette.getColor(64, 0, 0);
                    removeButtonColorIcon = MapColorPalette.getColor(90, 0, 0);
                }
                int removeButtonBorder = (ITEM_HEIGHT - REMOVE_BUTTON_SIZE) / 2;
                int removeButtonX = this.getWidth() - REMOVE_BUTTON_SIZE - removeButtonBorder;
                int removeButtonY = removeButtonBorder;

                // Rounded rectangle (we cut off the corners so it looks nicer)
                view.fillRectangle(removeButtonX + 1, removeButtonY + 1,
                        REMOVE_BUTTON_SIZE - 2, REMOVE_BUTTON_SIZE - 2,
                        removeButtonColorBg);
                view.drawRectangle(removeButtonX, removeButtonY,
                        REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE,
                        removeButtonColorIcon);
                view.writePixel(removeButtonX, removeButtonY, MapColorPalette.COLOR_TRANSPARENT);
                view.writePixel(removeButtonX + REMOVE_BUTTON_SIZE - 1, removeButtonY, MapColorPalette.COLOR_TRANSPARENT);
                view.writePixel(removeButtonX, removeButtonY + REMOVE_BUTTON_SIZE - 1, MapColorPalette.COLOR_TRANSPARENT);
                view.writePixel(removeButtonX + REMOVE_BUTTON_SIZE - 1,
                        removeButtonY + REMOVE_BUTTON_SIZE - 1,
                        MapColorPalette.COLOR_TRANSPARENT);

                // X
                view.drawLine(removeButtonX + 1,
                        removeButtonY + 1,
                        removeButtonX + REMOVE_BUTTON_SIZE - 2,
                        removeButtonY + REMOVE_BUTTON_SIZE - 2,
                        removeButtonColorIcon);
                view.drawLine(removeButtonX + 1,
                        removeButtonY + REMOVE_BUTTON_SIZE - 2,
                        removeButtonX + REMOVE_BUTTON_SIZE - 2,
                        removeButtonY + 1,
                        removeButtonColorIcon);
            }
        }
    }
}
