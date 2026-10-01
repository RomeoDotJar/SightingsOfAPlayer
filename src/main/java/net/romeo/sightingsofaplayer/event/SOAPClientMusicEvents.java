package net.romeo.sightingsofaplayer.event;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.init.SOAPSounds;
import net.romeo.sightingsofaplayer.network.SOAPMusicControlPayload;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import org.slf4j.Logger;

import static net.romeo.sightingsofaplayer.network.SOAPMusicControlPayload.Action.START;

/**
 * Client half of a soap_event's soundtrack: silences the vanilla ambient music, plays the event's
 * own track instead and carries out the playback commands the server sends (start, pause, resume,
 * stop, volume, speed) - the music counterpart of {@code SOAPClientVisualEvents}.
 * <p>
 * How each control reaches the audio channel, and why:
 * <ul>
 *     <li><b>Starting</b> - the track is a {@link TickableSoundInstance} handed to
 *     {@link SoundManager#play(SoundInstance)}; the ambient soundtrack that is currently playing is
 *     ended by cancelling {@link SelectMusicEvent}, which vanilla's own {@code MusicManager} reacts
 *     to by stopping the current track and never picking a new one. Once the track ends on its own
 *     the cancellation stops and ambient music resumes on the next selection.</li>
 *     <li><b>Volume / speed</b> - for tickable instances {@code SoundEngine} re-reads
 *     {@link SoundInstance#getVolume()} / {@link SoundInstance#getPitch()} every tick and pushes the
 *     results to the OpenAL channel. The track overrides both getters to return the fields below,
 *     so changing a field is all it takes for the new value to be applied within one tick - and
 *     because the values flow through the vanilla getters, the category-volume logic (music slider,
 *     zero-volume cutoff) keeps working unchanged.</li>
 *     <li><b>Pause / resume</b> - the vanilla API has no per-sound pause, only the all-channels
 *     {@code SoundEngine#pause()}/{@code resume()}. The running channel handle is therefore looked
 *     up (reflectively, once-cached: {@code SoundManager.soundEngine} -&gt;
 *     {@code SoundEngine.instanceToChannel}) and {@link Channel#pause()} /
 *     {@link Channel#unpause()} are executed on it, exactly what vanilla does internally. OpenAL
 *     keeps the playback position, so resuming continues where the track left off. The pause is
 *     re-asserted on every client tick because vanilla's global resume (e.g. after the pause menu)
 *     would otherwise unpause our channel behind our back.</li>
 *     <li><b>Stop</b> - plain {@link SoundManager#stop(SoundInstance)}, the vanilla path.</li>
 * </ul>
 * Everything runs on the client main thread: the payload handler enqueues onto it and the tick
 * hook fires on it, so the bookkeeping fields are never touched concurrently.
 */
@EventBusSubscriber(modid = SOAP.MODID, value = Dist.CLIENT)
public final class SOAPClientMusicEvents {

    /** OpenAL only accepts pitches in this range, the same limits vanilla clamps to. */
    private static final float MIN_SPEED = 0.5F;
    private static final float MAX_SPEED = 2.0F;

    /** The running soundtrack, or {@code null} when the event's music is not playing. */
    private static @Nullable Track track;
    /** Whether the track is held paused by the server. */
    private static boolean paused;

    /** Requested loudness, 0.0 .. 1.0; the sound engine multiplies in the music slider itself. */
    private static float volume = 1.0F;
    /** Requested playback speed, 0.5 .. 2.0, 1.0 = original. */
    private static float speed = 1.0F;

    /** dynamicVolume == volume unless currently fading. */
    private static float dynamicVolume = volume;
    /** How quickly the fading is happening. */
    private static float fadeSpeed = 0;
    /** True if FADE_OUT was initiated. */
    private static boolean fadingOut = false;

    private static @Nullable Field soundEngineField;
    private static @Nullable Field instanceToChannelField;
    private static boolean channelLookupWarned;

    private SOAPClientMusicEvents() {
    }

