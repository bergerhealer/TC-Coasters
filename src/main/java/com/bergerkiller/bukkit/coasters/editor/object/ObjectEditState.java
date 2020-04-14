package com.bergerkiller.bukkit.coasters.editor.object;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

public class ObjectEditState {
    private ItemStack selectedItem;

    public ObjectEditState() {
        this.selectedItem = new ItemStack(MaterialUtil.getFirst("RAIL", "LEGACY_RAILS"));
    }

    public ItemStack getSelectedItem() {
        return this.selectedItem;
    }

    public void setSelectedItem(ItemStack item) {
        this.selectedItem = item;
    }

    public void load(ConfigurationNode config) {
        ItemStack item = config.get("item", ItemStack.class);
        if (item != null) {
            this.selectedItem = item;
        }
    }

    public void save(ConfigurationNode config) {
        if (this.selectedItem == null) {
            config.remove("item");
        } else {
            config.set("item", this.selectedItem);
        }
    }
}

