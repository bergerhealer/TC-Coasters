package com.bergerkiller.bukkit.coasters.editor.object.ui.lod;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A list of LODItemStack entries in a List of LODs.
 * Has methods for adding new LODs, removing them and
 * modifying them.
 */
public abstract class ItemLODListWidget extends MapWidgetScroller {
    private static final int LIST_WIDTH = 106;
    private static final int LIST_HEIGHT = 90;
    private static final int ITEM_HEIGHT = 17;
    private static final int ICON_SIZE = ITEM_HEIGHT - 1;
    private static final int REMOVE_BUTTON_SIZE = 11;
    private static final int BUTTON_INDEX_SET_ITEM = 0;
    private static final int BUTTON_INDEX_HIDE_ITEM = 1;
    private static final int BUTTON_INDEX_THRESHOLD = 2;
    private static final int BUTTON_INDEX_DELETE = 3;
    private final TCCoasters plugin;
    private final MapTexture itemHiddenTexture;
    private LODItemStack.List lodList;

    /**
     * Called when the LOD list is re-configured
     *
     * @param lodList Updated List of LOD ItemStacks
     */
    public abstract void onLODChanged(LODItemStack.List lodList);

    public ItemLODListWidget(TCCoasters plugin, LODItemStack.List lodList) {
        this.plugin = plugin;
        this.lodList = lodList;
        this.itemHiddenTexture = MapTexture.loadPluginResource(plugin,
                "com/bergerkiller/bukkit/coasters/resources/item_hidden_button.png");
        this.setSize(LIST_WIDTH, LIST_HEIGHT);
        this.setScrollPadding(5);

        for (int i = 0; i < lodList.size(); i++) {
            this.addContainerWidget(new LODItemWidget(i));
        }

        this.addContainerWidget(new ExpandWidget());
    }

    private LODItemWidget getItemWidget(int lodIndex) {
        return (LODItemWidget) getContainer().getWidget(lodIndex);
    }

    public void removeLODItem(int lodIndex) {
        if (lodList.size() == 1) {
            return;
        }

        getContainer().removeWidget(getContainer().getWidget(lodIndex));
        lodList = lodList.removeLOD(lodIndex);

        for (int i = lodIndex; i < lodList.size(); i++) {
            LODItemWidget itemWidget = getItemWidget(i);
            itemWidget.resetEditingDistance();
            itemWidget.updateIndex(i);
            itemWidget.invalidate();
        }

        ((ListRowWidget) getContainer().getWidget(getContainer().getWidgetCount() - 1)).updateIndex(lodList.size());

        getItemWidget(Math.min(lodList.size() - 1, lodIndex)).focus();

        onLODChanged(lodList);
    }

    public void addNewLOD() {
        lodList = lodList.addNewLOD();

        // Swap out the expand button with a new LOD item widget, and focus it
        getContainer().removeWidget(getContainer().getWidget(getContainer().getWidgetCount() - 1));
        addContainerWidget(new LODItemWidget(lodList.size() - 1)).focus();
        addContainerWidget(new ExpandWidget());

        onLODChanged(lodList);
    }

    private static class ListRowWidget extends MapWidget {
        protected int index;

        public ListRowWidget(int index) {
            this.updateIndex(index);
            this.setFocusable(true);
        }

        public void updateIndex(int index) {
            this.index = index;
            this.setBounds(0, index * ITEM_HEIGHT, LIST_WIDTH, ITEM_HEIGHT);
        }

        protected byte getRowBackgroundColor() {
            return isFocused()
                    ? MapColorPalette.getColor(70, 70, 70)
                    : MapColorPalette.getColor(40, 40, 40);
        }

        @Override
        public void onDraw() {
            view.fill(getRowBackgroundColor());
        }
    }

    private class LODItemWidget extends ListRowWidget {
        private int buttonIdx = BUTTON_INDEX_SET_ITEM;
        private boolean isEditingDistance = false;
        private int liveDistance = -1;
        private boolean liveDistanceChanged = false;
        private boolean focusFromEditingDistance = false;
        private boolean isHoldingKeyUp = false;
        private boolean isHoldingKeyDown = false;
        private final MapTexture itemIcon;

        public LODItemWidget(int index) {
            super(index);
            this.itemIcon = MapTexture.createEmpty(ICON_SIZE, ICON_SIZE);
            updateItemImage();
        }