    /**
     * Executes one playback command received from the server. Called on the client main thread
     * through {@code IPayloadContext#enqueueWork}; unknown or premature commands (resuming while
     * nothing plays, volume changes without a start) are harmless no-ops.
     */
    public static void apply(SOAPMusicControlPayload payload) {
        //SOAP.LOGGER.info("{} {} {}", payload.action().toString(), payload.id(), track!=null?track.getLocation().getPath():"");
        if (payload.action()!=START && track!=null && !Objects.equals(payload.id(), track.getLocation().getPath()))
            return;

        switch (payload.action()) {
            case START -> start(payload.volume(), payload.speed(), payload.id());
            case PAUSE -> pause();
            case RESUME -> resume();
            case STOP -> stop();
            case SET_VOLUME -> setVolume(payload.volume());
            case SET_SPEED -> setSpeed(payload.speed());
            case FADE_VOLUME -> doFadeVolume(payload.volume(),payload.speed());
            case FADE_OUT -> doFadeOut(payload.volume(),payload.speed());
        }
    }

    // -------------------------------------------------------------------------------------------------
    // Playback controls
    // -------------------------------------------------------------------------------------------------

    /** Stops any previous event track, ends ambient music and begins the new track. */
    private static void start(float newVolume, float newSpeed, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        stop();
        volume = dynamicVolume = clampVolume(newVolume);
        speed = clampSpeed(newSpeed);
        paused = false;
        fadingOut = false;
        fadeSpeed = 0;
        track = new Track(SOAPSounds.getSoundEvent(id));
        // Ambient music is stopped by the SelectMusicEvent cancellation in onSelectMusic: the next
        // MusicManager tick sees "no music selected", stops whatever is playing and stays quiet
        // for as long as this track exists.
        minecraft.getSoundManager().play(track);
    }

    /** Freezes the track at its current playback position. No-op without a playing track. */
    private static void pause() {
        if (track == null || paused) {
            return;
        }
        paused = true;
        applyToChannel(Channel::pause);
    }

    /** Continues a track frozen by {@link #pause()}. No-op while nothing is paused. */
    private static void resume() {
        if (track == null || !paused) {
            return;
        }
        paused = false;
        applyToChannel(Channel::unpause);
    }

