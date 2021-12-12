package com.bergerkiller.bukkit.coasters.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a cloud-annotated method requires the at least one
 * node to be selected. De-selects nodes that cannot be modified.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandRequiresSelectedNodes {
}
