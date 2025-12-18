package ac.boar.anticheat.check.impl.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.annotations.CheckInfo;
import ac.boar.anticheat.check.api.impl.OffsetHandlerCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.HashMap;
import java.util.Map;

@CheckInfo(name = "Prediction")
public class Prediction extends OffsetHandlerCheck {
    private final Map<String, Check> checks = new HashMap<>();

    public Prediction(BoarPlayer player) {
        super(player);

        this.checks.put("Phase", new Check(player, "Phase", "", false));
        this.checks.put("Velocity", new Check(player, "Velocity", "", false));
        this.checks.put("Strafe", new Check(player, "Strafe", "", false));
        this.checks.put("Speed", new Check(player, "Speed", "", false));
        this.checks.put("Flight", new Check(player, "Flight", "", false));
        this.checks.put("Collisions", new Check(player, "Collisions", "", false));
    }

    @Override
    public void onPredictionComplete(float offset) {
        if (player.tick < 10) {
            return;
        }

        if (offset < player.getMaxOffset()) {
            return;
        }

        if (!shouldDoFail() || offset < Boar.getConfig().alertThreshold()) {
            player.getTeleportUtil().rewind(player.tick);
            return;
        }

        boolean isGliding = player.getFlagTracker().has(EntityFlag.GLIDING);
        boolean recentGlidingChange = player.ticksSinceGliding < 15 || player.ticksSinceStoppedGliding < 15;
        boolean hasGlideBoost = player.glideBoostTicks > 0;

        if (isGliding || recentGlidingChange || hasGlideBoost) {
            float yawDelta = Math.abs(player.yaw - player.prevYaw);
            float pitchDelta = Math.abs(player.pitch - player.prevPitch);

            if (yawDelta > 10.0F || pitchDelta > 10.0F) {
                player.getTeleportUtil().rewind(player.tick);
                return;
            }

            if (offset < 1.0F) {
                player.getTeleportUtil().rewind(player.tick);
                return;
            }
        }

        boolean isSwimming = player.getFlagTracker().has(EntityFlag.SWIMMING);
        boolean recentWaterChange = player.ticksSinceWaterExit >= 0 && player.ticksSinceWaterExit < 10;
        boolean recentSwimmingChange = player.ticksSinceStoppedSwimming > 0 && player.ticksSinceStoppedSwimming < 10;

        if (isSwimming || player.touchingWater || recentWaterChange || recentSwimmingChange) {
            if (offset < 0.5F) {
                player.getTeleportUtil().rewind(player.tick);
                return;
            }
        }

        boolean recentVelocity = player.ticksSinceVelocity >= 0 && player.ticksSinceVelocity < 10;
        boolean hasQueuedVelocity = !player.queuedVelocities.isEmpty();

        if (recentVelocity || hasQueuedVelocity) {
            if (offset < 2.0F) {
                player.getTeleportUtil().rewind(player.tick);
                return;
            }
        }

        boolean nearSmallHitbox = player.nearLowBlock || player.nearThinBlock || player.nearDripstone;

        if (nearSmallHitbox) {
            if (offset < 2.0F) {
                player.getTeleportUtil().rewind(player.tick);
                return;
            }
        }

        player.getTeleportUtil().rewind(player.tick);

        boolean claimedHorizontal = player.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION);
        boolean claimedVertical = player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION);

        boolean jumpingNearWall = player.ticksSinceJump < 5 && player.nearWall;
        boolean recentJump = player.ticksSinceJump < 3;

        if (!isGliding && !recentGlidingChange && !jumpingNearWall && !recentJump && !recentVelocity && !hasQueuedVelocity && !nearSmallHitbox) {
            if (claimedVertical != player.verticalCollision || claimedHorizontal != player.horizontalCollision) {
                fail("Phase", "o: " + offset + ", expect: (" + player.horizontalCollision + "," + player.verticalCollision + "), actual: (" + claimedHorizontal + "," + claimedVertical + ")");
            }
        }

        if (player.bestPossibility.getType() == VectorType.VELOCITY && offset > 0.1F) {
            if (!isGliding && !hasGlideBoost) {
                fail("Velocity", "o: " + offset);
            }
            return;
        }

        float eotDiff = player.unvalidatedTickEnd.distanceTo(player.velocity);
        if (eotDiff < player.getMaxOffset() && offset > 5.0E-4F) {
            if (!isGliding && !recentGlidingChange && !recentVelocity && !hasQueuedVelocity && !nearSmallHitbox) {
                fail("Collisions", "o: " + offset);
            }
        }

        Vec3 actual = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 predicted = player.position.subtract(player.prevUnvalidatedPosition);

        boolean cornerOrWallCollision = player.horizontalCollision && (actual.x == 0 || actual.z == 0);

        if (!isGliding && !recentGlidingChange && !isSwimming && !recentWaterChange && !jumpingNearWall && !cornerOrWallCollision && !recentVelocity && !hasQueuedVelocity && !nearSmallHitbox) {
            if (!MathUtil.sameDirectionHorizontal(actual, predicted)) {
                fail("Strafe", "o: " + offset + ", expected direction: " + MathUtil.signAll(predicted).horizontalToString() + ", actual direction: " + MathUtil.signAll(actual).horizontalToString());
            }
        }

        float squaredActual = actual.horizontalLengthSquared(), squaredPredicted = predicted.horizontalLengthSquared();
        float speedDiff = squaredActual - squaredPredicted;

        float speedThreshold = 1.0E-4F;
        if (isGliding || recentGlidingChange || hasGlideBoost) {
            speedThreshold = 0.5F;
        } else if (isSwimming || player.touchingWater || recentWaterChange) {
            speedThreshold = 0.1F;
        } else if (jumpingNearWall || cornerOrWallCollision) {
            speedThreshold = 0.15F;
        } else if (recentVelocity || hasQueuedVelocity) {
            speedThreshold = 0.5F;
        } else if (nearSmallHitbox) {
            speedThreshold = 1.0F;
        }

        if (speedDiff > speedThreshold) {
            fail("Speed", "o: " + offset + ", expected: " + squaredPredicted + ", actual: " + squaredActual);
        }

        float yThreshold = player.getMaxOffset();
        if (isGliding || recentGlidingChange || hasGlideBoost) {
            yThreshold = 0.5F;
        } else if (isSwimming || player.touchingWater || recentWaterChange) {
            yThreshold = 0.3F;
        } else if (jumpingNearWall || recentJump) {
            yThreshold = 0.3F;
        } else if (recentVelocity || hasQueuedVelocity) {
            yThreshold = 1.5F;
        } else if (nearSmallHitbox) {
            yThreshold = 1.5F;
        }

        if (Math.abs(player.position.y - player.unvalidatedPosition.y) > yThreshold) {
            fail("Flight", "o: " + offset);
        }
    }

    public boolean shouldDoFail() {
        if (player.tickSinceBlockResync > 0 || player.insideUnloadedChunk || player.getTeleportUtil().isTeleporting() || player.sinceLoadingScreen <= 5) {
            return false;
        }
        if (!player.compensatedWorld.isChunkLoaded((int) player.position.x, (int) player.position.z)) {
            return false;
        }
        if (player.ticksSinceTeleport < 5) {
            return false;
        }
        return true;
    }

    public void fail(String name, String verbose) {
        if (Boar.getConfig().disabledChecks().contains(name)) {
            return;
        }

        this.checks.get(name).fail(verbose);
    }
}