    /** Ends playback immediately; vanilla ambient music takes over again afterwards. */
    private static void stop() {
        Track current = track;
        if (current == null) {
            return;
        }
        track = null;
        paused = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().stop(current);
        }
    }

    /** Changes loudness for the rest of the track, 0.0 (silent) .. 1.0 (full). */
    private static void setVolume(float newVolume) {
        dynamicVolume = clampVolume(newVolume);
        if (fadeSpeed == 0) {
            volume = dynamicVolume;
        }
    }

    /** Fade loudness gradually for the rest of the track, 0.0 (silent) .. 1.0 (full). */
    private static void doFadeVolume(float newVolume, float fadeDuration) {
        volume = clampVolume(newVolume);
        fadeSpeed = (newVolume-dynamicVolume)/fadeDuration/SOAPServerTimeData.TICKS_PER_SECOND;
        //SOAP.LOGGER.info("fadeSpeed = {}",fadeSpeed);
    }

    /** Fade loudness gradually for the rest of the track, 0.0 (silent) .. 1.0 (full). */
    private static void doFadeOut(float newVolume, float fadeDuration) {
        doFadeVolume(newVolume,fadeDuration);
        fadingOut = true;
    }

    /** Changes playback speed for the rest of the track, 0.5 (half) .. 2.0 (double). */
    private static void setSpeed(float newSpeed) {
        speed = clampSpeed(newSpeed);
    }

    // -------------------------------------------------------------------------------------------------
    // Event hooks
    // -------------------------------------------------------------------------------------------------

    /**
     * While the event's track runs, vanilla must not pick - and therefore not start - any ambient
     * music. Cancelling with no replacement makes the music manager stop the current soundtrack on
     * its very next tick and stay silent, which is exactly the hand-over the event wants.
     */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (track != null) {
            event.overrideMusic(null);
        }
    }

    /**
     * Re-asserts the pause every tick (vanilla's global resume can unpause all channels at once)
     * and notices when the track is over - it ran out, or the engine dropped it, e.g. because the
     * music slider was turned down. Ending the state here is what lets ambient music come back
     * without any further packets.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Track current = track;
        if (current == null) {
            return;
        }
        //SOAP.LOGGER .info("PLAYING volume={} dynamicVolume={} fadeSpeed={} fadingOut={}",
        //        volume,dynamicVolume,fadeSpeed,fadingOut);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (paused) {
            applyToChannel(Channel::pause);
        }
        if (!minecraft.getSoundManager().isActive(current)) {
            finish();
            //SOAP.LOGGER.info("FINISHED");
            return;
        }
        // Gradual fading
        if (volume != dynamicVolume && fadeSpeed != 0) {
            //SOAP.LOGGER.info("FADING FROM {} TO {}", dynamicVolume, volume);

            if (Math.abs(volume-dynamicVolume) <= Math.abs(fadeSpeed)) {
                fadeSpeed = 0;

                if (fadingOut)
                    stop();
                else
                    setVolume(volume);
            }
            else {
                dynamicVolume += fadeSpeed;
                setVolume(dynamicVolume);
            }
        }
        try {
            ChannelHandle handle = channelHandleOf(current);
            // No handle means the engine already dropped the instance (zero music volume, reload),
            // a stopped handle means the channel finished; either way there is nothing to control.
            if (handle == null || handle.isStopped()) {
                finish();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnChannelLookupOnce(e);
        }
    }

    /** Leaving the world clears the track so a stale state can never survive into the next session. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    // -------------------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------------------

    /** Clears the track state without touching the sound engine (the channel is already gone). */
    private static void finish() {
        track = null;
        paused = false;
    }

    private static float clampVolume(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static float clampSpeed(float value) {
        return Mth.clamp(value, MIN_SPEED, MAX_SPEED);
    }

    /** Runs an action on the OpenAL channel behind the current track, if it has one yet. */
    private static void applyToChannel(Consumer<Channel> action) {
        Track current = track;
        if (current == null) {
            return;
        }
        try {
            ChannelHandle handle = channelHandleOf(current);
            if (handle != null) {
                handle.execute(action);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnChannelLookupOnce(e);
        }
    }

    /**
     * The channel handle vanilla is currently playing this instance on. Both fields are private
     * vanilla internals, read reflectively once and cached - they are the same two fields
     * {@code SoundEngine#stop(SoundInstance)} and {@code SoundEngine#tickNonPaused()} use, and
     * reading them from the main thread is safe because that is the only thread that mutates the
     * map.
     */
    private static ChannelHandle channelHandleOf(Track current) throws ReflectiveOperationException {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundEngineField == null) {
            soundEngineField = SoundManager.class.getDeclaredField("soundEngine");
            soundEngineField.setAccessible(true);
        }
        SoundEngine soundEngine = (SoundEngine) soundEngineField.get(soundManager);
        if (instanceToChannelField == null) {
            instanceToChannelField = SoundEngine.class.getDeclaredField("instanceToChannel");
            instanceToChannelField.setAccessible(true);
        }
        Map<?, ?> channels = (Map<?, ?>) instanceToChannelField.get(soundEngine);
        return (ChannelHandle) channels.get(current);
    }

    /** Logs a failed channel lookup once; afterwards pause/resume degrade silently but safely. */
    private static void warnChannelLookupOnce(Exception e) {
        if (!channelLookupWarned) {
            channelLookupWarned = true;
            SOAP.LOGGER.warn("Cannot reach the audio channel of the soap_event soundtrack; "
                    + "pause/resume of the music will not work", e);
        }
    }

    /**
     * The event's soundtrack: {@code SoundEvents.MUSIC_DISC_PIGSTEP} played as a non-looping,
     * position-less tickable sound in the music category, so it obeys the music slider like any
     * other music. The tickable contract is what buys live volume/speed control (the engine
     * re-reads the getters every tick), while stopping is left entirely to the controller - a
     * paused track must not look finished to the engine.
     */
    private static final class Track extends AbstractSoundInstance implements TickableSoundInstance {

        private Track(SoundEvent sound) {
            super(sound, SoundSource.MUSIC,
                    SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        /** Live loudness; qualified because the superclass declares its own {@code volume} field. */
        @Override
        public float getVolume() {
            return SOAPClientMusicEvents.dynamicVolume;
        }

        /** Live playback speed, read by the engine every tick and applied as the channel pitch. */
        @Override
        public float getPitch() {
            return SOAPClientMusicEvents.speed;
        }

        @Override
        public void tick() {
            // Intentionally empty: SoundEngine already re-reads getVolume()/getPitch() for every
            // tickable instance and applies them to the channel, which is how a setVolume/setSpeed
            // call reaches a track that is already playing.
        }

        @Override
        public boolean isStopped() {
            // Only the controller stops the track (stop(), the event's queued end, logout), so a
            // paused track is never treated as finished either.
            return false;
        }
    }
}
