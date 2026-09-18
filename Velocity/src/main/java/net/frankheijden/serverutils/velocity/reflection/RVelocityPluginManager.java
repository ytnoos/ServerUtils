package net.frankheijden.serverutils.velocity.reflection;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import dev.frankheijden.minecraftreflection.ClassObject;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import dev.frankheijden.minecraftreflection.exceptions.MinecraftReflectionException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class RVelocityPluginManager {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.plugin.VelocityPluginManager");

    private RVelocityPluginManager() {}

    /**
     * Retrieves the plugin map. Key is the id of the plugin.
     */
    public static Map<String, PluginContainer> getPlugins(PluginManager manager) {
        String fieldName = "plugins";
        try {
            reflection.getClazz().getField(fieldName);
        } catch (NoSuchFieldException ex) {
            fieldName = "pluginsById";
        }

        return reflection.get(manager, fieldName);
    }

    public static Map<Object, PluginContainer> getPluginInstances(PluginManager manager) {
        return reflection.get(manager, "pluginInstances");
    }

    /**
     * Removes a plugin from every registry used by Velocity.
     */
    public static void unregisterPlugin(
            PluginManager manager,
            PluginContainer container,
            Object instance
    ) {
        getPlugins(manager).entrySet().removeIf(entry -> entry.getValue() == container);
        getPluginInstances(manager).remove(instance);

        getIterablePlugins(manager).ifPresent(containers -> containers.remove(container));
    }

    private static Optional<Collection<PluginContainer>> getIterablePlugins(PluginManager manager) {
        try {
            return Optional.of(reflection.get(manager, "plugins"));
        } catch (MinecraftReflectionException ex) {
            if (ex.getCause() instanceof NoSuchFieldException) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public static void registerPlugin(PluginManager manager, PluginContainer container) {
        reflection.invoke(manager, "registerPlugin", ClassObject.of(PluginContainer.class, container));
    }
}
