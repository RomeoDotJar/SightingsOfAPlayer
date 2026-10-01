package net.romeo.sightingsofaplayer.soap_event.impl;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.romeo.sightingsofaplayer.event.SOAPServerTimeEvents;
import net.romeo.sightingsofaplayer.init.SOAPSounds;
import net.romeo.sightingsofaplayer.network.SOAPMusicControlPayload;
import net.romeo.sightingsofaplayer.network.SOAPMusicControlPayload.Action;
import net.romeo.sightingsofaplayer.network.SOAPVisualEffectsPayload;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Every GUI element starts visibly shaking, the field of view beats like a heartbeat and two black
 * letterbox bars close in on the top and bottom of the screen - then it all stops on its own.
 * <p>
 * The event itself touches nothing but the network: it flips a switch on for everyone online and
 * schedules the switch-off through {@link SOAPServerTimeEvents}, the same clock the event system
 * runs on, so the effects last exactly {@value #EFFECT_DURATION_TICKS} ticks. Everything drawn on
 * screen - the shake, the heartbeat pulse, the bars - is computed by the client, which is also why
 * no movement speed, potion or attribute effect is ever applied: the heartbeat is only what the
 * player sees.
 * <p>
 * While the distortion runs, every affected client also drops its ambient soundtrack and starts the
 * event's own music ({@code SoundEvents.MUSIC_DISC_PIGSTEP}) instead. This class steers that
 * soundtrack the same way it steers the visuals - with playback commands on the wire:
 * {@value #EFFECT_DURATION_TICKS} ends the music together with the visuals; the track is longer
 * than the effects, so until then it plays on unless stopped or paused earlier.
 */
public class EventDebugScreenDistortion extends SoapEvent {

    public static final String ID = "debug_screen_distortion";

    /** How long the effects last, in server ticks, before the queued stop switches them off. */
    public static final int EFFECT_DURATION_TICKS = 250;

    /** Loudness the soundtrack starts at, {@code 0.0} (silent) .. {@code 1.0} (full). */
    public static final float MUSIC_VOLUME = 1.0F;

    /** Playback speed the soundtrack starts at, {@code 0.5} (half) .. {@code 2.0} (double). */
    public static final float MUSIC_SPEED = 1.0F;

    /**
     * The players the running soundtrack belongs to, snapshotted exactly like the visual audience:
     * pause/resume/stop and the volume/speed setters only ever talk to these clients.
     */
    private List<ServerPlayer> musicAudience = List.of();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.ForceOnly;
    }

    @Override
    public int tensionGain() {
        return 0;
    }

    /** Only ever with somebody there to see it. */
    @Override
    public boolean canRun(SoapEventContext context) {
        return !context.players().isEmpty();
    }

    @Override
    public boolean run(SoapEventContext context) {
        // Snapshot the audience: the stop has to reach exactly the players who were here when the
        // event started, not whoever happens to be online when the twenty seconds are up.
        List<ServerPlayer> targets = List.copyOf(context.players());
        if (targets.isEmpty()) {
            return false;
        }

        // Both halves of the event start together: the visuals switch on and the soundtrack begins
        // at its default loudness and speed on the very same clients.
        musicAudience = targets;
        float musicVolume = MUSIC_VOLUME;
        float musicSpeed = MUSIC_SPEED;
        SOAPMusicControlPayload musicOn = new SOAPMusicControlPayload(Action.START, musicVolume, musicSpeed, SOAPSounds.CHASE_TEST);

        for (ServerPlayer player : targets) {
            PacketDistributor.sendToPlayer(player, new SOAPVisualEffectsPayload(true));
            PacketDistributor.sendToPlayer(player, musicOn);
        }

        SOAPServerTimeEvents.queueWork(EFFECT_DURATION_TICKS, () -> {

            for (ServerPlayer player : targets) {
                if (!player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, new SOAPVisualEffectsPayload(false));
                    PacketDistributor.sendToPlayer(player, new SOAPMusicControlPayload(Action.SET_SPEED, 0.0F, .8f, SOAPSounds.CHASE_TEST));
                    PacketDistributor.sendToPlayer(player, new SOAPMusicControlPayload(Action.FADE_OUT, 0.0F, 2, SOAPSounds.CHASE_TEST));
                }
            }
        });

        return true;
    }

    // -------------------------------------------------------------------------------------------------
    // Soundtrack playback controls
    // -------------------------------------------------------------------------------------------------

    /** Sends one playback command to the audience of the running soundtrack. */
    private void sendMusic(Action action, float volume, float speed, String id) {
        SOAPMusicControlPayload payload = new SOAPMusicControlPayload(action, volume, speed, id);
        for (ServerPlayer player : musicAudience) {
            if (!player.hasDisconnected()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
