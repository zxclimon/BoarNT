package ac.boar.anticheat.prediction;

import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.input.VelocityData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.prediction.engine.impl.GlidingPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.GroundAndAirPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.fluid.LavaPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.fluid.WaterPredictionEngine;
import ac.boar.anticheat.prediction.ticker.impl.PlayerTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class PredictionRunner {
    private final BoarPlayer player;

    public void run() {
        if (!this.findBestTickStartVelocity()) {
            return;
        }

        new PlayerTicker(player).tick();
        player.predictionResult = new PredictionData(player.beforeCollision.clone(), player.afterCollision.clone(), player.velocity.clone());
        player.lastTickFinalVelocity = player.velocity.clone();
    }

    private boolean findBestTickStartVelocity() {
        final PredictionEngine engine = createEngine();
        final List<Vector> possibleVelocities = new ArrayList<>();

        boolean forceVelocity = false;
        VelocityData forcedVelocity = null;

        for (final VelocityData data : player.queuedVelocities.values()) {
            if (data.stackId() > player.receivedStackId.get()) {
                break;
            }
            forcedVelocity = data;
            forceVelocity = true;
        }

        if (forceVelocity) {
            possibleVelocities.add(new Vector(VectorType.VELOCITY, forcedVelocity.velocity(), forcedVelocity.stackId()));
        } else {
            possibleVelocities.add(new Vector(VectorType.NORMAL, player.velocity.clone()));

            VelocityData nearestVelocity = null;
            for (final VelocityData data : player.queuedVelocities.values()) {
                if ((data.stackId() - 1) > player.receivedStackId.get()) {
                    break;
                }
                nearestVelocity = data;
            }

            if (nearestVelocity != null) {
                possibleVelocities.add(new Vector(VectorType.VELOCITY, nearestVelocity.velocity(), nearestVelocity.stackId()));
            }
        }

        float closetDistance = Float.MAX_VALUE;

        if (possibleVelocities.size() == 1) {
            player.bestPossibility = possibleVelocities.get(0);
        } else {
            final Vec3 oldInput = player.input.clone();
            boolean isGliding = player.getFlagTracker().has(EntityFlag.GLIDING);

            for (Vector possibility : possibleVelocities) {
                Vec3 vec3 = possibility.getVelocity().clone();

                vec3 = player.jump(vec3);
                vec3 = engine.travel(vec3);

                if (player.stuckSpeedMultiplier.lengthSquared() > 1.0E-7) {
                    vec3 = vec3.multiply(player.stuckSpeedMultiplier);
                }

                Vec3 vec32 = Collider.collide(player, Collider.maybeBackOffFromEdge(player, vec3));
                boolean horizontal = !MathUtil.equal(vec3.x, vec32.x) || !MathUtil.equal(vec3.z, vec32.z);
                boolean vertical = vec3.y != vec32.y;

                float distance = player.position.add(vec32).squaredDistanceTo(player.unvalidatedPosition);

                if (!isGliding) {
                    if (horizontal != player.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION)) {
                        distance += 1.0E-6f;
                    }
                    if (vertical != player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION)) {
                        distance += 1.0E-6f;
                    }
                }

                if (distance <= closetDistance) {
                    closetDistance = distance;
                    player.bestPossibility = possibility;
                }
            }

            player.input = oldInput;
        }

        if (player.bestPossibility == null) {
            return false;
        }

        player.velocity = player.bestPossibility.getVelocity();
        return true;
    }

    private PredictionEngine createEngine() {
        if (player.touchingWater) {
            return new WaterPredictionEngine(player);
        } else if (player.isInLava()) {
            return new LavaPredictionEngine(player);
        } else if (player.getFlagTracker().has(EntityFlag.GLIDING)) {
            return new GlidingPredictionEngine(player);
        } else {
            return new GroundAndAirPredictionEngine(player);
        }
    }
}
