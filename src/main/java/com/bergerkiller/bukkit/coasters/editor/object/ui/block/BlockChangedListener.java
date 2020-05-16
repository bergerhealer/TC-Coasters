package com.bergerkiller.bukkit.coasters.editor.object.ui.block;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * General interface for handling when block data in a view changes
 */
public interface BlockChangedListener {
    void onBlockChanged(BlockData block);
}
