package us.potatoboy.invview;

import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PermissionsCompat {
    private PermissionsCompat() {}

    public static boolean check(ServerCommandSource source, String node, int fallbackOpLevel) {
        // Try fabric-permissions-api if present
        try {
            Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method m = clazz.getMethod("check", ServerCommandSource.class, String.class);
            Object result = m.invoke(null, source, node);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
            // ignore and fallback
        }
        // Fallback: vanilla op level
        return source.hasPermissionLevel(fallbackOpLevel);
    }

    public static boolean check(UUID subject, String node, boolean defaultValue) {
        try {
            Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method m = clazz.getMethod("check", UUID.class, String.class, boolean.class);
            Object result = m.invoke(null, subject, node, defaultValue);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
            // ignore and fallback
        }
        return defaultValue;
    }
}
