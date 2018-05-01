package com.bergerkiller.bukkit.coasters.map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.meta.TrackCoaster;
import com.bergerkiller.bukkit.coasters.meta.TrackEditState;
import com.bergerkiller.bukkit.coasters.meta.TrackNode;
import com.bergerkiller.bukkit.coasters.meta.TrackWorldStorage;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;

public class TCCoastersDisplay extends MapDisplay {

    private final MapWidgetTabView tabView = new MapWidgetTabView() {
        @Override
        public void onAttached() {
            for (TrackEditState.Mode mode : TrackEditState.Mode.values()) {
                MapWidgetTabView.Tab tab = this.addTab();
                switch (mode) {
                case POSITION:
                    addPositionWidgets(tab);
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
                for (TrackEditState.Mode mode : TrackEditState.Mode.values()) {
                    this.addItem(mode.getName());
                }
                this.setSelectedItem(getState().getMode().getName());
                this.setBounds(5, 5, 128-10, 11);
                super.onAttached();
            }

            @Override
            public void onSelectedItemChanged() {
                getState().setMode(TrackEditState.Mode.fromName(this.getSelectedItem()));
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

    public TrackEditState getState() {
        return getPlugin().getEditState(getPlayer());
    }
    
    public Player getPlayer() {
        return this.getOwners().get(0);
    }

    public TrackWorldStorage getTracks() {
        return getPlugin().getTracks(getPlayer().getWorld());
    }

    @Override
    public TCCoasters getPlugin() {
        return (TCCoasters) super.getPlugin();
    }
}
