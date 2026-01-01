package ac.boar.anticheat.alert;

import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.session.GeyserSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertManager {
    public static final UUID CONSOLE_UUID = new UUID(0, 0);

    private static final String PREFIX = "§3Boar §7>§r ";
    private static final String BEDROCK_PREFIX = "§sBoar §i>§r ";

    private final Map<UUID, CommandSource> sources = new ConcurrentHashMap<>();

    public void alert(String message) {
        sources.values().forEach(source -> source.sendMessage(getPrefix(source) + "§3" + message));
    }

    public void alertToPlayers(List<CommandSource> targets, String message) {
        targets.forEach(source -> source.sendMessage(getPrefix(source) + "§3" + message));
    }

    public void debug(String message) {
    }

    public String getPrefix(CommandSource source) {
        return source.connection() != null ? BEDROCK_PREFIX : PREFIX;
    }

    public String getPrefix(GeyserSession session) {
        return BEDROCK_PREFIX;
    }

    public boolean hasAlert(CommandSource source) {
        return sources.containsKey(getSourceId(source));
    }

    public void addAlert(CommandSource source) {
        sources.put(getSourceId(source), source);
    }

    public void removeAlert(CommandSource source) {
        sources.remove(getSourceId(source));
    }

    public void shutdown() {
    }

    private UUID getSourceId(CommandSource source) {
        return source.isConsole() ? CONSOLE_UUID : source.playerUuid();
    }
}
