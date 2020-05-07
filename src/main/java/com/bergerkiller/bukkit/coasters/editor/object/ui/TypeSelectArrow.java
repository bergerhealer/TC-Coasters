package com.bergerkiller.bukkit.coasters.editor.object.ui;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

public class TypeSelectArrow extends MapWidget {
    private final MapTexture texture;
    private boolean pressed;

    public TypeSelectArrow(TCCoasters plugin, boolean right) {
        MapTexture texture = MapTexture.loadPluginResource(plugin, "com/bergerkiller/bukkit/coasters/resources/large_right_arrow.png");
        if (right) {
            this.texture = texture;
        } else {
            this.texture = MapTexture.flipH(texture);
        }
        this.setSize(this.texture.getWidth(), this.texture.getHeight());
        this.pressed = false;
    }

    public void setPressed(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            this.invalidate();
        }
    }

    @Override
    public void onDraw() {
        this.view.draw(this.texture, 0, 0, this.pressed ?
                MapColorPalette.COLOR_GREEN : MapColorPalette.COLOR_WHITE);
    }

    @Override
    public void onAttached() {
        this.setVisible(false);
    }
}
