package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.DragListener;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

/**
 * Shows sliders for adjusting the x/y/z position of the track object,
 * and its orientation.
 */
public class TypePositionMenu extends MapWidgetMenu implements DragListener {
    protected final PlayerEditState state;
    protected final MapWidgetScroller scroller = new MapWidgetScroller();
    protected int slider_width = 86;
    private MapWidgetNumberBox num_width;
    private MapWidgetNumberBox num_pos_x, num_pos_y, num_pos_z;
    private MapWidgetNumberBox num_rot_x, num_rot_y, num_rot_z;

    public TypePositionMenu(Supplier<PlayerEditState> stateSupplier) {
        this.state = stateSupplier.get();
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        this.scroller.setBounds(5, 5, getWidth() - 7, getHeight() - 10);
        this.scroller.setScrollPadding(20);
        this.addWidget(this.scroller);
    }

    /**
     * Adds a slider at the bottom of this position menu. Automatically calculates
     * the position of the slider. Assumes 1 pixel of padding above.
     *
     * @param labelText Text to put left of the widget
     * @param widget Widget to add
     * @return Input widget
     * @param <T> Widget type
     */
    protected <T extends MapWidget> T addSlider(String labelText, T widget) {
        return slider(widget).label(labelText).create();
    }

    /**
     * Starts setting up the layout of a new slider. Call create() to add it.
     *
     * @param widget Widget
     * @return builder
     * @param <T> Widget type
     */
    protected <T extends MapWidget> SliderBuilder<T> slider(T widget) {
        return new SliderBuilder<T>(widget);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        num_width = addSlider("Width", new MapWidgetNumberBox() { // Object width
            @Override
            public String getAcceptedPropertyName() {
                return "Object Width";
            }

            @Override
            public void onValueChanged() {
                state.getObjects().transformSelectedType(type -> type.setWidth(getValue()));
            }
        });
        num_width.setRange(0.0, Double.MAX_VALUE);
        num_width.setInitialValue(width());

        num_pos_x = addSlider("Pos.X", new MapWidgetNumberBox() { // Position X
            @Override
            public String getAcceptedPropertyName() {
                return "Position X-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_x.setInitialValue(position().getX());

        num_pos_y = addSlider("Pos.Y", new MapWidgetNumberBox() { // Position Y
            @Override
            public String getAcceptedPropertyName() {
                return "Position Y-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_y.setInitialValue(position().getY());

        num_pos_z = addSlider("Pos.Z", new MapWidgetNumberBox() { // Position Z
            @Override
            public String getAcceptedPropertyName() {
                return "Position Z-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_z.setInitialValue(position().getZ());

        num_rot_x = addSlider("Pitch", new MapWidgetNumberBox() { // Rotation X (pitch)
            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Pitch";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_x.setIncrement(0.1);
        num_rot_x.setInitialValue(rotation().getX());

        num_rot_y = addSlider("Yaw", new MapWidgetNumberBox() { // Rotation Y (yaw)
            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Yaw";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_y.setIncrement(0.1);
        num_rot_y.setInitialValue(rotation().getY());

        num_rot_z = addSlider("Roll", new MapWidgetNumberBox() { // Rotation Z (roll)
            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Roll";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_z.setIncrement(0.1);
        num_rot_z.setInitialValue(rotation().getZ());

        state.getObjects().addDragListener(this);
        display.playSound(SoundEffect.PISTON_EXTEND);
    }

    @Override
    public void onDetached() {
        super.onDetached();

        state.getObjects().removeDragListener(this);
        display.playSound(SoundEffect.PISTON_CONTRACT);
    }

    private Vector position() {
        Matrix4x4 transform = state.getObjects().getSelectedType().getTransform();
        return (transform == null) ? new Vector() : transform.toVector();
    }

    private Vector rotation() {
        Matrix4x4 transform = state.getObjects().getSelectedType().getTransform();
        return (transform == null) ? new Vector() : transform.getYawPitchRoll();
    }

    private double width() {
        return state.getObjects().getSelectedType().getWidth();
    }

    protected <T extends TrackObjectType<?>, P> P getProperty(Class<T> type, Function<T, P> getter, P defaultValue) {
        TrackObjectType<?> selected = state.getObjects().getSelectedType();
        if (type.isInstance(selected)) {
            return getter.apply(type.cast(selected));
        } else {
            return defaultValue;
        }
    }

    protected <T extends TrackObjectType<?>, P> void setProperty(Class<T> type, BiFunction<T, P, T> setter, P value) {
        state.getObjects().transformSelectedType(type, t -> setter.apply(t, value));
    }

    private void updateTransform() {
        Matrix4x4 transform;
        if (this.num_pos_x.getValue() == 0.0 &&
            this.num_pos_y.getValue() == 0.0 &&
            this.num_pos_z.getValue() == 0.0 &&
            this.num_rot_x.getValue() == 0.0 &&
            this.num_rot_y.getValue() == 0.0 &&
            this.num_rot_z.getValue() == 0.0)
        {
            transform = null; // identity
        } else {
            transform = new Matrix4x4();
            transform.translate(this.num_pos_x.getValue(),
                                this.num_pos_y.getValue(),
                                this.num_pos_z.getValue());
            transform.rotateYawPitchRoll(this.num_rot_x.getValue(),
                                         this.num_rot_y.getValue(),
                                         this.num_rot_z.getValue());
        }
        state.getObjects().transformSelectedType(type -> type.setTransform(transform));
    }

    @Override
    public void onDrag(double delta) {
        MapWidget focused = this.display.getFocusedWidget();
        if (focused instanceof MapWidgetNumberBox) {
            MapWidgetNumberBox num = (MapWidgetNumberBox) focused;
            num.setValue(num.getValue() + delta);
        }
    }

    protected class SliderBuilder<T extends MapWidget> {
        private final T widget;
        private int topPadding = 1;
        private final List<Label> labels = new ArrayList<>();

        public SliderBuilder(T widget) {
            this.widget = widget;
            widget.setSize(slider_width, 11);
        }

        public SliderBuilder<T> topPadding(int topPadding) {
            this.topPadding = topPadding;
            return this;
        }

        public SliderBuilder<T> width(int width) {
            widget.setSize(width, widget.getHeight());
            return this;
        }

        public SliderBuilder<T> height(int height) {
            widget.setSize(widget.getWidth(), height);
            return this;
        }

        public SliderBuilder<T> label(String text) {
            return label(3, text);
        }

        public SliderBuilder<T> label(int y, String text) {
            if (!LogicUtil.nullOrEmpty(text)) {
                labels.add(new Label(y, text));
            }
            return this;
        }

        public T create() {
            int y;
            if (scroller.getContainer().getWidgetCount() == 0) {
                y = 0;
            } else {
                y = scroller.getContainer().getHeight();
                for (MapWidget child : scroller.getContainer().getWidgets()) {
                    y = Math.max(y, child.getY() + child.getHeight());
                }
                y += topPadding;
            }

            // Position widget itself and add it
            widget.setPosition(25, y);
            scroller.addContainerWidget(widget);

            // Create labels left of the widget
            for (Label labelCfg : labels) {
                MapWidgetText label = new MapWidgetText();
                label.setFont(MapFont.TINY);
                label.setText(labelCfg.text);
                label.setPosition(0, labelCfg.y + y);
                label.setColor(MapColorPalette.getSpecular(labelColor, 0.5f));
                scroller.addContainerWidget(label);
            }

            return widget;
        }
    }

    private static class Label {
        public final int y;
        public final String text;

        public Label(int y, String text) {
            this.y = y;
            this.text = text;
        }
    }
}
