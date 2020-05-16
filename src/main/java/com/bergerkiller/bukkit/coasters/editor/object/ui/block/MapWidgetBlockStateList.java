package com.bergerkiller.bukkit.coasters.editor.object.ui.block;

import java.awt.Dimension;
import java.util.Map;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.BlockState;

public class MapWidgetBlockStateList extends MapWidget {
    private static final int ROW_HEIGHT = 8;
    private static final int NAME_STATE_GAP = 2;
    private BlockData block = null;

    public MapWidgetBlockStateList() {
        this.setFocusable(false);
        this.setDepthOffset(2);
    }

    public void setBlock(BlockData block) {
        if (this.block != block) {
            this.block = block;
            if (this.block != null) {
                this.setSize(this.getWidth(),
                        NAME_STATE_GAP + (1 + this.block.getStates().size()) * ROW_HEIGHT);
            }
            this.invalidate();
        }
    }

    @Override
    public void onDraw() {
        if (this.block == null) {
            return;
        }
        int y = 0;

        // Block name
        drawText(y, this.block.getBlockName());
        y += ROW_HEIGHT + NAME_STATE_GAP;
        for (Map.Entry<BlockState<?>, Comparable<?>> entry : this.block.getStates().entrySet()) {
            String text = entry.getKey().name() + " = " + entry.getKey().valueName(entry.getValue());
            drawText(y, text);
            y += ROW_HEIGHT;
        }
    }

    private void drawText(int y, String text) {
        Dimension size = view.calcFontSize(MapFont.MINECRAFT, text);
        int x = (this.getWidth() - size.width) / 2;
        view.fillRectangle(x, y, size.width + 1, size.height, MapColorPalette.COLOR_BLACK);
        view.draw(MapFont.MINECRAFT, x + 1, y, MapColorPalette.COLOR_WHITE, text);
        y += ROW_HEIGHT;
    }
}
