package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

/**
 * Shows sliders for adjusting the x/y/z position of the track object,
 * and its orientation.
 */
public class TypePositionMenu extends MapWidgetMenu {
    private final Supplier<PlayerEditState> stateSupplier;
    private MapWidgetNumberBox num_pos_x, num_pos_y, num_pos_z;
    private MapWidgetNumberBox num_rot_x, num_rot_y, num_rot_z;
    private boolean isLoadingWidgets;

    public TypePositionMenu(Supplier<PlayerEditState> stateSupplier) {
        this.stateSupplier = stateSupplier;
        this.isLoadingWidgets = false;
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        isLoadingWidgets = true;

        int slider_width = 86;
        int y_offset = 5;
        int y_step = 12;

        // Spacing
        y_offset += 3;

        //this.transformType
        num_pos_x = this.addWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(position().getX());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position X-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_x.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.X");
        y_offset += y_step;

        num_pos_y = this.addWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(position().getY());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Y-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_y.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Y");
        y_offset += y_step;

        num_pos_z = this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(position().getZ());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Z-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_pos_z.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        num_rot_x = this.addWidget(new MapWidgetNumberBox() { // Rotation X (pitch)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(rotation().getX());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Pitch";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_x.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Pitch");
        y_offset += y_step;

        num_rot_y = this.addWidget(new MapWidgetNumberBox() { // Rotation Y (yaw)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(rotation().getY());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Yaw";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_y.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Yaw");
        y_offset += y_step;

        num_rot_z = this.addWidget(new MapWidgetNumberBox() { // Rotation Z (roll)
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(rotation().getZ());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Roll";
            }

            @Override
            public void onValueChanged() {
                updateTransform();
            }
        });
        num_rot_z.setBounds(30, y_offset, slider_width, 11);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;

        isLoadingWidgets = false;
    }

    private Vector position() {
        Matrix4x4 transform = this.stateSupplier.get().getObjects().getSelectedType().getTransform();
        return (transform == null) ? new Vector() : transform.toVector();
    }

    private Vector rotation() {
        Matrix4x4 transform = this.stateSupplier.get().getObjects().getSelectedType().getTransform();
        return (transform == null) ? new Vector() : transform.getYawPitchRoll();
    }

    private void updateTransform() {
        if (this.isLoadingWidgets) {
            return;
        }

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
        stateSupplier.get().getObjects().transformSelectedType(type -> type.setTransform(transform));
    }
}
