package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.mappings.BlockMappings;
import ac.boar.protocol.event.CloudburstPacketEvent;
import ac.boar.protocol.listener.PacketListener;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class PostAuthInputPackets implements PacketListener {
    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof PlayerAuthInputPacket packet) {
            player.dirtyRiptide = false;
            player.thisTickSpinAttack = false;
            player.thisTickOnGroundSpinAttack = false;
            player.doingInventoryAction = false;
            player.hasDepthStrider = false;
            player.nearBamboo = false;
            player.nearLowBlock = false;
            player.nearThinBlock = false;

            Vector3i below = player.position.subtract(0, 1, 0).toVector3i();
            boolean nearShulker = BlockMappings.getShulkerBlocks().contains(player.compensatedWorld.getBlockState(below, 0).getState().block())
                    || BlockMappings.getShulkerBlocks().contains(player.compensatedWorld.getBlockState(below.add(1, 0, 0), 0).getState().block())
                    || BlockMappings.getShulkerBlocks().contains(player.compensatedWorld.getBlockState(below.add(-1, 0, 0), 0).getState().block())
                    || BlockMappings.getShulkerBlocks().contains(player.compensatedWorld.getBlockState(below.add(0, 0, 1), 0).getState().block())
                    || BlockMappings.getShulkerBlocks().contains(player.compensatedWorld.getBlockState(below.add(0, 0, -1), 0).getState().block());
            if (nearShulker) {
                player.ticksSinceShulker = 0;
            } else if (player.ticksSinceShulker < 100) {
                player.ticksSinceShulker++;
            }

            player.getTeleportUtil().getAuthInputHistory().put(packet.getTick(), new TickData(packet, player.getFlagTracker().cloneFlags(), player.dimensions));

            if (player.vehicleData != null && player.getSession().getPlayerEntity().getVehicle() == null) {
                event.setCancelled(true);
            }

            if (player.getTeleportUtil().isTeleporting()) {
                packet.setPosition(player.position.add(0, player.getYOffset(), 0).toVector3f());
                LegacyAuthInputPackets.correctInputData(player, packet);
                return;
            }

            if (player.tickSinceBlockResync > 0) player.tickSinceBlockResync--;
            player.getTeleportUtil().pollRewindHistory();
        }
    }
}