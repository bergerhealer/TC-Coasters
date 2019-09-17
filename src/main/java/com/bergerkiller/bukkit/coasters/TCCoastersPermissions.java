package com.bergerkiller.bukkit.coasters;

import org.bukkit.permissions.PermissionDefault;

import com.bergerkiller.bukkit.common.permissions.PermissionEnum;

public class TCCoastersPermissions extends PermissionEnum {
    public static final TCCoastersPermissions USE = new TCCoastersPermissions("train.coasters.use", PermissionDefault.OP, "The player can use TC Coasters");
    public static final TCCoastersPermissions LOCK = new TCCoastersPermissions("train.coasters.lock", PermissionDefault.OP, "The player can lock and unlock coasters to disable modification");

    private TCCoastersPermissions(final String node, final PermissionDefault permdefault, final String desc) {
        super(node, permdefault, desc);
    }
}
