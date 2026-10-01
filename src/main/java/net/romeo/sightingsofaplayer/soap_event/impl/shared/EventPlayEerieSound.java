package net.romeo.sightingsofaplayer.soap_event.impl.shared;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

import java.util.function.Supplier;

/**
 * An eerie sound plays on top of a player, and a second event is dragged along with it.
 * <p>
 * Shows the two things a {@code soap_event} can do beyond acting on the world directly: it has a
 * real condition ({@link #canRun} - night only) and it can ask the system for another event then
 * and there, which is what the second half of its name is about.
 */
public class EventPlayEerieSound extends SoapEvent {
    public static final String ID = "play_eerie_sound";
    private static final int RADIUS_MIN = 16;
    private static final int RADIUS_MAX = 64;

    /** Everything this event may pick from. All of it is uncomfortable to hear at night. */
    private static final SoundEvent[] EERIE_SOUNDS = {
            SoundEvents.AMBIENT_CAVE.value(),
            //SoundEvents.ENDERMAN_STARE,
            //SoundEvents.SCULK_SHRIEKER_SHRIEK,
            //SoundEvents.WARDEN_NEARBY_CLOSER
    };

    @Override
    public Myth[] relatedMyths() {
        return new Myth[] { Myth.HEROBRINE };
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public float nextDelayMultiplier() {
        return .0625f;
    }

    @Override
    public float selectionWeight() {
        return .25f;
    }

    @Override
    public Categories category() {
        return Categories.Small;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        return super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerLevel level = context.overworld();
        ServerPlayer target = context.randomPlayer();

        Supplier<Integer> generator = () -> context.random().nextIntBetweenInclusive(RADIUS_MIN,RADIUS_MAX) * (context.random().nextBoolean()?-1:1);

        double x;
        double y;
        double z;
        if (target != null) {
            x = target.getX() + generator.get();
            y = target.getY();
            z = target.getZ() + generator.get();
        } else {
            BlockPos spawn = level.getSharedSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY();
            z = spawn.getZ() + 0.5D;
        }

        SoundEvent sound = EERIE_SOUNDS[randomIndex(context.random(), EERIE_SOUNDS.length)];
        level.playSound(null, x, y, z, sound, SoundSource.AMBIENT,
                64.0F, 0.6F + context.random().nextFloat() * 0.3F);

        return true;
    }
}
