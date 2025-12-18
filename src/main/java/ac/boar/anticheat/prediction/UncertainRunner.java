package ac.boar.anticheat.prediction;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class UncertainRunner {
    private final BoarPlayer player;

    public void uncertainPushTowardsTheClosetSpace() {
        if (player.velocity.distanceTo(player.unvalidatedTickEnd) <= 1.0E-4) {
            return;
        }

        if (Math.abs(player.unvalidatedTickEnd.y - player.velocity.y) > 1.0E-4) {
            return;
        }

        Vec3 pushTowardsClosetSpaceVel = player.unvalidatedTickEnd.subtract(player.velocity);

        if (pushTowardsClosetSpaceVel.horizontalLengthSquared() > 0.01 + 1.0E-3F) {
            return;
        }

        player.nearBamboo = false;

        final List<Box> collisions = player.compensatedWorld.collectColliders(new ArrayList<>(), player.boundingBox.expand(1.0E-3F));
        if (collisions.isEmpty() && !player.nearBamboo) {
            return;
        }

        final int signX = MathUtil.sign(pushTowardsClosetSpaceVel.x), signZ = MathUtil.sign(pushTowardsClosetSpaceVel.z);
        final int targetX = GenericMath.floor(player.position.x) + signX, targetZ = GenericMath.floor(player.position.z) + signZ;
        final Box box = new Box(targetX, player.boundingBox.minY, targetZ, targetX + 1, player.boundingBox.maxY, targetZ + 1).contract(1.0E-3F);

        player.velocity = player.unvalidatedTickEnd.clone();
    }

    public float extraOffsetNonTickEnd(float offset) {
        float extra = 0;

        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);
        boolean validYOffset = Math.abs(player.position.y - player.unvalidatedPosition.y) - extra <= player.getMaxOffset();
        boolean actualSpeedSmallerThanPredicted = actual.horizontalLengthSquared() < predicted.horizontalLengthSquared();
        boolean sameDirection = MathUtil.sameDirection(actual, predicted);
        boolean sameDirectionOrZero = (MathUtil.sign(actual.x) == MathUtil.sign(predicted.x) || actual.x == 0)
                && MathUtil.sign(actual.y) == MathUtil.sign(predicted.y) && (MathUtil.sign(actual.z) == MathUtil.sign(predicted.z) || actual.z == 0);
        if (validYOffset && (sameDirection || sameDirectionOrZero) && actualSpeedSmallerThanPredicted && player.nearBamboo && player.horizontalCollision) {
            extra = offset;
        }

        return extra;
    }

    public float extraOffset(float offset) {
        float extra = 0;

        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);

        boolean validYOffset = Math.abs(player.position.y - player.unvalidatedPosition.y) - extra <= player.getMaxOffset();
        boolean sameDirection = MathUtil.sameDirection(actual, predicted);
        boolean actualSpeedSmallerThanPredicted = actual.horizontalLengthSquared() < predicted.horizontalLengthSquared();
        boolean sameDirectionOrZero = (MathUtil.sign(actual.x) == MathUtil.sign(predicted.x) || actual.x == 0)
                && (MathUtil.sign(actual.z) == MathUtil.sign(predicted.z) || actual.z == 0);

        if (player.thisTickSpinAttack) {
            extra += player.thisTickOnGroundSpinAttack ? 0.08F : 0.008F;
        }

        boolean haveSoulSpeed = CompensatedInventory.getEnchantments(player.compensatedInventory.armorContainer.get(3).getData()).containsKey(BedrockEnchantment.SOUL_SPEED);
        if (player.soulSandBelow && !haveSoulSpeed && validYOffset && actualSpeedSmallerThanPredicted && sameDirection) {
            extra = offset;
        }

        if (player.beingPushByLava && validYOffset) {
            extra += 0.004F;
            if (sameDirection) {
                if (player.input.horizontalLengthSquared() > 0) {
                    Vec3 subtractedSpeed = actual.subtract(MathUtil.sign(player.afterCollision.x) * 0.02F, 0, MathUtil.sign(player.afterCollision.z) * 0.02F);
                    if (subtractedSpeed.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                        extra = offset;
                    }
                } else {
                    if (actual.horizontalLengthSquared() < predicted.horizontalLengthSquared()) {
                        extra = offset;
                    }
                }
            }
        }

        if (player.hasDepthStrider) {
            if (actualSpeedSmallerThanPredicted && validYOffset) {
                extra = offset;
            }
        }

        boolean isGliding = player.getFlagTracker().has(EntityFlag.GLIDING);
        boolean recentGlidingStart = player.ticksSinceGliding < 15;
        boolean recentGlidingStop = player.ticksSinceStoppedGliding < 20;

        if (isGliding) {
            float yawDelta = Math.abs(player.yaw - player.prevYaw);
            float pitchDelta = Math.abs(player.pitch - player.prevPitch);
            boolean rapidRotation = yawDelta > 15.0F || pitchDelta > 15.0F;

            if (rapidRotation && offset < 2.0F) {
                extra = Math.max(extra, offset);
            }

            if (sameDirection && offset < 1.0F) {
                extra = Math.max(extra, offset);
            }

            if ((sameDirectionOrZero || actualSpeedSmallerThanPredicted) && offset < 0.8F) {
                extra = Math.max(extra, offset);
            }

            if (validYOffset && offset < 0.5F) {
                extra = Math.max(extra, offset);
            }

            boolean nearGround = player.position.y - GenericMath.floor(player.position.y) < 0.5F;
            if (nearGround && offset < 0.3F) {
                extra = Math.max(extra, offset);
            }

            if (player.glideBoostTicks > 0) {
                extra = Math.max(extra, offset < 2.0F ? offset : 0);
            }
        }

        if (recentGlidingStart && offset < 1.5F) {
            extra = Math.max(extra, offset);
        }

        if (recentGlidingStop && offset < 1.5F) {
            extra = Math.max(extra, offset);
        }

        boolean waterExit = player.wasInWaterBeforePrediction && !player.touchingWater;
        boolean waterEntry = !player.wasInWaterBeforePrediction && player.touchingWater;
        boolean isSwimming = player.getFlagTracker().has(EntityFlag.SWIMMING);

        if (player.touchingWater || isSwimming) {
            if (offset < 0.5F) {
                extra = Math.max(extra, offset);
            }
            if (sameDirection && offset < 1.0F) {
                extra = Math.max(extra, offset);
            }
            if (sameDirectionOrZero && offset < 0.8F) {
                extra = Math.max(extra, offset);
            }
        }

        if ((waterExit || waterEntry) && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        boolean recentWaterExit = player.ticksSinceWaterExit >= 0 && player.ticksSinceWaterExit < 10;
        boolean recentSwimmingStop = player.ticksSinceStoppedSwimming > 0 && player.ticksSinceStoppedSwimming < 10;

        if ((recentWaterExit || recentSwimmingStop) && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        // https://youtube.com/shorts/VRXi7ytV290?si=-QsX8M-ojReYou9U
        boolean usingItem = player.getFlagTracker().has(EntityFlag.USING_ITEM);
        boolean recentItemUseStop = player.ticksSinceItemUse >= 0 && player.ticksSinceItemUse < 10;
        boolean recentItemUseStart = player.ticksSinceStartedItemUse < 10;
        if ((usingItem || recentItemUseStop || recentItemUseStart) && validYOffset && sameDirection) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceVelocity >= 0 && player.ticksSinceVelocity < 10) {
            extra = Math.max(extra, offset);
        }

        boolean hasQueuedVelocity = !player.queuedVelocities.isEmpty();
        if (hasQueuedVelocity && offset < 2.0F) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSincePowderSnow >= 0 && player.ticksSincePowderSnow < 10) {
            extra = Math.max(extra, offset);
        }

        float actualYDelta = player.unvalidatedPosition.y - player.prevUnvalidatedPosition.y;
        float predictedYDelta = player.position.y - player.prevUnvalidatedPosition.y;
        boolean actualYSmallerOrEqual = actualYDelta <= predictedYDelta + 0.01F;

        boolean scaffoldingMovement = Math.abs(player.unvalidatedTickEnd.y - 0.15F) < 0.02F
                || Math.abs(player.unvalidatedTickEnd.y + 0.15F) < 0.02F
                || Math.abs(actualYDelta - 0.15F) < 0.02F
                || Math.abs(actualYDelta + 0.15F) < 0.02F;

        if ((player.scaffoldDescend || scaffoldingMovement) && actualYSmallerOrEqual) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceScaffolding >= 0 && player.ticksSinceScaffolding < 3 && actualYSmallerOrEqual) {
            extra = Math.max(extra, offset);
        }

        if (player.nearShulker && Math.abs(actualYDelta) <= 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceHoneyBlock >= 0 && player.ticksSinceHoneyBlock < 10) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceSneakToggle >= 0 && player.ticksSinceSneakToggle < 5) {
            extra = Math.max(extra, offset);
        }

        // Я вообще в шоке (Коммент с исправляем обход случайно нет то написал, а именно фикс ложного детекта)
        boolean lookingDown = Math.abs(player.pitch) > 80;
        if (lookingDown && actualSpeedSmallerThanPredicted && validYOffset && offset < 0.1F) {
            extra = Math.max(extra, offset);
        }

        if (player.nearLowBlock) {
            if (offset < 2.0F) {
                extra = Math.max(extra, offset);
            }
        }

        if (player.nearThinBlock) {
            if (offset < 1.5F) {
                extra = Math.max(extra, offset);
            }
        }

        if (player.nearDripstone && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (player.ticksSinceTeleport < 5) {
            extra = Math.max(extra, offset);
        }

        boolean jumpingNearWall = player.ticksSinceJump < 5 && player.nearWall;
        boolean recentJump = player.ticksSinceJump < 3;
        if (jumpingNearWall && offset < 1.5F) {
            extra = Math.max(extra, offset);
        }

        if (recentJump && player.horizontalCollision && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        boolean stepUpSituation = player.horizontalCollision && actual.y > 0 && actual.y < 0.7F;
        if (stepUpSituation && sameDirectionOrZero && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        boolean cornerCollision = player.horizontalCollision && (actual.x == 0 || actual.z == 0) && predicted.horizontalLengthSquared() > 0;
        if (cornerCollision && offset < 0.3F) {
            extra = Math.max(extra, offset);
        }

        boolean headBonk = player.verticalCollision && !player.onGround;
        if (headBonk && offset < 0.1F) {
            extra = Math.max(extra, offset);
        }

        boolean recentHeadBonk = player.ticksSinceHeadBonk >= 0 && player.ticksSinceHeadBonk < 5;
        if (recentHeadBonk && offset < 0.05F) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }
}
