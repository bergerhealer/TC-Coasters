package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemLODSelectButton;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.object.ui.TypeSelectMenu;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public enum PlayerEditMode {
    DISABLED("Disabled (hidden)", 0, 1, PlayerEditMode::createEmptyView),
    CREATE("Create Track", 20, 4, PlayerEditMode::createEmptyView),
    POSITION("Change Position", 0, 1, PlayerEditMode::createPositionView),
    ORIENTATION("Change Orientation", 0, 1, PlayerEditMode::createOrientationView),
    RAILS("Change Rail Block", 0, 1, PlayerEditMode::createRailsView),
    ANIMATION("Manage Animations", 0, 1, PlayerEditMode::createAnimationsView),
    OBJECT("Track Objects", 10, 3, PlayerEditMode::createTrackObjectsView),
    DELETE("Delete Track", 10, 3, PlayerEditMode::createEmptyView);

    private final int _autoInterval;
    private final int _autoDelay;
    private final String _name;
    private final BiConsumer<MapWidgetTabView.Tab, Supplier<PlayerEditState>> _createViewMethod;

    // name: displayed in the UI
    // autoDelay: how many ticks of holding right-click until continuously activating
    // autoInterval: tick interval of activation while holding right-click
    private PlayerEditMode(String name, int autoDelay, int autoInterval, BiConsumer<MapWidgetTabView.Tab, Supplier<PlayerEditState>> createViewMethod) {
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
     * @param stateSupplier Supplier for the edit state of the player
     */
    public void createView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        this._createViewMethod.accept(tab, stateSupplier);
    }

    private static void createEmptyView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
    }

    private static void createPositionView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        final int ADJ_BTN_WIDTH = 24;
        final int ADJ_BTN_HEIGHT = 12;
        final int ADJ_BTN_OFFSET = ADJ_BTN_WIDTH + 3;
        int y = 5;
        for (final char axis : new char[] { 'x', 'y', 'z'} ) {
            tab.addWidget(new MapWidgetText())
                .setText("Align " + axis)
                .setColor(MapColorPalette.COLOR_WHITE)
                .setBounds(0, y + 1, 34, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(stateSupplier, axis, 0.0625);
                }
            }).setText("Min").setBounds(38 + 0*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(stateSupplier, axis, 0.5);
                }
            }).setText("Mid").setBounds(38 + 1*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(stateSupplier, axis, 1.0 - 0.0625);
                }
            }).setText("Max").setBounds(38 + 2*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            y += 16;
        }

        // Make the line straight or curved with simple buttons
        // Also done using /tcc straighten and /tcc curved
        {
            y += 3; // Space between

            tab.addWidget(new MapWidgetText())
                .setText("Connection Style")
                .setColor(MapColorPalette.COLOR_WHITE)
                .setBounds(16, y + 1, 74, ADJ_BTN_HEIGHT);
            y += ADJ_BTN_HEIGHT;

            tab.addWidget(new MapWidgetButton() {
                boolean hasSelectedJunction = false;
                boolean hasSelectedEndNode = false;
                boolean hasSelectedCurvedNodes = false;
                boolean hasSelectedStraightNodes = false;
                boolean makeStraight = true;

                @Override
                public void onAttached() {
                    super.onAttached();
                    checkSelectedNodes();
                    updateText();
                }

                @Override
                public void onStatusChanged(MapStatusEvent event) {
                    super.onStatusChanged(event);

                    if (event.getName().equals("Editor::EditedNodes::Changed")) {
                        checkSelectedNodes();
                        updateText();
                    }
                }

                @Override
                public void onActivate() {
                    try {
                        boolean newMakeStraight = !makeStraight;
                        if (makeStraight) {
                            stateSupplier.get().makeConnectionsStraight();
                        } else {
                            stateSupplier.get().makeConnectionsCurved();
                        }
                        getDisplay().playSound(SoundEffect.CLICK);
                        makeStraight = newMakeStraight;
                        updateText();
                    } catch (ChangeCancelledException ex) {
                        getDisplay().playSound(SoundEffect.EXTINGUISH);
                    }
                }

                private void checkSelectedNodes() {
                    hasSelectedJunction = false;
                    hasSelectedEndNode = false;
                    hasSelectedCurvedNodes = false;
                    hasSelectedStraightNodes = false;
                    for (TrackNode node : stateSupplier.get().getEditedNodes()) {
                        List<TrackConnection> connections = node.getConnections();
                        if (connections.size() <= 1) {
                            hasSelectedEndNode = true;
                        } else if (connections.size() >= 3) {
                            hasSelectedJunction = true;
                        } else if (connections.get(0).isZeroLength() || connections.get(1).isZeroLength()) {
                            hasSelectedStraightNodes = true;
                        } else {
                            hasSelectedCurvedNodes = true;
                        }
                    }
                    if (hasSelectedCurvedNodes && !hasSelectedStraightNodes) {
                        makeStraight = true;
                    } else if (!hasSelectedCurvedNodes && hasSelectedStraightNodes) {
                        makeStraight = false;
                    }
                }

                private void updateText() {
                    if (hasSelectedCurvedNodes || hasSelectedStraightNodes) {
                        this.setEnabled(true);
                        this.setText(makeStraight ? "Make Straight" : "Make Curved");
                    } else if (hasSelectedJunction) {
                        this.setEnabled(false);
                        this.setText("Junction");
                    } else if (hasSelectedEndNode) {
                        this.setEnabled(false);
                        this.setText("End Node");
                    } else {
                        this.setEnabled(false);
                        this.setText("No Selection");
                    }
                }
            }).setBounds(10, y, 96, ADJ_BTN_HEIGHT);

            y += 16;
        }
    }

    private static void alignPosition(Supplier<PlayerEditState> stateSupplier, char axis, double value) {
        PlayerEditState state = stateSupplier.get();
        try {
            if (axis == 'x') {
                state.transformPosition(position -> position.setX(position.getBlockX() + value));
            } else if (axis == 'y') {
                state.transformPosition(position -> position.setY(position.getBlockY() + value));
            } else if (axis == 'z') {
                state.transformPosition(position -> position.setZ(position.getBlockZ() + value));
            }
        } catch (ChangeCancelledException ex) {}
    }

    private static void createOrientationView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        final int ADJ_BTN_WIDTH = 38;
        final int ADJ_BTN_HEIGHT = 12;
        int x = 0;
        int y = 5;
        for (BlockFace face : new BlockFace[] {
                BlockFace.UP, BlockFace.NORTH, BlockFace.EAST,
                BlockFace.DOWN, BlockFace.SOUTH, BlockFace.WEST})
        {
            // Add button
            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    try {
                        stateSupplier.get().setOrientation(FaceUtil.faceToVector(face));
                    } catch (ChangeCancelledException e) {
                        // Not possible
                    }
                }
            }).setText(face.name()).setBounds(x, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            // Next x/y position
            x += ADJ_BTN_WIDTH + 2;
            if (face == BlockFace.EAST) {
                y += ADJ_BTN_HEIGHT + 2;
                x = 0;
            }
        }
    }

    private static void createRailsView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                setText("Reset");
            }

            @Override
            public void onActivate() {
                try {
                    stateSupplier.get().resetRailsBlocks();
                } catch (ChangeCancelledException e) {
                    // Not possible
                }
            }
        }).setBounds(10, 10, 100, 12);

        IntVector3 value = IntVector3.ZERO;
        for (TrackNode node : stateSupplier.get().getEditedNodes()) {
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
                            stateSupplier.get().setRailBlock(new IntVector3(
                                    coord_boxes[0].getValue(),
                                    coord_boxes[1].getValue(),
                                    coord_boxes[2].getValue()));
                        } catch (ChangeCancelledException e) {
                            // Not possible
                        }
                    }
                }

                // Overrides on new version of TrainCarts!
                @Override
                public String getValueText() {
                    return Integer.toString((int) getValue());
                }

                @Override
                public void onTick() {
                    TrackNode node = stateSupplier.get().getLastEditedNode();
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

    private static void createAnimationsView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        final MapWidgetSubmitText addNodeAskText = tab.addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAttached() {
                this.setDescription("Enter animation name");
            }

            @Override
            public void onAccept(String text) {
                PlayerEditState state = stateSupplier.get();
                for (TrackNode node : state.getEditedNodes()) {
                    node.saveAnimationState(text);
                }
                state.setSelectedAnimation(text);
                TCCoastersLocalization.ANIMATION_ADD.message(state.getPlayer(), text, Integer.toString(state.getEditedNodes().size()));
                getDisplay().playSound(SoundEffect.CLICK);
            }
        });

        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Add");
                super.onAttached();
            }

            @Override
            public void onActivate() {
                addNodeAskText.activate();
            }
        }).setBounds(11, 34, 43, 12);

        final MapWidgetButton removeButton = tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Remove");
                super.onAttached();
            }

            @Override
            public void onActivate() {
                PlayerEditState state = stateSupplier.get();
                String name = state.getSelectedAnimation();
                if (name != null) {
                    ArrayList<TrackNode> nodes = new ArrayList<TrackNode>(state.getSelectedAnimationNodes());
                    for (TrackNode node : nodes) {
                        node.removeAnimationState(name);
                    }
                    state.setSelectedAnimation(null);
                    TCCoastersLocalization.ANIMATION_REMOVE.message(state.getPlayer(), name, Integer.toString(nodes.size()));
                    getDisplay().playSound(SoundEffect.EXTINGUISH);

                    // Focus animation selection menu. Kinda hard to do as we don't have a final field for it :(
                    tab.getWidget(4).focus();
                }
            }
        });
        removeButton.setBounds(62, 34, 43, 12);

        final MapWidgetSubmitText renameNodeAskText = tab.addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAttached() {
                this.setDescription("Enter new animation name");
            }

            @Override
            public void onAccept(String newName) {
                PlayerEditState state = stateSupplier.get();
                String name = state.getSelectedAnimation();
                if (name != null) {
                    ArrayList<TrackNode> nodes = new ArrayList<TrackNode>(state.getSelectedAnimationNodes());
                    if (nodes.isEmpty()) {
                        return; // Wut?
                    }

                    // First check that the name isn't already used
                    boolean newNameUsed = false;
                    for (TrackNode node : nodes) {
                        if (node.hasAnimationState(newName)) {
                            newNameUsed = true;
                            break;
                        }
                    }
                    if (newNameUsed) {
                        display.playSound(SoundEffect.EXTINGUISH);
                        state.getPlayer().sendMessage(ChatColor.RED + "Track animation name '" + newName + "' is already used!");
                        return;
                    }

                    int numChanged = 0;
                    for (TrackNode node : nodes) {
                        if (node.renameAnimationState(name, newName)) {
                            numChanged++;
                        }
                    }
                    state.setSelectedAnimation(newName);
                    TCCoastersLocalization.ANIMATION_RENAME.message(state.getPlayer(), name, newName, Integer.toString(numChanged));
                    getDisplay().playSound(SoundEffect.CLICK);
                }
            }
        });

        final MapWidgetButton renameButton = tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Rename");
                super.onAttached();
            }

            @Override
            public void onActivate() {
                renameNodeAskText.activate();
            }
        });
        renameButton.setBounds(11, 48, 43, 12);

        tab.addWidget(new MapWidgetText() {
            @Override
            public void onAttached() {
                this.setColor(MapColorPalette.COLOR_YELLOW);
                this.setText("Selected animation:");
            }
        }).setBounds(13, 3, 100, 12);

        tab.addWidget(new MapWidgetSelectionBox() {
            private boolean ignoreSelectedItemChange = false;

            @Override
            public void onAttached() {
                this.loadItems();
                super.onAttached();
            }

            @Override
            public void onStatusChanged(MapStatusEvent event) {
                super.onStatusChanged(event);
                if (event.isName("PlayerEditState::EditedAnimationNamesChanged")) {
                    loadItems();
                }
            }

            @Override
            public void onSelectedItemChanged() {
                boolean isAnimationSelected = (this.getSelectedIndex() >= 1);
                if (!this.ignoreSelectedItemChange) {
                    stateSupplier.get().setSelectedAnimation(isAnimationSelected ? this.getSelectedItem() : null);
                }
                removeButton.setEnabled(isAnimationSelected);
                renameButton.setEnabled(isAnimationSelected);
            }

            @Override
            public void onActivate() {
                stateSupplier.get().setSelectedAnimation(null);
                getDisplay().playSound(SoundEffect.EXTINGUISH);
            }

            private void loadItems() {
                PlayerEditState state = stateSupplier.get();
                this.ignoreSelectedItemChange = true;
                this.clearItems();
                this.addItem("<None>");
                this.setSelectedIndex(0);
                for (String animationName : state.getEditedAnimationNames()) {
                    this.addItem(animationName);
                    if (animationName.equals(state.getSelectedAnimation())) {
                        this.setSelectedItem(animationName);
                    }
                }
                this.ignoreSelectedItemChange = false;
            }
        }).setBounds(12, 14, 94, 12);
    }

    private static void createTrackObjectsView(MapWidgetTabView.Tab tab, Supplier<PlayerEditState> stateSupplier) {
        final TypeSelectMenu typeSelector = new TypeSelectMenu(stateSupplier);
        tab.addWidget(typeSelector).setBounds(40, 0, 36, 36);

        final int BUTTON_Y_STEP = 15;

        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                typeSelector.getSelectedType().openPositionMenu(tab, stateSupplier);
            }
        }).setText("Position").setBounds(26, 40 + 0 * BUTTON_Y_STEP, 64, 13);

        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                try {
                    PlayerEditState state = stateSupplier.get();
                    if (state.getObjects().hasEditedObjects()) {
                        state.getObjects().flipObject();
                        getDisplay().playSound(SoundEffect.CLICK);
                    }
                } catch (ChangeCancelledException e) {
                    getDisplay().playSound(SoundEffect.EXTINGUISH);
                }
            }
        }).setText("Flip").setBounds(26, 40 + 1 * BUTTON_Y_STEP, 64, 13);

        tab.addWidget(new ItemLODSelectButton(stateSupplier)).setText("LOD")
                .setBounds(26, 40 + 2 * BUTTON_Y_STEP, 64, 13);

        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                try {
                    PlayerEditState state = stateSupplier.get();
                    if (state.getObjects().hasEditedObjects()) {
                        state.getObjects().deleteObjects();
                        getDisplay().playSound(SoundEffect.ITEM_BREAK);
                    }
                } catch (ChangeCancelledException e) {
                    getDisplay().playSound(SoundEffect.EXTINGUISH);
                }
            }
        }).setText("Delete").setBounds(26, 40 + 3 * BUTTON_Y_STEP, 64, 13);
    }
}
