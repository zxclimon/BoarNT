package ac.boar.anticheat.prediction.engine.impl;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;

public class GlidingPredictionEngine extends PredictionEngine {
    public GlidingPredictionEngine(final BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        float velX = vec3.x, velY = vec3.y, velZ = vec3.z;

        float pitchRad = player.pitch * MathUtil.DEGREE_TO_RAD;

        final Vec3 view = MathUtil.getRotationVector(player.pitch, player.yaw);

        float horizontalViewLen = view.horizontalLength();
        float viewLenSq = view.lengthSquared();
        float currentHorizontalSpeed = vec3.horizontalLength();

        float cosPitch = TrigMath.cos(pitchRad);
        float cosPitchSq = cosPitch * cosPitch;

        float sqrtViewLen = (float) GenericMath.sqrt(viewLenSq);
        float liftFactor = Math.min(sqrtViewLen * 2.5F, 1.0F) * cosPitchSq;

        float gravity = player.getEffectiveGravity(vec3);
        velY -= (liftFactor * 0.75F - 1.0F) * -gravity;

        if (velY < 0.0F && horizontalViewLen > 0.0F) {
            float diveFactor = velY * -0.1F * liftFactor;
            float invHorizontalLen = 1.0F / horizontalViewLen;
            velX += view.x * diveFactor * invHorizontalLen;
            velY += diveFactor;
            velZ += view.z * diveFactor * invHorizontalLen;
        }

        if (pitchRad < 0.0F && horizontalViewLen > 0.0F) {
            float pullUpFactor = TrigMath.sin(pitchRad) * currentHorizontalSpeed * -0.04F;
            float invHorizontalLen = 1.0F / horizontalViewLen;
            velX -= pullUpFactor * view.x * invHorizontalLen;
            velY += pullUpFactor * 3.2F;
            velZ -= pullUpFactor * view.z * invHorizontalLen;
        }

        if (horizontalViewLen > 0.0F) {
            float invHorizontalLen = 1.0F / horizontalViewLen;
            velX += (invHorizontalLen * view.x * currentHorizontalSpeed - velX) * 0.1F;
            velZ += (invHorizontalLen * view.z * currentHorizontalSpeed - velZ) * 0.1F;
        }

        if (player.glideBoostTicks > 0) {
            velX += (view.x * 0.1F) + (((view.x * 1.5F) - velX) * 0.5F);
            velY += (view.y * 0.1F) + (((view.y * 1.5F) - velY) * 0.5F);
            velZ += (view.z * 0.1F) + (((view.z * 1.5F) - velZ) * 0.5F);
        }

        return new Vec3(velX * 0.99F, velY * 0.98F, velZ * 0.99F);
    }

    @Override
    public void finalizeMovement() {
    }
}
