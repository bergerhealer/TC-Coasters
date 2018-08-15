package com.bergerkiller.bukkit.coasters.editor;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class TCCoastersDisplay extends MapDisplay {

    private final MapWidgetTabView tabView = new MapWidgetTabView() {
        @Override
        public void onAttached() {
            for (PlayerEditState.Mode mode : PlayerEditState.Mode.values()) {
                MapWidgetTabView.Tab tab = this.addTab();
                switch (mode) {
                case POSITION:
                    addPositionWidgets(tab);
                    break;
                case RAILS:
                    addRailsWidgets(tab);
                    break;
                default:
                    break;
                }
            }
            this.setSelectedIndex(getState().getMode().ordinal());
            this.setBounds(5, 20, 128-10, 90);
        }
    };

    @Override
    public void onAttached() {
        getLayer().draw(this.loadTexture("com/bergerkiller/bukkit/coasters/resources/coaster_bg.png"), 0, 0);

        this.setReceiveInputWhenHolding(true);

        this.addWidget(new MapWidgetSelectionBox() {
            @Override
            public void onAttached() {
                for (PlayerEditState.Mode mode : PlayerEditState.Mode.values()) {
                    this.addItem(mode.getName());
                }
                this.setSelectedItem(getState().getMode().getName());
                this.setBounds(5, 5, 128-10, 11);
                super.onAttached();
            }

            @Override
            public void onSelectedItemChanged() {
                getState().setMode(PlayerEditState.Mode.fromName(this.getSelectedItem()));
                tabView.setSelectedIndex(getState().getMode().ordinal());
            }
        });

        this.addWidget(this.tabView);

        /*
        this.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Create");
                this.setBounds(10, 30, 108, 16);
            }

            @Override
            public void onActivate() {
                Location loc = getPlayer().getEyeLocation();
                loc.add(loc.getDirection().multiply(0.5));
                TrackNode newNode = null;
                if (state.hasEditedNodes()) {
                    for (TrackNode node : state.getEditedNodes()) {
                        if (newNode == null) {
                            newNode = getTracks().addNode(node, loc.toVector());
                        } else {
                            getTracks().connect(node, newNode);
                        }
                    }
                } else {
                    newNode = getTracks().createNew(loc.toVector()).getNodes().get(0);
                }
                state.clearEditedNodes();
                state.setEditing(newNode, true);
            }
        });

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Split");
                this.setBounds(10, 50, 108, 16);
            }

            @Override
            public void onActivate() {
                
            }
        });

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                this.setText("Delete");
                this.setBounds(10, 70, 108, 16);
            }

            @Override
            public void onActivate() {
                
            }
        });
        */
    }

    private void addRailsWidgets(MapWidgetTabView.Tab tab) {
        tab.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                setText("Reset");
            }

            @Override
            public void onActivate() {
                getState().resetRailsBlocks();
            }
        }).setBounds(10, 10, 100, 12);

        IntVector3 value = IntVector3.ZERO;
        for (TrackNode node : this.getState().getEditedNodes()) {
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
                        getState().setRailBlock(new IntVector3(
                                coord_boxes[0].getValue(),
                                coord_boxes[1].getValue(),
                                coord_boxes[2].getValue()));
                    }
                }

                @Override
                public void onTick() {
                    TrackNode node = getState().getLastEditedNode();
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

    private void addPositionWidgets(MapWidgetTabView.Tab tab) {
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
                    alignPosition(axis, 0.0625);
                }
            }).setText("Min").setBounds(36 + 0*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(axis, 0.5);
                }
            }).setText("Mid").setBounds(36 + 1*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    alignPosition(axis, 1.0 - 0.0625);
                }
            }).setText("Max").setBounds(36 + 2*ADJ_BTN_OFFSET, y, ADJ_BTN_WIDTH, ADJ_BTN_HEIGHT);

            y += 16;
        }
    }

    private void alignPosition(char axis, double value) {
        for (TrackNode node : getState().getEditedNodes()) {
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

    public PlayerEditState getState() {
        return getPlugin().getEditState(getPlayer());
    }
    
    public Player getPlayer() {
        return this.getOwners().get(0);
    }

    public CoasterWorldAccess getCoasterWorld() {
        return getState().getCoasterWorld();
    }

    @Override
    public TCCoasters getPlugin() {
        return (TCCoasters) super.getPlugin();
    }
}