        public void resetEditingDistance() {
            liveDistance = -1;
            isEditingDistance = false;
            isHoldingKeyDown = false;
            isHoldingKeyUp = false;
            invalidate();
        }

        public void updateItemImage() {
            this.itemIcon.clear();
            lodList.getItem(this.index).drawIcon(plugin, this.itemIcon);
            this.invalidate();
        }

        public void applyLiveDistance() {
            if (liveDistanceChanged) {
                liveDistanceChanged = false;
            } else {
                return;
            }

            // For focusing the right row later
            LODItemStack distanceUpdatedLODItem = lodList.getItem(index).withDistanceThreshold(liveDistance);

            // Update distances. This could result in items being re-ordered.
            LODItemStack.List oldList = lodList;
            LODItemStack.List newList = lodList.update(index, distanceUpdatedLODItem);
            lodList = newList; // Ensures update functions work properly

            // Re-sync all widget in case order / items have changed
            for (int i = 0; i < oldList.size(); i++) {
                LODItemStack oldLODItem = oldList.getItem(i);
                LODItemStack newLODItem = newList.getItem(i);
                LODItemWidget widget = getItemWidget(i);

                if (widget.isEditingDistance) {
                    widget.resetEditingDistance();
                }

                if (!oldLODItem.equals(newLODItem)) {
                    if (!Objects.equals(oldLODItem.getItem(), newLODItem.getItem())) {
                        widget.updateItemImage();
                    }
                    widget.buttonIdx = BUTTON_INDEX_SET_ITEM;
                    widget.isEditingDistance = false;
                    widget.invalidate();
                }
            }

            // Look up the item this widget is now at. Current index has preference.
            // Then focus this one and ensure editing of the distance is resumed (in case it was moved)
            if (isFocused()) {
                LODItemWidget itemWidget = null;
                if (distanceUpdatedLODItem.equals(lodList.getItem(index))) {
                    itemWidget = getItemWidget(index);
                } else {
                    for (int i = 0; i < lodList.size(); i++) {
                        if (i != index && distanceUpdatedLODItem.equals(lodList.getItem(i))) {
                            itemWidget = getItemWidget(i);
                            break;
                        }
                    }
                }
                if (itemWidget != null) {
                    itemWidget.buttonIdx = BUTTON_INDEX_THRESHOLD;
                    itemWidget.isEditingDistance = true;
                    itemWidget.liveDistance = distanceUpdatedLODItem.getDistanceThreshold();
                    itemWidget.invalidate();

                    if (itemWidget != this) {
                        itemWidget.focusFromEditingDistance = true;
                        itemWidget.focus();
                    }
                }
            }

            onLODChanged(lodList);
        }

        @Override
        public void onFocus() {
            super.onFocus();

            // When a new item is selected (focused) we don't want the editing state to reset
            if (focusFromEditingDistance) {
                focusFromEditingDistance = false;
            } else {
                buttonIdx = BUTTON_INDEX_SET_ITEM;
                isEditingDistance = false;
                isHoldingKeyUp = false;
                isHoldingKeyDown = false;
            }
        }

        @Override
        public void onBlur() {
            super.onBlur();
            applyLiveDistance();
        }

        @Override
        public boolean onItemDrop(Player player, ItemStack item) {
            lodList = lodList.update(index, lodList.getItem(index).withItem(item));
            updateItemImage();
            onLODChanged(lodList);
            return true;
        }

