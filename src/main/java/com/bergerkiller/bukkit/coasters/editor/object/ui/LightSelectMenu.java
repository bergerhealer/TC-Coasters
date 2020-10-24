package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeLight;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLight.LightType;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetToggleButton;

/**
 * Shows a menu to change the type of light, and light level, of a light object
 */
public class LightSelectMenu extends MapWidgetMenu {
    private final Supplier<PlayerEditState> stateSupplier;

    public LightSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this.stateSupplier = stateSupplier;
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.getColor(170, 140,  0));
        this.labelColor = MapColorPalette.COLOR_BLACK;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        final TrackObjectType<?> type = stateSupplier.get().getObjects().getSelectedType();
        LightType lightType = (type instanceof TrackObjectTypeLight) ?
                ((TrackObjectTypeLight) type).getType() : LightType.BLOCK;
        int lightLevel = (type instanceof TrackObjectTypeLight) ?
                ((TrackObjectTypeLight) type).getLevel() : 15;

        this.addWidget(new MapWidgetText())
            .setColor(MapColorPalette.COLOR_BLACK)
            .setShadowColor(MapColorPalette.COLOR_TRANSPARENT)
            .setText("Light Type")
            .setPosition(32, 10);

        this.addWidget(new MapWidgetToggleButton<LightType>() {
            @Override
            public void onSelectionChanged() {
                stateSupplier.get().getObjects().transformSelectedType(TrackObjectTypeLight.class,
                        t -> t.setType(getSelectedOption()));
            }
        }).addOptions(t -> t.name(), LightType.class)
          .setSelectedOption(lightType)
          .setBounds(32, 20, 52, 14);

        this.addWidget(new MapWidgetText())
            .setColor(MapColorPalette.COLOR_BLACK)
            .setShadowColor(MapColorPalette.COLOR_TRANSPARENT)
            .setText("Light Level")
            .setPosition(32, 50);

        MapWidgetNumberBox levelBox = this.addWidget(new MapWidgetNumberBox() {
            @Override
            public void onAttached() {
                super.onAttached();
                this.setRange(1.0, 15.0);
                this.setIncrement(1.0);
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Light Level";
            }

            @Override
            public void onValueChanged() {
                stateSupplier.get().getObjects().transformSelectedType(TrackObjectTypeLight.class,
                        t -> t.setLevel((int) getValue()));
            }
        });
        levelBox.setValue((double) lightLevel);
        levelBox.setBounds(25, 61, 66, 11);

        display.playSound(SoundEffect.PISTON_EXTEND);
    }

    @Override
    public void onDetached() {
        display.playSound(SoundEffect.PISTON_CONTRACT);
    }
}
