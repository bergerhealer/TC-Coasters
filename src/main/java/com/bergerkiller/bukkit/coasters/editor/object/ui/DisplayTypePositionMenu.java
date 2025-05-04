package com.bergerkiller.bukkit.coasters.editor.object.ui;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.display.TrackObjectTypeDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import org.bukkit.util.Vector;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adds sliders for the item display clip, brightness and size
 */
public class DisplayTypePositionMenu extends TypePositionMenu {

    public DisplayTypePositionMenu(Supplier<PlayerEditState> stateSupplier) {
        super(stateSupplier);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Adjust clip
        {
            MapWidgetNumberBox clip = slider(new MapWidgetNumberBox() {
                @Override
                public void onValueChanged() {
                    this.setTextOverride((getValue() == 0.0) ? "<Disabled>" : null);
                    setProperty(TrackObjectTypeDisplay.class,
                            TrackObjectTypeDisplay::setClip,
                            getValue());
                }
            }).label("Clip").topPadding(2).create();

            clip.setInitialValue(getProperty(TrackObjectTypeDisplay.class,
                    TrackObjectTypeDisplay::getClip, 0.0));
            clip.setTextOverride((clip.getValue() == 0.0) ? "<Disabled>" : null);
        }

        // Adjust brightness (two number boxes. Resetting one resets both)
        {
            BrightnessNumberBox blockLight = slider(new BrightnessNumberBox(Brightness::withBlockLight))
                    .label("Block L.").topPadding(2).create();
            BrightnessNumberBox skyLight = slider(new BrightnessNumberBox(Brightness::withSkyLight))
                    .label("Sky L.").create();

            Brightness brightness = getProperty(TrackObjectTypeDisplay.class,
                    TrackObjectTypeDisplay::getBrightness, Brightness.UNSET);
            if (brightness == Brightness.UNSET) {
                blockLight.setInitialValue(0.0);
                blockLight.setTextOverride("<Unset>");
                skyLight.setInitialValue(0.0);
                skyLight.setTextOverride("<Unset>");
            } else {
                blockLight.setInitialValue(brightness.blockLight());
                blockLight.setTextOverride(null);
                skyLight.setInitialValue(brightness.skyLight());
                skyLight.setTextOverride(null);
            }
        }

        // Adjust size
        {
            MapWidgetSizeBox size = slider(new MapWidgetSizeBox() {
                @Override
                public void onSizeChanged() {
                    setProperty(TrackObjectTypeDisplay.class,
                            TrackObjectTypeDisplay::setSize,
                            new Vector(x.getValue(), y.getValue(), z.getValue()));

                }
            }).height(35)
              .label(3, "Size X")
              .label(15, "Size Y")
              .label(27, "Size Z")
              .create();

            Vector sizeVal = getProperty(TrackObjectTypeDisplay.class,
                    TrackObjectTypeDisplay::getSize,
                    new Vector(1, 1, 1));
            size.setInitialSize(sizeVal.getX(), sizeVal.getY(), sizeVal.getZ());
        }
    }

    private class BrightnessNumberBox extends MapWidgetNumberBox {
        private final BiFunction<Brightness, Integer, Brightness> setter;
        private boolean suppressValueChange = false;

        public BrightnessNumberBox(BiFunction<Brightness, Integer, Brightness> setter) {
            this.setter = setter;
            setRange(0.0, 15.0);
            setIncrement(1.0);
        }

        @Override
        public void onValueChanged() {
            if (suppressValueChange) {
                return;
            }

            forAllBrightnessWidgets(w -> w.setTextOverride(null));

            setProperty(TrackObjectTypeDisplay.class,
                    (display, value) -> {
                        Brightness curr = display.getBrightness();
                        if (curr == Brightness.UNSET) {
                            curr = Brightness.NONE;
                        }
                        return display.setBrightness(setter.apply(curr, value.intValue()));
                    },
                    getValue());
        }

        public void setUnset() {
            setTextOverride("<Unset>");
            suppressValueChange = true;
            setValue(0.0);
            suppressValueChange = false;
        }

        @Override
        public void onResetValue() {
            forAllBrightnessWidgets(BrightnessNumberBox::setUnset);

            setProperty(TrackObjectTypeDisplay.class,
                    TrackObjectTypeDisplay::setBrightness,
                    Brightness.UNSET);
        }

        private void forAllBrightnessWidgets(Consumer<BrightnessNumberBox> action) {
            action.accept(this);
            for (MapWidget widget : getParent().getWidgets()) {
                if (widget != this && widget instanceof BrightnessNumberBox) {
                    action.accept((BrightnessNumberBox) widget);
                }
            }
        }
    }
}
