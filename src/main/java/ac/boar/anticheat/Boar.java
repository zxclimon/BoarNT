package ac.boar.anticheat;

import ac.boar.anticheat.alert.AlertManager;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.config.ConfigLoader;
import ac.boar.anticheat.packets.input.AuthInputPackets;
import ac.boar.anticheat.packets.input.PostAuthInputPackets;
import ac.boar.anticheat.packets.other.PacketCheckRunner;
import ac.boar.anticheat.packets.player.*;
import ac.boar.anticheat.packets.server.ServerChunkPackets;
import ac.boar.anticheat.packets.server.ServerEntityPackets;
import ac.boar.anticheat.packets.server.ServerDataPackets;
import ac.boar.geyser.GeyserBoar;
import ac.boar.mappings.BlockMappings;
import lombok.Getter;

import ac.boar.anticheat.packets.other.NetworkLatencyPackets;

import ac.boar.anticheat.player.manager.BoarPlayerManager;
import ac.boar.protocol.PacketEvents;
import lombok.Setter;

@Getter
public class Boar {
    @Getter
    private final static Boar instance = new Boar();
    @Getter @Setter
    private static Config config;
    private Boar() {}

    private BoarPlayerManager playerManager;
    private AlertManager alertManager;

    public void init(GeyserBoar instance) {
        config = ConfigLoader.load(instance, GeyserBoar.class, Config.class, Config.DEFAULT_CONFIG);
        BlockMappings.load();

        this.playerManager = new BoarPlayerManager();
        this.alertManager = new AlertManager();

        PacketEvents.getApi().register(new NetworkLatencyPackets());
        PacketEvents.getApi().register(new ServerChunkPackets());
        PacketEvents.getApi().register(new ServerEntityPackets());
        PacketEvents.getApi().register(new ServerDataPackets());
        PacketEvents.getApi().register(new PlayerEffectPackets());
        PacketEvents.getApi().register(new PlayerVelocityPackets());
        PacketEvents.getApi().register(new PlayerInventoryPackets());
        PacketEvents.getApi().register(new PacketCheckRunner());
        PacketEvents.getApi().register(new AuthInputPackets());
        PacketEvents.getApi().register(new PostAuthInputPackets());
    }

    public void terminate(GeyserBoar instance) {
        PacketEvents.getApi().terminate();
        this.playerManager.clear();
        this.alertManager.shutdown();

        ConfigLoader.save(instance, GeyserBoar.class, config);
    }

    public static void debug(String message) {
        if (!config.debugMode()) {
            return;
        }
        instance.alertManager.debug(message);
    }
}
