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

    // Кэшированные данные о движении для текущего тика
    private Vec3 actual;      // Реальное движение игрока
    private Vec3 predicted;   // Наше предсказанное движение
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

    /**
     * Extra offset для случаев когда tick end velocity не совпадает
     * В основном для бамбука
     */
    public float extraOffsetNonTickEnd(float offset) {
        initializeMovementData();

        if (validYOffset && (sameDirection || sameDirectionOrZero) && actualSpeedSmaller && player.nearBamboo && player.horizontalCollision) {
            return offset;
        }
        return 0;
    }

    /**
     * Главный метод считает сколько offset можно простить игроку
     * проходит по всем возможным причинам расхождения и берёт максимум
     */
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

    /**
     * трезубец игрок летит с огромной скоростью предсказать точно нереально
     * даем небольшой tolerance
     */
    private float handleRiptide(float offset) {
        if (player.thisTickSpinAttack) {
            return player.thisTickOnGroundSpinAttack ? 0.08F : 0.008F;
        }
        return 0;
    }

    /**
     * Soul sand замедляет игрока, но точное замедление зависит от зачара Soul Speed
     */
    private float handleSoulSand(float offset) {
        boolean hasSoulSpeed = CompensatedInventory.getEnchantments(
                player.compensatedInventory.armorContainer.get(3).getData()
        ).containsKey(BedrockEnchantment.SOUL_SPEED);

        if (player.soulSandBelow && !hasSoulSpeed && validYOffset && actualSpeedSmaller && sameDirection) {
            return offset;
        }
        return 0;
    }

    /**
     * Лава
     */
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

    /**
     * Странник глубин меняет скорость в воде
     */
    private float handleDepthStrider(float offset) {
        if (player.hasDepthStrider && actualSpeedSmaller && validYOffset) {
            return offset;
        }
        return 0;
    }

    /**
     * элитры :(  самая сложная механика для предсказания
     * Зависит от pitch, yaw, скорости, высоты куча факторов
     * даем большой tolerance особенно при резких поворотах
     */
    private float handleGliding(float offset) {
        float extra = 0;
        boolean isGliding = player.getFlagTracker().has(EntityFlag.GLIDING);
        boolean recentStart = player.ticksSinceGliding < 15;
        boolean recentStop = player.ticksSinceStoppedGliding < 20;

        if (isGliding) {
            float yawDelta = Math.abs(player.yaw - player.prevYaw);
            float pitchDelta = Math.abs(player.pitch - player.prevPitch);

            // Резкий поворот - предсказание будет неточным
            if ((yawDelta > 15.0F || pitchDelta > 15.0F) && offset < 2.0F) extra = Math.max(extra, offset);
            if (sameDirection && offset < 1.0F) extra = Math.max(extra, offset);
            if ((sameDirectionOrZero || actualSpeedSmaller) && offset < 0.8F) extra = Math.max(extra, offset);
            if (validYOffset && offset < 0.5F) extra = Math.max(extra, offset);

            // Близко к земле  collision  может отличаться
            boolean nearGround = player.position.y - GenericMath.floor(player.position.y) < 0.5F;
            if (nearGround && offset < 0.3F) extra = Math.max(extra, offset);

            // буст от фейра  вообще непредсказуемо
            if (player.glideBoostTicks > 0 && offset < 2.0F) extra = Math.max(extra, offset);
        }

        // Переходные состояния - начало/конец полёта
        if (recentStart && offset < 1.5F) extra = Math.max(extra, offset);
        if (recentStop && offset < 1.5F) extra = Math.max(extra, offset);

        return extra;
    }

    /**
     * Вода
     *  Вход/выход из воды и т.д лень писать
     */
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

        // Плывём вдоль стены
        if (inWater && player.horizontalCollision) extra = Math.max(extra, offset);

        // В воде и движемся в том же направлении flow может отличаться
        if (inWater && sameDirectionOrZero && offset < 0.2F) extra = Math.max(extra, offset);

        // Выход из воды  резкая смена физики
        if (waterExit && sameDirection && actualSpeedSmaller && offset < 0.6F) {
            extra = Math.max(extra, offset);
        }

        // Вход в воду или всплытие flow сразу начинает толкать
        if (waterEntry && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (swimmingUp && actualSpeedSmaller && sameDirection && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        // Просто в воде небольшой tolerance
        if ((isSwimming || inWater) && sameDirection && actualSpeedSmaller && offset < 0.2F) {
            extra = Math.max(extra, offset);
        }

        // Всплываем быстро
        if (inWater && actual.y > 0.2F && actualSpeedSmaller && sameDirection && offset < 0.15F) {
            extra = Math.max(extra, offset);
        }

        if (inWater && actual.y > 0.3F && actualSpeedSmaller && sameDirection && offset < 0.5F) {
            extra = Math.max(extra, offset);
        }

        if (inWater && validWaterY && actualSpeedSmaller && offset < 0.3F) {
            extra = Math.max(extra, offset);
        }

        // Недавно вышли из воды инерция
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

    /**
     * Использование предметов еда, лук, щит замедляет игрока
     */
    private float handleItemUse(float offset) {
        boolean usingItem = player.getFlagTracker().has(EntityFlag.USING_ITEM);
        boolean recentStop = player.ticksSinceItemUse >= 0 && player.ticksSinceItemUse < 10;
        boolean recentStart = player.ticksSinceStartedItemUse < 10;

        if ((usingItem || recentStop || recentStart) && validYOffset && sameDirection) {
            return offset;
        }
        return 0;
    }

    /**
     * velocity от сервера  пока не подтвердили получени даем tolerance
     */
    private float handleVelocity(float offset) {
        float extra = 0;

        // Недавно получили velocity
        if (player.ticksSinceVelocity >= 0 && player.ticksSinceVelocity < 10) {
            extra = Math.max(extra, offset);
        }

        // Есть velocity в очереди которые ещё не применили
        if (!player.queuedVelocities.isEmpty() && offset < 2.0F) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }

    /**
     * Нестандартные блоки
     */
    private float handleSpecialBlocks(float offset) {
        float extra = 0;
        boolean actualYSmaller = actualYDelta <= predictedYDelta + 0.01F;

        // рыхлый снег можно проваливаться или ходить по нему
        if (player.ticksSincePowderSnow >= 0 && player.ticksSincePowderSnow < 10) {
            extra = Math.max(extra, offset);
        }

        //Scaffolding фиксированная скорость подъёма/спуска
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

        // шалкер открывается и выталкивает
        if (player.nearShulker && Math.abs(actualYDelta) <= 0.5F) extra = Math.max(extra, offset);

        // блок мёда - замедление и прилипание
        if (player.ticksSinceHoneyBlock >= 0 && player.ticksSinceHoneyBlock < 10) extra = Math.max(extra, offset);

        // Блоки с маленькими/странными collision
        if (player.nearLowBlock && offset < 2.0F) extra = Math.max(extra, offset);
        if (player.nearThinBlock && offset < 1.5F) extra = Math.max(extra, offset);
        if (player.nearDripstone && offset < 0.5F) extra = Math.max(extra, offset);

        return extra;
    }

    /**
     * Коллизии
     * - Углы блоков
     * - Прыжки рядом со стеной
     * - Удар головой о потолок и т.д т.п
     */
    private float handleCollisions(float offset) {
        float extra = 0;
        // просто погрешность
        if (offset < 0.001F) extra = Math.max(extra, offset);

        boolean jumpingNearWall = player.ticksSinceJump < 5 && player.nearWall;
        boolean recentJump = player.ticksSinceJump < 3;
        boolean stepUp = player.horizontalCollision && actual.y > 0 && actual.y < 0.7F;
        boolean corner = player.horizontalCollision && (actual.x == 0 || actual.z == 0) && predicted.horizontalLengthSquared() > 0;
        boolean headBonk = player.verticalCollision && !player.onGround;
        boolean recentHeadBonk = player.ticksSinceHeadBonk >= 0 && player.ticksSinceHeadBonk < 5;

        // Step up игрок поднялся на блок
        // часто бывает на угловых ступеньках где collision отличается
        boolean possibleStepUp = actual.y > 0 && actual.y <= 0.6F && predicted.y <= 0;
        if (possibleStepUp && offset < 0.6F) extra = Math.max(extra, offset);

        // Прыжок рядом со стеной траектория меняется
        if (jumpingNearWall && offset < 1.5F) extra = Math.max(extra, offset);
        if (recentJump && player.horizontalCollision && offset < 0.5F) extra = Math.max(extra, offset);

        // Step up с horizontal collision
        if (stepUp && sameDirectionOrZero && offset < 0.5F) extra = Math.max(extra, offset);

        // Угол блока игрок застревает на углу
        if (corner && offset < 0.3F) extra = Math.max(extra, offset);

        // Удар головой о потолок
        if (headBonk && offset < 0.1F) extra = Math.max(extra, offset);
        if (recentHeadBonk && offset < 0.05F) extra = Math.max(extra, offset);

        return extra;
    }

    /**
     * Разное:
     * - Переключение sneak и т.д т.п
     */
    private float handleMiscellaneous(float offset) {
        float extra = 0;
        // Sneak toggle
        if (player.ticksSinceSneakToggle >= 0 && player.ticksSinceSneakToggle < 5) {
            extra = Math.max(extra, offset);
        }
        // Смотрим почти вертикально
        if (Math.abs(player.pitch) > 80 && actualSpeedSmaller && validYOffset && offset < 0.1F) {
            extra = Math.max(extra, offset);
        }

        // После телепортации даём время на синхронизацию
        if (player.ticksSinceTeleport < 5) {
            extra = Math.max(extra, offset);
        }

        return extra;
    }
}
