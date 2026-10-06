package net.frankheijden.serverutils.velocity.reflection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.frankheijden.minecraftreflection.MinecraftReflection;
import dev.frankheijden.minecraftreflection.Reflection;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import net.frankheijden.serverutils.common.utils.ReflectionUtils;
import net.frankheijden.serverutils.velocity.ServerUtils;

public class RVelocityCommandManager {

    private static final MinecraftReflection reflection = MinecraftReflection
            .of("com.velocitypowered.proxy.command.VelocityCommandManager");
    private static final String HANDLER_CLASS_NAME = CommandRegistrarInvocationHandler.class.getName();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private RVelocityCommandManager() {}

    public static CommandDispatcher<CommandSource> getDispatcher(CommandManager manager) {
        return reflection.get(manager, "dispatcher");
    }

    /**
     * Wraps Velocity's command registrars so every registration is reported to {@code registrationConsumer}
     * together with the plugins that took part in it. Wrappers left by an earlier ServerUtils instance are
     * removed first, so wrappers never stack up across ServerUtils reloads.
     */
    public static void proxyRegistrars(
            ProxyServer proxy,
            ClassLoader loader,
            BiConsumer<Collection<PluginContainer>, CommandMeta> registrationConsumer
    ) {
        Class<?> commandRegistrarClass;
        try {
            commandRegistrarClass = Class.forName("com.velocitypowered.proxy.command.registrar.CommandRegistrar");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            return;
        }

        List<Object> proxiedRegistrars = new ArrayList<>();
        for (Object registrar : unwrappedRegistrars(proxy)) {
            proxiedRegistrars.add(Proxy.newProxyInstance(
                    loader,
                    new Class[]{ commandRegistrarClass },
                    new CommandRegistrarInvocationHandler(proxy, registrar, loader, registrationConsumer)
            ));
        }
        setRegistrars(proxy, proxiedRegistrars);
    }

    /**
     * Puts back Velocity's own registrars. Called when ServerUtils is disabled, so its classes are no longer
     * on the command registration path once its classloader is closed.
     */
    public static void restoreRegistrars(ProxyServer proxy) {
        setRegistrars(proxy, unwrappedRegistrars(proxy));
    }

    /**
     * Aliases whose {@link CommandMeta#getPlugin()} is the given plugin container or instance.
     */
    public static Set<String> findOwnedAliases(ProxyServer proxy, PluginContainer container, Object pluginInstance) {
        CommandManager commandManager = proxy.getCommandManager();
        List<String> rootAliases = new ArrayList<>();
        for (CommandNode<CommandSource> node : getDispatcher(commandManager).getRoot().getChildren()) {
            rootAliases.add(node.getName());
        }

        Set<String> owned = new LinkedHashSet<>();
        for (String alias : rootAliases) {
            CommandMeta meta = commandManager.getCommandMeta(alias);
            if (meta == null) continue;
            Object owner = meta.getPlugin();
            if (owner != null && (owner == container || owner == pluginInstance)) {
                owned.add(alias);
            }
        }
        return owned;
    }

    @SuppressWarnings("rawtypes")
    private static List<Object> unwrappedRegistrars(ProxyServer proxy) {
        List<Object> registrars = new ArrayList<>();
        for (Object registrar : (List) reflection.get(proxy.getCommandManager(), "registrars")) {
            registrars.add(unwrap(registrar));
        }
        return registrars;
    }

    private static Object unwrap(Object registrar) {
        Object current = registrar;
        while (Proxy.isProxyClass(current.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(current);
            // Match by name: a wrapper may come from a ServerUtils instance with another classloader.
            if (!handler.getClass().getName().equals(HANDLER_CLASS_NAME)) break;
            try {
                Field field = handler.getClass().getDeclaredField("commandRegistrar");
                field.setAccessible(true);
                current = field.get(handler);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                break;
            }
        }
        return current;
    }

    private static void setRegistrars(ProxyServer proxy, List<Object> registrars) {
        Field registrarsField = Reflection.getAccessibleField(reflection.getClazz(), "registrars");
        ReflectionUtils.doPrivilegedWithUnsafe(unsafe -> {
            long offset = unsafe.objectFieldOffset(registrarsField);
            unsafe.putObject(proxy.getCommandManager(), offset, registrars);
        });
    }

    public static final class CommandRegistrarInvocationHandler implements InvocationHandler {

        private final ProxyServer proxy;
        private final Object commandRegistrar;
        private final ClassLoader ownLoader;
        private final BiConsumer<Collection<PluginContainer>, CommandMeta> registrationConsumer;

        /**
         * Constructs a new {@link CommandRegistrarInvocationHandler}.
         */
        public CommandRegistrarInvocationHandler(
                ProxyServer proxy,
                Object commandRegistrar,
                ClassLoader ownLoader,
                BiConsumer<Collection<PluginContainer>, CommandMeta> registrationConsumer
        ) {
            this.proxy = proxy;
            this.commandRegistrar = commandRegistrar;
            this.ownLoader = ownLoader;
            this.registrationConsumer = registrationConsumer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object obj = method.invoke(commandRegistrar, args);
            if (method.getName().equals("register")) {
                handleRegisterMethod((CommandMeta) args[0]);
            }
            return obj;
        }

        /**
         * Every plugin with code on the registration stack owns the command: a plugin that registers through a
         * library shipped by another plugin (e.g. Lamp inside Dictation) is credited too, and unloading either
         * one removes the command. ServerUtils itself is skipped: it is on the stack of every plugin it loads.
         */
        private void handleRegisterMethod(CommandMeta commandMeta) {
            // Velocity's own classes are on every stack; its "velocity" container is never unloaded.
            ClassLoader proxyLoader = proxy.getClass().getClassLoader();
            Set<ClassLoader> loaders = STACK_WALKER.walk(frames -> {
                Set<ClassLoader> found = new LinkedHashSet<>();
                frames.forEach(frame -> {
                    ClassLoader frameLoader = frame.getDeclaringClass().getClassLoader();
                    if (frameLoader != null && frameLoader != ownLoader && frameLoader != proxyLoader) {
                        found.add(frameLoader);
                    }
                });
                return found;
            });

            List<PluginContainer> owners = new ArrayList<>();
            for (PluginContainer container : proxy.getPluginManager().getPlugins()) {
                container.getInstance()
                        .filter(instance -> loaders.contains(instance.getClass().getClassLoader()))
                        .ifPresent(instance -> owners.add(container));
            }

            if (owners.isEmpty()) {
                ServerUtils.getInstance().getLogger().debug(
                        "Couldn't find the registering plugin for the following aliases: {}",
                        commandMeta.getAliases()
                );
                return;
            }
            registrationConsumer.accept(owners, commandMeta);
        }
    }
}
