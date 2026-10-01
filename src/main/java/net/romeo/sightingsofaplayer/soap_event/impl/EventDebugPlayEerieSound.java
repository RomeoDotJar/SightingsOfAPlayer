package net.romeo.sightingsofaplayer.soap_event.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * An eerie sound plays on top of a player, and a second event is dragged along with it.
 * <p>
 * Shows the two things a {@code soap_event} can do beyond acting on the world directly: it has a
 * real condition ({@link #canRun} - night only) and it can ask the system for another event then
 * and there, which is what the second half of its name is about.
 */
public class EventDebugPlayEerieSound extends SoapEvent {

    /** Everything this event may pick from. All of it is uncomfortable to hear at night. */
    private static final SoundEvent[] EERIE_SOUNDS = {
            SoundEvents.AMBIENT_CAVE.value(),
            SoundEvents.ENDERMAN_STARE,
            SoundEvents.SCULK_SHRIEKER_SHRIEK,
            SoundEvents.WARDEN_NEARBY_CLOSER
    };

    @Override
    public String id() {
        return "debug_play_eerie_sound";
    }

    @Override
    public float nextDelayMultiplier() {
        return .1f;
    }

    @Override
    public float selectionWeight() {
        return 1f;
    }

    /**
     * Night only, exactly as specified. Checked when the event is picked and once more just before
     * it runs, so an event chosen at dusk is dropped rather than fired in broad daylight.
     */
    @Override
    public boolean canRun(SoapEventContext context) {
        return context.isNight();
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerLevel level = context.overworld();
        ServerPlayer target = context.randomPlayer();

        // The sound is meant for whoever is there, so a random player is the first choice; with
        // nobody online it falls back to the world spawn. That way the event always does something
        // instead of burning its turn, which matters because "night" is its only condition.
        double x;
        double y;
        double z;
        if (target != null) {
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        } else {
            BlockPos spawn = level.getSharedSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY();
            z = spawn.getZ() + 0.5D;
        }

        SoundEvent sound = EERIE_SOUNDS[randomIndex(context.random(), EERIE_SOUNDS.length)];
        level.playSound(null, x, y, z, sound, SoundSource.AMBIENT,
                1.0F, 0.8F + context.random().nextFloat() * 0.4F);

        // The other half of the name: pull another event in right now. The capability comes from
        // the base class, which also keeps this event from being chosen again by its own chain.
        return true;
    }
}
