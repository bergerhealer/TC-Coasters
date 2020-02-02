package com.bergerkiller.bukkit.coasters.editor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public enum PlayerEditMode {
    DISABLED("Disabled (hidden)", 0, 1, PlayerEditMode::createEmptyView),
    CREATE("Create Track", 20, 4, PlayerEditMode::createEmptyView),
    POSITION("Change Position", 0, 1, PlayerEditMode::createPositionView),
    ORIENTATION("Change Orientation", 0, 1, PlayerEditMode::createEmptyView),
    RAILS("Change Rails Block", 0, 1, PlayerEditMode::createRailsView),
    DELETE("Delete Track", 10, 3, PlayerEditMode::createEmptyView);

    private final int _autoInterval;
    private final int _autoDelay;
    private final String _name;
    private final BiConsumer<MapWidgetTabView.Tab, PlayerEditState> _createViewMethod;

    // name: displayed in the UI
    // autoDelay: how many ticks of holding right-click until continuously activating
    // autoInterval: tick interval of activation while holding right-click
    private PlayerEditMode(String name, int autoDelay, int autoInterval, BiConsumer<MapWidgetTabView.Tab, PlayerEditState> createViewMethod) {
        this._name = name;
        this._autoDelay = autoDelay;
        this._autoInterval = autoInterval;
        this._createViewMethod = createViewMethod;
    }

    public boolean autoActivate(int tick) {
        tick -= this._autoDelay;
        return tick >= 0 && (_autoInterval <= 0 || (tick % _autoInterval) == 0);
    }

    public String getName() {
        return this._name;
    }

    public static PlayerEditMode fromName(String name) {
        for (PlayerEditMode mode : values()) {
            if (mode.getName().equals(name)) {
                return mode;
            }
        }
        return CREATE;
    }

    /**
     * Creates the menu for an edit mode in the editor map
     * 
     * @param tab The tab view to fill with widgets
     * @param state The edit state of the player
     */
    public void createView(MapWidgetTabView.Tab tab, PlayerEditState state) {
        this._createViewMethod.accept(tab, state);
    }

    private static void createEmptyView(MapWidgetTabView.Tab tab, PlayerEditState state) {
    }

    private static void createPositionView(MapWidgetTabView.Tab tab, PlayerEditState state) {
        final int ADJ_BTN_WIDTH = 22;
        final int ADJ_BTN_HEIGHT = 12;
        final int ADJ_BTN_OFFSET = ADJ_BTN_WIDTH + 2;
        int y = 0;
        for (final char axis : new char[] { 'x', 'y', 'z'} ) {
            tab.addWidget(new MapWidgetText())
                .setText("Align " + axis)
                .setColor(MapColorPalette.COLOR_WHITE)
                .setBounds(0, y + 1, 34, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(state, axis, 0.0625);
                }
            }).setText("Min").setBounds(36 + 0*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(state, axis, 0.5);
                }
            }).setText("Mid").setBounds(36 + 1*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(state, axis, 1.0 - 0.0625);
                }
            }).setText("Max").setBounds(36 + 2*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            y += 16;
        }
    }

    private static void alignPosition(PlayerEditState state, char axis, double value) {
        state.deselectLockedNodes();
        for (TrackNode node : state.getEditedNodes()) {
            Vector v = node.getPosition().clone();
            if (axis == 'x') {
                v.setX(v.getBlockX() + value);
            } else if (axis == 'y') {
                v.setY(v.getBlockY() + value);
            } else if (axis == 'z') {
                v.setZ(v.getBlockZ() + value);
            }
            node.setPosition(v);
        }
    }

    private static void createRailsView(MapWidgetTabView.Tab tab, PlayerEditState state) {
        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                setText("Reset");
            }

            @Override
            public void onActivate() {
                try {
                    state.resetRailsBlocks();
                } catch (ChangeCancelledException e) {
                    // Not possible
                }
            }
        }).setBounds(10, 10, 100, 12);

        IntVector3 value = IntVector3.ZERO;
        for (TrackNode node : state.getEditedNodes()) {
            value = node.getRailBlock(true);
            break;
        }
        int y = 30;
        String[] coord_names = new String[] { "x", "y", "z"};
        final AtomicBoolean finished_loading = new AtomicBoolean();
        final MapWidgetNumberBox[] coord_boxes = new MapWidgetNumberBox[3];
        for (int index = 0; index < 3; index++) {
            
            tab.addWidget(new MapWidgetText()).setText(coord_names[index])
                .setColor(MapColorPalette.COLOR_RED)
                .setBounds(10, y + 2, 18, 12);

            MapWidgetNumberBox box = tab.addWidget(new MapWidgetNumberBox() {
                @Override
                public void onValueChanged() {
                    if (finished_loading.get()) {
                        try {
                            state.setRailBlock(new IntVector3(
                                    coord_boxes[0].getValue(),
                                    coord_boxes[1].getValue(),
                                    coord_boxes[2].getValue()));
                        } catch (ChangeCancelledException e) {
                            // Not possible
                        }
                    }
                }

                @Override
                public void onTick() {
                    TrackNode node = state.getLastEditedNode();
                    if (node != null) {
                        IntVector3 rails = node.getRailBlock(true);
                        int val;
                        if (this == coord_boxes[0]) {
                            val = rails.x;
                        } else if (this == coord_boxes[1]) {
                            val = rails.y;
                        } else {
                            val = rails.z;
                        }
                        if (val != (int) this.getValue()) {
                            finished_loading.set(false);
                            this.setValue(val);
                            finished_loading.set(true);
                        }
                    }
                }
            });
            coord_boxes[index] = box;
            box.setIncrement(1.0);
            box.setBounds(20, y, 100-20, 12);
            if (index == 0) {
                box.setValue(value.x);
            } else if (index == 1) {
                box.setValue(value.y);
            } else {
                box.setValue(value.z);
            }

            y += 14;
        }
        finished_loading.set(true);
    }
}
