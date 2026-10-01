package net.romeo.sightingsofaplayer.event;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.romeo.sightingsofaplayer.SOAP;
import org.joml.Random;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of the visual-effects soap_event: the screen shake, the heartbeat FOV and the
 * letterbox bars.
 * <p>
 * The whole suite hangs off one flag, {@link #setEffectsActive(boolean)}, which the server flips
 * on when the event runs and off when the queued stop arrives. While the flag is down nothing here
 * costs more than a boolean check per frame, so the effects vanish cleanly the moment they are
 * cancelled.
 * <p>
 * How each effect is achieved, and why:
 * <ul>
 *     <li><b>Shake</b> - every frame a fresh random offset is chosen. The HUD offset is applied to
 *     the pose before any layer renders and undone afterwards, which catches the crosshair, hotbar
 *     icons, health/armor/hunger, chat, titles and the F3 overlay in one move. Screens (pause menu
 *     and friends) get the same treatment through their own Pre/Post pair, with a push/pop so the
 *     offset can never leak past the screen. The mouse cursor is not a drawn element - Minecraft
 *     uses the system cursor - so it is shaken by nudging its real position with GLFW, subtracting
 *     the previously applied nudge first so the jitter never accumulates into drift.</li>
 *     <li><b>Heartbeat FOV</b> - a smooth lub-dub-dip curve, keyed to wall-clock time so it keeps
 *     beating even while the game is paused, multiplied onto whatever FOV modifier the game already
 *     computed. No movement speed, potion or attribute is touched: the widening is purely what the
 *     player sees.</li>
 *     <li><b>Letterbox bars</b> - two flat black fills covering {@value #LETTERBOX_FRACTION} of
 *     the screen height each. They are drawn before the shake offset is applied, at the very start
 *     of the HUD pass, which puts them behind the HUD widgets but above the world - so the hotbar
 *     keeps visibly rattling on top of the lower bar instead of being swallowed by it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = SOAP.MODID, value = Dist.CLIENT)
public final class SOAPClientVisualEvents {

    private static final Random random = new Random();

    /** How far, in scaled GUI units, HUD and screen elements may jump on any given frame. */
    private static final float SHAKE_AMPLITUDE = 1.5F;

    /** Share of the screen height each of the two letterbox bars covers. */
    private static final float LETTERBOX_FRACTION = 0.14F;
    private static final int LETTERBOX_FADE_SPEED = 200;

    private static final int LETTERBOX_COLOR = 0xFF000000;

    /** One heartbeat takes a second: lub, dub, then a short dip below the baseline. */
    private static final long HEARTBEAT_PERIOD_MS = 600L;

    /** Widest the FOV may swell at the peak of a beat, as a fraction of the vanilla modifier. */
    private static final float PEAK_FOV_INCREASE = 0.05F;

    /** Deepest the FOV may sink between beats, as a fraction of the vanilla modifier. */
    private static final float TROUGH_FOV_DECREASE = 0.05F;

    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean effectsActive;

    private static boolean hudShakeApplied;
    private static float hudShakeX;
    private static float hudShakeY;

    private static boolean screenShakeApplied;

    private static float dynamicBarHeight = 0;
    private static int fps;

    private static boolean blinked = true;

    /**
     * Switches the whole suite on or off. Called on the client main thread from the payload
     * handler registered in {@code SOAPPayloads}, so it is safe to touch render state here.
     */
    public static void setEffectsActive(boolean active) {
        if (effectsActive == active) {
            return;
        }
        effectsActive = active;

        // TODO: NOT YET SURE IF THIS SHOULD STAY OR GO
        //if (!active)
        //    blinked = false;
    }

    // -------------------------------------------------------------------------------------------------
    // HUD: crosshair, hotbar, health/armor/hunger, chat, titles, F3 - everything in Gui
    // -------------------------------------------------------------------------------------------------

    /**
     * Letterbox first, then the shake offset: the bars must stay put while the HUD slides over
     * them, and drawing them here - ahead of every layer - is what keeps the hotbar visible on top
     * of the lower bar.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        fps = Minecraft.getInstance().getFps();

        if (fps<=0) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        drawLetterbox(guiGraphics);

        if (!effectsActive) {
            return;
        }

        hudShakeX = nextShake();
        hudShakeY = nextShake();
        hudShakeApplied = true;
        guiGraphics.pose().translate(hudShakeX, hudShakeY, 0.0F);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        // Restored unconditionally: if the effect was switched off between Pre and Post for any
        // reason, the pose still has to come back to where it started.
        if (hudShakeApplied) {
            event.getGuiGraphics().pose().translate(-hudShakeX, -hudShakeY, 0.0F);
            hudShakeApplied = false;
        }
    }

    // -------------------------------------------------------------------------------------------------
    // Screens: pause menu buttons, chat input, inventories - everything drawn through Screen#render
    // -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!effectsActive || Minecraft.getInstance().level == null) {
            return;
        }

        screenShakeApplied = true;
        float shakeX = nextShake();
        float shakeY = nextShake();
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(shakeX, shakeY, 0.0F);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (screenShakeApplied) {
            event.getGuiGraphics().pose().popPose();
            screenShakeApplied = false;
        }
    }

    // -------------------------------------------------------------------------------------------------
    // Heartbeat FOV - purely what the eye sees, never a movement speed change
    // -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (!effectsActive) {
            return;
        }

        event.setNewFovModifier(event.getNewFovModifier() * heartbeatFovFactor());
    }

    // -------------------------------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------------------------------

    /**
     * Leaving the world clears the flag so a stale effect can never survive into the next session,
     * and puts the real cursor back where the player left it.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        setEffectsActive(false);
    }

    /** One frame's shake offset, uniformly random in {@code [-SHAKE_AMPLITUDE, SHAKE_AMPLITUDE]}. */
    private static float nextShake() {
        return (RANDOM.nextFloat() * 2.0F - 1.0F) * SHAKE_AMPLITUDE;
    }

    private static void drawLetterbox(GuiGraphics guiGraphics) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        int fadeSpeed = !effectsActive ? LETTERBOX_FADE_SPEED*2 : LETTERBOX_FADE_SPEED;

        int barHeight = effectsActive ? Math.round(height * LETTERBOX_FRACTION) : (blinked ? 0 : height/2);

        float diff = barHeight-dynamicBarHeight;

        if (Math.abs(diff) < fadeSpeed/fps) {
            dynamicBarHeight = barHeight;

            if (!blinked) {
                blinked = true;
            }
        }
        else if (diff > 0) {
            dynamicBarHeight += fadeSpeed/fps;
        }
        else {
            dynamicBarHeight -= fadeSpeed/fps;
        }

        guiGraphics.fill(0, 0, width, (int)dynamicBarHeight, LETTERBOX_COLOR);
        guiGraphics.fill(0, height - (int)dynamicBarHeight, width, height, LETTERBOX_COLOR);
    }

    /**
     * The heartbeat as a multiplier around 1.0: two swells ("lub", softer "dub") that widen the
     * FOV, followed by a shallow dip below the baseline before it settles. Keyed to wall-clock time
     * so the pulse keeps its rhythm even while the game is paused.
     */
    private static float heartbeatFovFactor() {
        if (random.nextInt(8)==1)
            return 1;

        double phase = (Util.getMillis() % HEARTBEAT_PERIOD_MS) / (double) HEARTBEAT_PERIOD_MS;

        float swell = (float) (bump(phase, 0.00D, 0.20D) + 0.6D * bump(phase, 0.28D, 0.44D));
        float dip = (float) bump(phase, 0.52D, 0.78D);

        return 1.0F + PEAK_FOV_INCREASE * swell - TROUGH_FOV_DECREASE * dip;
    }

    /** A smooth, always non-negative hump rising from 0 at {@code start} back to 0 at {@code end}. */
    private static double bump(double phase, double start, double end) {
        if (phase < start || phase > end) {
            return 0.0D;
        }

        double x = (phase - start) / (end - start);
        double sine = Math.sin(Math.PI * x);
        return sine * sine;
    }
}
