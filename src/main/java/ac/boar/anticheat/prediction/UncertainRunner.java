package ac.boar.anticheat.prediction;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class UncertainRunner {
    private static final float EPSILON = 1.0E-4F;
    private static final float SCAFFOLDING_SPEED = 0.15F;
    private static final float SCAFFOLDING_TOLERANCE = 0.02F;

    private final BoarPlayer player;

    private Vec3 actual;
    private Vec3 predicted;
    private float actualYDelta;
    private float predictedYDelta;

    private boolean validYOffset;
    private boolean sameDirection;
    private boolean actualSpeedSmaller;
    private boolean sameDirectionOrZero;

    public void uncertainPushTowardsTheClosetSpace() {
        if (player.velocity.distanceTo(player.unvalidatedTickEnd) <= EPSILON) {
            return;
        }

        if (Math.abs(player.unvalidatedTickEnd.y - player.velocity.y) > EPSILON) {
            return;
        }

        Vec3 pushVel = player.unvalidatedTickEnd.subtract(player.velocity);
        if (pushVel.horizontalLengthSquared() > 0.01F + 1.0E-3F) {
            return;
        }

        player.nearBamboo = false;

        List<Box> collisions = player.compensatedWorld.collectColliders(new ArrayList<>(), player.boundingBox.expand(1.0E-3F));
        if (collisions.isEmpty() && !player.nearBamboo) {
            return;
        }

        player.velocity = player.unvalidatedTickEnd.clone();
    }

    public float extraOffsetNonTickEnd(float offset) {
        initializeMovementData();

        if (validYOffset && (sameDirection || sameDirectionOrZero) && actualSpeedSmaller && player.nearBamboo && player.horizontalCollision) {
            return offset;
        }
        return 0;
    }

    public float extraOffset(float offset) {
        initializeMovementData();

        float extra = 0;

        extra = Math.max(extra, handleRiptide(offset));
        extra = Math.max(extra, handleSoulSand(offset));
        extra = Math.max(extra, handleLavaPush(offset));
        extra = Math.max(extra, handleDepthStrider(offset));
        extra = Math.max(extra, handleGliding(offset));
        extra = Math.max(extra, handleWater(offset));
        extra = Math.max(extra, handleItemUse(offset));
        extra = Math.max(extra, handleVelocity(offset));
        extra = Math.max(extra, handleSpecialBlocks(offset));
        extra = Math.max(extra, handleCollisions(offset));
        extra = Math.max(extra, handleMiscellaneous(offset));

        return extra;
    }

    private void initializeMovementData() {
        this.actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        this.predicted = player.position.subtract(player.prevUnvalidatedPosition);
        this.actualYDelta = player.unvalidatedPosition.y - player.prevUnvalidatedPosition.y;
        this.predictedYDelta = player.position.y - player.prevUnvalidatedPosition.y;

        this.validYOffset = Math.abs(player.position.y - player.unvalidatedPosition.y) <= player.getMaxOffset();
        this.sameDirection = MathUtil.sameDirection(actual, predicted);
        this.actualSpeedSmaller = actual.horizontalLengthSquared() < predicted.horizontalLengthSquared();
        this.sameDirectionOrZero = (MathUtil.sign(actual.x) == MathUtil.sign(predicted.x) || actual.x == 0)
                && (MathUtil.sign(actual.z) == MathUtil.sign(predicted.z) || actual.z == 0);
    }

    private float handleRiptide(float offset) {
        if (player.thisTickSpinAttack) {
            return player.thisTickOnGroundSpinAttack ? 0.08F : 0.008F;
        }
        return 0;
    }

    private float handleSoulSand(float offset) {
        boolean hasSoulSpeed = CompensatedInventory.getEnchantments(
                player.compensatedInventory.armorContainer.get(3).getData()
        ).containsKey(BedrockEnchantment.SOUL_SPEED);

        if (player.soulSandBelow && !hasSoulSpeed && validYOffset && actualSpeedSmaller && sameDirection) {
            return offset;
        }
        return 0;
    }

    private float handleLavaPush(float offset) {
        if (!player.beingPushByLava || !validYOffset) {
            return 0;
        }

        float extra = 0.004F;
        if (sameDirection) {
            if (player.input.horizontalLengthSquared() > 0) {
                Vec3 subtracted = actual.subtract(MathUtil.sign(player.afterCollision.x) * 0.02F, 0, MathUtil.sign(player.afterCollision.z) * 0.02F);
                if (subtracted.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                    return offset;
                }
            } else if (actual.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                return offset;
            }
        }
        return extra;
    }

    private float handleDepthStrider(float offset) {
        if (player.hasDepthStrider && actualSpeedSmaller && validYOffset) {
            return offset;
        }
        return 0;
    }

    private float handleGliding(float offset) {
        float extra = 0;
        boolean isGliding = player.getFlagTracker().has(EntityFlag.GLIDING);
        boolean recentStart = player.ticksSinceGliding < 15;
        boolean recentStop = player.ticksSinceStoppedGliding < 20;

        if (isGliding) {
            float yawDelta = Math.abs(player.yaw - player.prevYaw);
            float pitchDelta = Math.abs(player.pitch - player.prevPitch);

            if ((yawDelta > 15.0F || pitchDelta > 15.0F) && offset < 2.0F) extra = Math.max(extra, offset);
            if (sameDirection && offset < 1.0F) extra = Math.max(extra, offset);
            if ((sameDirectionOrZero || actualSpeedSmaller) && offset < 0.8F) extra = Math.max(extra, offset);
            if (validYOffset && offset < 0.5F) extra = Math.max(extra, offset);

            boolean nearGround = player.position.y - GenericMath.floor(player.position.y) < 0.5F;
            if (nearGround && offset < 0.3F) extra = Math.max(extra, offset);
            if (player.glideBoostTicks > 0 && offset < 2.0F) extra = Math.max(extra, offset);
        }

        if (recentStart && offset < 1.5F) extra = Math.max(extra, offset);
        if (recentStop && offset < 1.5F) extra = Math.max(extra, offset);

        return extra;
    }

    private float handleWater(float offset) {
        float extra = 0;
        boolean isSwimming = player.getFlagTracker().has(EntityFlag.SWIMMING);
        boolean inWater = player.touchingWater;
        boolean waterExit = player.wasInWaterBeforePrediction && !inWater;
        boolean waterEntry = !player.wasInWaterBeforePrediction && inWater;
        boolean swimmingUp = inWater && player.pitch < 0;

        boolean recentWaterExit = player.ticksSinceWaterExit >= 0 && player.ticksSinceWaterExit < 10;
        boolean recentSwimStop = player.ticksSinceStoppedSwimming > 0 && player.ticksSinceStoppedSwimming < 10;
        boolean veryRecentExit = player.ticksSinceWaterExit >= 0 && player.ticksSinceWaterExit < 3;

        float yOffset = Math.abs(player.position.y - player.unvalidatedPosition.y);
        boolean validWaterY = yOffset < 0.5F;

        if (waterExit && sameDirection && actualSpeedSmaller && offset < 0.6F) {
            extra = Math.max(extra, offset);
        }

        if ((waterEntry || swimmingUp) && actualSpeedSmaller && sameDirection && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if ((isSwimming || inWater) && sameDirection && actualSpeedSmaller && offset < 0.2F) {
            extra = Math.max(extra, offset);
        }

        if (inWater && actual.y > 0.2F && actualSpeedSmaller && sameDirection && offset < 0.15F) {
            extra = Math.max(extra, offset);
        }

        if (inWater && actual.y > 0.3F && actualSpeedSmaller && sameDirection && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (inWater && validWaterY && actualSpeedSmaller && offset < 0.3F) {
            extra = Math.max(extra, offset);
        }

        if (veryRecentExit && sameDirection && actualSpeedSmaller && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (recentWaterExit && sameDirection && actualSpeedSmaller && offset < 0.3F) {
            extra = Math.max(extra, offset);
        }

        if (recentSwimStop && actualSpeedSmaller && sameDirection && offset < 0.3F) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }

    private float handleItemUse(float offset) {
        boolean usingItem = player.getFlagTracker().has(EntityFlag.USING_ITEM);
        boolean recentStop = player.ticksSinceItemUse >= 0 && player.ticksSinceItemUse < 10;
        boolean recentStart = player.ticksSinceStartedItemUse < 10;

        if ((usingItem || recentStop || recentStart) && validYOffset && sameDirection) {
            return offset;
        }
        return 0;
    }

    private float handleVelocity(float offset) {
        float extra = 0;

        if (player.ticksSinceVelocity >= 0 && player.ticksSinceVelocity < 10) {
            extra = Math.max(extra, offset);
        }

        if (!player.queuedVelocities.isEmpty() && offset < 2.0F) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }

    private float handleSpecialBlocks(float offset) {
        float extra = 0;
        boolean actualYSmaller = actualYDelta <= predictedYDelta + 0.01F;

        if (player.ticksSincePowderSnow >= 0 && player.ticksSincePowderSnow < 10) {
            extra = Math.max(extra, offset);
        }

        boolean scaffolding = Math.abs(player.unvalidatedTickEnd.y - SCAFFOLDING_SPEED) < SCAFFOLDING_TOLERANCE
                || Math.abs(player.unvalidatedTickEnd.y + SCAFFOLDING_SPEED) < SCAFFOLDING_TOLERANCE
                || Math.abs(actualYDelta - SCAFFOLDING_SPEED) < SCAFFOLDING_TOLERANCE
                || Math.abs(actualYDelta + SCAFFOLDING_SPEED) < SCAFFOLDING_TOLERANCE;

        if ((player.scaffoldDescend || scaffolding) && actualYSmaller) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceScaffolding >= 0 && player.ticksSinceScaffolding < 3 && actualYSmaller) {
            extra = Math.max(extra, offset);
        }

        if (player.nearShulker && Math.abs(actualYDelta) <= 0.5F) extra = Math.max(extra, offset);
        if (player.ticksSinceHoneyBlock >= 0 && player.ticksSinceHoneyBlock < 10) extra = Math.max(extra, offset);
        if (player.nearLowBlock && offset < 2.0F) extra = Math.max(extra, offset);
        if (player.nearThinBlock && offset < 1.5F) extra = Math.max(extra, offset);
        if (player.nearDripstone && offset < 0.5F) extra = Math.max(extra, offset);

        return extra;
    }

    private float handleCollisions(float offset) {
        float extra = 0;

        boolean jumpingNearWall = player.ticksSinceJump < 5 && player.nearWall;
        boolean recentJump = player.ticksSinceJump < 3;
        boolean stepUp = player.horizontalCollision && actual.y > 0 && actual.y < 0.7F;
        boolean corner = player.horizontalCollision && (actual.x == 0 || actual.z == 0) && predicted.horizontalLengthSquared() > 0;
        boolean headBonk = player.verticalCollision && !player.onGround;
        boolean recentHeadBonk = player.ticksSinceHeadBonk >= 0 && player.ticksSinceHeadBonk < 5;

        if (jumpingNearWall && offset < 1.5F) extra = Math.max(extra, offset);
        if (recentJump && player.horizontalCollision && offset < 0.5F) extra = Math.max(extra, offset);
        if (stepUp && sameDirectionOrZero && offset < 0.5F) extra = Math.max(extra, offset);
        if (corner && offset < 0.3F) extra = Math.max(extra, offset);
        if (headBonk && offset < 0.1F) extra = Math.max(extra, offset);
        if (recentHeadBonk && offset < 0.05F) extra = Math.max(extra, offset);

        return extra;
    }

    private float handleMiscellaneous(float offset) {
        float extra = 0;

        if (player.ticksSinceSneakToggle >= 0 && player.ticksSinceSneakToggle < 5) {
            extra = Math.max(extra, offset);
        }

        if (Math.abs(player.pitch) > 80 && actualSpeedSmaller && validYOffset && offset < 0.1F) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceTeleport < 5) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }
}