        @Override
        public void onActivate() {
            if (buttonIdx == BUTTON_INDEX_SET_ITEM) {
                // Show item configuration dialog
                parent.addWidget(new ItemLODItemSelectMenu() {
                    @Override
                    public ItemStack getInitialItem() {
                        return lodList.getItem(index).getItem();
                    }

                    @Override
                    public void onItemUpdated(ItemStack item) {
                        lodList = lodList.update(index, lodList.getItem(index).withItem(item));
                        updateItemImage();
                        onLODChanged(lodList);
                    }
                });
            } else if (buttonIdx == BUTTON_INDEX_HIDE_ITEM) {
                // Set item to nothing
                buttonIdx = BUTTON_INDEX_SET_ITEM;
                lodList = lodList.update(index, lodList.getItem(index).withItem(null));
                updateItemImage();
                onLODChanged(lodList);
            } else if (buttonIdx == BUTTON_INDEX_THRESHOLD) {
                // Toggle editing the distance
                isEditingDistance = !isEditingDistance;
                liveDistance = lodList.getItem(index).getDistanceThreshold();
                invalidate();
            } else if (buttonIdx == BUTTON_INDEX_DELETE) {
                // Removal
                removeLODItem(index);
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (this.isFocused()) {
                if (event.getKey() == MapPlayerInput.Key.LEFT) {
                    buttonIdx = Math.max(0, buttonIdx - 1);
                    if (buttonIdx == BUTTON_INDEX_HIDE_ITEM && lodList.getItem(index).getItem() == null) {
                        buttonIdx = BUTTON_INDEX_SET_ITEM;
                    }
                    isEditingDistance = false;
                    invalidate();
                    return;
                } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                    // With only 1 LOD, only the item can be configured (button idx 0)
                    // So in that case left/right does nothing
                    if (lodList.size() > 1) {
                        buttonIdx = Math.min(3, buttonIdx + 1);
                        if (buttonIdx == BUTTON_INDEX_HIDE_ITEM && lodList.getItem(index).getItem() == null) {
                            buttonIdx = BUTTON_INDEX_THRESHOLD;
                        }
                        isEditingDistance = false;
                        invalidate();
                    }
                    return;
                } else if (this.isEditingDistance) {
                    if (event.getKey() == MapPlayerInput.Key.UP) {
                        isHoldingKeyUp = true;
                        liveDistance = Math.min(10000, liveDistance + getIncrease(event.getRepeat()));
                        liveDistanceChanged = true;
                        invalidate();
                        return;
                    } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                        isHoldingKeyDown = true;
                        liveDistance = Math.max(0, liveDistance - getIncrease(event.getRepeat()));
                        liveDistanceChanged = true;
                        invalidate();
                        return;
                    }
                }
            }

            super.onKeyPressed(event);
        }

        private int getIncrease(int repeat) {
            return (int) Math.pow(2.0, (double) (repeat / 40));
        }

        @Override
        public void onKeyReleased(MapKeyEvent event) {
            applyLiveDistance();
            if (event.getKey() == MapPlayerInput.Key.UP) {
                isHoldingKeyUp = false;
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                isHoldingKeyDown = false;
            }
            super.onKeyReleased(event);
        }

