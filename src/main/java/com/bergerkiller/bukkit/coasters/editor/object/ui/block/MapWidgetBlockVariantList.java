package com.bergerkiller.bukkit.coasters.editor.object.ui.block;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.BlockState;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;

/**
 * Interactive widget that pops down a full list of item base types when
 * activated, and allows switching between item/block variants using left/right.
 */
public abstract class MapWidgetBlockVariantList extends MapWidget implements SetValueTarget, BlockChangedListener {
    private final List<BlockChangedListener> itemChangedListeners = new ArrayList<BlockChangedListener>();
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);
    private final MapTexture background;
    private List<BlockData> variants;
    private MapBlockTextureCache iconCache = MapBlockTextureCache.create(16, 16);
    private int variantIndex = 0;

    public MapWidgetBlockVariantList() {
        this.background = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_selector_bg.png");
        this.setSize(100, 18);
        this.setFocusable(true);
        this.variants = new ArrayList<BlockData>(0);

        this.nav_left.setPosition(0, 4);
        this.nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 4);
        this.nav_left.setVisible(false);
        this.nav_right.setVisible(false);
        this.addWidget(this.nav_left);
        this.addWidget(this.nav_right);
        this.setRetainChildWidgets(true);

        this.itemChangedListeners.add(this);
    }

    public BlockData getBlock() {
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            return this.variants.get(this.variantIndex);
        } else {
            return null;
        }
    }

    public MapWidgetBlockVariantList setBlock(BlockData block) {
        if (block == null) {
            this.variants = new ArrayList<BlockData>(0);
            this.variantIndex = 0;
            this.invalidate();
            this.fireItemChangeEvent();
            return this;
        }

        // Find all block data variants
        this.variants.clear();
        this.variants.add(block);
        for (BlockState<?> state : block.getStates().keySet()) {
            List<BlockData> tmp = new ArrayList<BlockData>(this.variants);
            this.variants.clear();
            for (Comparable<?> value : state.values()) {
                for (BlockData original : tmp) {
                    try {
                        this.variants.add(original.setState(state, value));
                    } catch (Throwable t) {} // meh!
                }
            }
        }

        // Find the item in the variants to deduce the currently selected index
        this.variantIndex = 0;
        for (int i = 0; i < this.variants.size(); i++) {
            BlockData variant = this.variants.get(i);
            if (variant.equals(block)) {
                this.variantIndex = i;
                break; // Final!
            }
        }

        this.invalidate();
        this.fireItemChangeEvent();

        return this;
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Block Information";
    }

    @Override
    public boolean acceptTextValue(String value) {
        // Try parsing the item name from the value
        value = value.trim();
        int nameEnd = 0;
        while (nameEnd < value.length()) {
            if (value.charAt(nameEnd) == '{' || value.charAt(nameEnd) == ' ') {
                break;
            } else {
                nameEnd++;
            }
        }
        String itemName = value.substring(0, nameEnd);
        if (nameEnd >= value.length()) {
            value = "";
        } else {
            value = value.substring(nameEnd).trim();
        }
        if (!ParseUtil.isNumeric(itemName)) {
            // Item name
            Material newItemMaterial = ParseUtil.parseMaterial(itemName, null);
            if (newItemMaterial == null) {
                return false;
            }
            BlockData newBlock = BlockData.fromMaterial(newItemMaterial);

            // Update
            this.setBlock(newBlock);
        } else {
            // Variant index (no item name specified)
            try {
                this.setVariantIndex(Integer.parseInt(itemName));
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void onFocus() {
        nav_left.setVisible(true);
        nav_right.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_left.setVisible(false);
        nav_right.setVisible(false);
    }

    @Override
    public void onDraw() {
        // Subregion where things are drawn
        // To the left and right are navigation buttons
        int selector_edge = this.nav_left.getWidth()+1;
        MapCanvas itemView = this.view.getView(selector_edge, 0, this.getWidth() - 2*selector_edge, this.getHeight());

        // Background
        itemView.draw(this.background, 0, 0);

        // Draw the same item with -2 to +2 variant indices
        int x = 1;
        int y = 1;
        for (int index = this.variantIndex - 2; index <= this.variantIndex + 2; index++) {
            // Check index valid
            if (index >= 0 && index < this.variants.size()) {
                itemView.draw(this.iconCache.get(this.variants.get(index)), x, y);
            }
            x += 17;
        }

        // If focused, show something to indicate that
        if (this.isFocused()) {
            int fx = 1 + 2 * 17;
            int fy = 1;
            itemView.drawRectangle(fx, fy, 16, 16, MapColorPalette.COLOR_RED);
        }
    }

    private void changeVariantIndex(int offset) {
        this.setVariantIndex(this.variantIndex + offset);
    }

    private void setVariantIndex(int newVariantIndex) {
        if (newVariantIndex < 0) {
            newVariantIndex = 0;
        } else if (newVariantIndex >= this.variants.size()) {
            newVariantIndex = this.variants.size()-1;
        }
        if (this.variantIndex == newVariantIndex) {
            return;
        }
        this.variantIndex = newVariantIndex;
        this.invalidate();
        this.fireItemChangeEvent();
        this.display.playSound(SoundEffect.CLICK);
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            nav_left.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.stopFocus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.LEFT) {
            changeVariantIndex(-1 - (event.getRepeat() / 40));
            nav_left.sendFocus();
        } else if (event.getKey() == Key.RIGHT) {
            changeVariantIndex(1 + (event.getRepeat() / 40));
            nav_right.sendFocus();
        } else {
            super.onKeyPressed(event);
        }
    }

    /**
     * Registers a listener called when the item is changed.
     * 
     * @param listener
     * @param fireEventNow when true, fires an item change event right now while registering
     */
    public void registerItemChangedListener(BlockChangedListener listener, boolean fireEventNow) {
        this.itemChangedListeners.add(listener);
        if (fireEventNow) {
            listener.onBlockChanged(this.getBlock());
        }
    }

    private void fireItemChangeEvent() {
        BlockData block = this.getBlock();
        for (BlockChangedListener listener : this.itemChangedListeners) {
            listener.onBlockChanged(block);
        }
    }
}
