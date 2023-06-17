package com.bergerkiller.bukkit.coasters.editor.object.ui;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.objects.display.TrackObjectTypeDisplay;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

/**
 * Adds sliders for the item display clip and size
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
}
