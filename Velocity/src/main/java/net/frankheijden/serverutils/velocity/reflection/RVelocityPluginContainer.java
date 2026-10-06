package net.frankheijden.serverutils.velocity.reflection;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import dev.frankheijden.minecraftreflection.ClassObject;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import java.util.concurrent.ExecutorService;

public class RVelocityPluginContainer {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer");

    private RVelocityPluginContainer() {}

    public static PluginContainer newInstance(PluginDescription description) {
        return reflection.newInstance(ClassObject.of(PluginDescription.class, description));
    }

    /**
     * Shuts down the plugin's lazily created executor, if any, so its threads stop referencing the
     * unloaded plugin's classloader. Reads the field directly: {@code getExecutorService()} would create one.
     */
    public static void shutdownExecutor(PluginContainer container) {
        if (!reflection.getClazz().isInstance(container)) return;
        ExecutorService service = reflection.get(container, "service");
        if (service != null) service.shutdown();
    }
}
