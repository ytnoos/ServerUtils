package net.frankheijden.serverutils.velocity.managers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

public class VelocityPluginCommandManager {

    private static final Gson gson = new Gson();

    private final Multimap<String, String> pluginCommands;
    private final Path path;

    public VelocityPluginCommandManager(Path path) {
        this.pluginCommands = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        this.path = path;
    }

    /**
     * Loads and constructs a new {@link VelocityPluginCommandManager} from the given {@link Path}.
     */
    public static VelocityPluginCommandManager load(Path path) throws IOException {
        VelocityPluginCommandManager manager = new VelocityPluginCommandManager(path);
        if (Files.exists(path)) {
            Map<String, Collection<String>> rawMap = gson.fromJson(
                    Files.newBufferedReader(path),
                    new TypeToken<Map<String, Collection<String>>>(){}.getType()
            );
            rawMap.forEach(manager.pluginCommands::putAll);
        }

        return manager;
    }

    /**
     * Attempts to find the plugin id for a given command alias.
     */
    public Optional<String> findPluginId(String alias) {
        for (Map.Entry<String, String> entry : pluginCommands.entries()) {
            if (alias.equals(entry.getValue())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Multimap<String, String> getPluginCommands() {
        return pluginCommands;
    }

    /**
     * Records the latest registration of {@code aliases}: earlier owners of those aliases are forgotten, so
     * unloading a plugin never removes a command another plugin registered again afterwards.
     */
    public void recordRegistration(Collection<String> pluginIds, Collection<String> aliases) {
        synchronized (pluginCommands) {
            pluginCommands.entries().removeIf(entry -> aliases.contains(entry.getValue()));
            for (String pluginId : pluginIds) {
                pluginCommands.putAll(pluginId, aliases);
            }
        }
    }

    /**
     * Removes and returns the aliases owned by {@code pluginId}, dropping them for co-owners as well.
     */
    public Set<String> takeCommands(String pluginId) {
        synchronized (pluginCommands) {
            Set<String> aliases = new LinkedHashSet<>(pluginCommands.removeAll(pluginId));
            forget(aliases);
            return aliases;
        }
    }

    /**
     * Drops {@code aliases} for every plugin.
     */
    public void forget(Collection<String> aliases) {
        synchronized (pluginCommands) {
            pluginCommands.entries().removeIf(entry -> aliases.contains(entry.getValue()));
        }
    }

    /**
     * Saves the map to the {@link Path} it was loaded from.
     */
    public void save() throws IOException {
        if (Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        Files.write(
                path,
                gson.toJson(pluginCommands.asMap()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}