        @Override
        public void onDraw() {
            super.onDraw();

            int selButtonIdx = isFocused() ? this.buttonIdx : -1;

            // Item icon
            if (selButtonIdx == BUTTON_INDEX_SET_ITEM) {
                this.view.draw(this.itemIcon, 0, 0);
                this.view.setBlendMode(MapBlendMode.ADD);
                this.view.fillRectangle(0, 0, ITEM_HEIGHT, ITEM_HEIGHT,
                        MapColorPalette.getColor(32, 32, 32));
                this.view.setBlendMode(MapBlendMode.OVERLAY);
                this.view.drawRectangle(0, 0, ITEM_HEIGHT, ITEM_HEIGHT, MapColorPalette.COLOR_WHITE);
            } else {
                this.view.draw(this.itemIcon, 0, 0);
            }

            // Make item invisible / hide item toggle
            {
                MapCanvas hideState;
                int hideIconW = itemHiddenTexture.getWidth() / 3;
                int hideIconH = itemHiddenTexture.getHeight();
                if (lodList.size() == 1 || lodList.getItem(index).getItem() == null) {
                    hideState = itemHiddenTexture.getView(hideIconW * 2, 0, hideIconW, hideIconH);
                } else if (selButtonIdx == BUTTON_INDEX_HIDE_ITEM) {
                    hideState = itemHiddenTexture.getView(hideIconW, 0, hideIconW, hideIconH);
                } else {
                    hideState = itemHiddenTexture.getView(0, 0, hideIconW, hideIconH);
                }
                this.view.draw(hideState, ITEM_HEIGHT + 4, (ITEM_HEIGHT - hideIconH) / 2);
            }

            // View distance threshold
            {
                byte textColor;
                if (isEditingDistance) {
                    textColor = MapColorPalette.COLOR_YELLOW;
                } else if (selButtonIdx == BUTTON_INDEX_THRESHOLD) {
                    textColor = MapColorPalette.COLOR_WHITE;
                } else {
                    textColor = MapColorPalette.getColor(158, 158, 158);
                }
                int viewThreshold = isEditingDistance
                        ? liveDistance
                        : lodList.getItem(index).getDistanceThreshold();
                int viewThresholdX = ITEM_HEIGHT + 19;
                int viewThresholdY = 5;
                String text = Integer.toString(viewThreshold);
                view.draw(MapFont.MINECRAFT, viewThresholdX, viewThresholdY, textColor, text);
                if (isEditingDistance) {
                    int lineEndX = viewThresholdX + (int) view.calcFontSize(MapFont.MINECRAFT, text).getWidth() - 2;
                    int lineY = viewThresholdY + 8;
                    view.drawLine(viewThresholdX, lineY, lineEndX, lineY, textColor);

                    // Show up/down arrow, colored if holding up
                    int arrowX = lineEndX + 2;
                    byte activeColor = MapColorPalette.getColor(200, 0, 0);
                    {
                        byte upColor = isHoldingKeyUp ? activeColor : textColor;
                        view.drawPixel(arrowX + 2, viewThresholdY, upColor);
                        view.drawLine(arrowX + 1, viewThresholdY + 1, arrowX + 3, viewThresholdY + 1, upColor);
                        view.drawLine(arrowX, viewThresholdY + 2, arrowX + 4, viewThresholdY + 2, upColor);
                    }
                    {
                        byte downColor = isHoldingKeyDown ? activeColor : textColor;
                        view.drawLine(arrowX, lineY - 2, arrowX + 4, lineY - 2, downColor);
                        view.drawLine(arrowX + 1, lineY - 1, arrowX + 3, lineY - 1, downColor);
                        view.drawPixel(arrowX + 2, lineY, downColor);
                    }
                }
            }

            // Remove button
            {
                byte removeButtonColorBg, removeButtonColorIcon;
                if (lodList.size() == 1) {
                    // Can't remove
                    removeButtonColorBg = MapColorPalette.getColor(64, 64, 64);
                    removeButtonColorIcon = MapColorPalette.getColor(90, 90, 90);
                } else if (selButtonIdx == BUTTON_INDEX_DELETE) {
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
                byte rowBgColor = getRowBackgroundColor();
                view.fillRectangle(removeButtonX + 1, removeButtonY + 1,
                        REMOVE_BUTTON_SIZE - 2, REMOVE_BUTTON_SIZE - 2,
                        removeButtonColorBg);
                view.drawRectangle(removeButtonX, removeButtonY,
                        REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE,
                        removeButtonColorIcon);
                view.writePixel(removeButtonX, removeButtonY, rowBgColor);
                view.writePixel(removeButtonX + REMOVE_BUTTON_SIZE - 1, removeButtonY, rowBgColor);
                view.writePixel(removeButtonX, removeButtonY + REMOVE_BUTTON_SIZE - 1, rowBgColor);
                view.writePixel(removeButtonX + REMOVE_BUTTON_SIZE - 1,
                        removeButtonY + REMOVE_BUTTON_SIZE - 1,
                        rowBgColor);

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

    /** Shows a button to add one more new LOD (duplicating the last one) */
    private class ExpandWidget extends ListRowWidget {

        public ExpandWidget() {
            super(lodList.size());
        }

        @Override
        public void onActivate() {
            addNewLOD();
        }

        @Override
        public void onDraw() {
            super.onDraw();

            MapWidgetButton.fillBackground(view.getView(2, 2, getWidth() - 4, getHeight() - 4),
                    true, isFocused());

            byte textColor, textShadowColor;
            if (this.isFocused()) {
                textColor = MapColorPalette.getColor(255, 255, 160);
                textShadowColor = MapColorPalette.getColor(63, 63, 40);
            } else {
                textColor = MapColorPalette.getColor(224, 224, 224);
                textShadowColor = MapColorPalette.getColor(56, 56, 56);
            }

            int textX = 20;
            int textY = 5;
            String text = "Add new LOD";

            view.draw(MapFont.MINECRAFT, textX + 1, textY + 1, textShadowColor, text);
            view.draw(MapFont.MINECRAFT, textX, textY, textColor, text);
        }
    }
}
