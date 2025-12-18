package ac.boar.anticheat.prediction.engine.impl.fluid;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

import java.util.Map;

public class WaterPredictionEngine extends PredictionEngine {
    private float tickEndSpeed;
    private boolean isSwimming;

    public WaterPredictionEngine(BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        this.isSwimming = player.getFlagTracker().has(EntityFlag.SWIMMING);

        ItemData boostSlot = player.compensatedInventory.armorContainer.get(3).getData();
        Map<BedrockEnchantment, Integer> enchantments = CompensatedInventory.getEnchantments(boostSlot);
        Integer depthStrider = enchantments.get(BedrockEnchantment.DEPTH_STRIDER);

        float h = 0;
        if (depthStrider != null) {
            h = 0.33333334f + 0.33333334f * (float)(depthStrider - 1);
        }

        if (!player.onGround && this.isSwimming) {
            h *= 0.5F;
        }

        this.tickEndSpeed = h;

        player.hasDepthStrider = this.tickEndSpeed > 0 && (!this.isSwimming || depthStrider >= 4);

        float speed = h > 0 ? 0.02F + ((player.getSpeed() - 0.02F) * h) : 0.02F;

        if (this.isSwimming) {
            float lookY = (float) -Math.sin(Math.toRadians(player.pitch));
            if (lookY > 0 && player.getInputData().contains(PlayerAuthInputData.JUMPING)) {
                vec3 = vec3.add(0, 0.04F, 0);
            }
        }

        return this.moveRelative(vec3, speed);
    }

    @Override
    public void finalizeMovement() {
        if (!this.isSwimming && !player.onGround) {
            this.tickEndSpeed *= 0.5F;
        }

        boolean sprinting = player.getFlagTracker().has(EntityFlag.SPRINTING);
        boolean fastTickEnd = sprinting || player.getInputData().contains(PlayerAuthInputData.STOP_SWIMMING) || this.isSwimming;

        float f = fastTickEnd ? 0.9F : 0.8F;
        f += (0.54600006f - f) * this.tickEndSpeed;

        player.velocity = player.velocity.multiply(f, 0.8F, f);
        player.velocity = this.getFluidFallingAdjustedMovement(player.getEffectiveGravity(), player.velocity);
    }

    private Vec3 getFluidFallingAdjustedMovement(float gravity, Vec3 motion) {
        if (player.hasEffect(Effect.LEVITATION)) {
            float y = motion.y + (((player.getEffect(Effect.LEVITATION).getAmplifier() + 1) * 0.05F) - motion.y) * 0.2F;
            return new Vec3(motion.x, y, motion.z);
        }

        if (gravity != 0.0 && !this.isSwimming) {
            return new Vec3(motion.x, motion.y - (gravity / 16.0F), motion.z);
        }

        return motion;
    }
}