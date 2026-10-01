package net.romeo.sightingsofaplayer.entity.custom;

import java.util.EnumSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;
import net.romeo.sightingsofaplayer.util.SOAPPlayerUtils;

/**
 * A silent, black silhouette that keeps its distance and never despawns on its own.
 * <p>
 * The creature is deliberately passive. It has no attack goal, no targeting and no way to hurt
 * anyone - it only ever stares and, at most, creeps a little closer. Everything it does is a
 * function of who is watching it:
 * <ul>
 *     <li><b>Approach</b> - {@link StalkNearestPlayerGoal} alternates between standing perfectly
 *     still and taking a very slow step towards the nearest player. It never actually reaches them;
 *     it stops at {@link #MIN_APPROACH} blocks.</li>
 *     <li><b>Being seen</b> - once a player keeps it inside a narrow cone around their crosshair with
 *     a clear line of sight for {@link #LOOK_VANISH_TICKS} ticks, it {@linkplain #discard() removes
 *     itself}. A casual glance towards the edge of the screen does not count - it has to be looked
 *     <i>at</i>. The client draws the censor bars over it under the exact same rule, so what the
 *     player sees and what the creature reacts to can never disagree.</li>
 * </ul>
 * <p>
 */
public class MythStalkerEntity extends PathfinderMob {

    /**
     * Half-angle around a player's crosshair that still counts as looking at this creature. The same
     * value is used by the client when it decides whether the censor bars should be drawn, which is
     * what keeps the visual and the behaviour in sync.
     */
    public static final float LOOK_FOV_DEGREES = 20.0F;

    /** {@link #LOOK_FOV_DEGREES} pre-folded into the dot-product test {@code SOAPPlayerUtils} wants. */
    private static final double LOOK_THRESHOLD = Math.cos(Math.toRadians(LOOK_FOV_DEGREES));

    /** How many consecutive ticks it must be kept in view before it gives up and vanishes. */
    private static final int LOOK_VANISH_TICKS = 10;

    /** How far away a player can be and still hold it in view. */
    private static final double LOOK_RANGE = 256;

    /** Ticks spent standing still / creeping forward before the creature switches to the other. */
    private static final int IDLE_TICKS = 140;
    private static final int WALK_TICKS = 80;

    /** How close it is willing to creep before it stops for good. */
    private static final double MIN_APPROACH = 16.0D;

    /** Fraction of {@link Attributes#MOVEMENT_SPEED} handed to the pathfinder - deliberately tiny. */
    private static final double CREEP_SPEED = 0.25D;

    /** Consecutive ticks a player has currently been looking at this creature. */
    private int lookedAtTicks;

    public MythStalkerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        addTag("soap_censored");
    }

    /**
     * Registered through {@code EntityAttributeCreationEvent}. Movement speed is the vanilla hostile
     * baseline; the slowness comes from {@link #CREEP_SPEED}, not from a crippled attribute, so the
     * creature can still be nudged onto a path if a player pushes against it.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, .23D)
                .add(Attributes.FOLLOW_RANGE, LOOK_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        Predicate<LivingEntity> predicate = livingEntity -> lookedAtTicks>0;

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StalkNearestPlayerGoal(this));
        //this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this,Player.class,512,10,10,predicate));
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // -------------------------------------------------------------------------------------------------
    // Vanishes when looked at
    // -------------------------------------------------------------------------------------------------

    /**
     * Runs on both sides, but only the logical server acts on it - the client needs nothing more than
     * {@link #isLookedAtBy(Player)} to decide whether the censor bars should cover the creature.
     */
    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide) {
            return;
        }

        if (this.isBeingLookedAt()) {
            if (++this.lookedAtTicks >= LOOK_VANISH_TICKS) {
                vanish();
            }
        } else {
            this.lookedAtTicks = 0;
        }
    }

    private void vanish() {
        this.discard();

        SOAPEventSavedData eventData = SOAPEventSavedData.get(getServer());
        eventData.addTension(150);

        SoundEvent sound = SoundEvents.AMBIENT_CAVE.value();
        level().playSound(null, blockPosition(), sound, SoundSource.AMBIENT,
                32.0F, 0.6F + level().getRandom().nextFloat() * 0.3F);
    }

    /** Whether <b>any</b> nearby player currently meets the "looking at it" test. */
    private boolean isBeingLookedAt() {
        for (Player player : this.level().players()) {
            if (this.isLookedAtBy(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The one shared definition of "this player is looking at the creature": inside
     * {@link #LOOK_FOV_DEGREES} of their crosshair, close enough, and not hidden behind a wall.
     * <p>
     * Used by the server to decide when to vanish and by the client to decide when to draw the
     * censor bars, so the two sides can never drift apart.
     */
    public boolean isLookedAtBy(Player player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }

        if (this.distanceToSqr(player) > LOOK_RANGE * LOOK_RANGE) {
            return false;
        }

        if (!SOAPPlayerUtils.lookingAt(player, this.getEyePosition(), LOOK_THRESHOLD)) {
            return false;
        }

        return player.hasLineOfSight(this);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private static final class StalkNearestPlayerGoal extends Goal {

        private static final double SEARCH_RANGE = LOOK_RANGE;

        private final MythStalkerEntity stalker;

        @Nullable
        private Player target;

        private boolean creeping;

        private int phaseTicks;

        StalkNearestPlayerGoal(MythStalkerEntity stalker) {
            this.stalker = stalker;
            // Both flags: while it runs, the goal owns the creature's movement and its head.
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // getNearestPlayer(Entity, double) already ignores creative and spectator players.
            this.target = this.stalker.level().getNearestPlayer(this.stalker, SEARCH_RANGE);
            return this.target != null && this.target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null
                    && this.target.isAlive()
                    && this.stalker.distanceToSqr(this.target) <= SEARCH_RANGE * SEARCH_RANGE;
        }

        @Override
        public void start() {
            // It always arrives by standing perfectly still.
            this.creeping = false;
            this.phaseTicks = IDLE_TICKS;
        }

        @Override
        public void stop() {
            this.target = null;
            this.stalker.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }

            this.stalker.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            if (--this.phaseTicks <= 0) {
                this.creeping = !this.creeping;
                this.phaseTicks = this.creeping ? WALK_TICKS : IDLE_TICKS;
            }

            if (!this.creeping || this.stalker.distanceTo(this.target) <= MIN_APPROACH) {
                this.stalker.getNavigation().stop();
                return;
            }

            // Only re-path once the previous leg is finished, otherwise the path is rebuilt every
            // single tick and the creature visibly stutters in place.
            if (this.stalker.getNavigation().isDone()) {
                this.stalker.getNavigation().moveTo(this.target, CREEP_SPEED);
            }

            if (target.level().isClientSide())
                return;
        }
    }

}
