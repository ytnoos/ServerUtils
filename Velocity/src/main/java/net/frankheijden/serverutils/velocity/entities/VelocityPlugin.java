package net.frankheijden.serverutils.velocity.entities;

import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.velocity.VelocityCommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.frankheijden.serverutils.common.entities.ServerUtilsPlugin;
import net.frankheijden.serverutils.velocity.ServerUtils;
import net.frankheijden.serverutils.velocity.commands.VelocityCommandPlugins;
import net.frankheijden.serverutils.velocity.commands.VelocityCommandServerUtils;
import net.frankheijden.serverutils.velocity.listeners.VelocityPlayerListener;
import net.frankheijden.serverutils.velocity.managers.VelocityPluginManager;
import net.frankheijden.serverutils.velocity.managers.VelocityTaskManager;

public class VelocityPlugin extends ServerUtilsPlugin<PluginContainer, ScheduledTask, VelocityAudience, CommandSource, VelocityPluginDescription> {

    private static final Executor COMMAND_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ServerUtils Command");
        thread.setDaemon(true);
        return thread;
    });

    private final ServerUtils plugin;
    private final VelocityPluginManager pluginManager;
    private final VelocityTaskManager taskManager;
    private final VelocityResourceProvider resourceProvider;
    private final VelocityAudienceProvider chatProvider;

    /**
     * Creates a new BungeePlugin instance of ServerUtils.
     * @param plugin The ServerUtils plugin.
     */
    public VelocityPlugin(ServerUtils plugin) {
        this.plugin = plugin;
        this.pluginManager = new VelocityPluginManager(
                plugin.getProxy(),
                plugin.getLogger(),
                plugin.getPluginCommandManager()
        );
        this.taskManager = new VelocityTaskManager(plugin);
        this.resourceProvider = new VelocityResourceProvider(plugin);
        this.chatProvider = new VelocityAudienceProvider(plugin);
    }

    /**
     * Commands run off the proxy thread but one at a time: two plugin loads or unloads at once raced
     * in the shared reflection cache (ConcurrentModificationException) and one of them hung.
     */
    @Override
    protected VelocityCommandManager<VelocityAudience> newCommandManager() {
        VelocityCommandManager<VelocityAudience> commandManager = new VelocityCommandManager<>(
                plugin.getPluginContainer(),
                plugin.getProxy(),
                AsynchronousCommandExecutionCoordinator.<VelocityAudience>newBuilder()
                        .withExecutor(COMMAND_EXECUTOR)
                        .build(),
                chatProvider::get,
                VelocityAudience::getSource
        );
        handleBrigadier(commandManager.brigadierManager());
        return commandManager;
    }

    @Override
    public VelocityPluginManager getPluginManager() {
        return this.pluginManager;
    }

    @Override
    public VelocityTaskManager getTaskManager() {
        return this.taskManager;
    }

    @Override
    public Platform getPlatform() {
        return Platform.VELOCITY;
    }

    @Override
    public PluginContainer getPlugin() {
        return plugin.getPluginContainer();
    }

    @Override
    public VelocityResourceProvider getResourceProvider() {
        return this.resourceProvider;
    }

    @Override
    public VelocityAudienceProvider getChatProvider() {
        return this.chatProvider;
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger(plugin.getLogger().getName());
    }

    @Override
    public File getDataFolder() {
        return this.plugin.getDataDirectory().toFile();
    }

    /**
     * Disabled: ServerUtilsUpdater embeds an old ServerUtils copy that cannot unload plugins on current
     * Velocity, and upstream releases would replace this fork. Update ServerUtils by restarting the proxy.
     */
    @Override
    public boolean supportsSelfUpdate() {
        return false;
    }

    @Override
    protected void enablePlugin() {
        plugin.getProxy().getEventManager().register(plugin, new VelocityPlayerListener(this));
    }

    @Override
    protected void reloadPlugin() {
        new VelocityCommandPlugins(this).register(commandManager);
        new VelocityCommandServerUtils(this).register(commandManager);
    }
}
