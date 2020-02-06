package com.bergerkiller.bukkit.coasters;

import org.bukkit.permissions.PermissionDefault;

import com.bergerkiller.bukkit.common.permissions.PermissionEnum;

public class TCCoastersPermissions extends PermissionEnum {
    public static final TCCoastersPermissions USE = new TCCoastersPermissions("train.coasters.use", PermissionDefault.OP, "The player can use TC Coasters anywhere");
    public static final TCCoastersPermissions PLOTSQUARED_USE = new TCCoastersPermissions("train.coasters.plotsquared.use", PermissionDefault.FALSE, "The player can use TC Coasters inside own plots. The train.coasters.use permission is not required.");
    public static final TCCoastersPermissions LOCK = new TCCoastersPermissions("train.coasters.lock", PermissionDefault.OP, "The player can lock and unlock coasters to disable modification");
    public static final TCCoastersPermissions IMPORT = new TCCoastersPermissions("train.coasters.import", PermissionDefault.OP, "The player can use the import command to download coasters from online hastebins");
    public static final TCCoastersPermissions EXPORT = new TCCoastersPermissions("train.coasters.export", PermissionDefault.OP, "The player can use the export command to upload tracks and share it online");
    public static final TCCoastersPermissions BUILD_ANIMATOR = new TCCoastersPermissions("train.coasters.build.animator", PermissionDefault.OP, "The player can build track animator signs");

    private TCCoastersPermissions(final String node, final PermissionDefault permdefault, final String desc) {
        super(node, permdefault, desc);
    }
}
