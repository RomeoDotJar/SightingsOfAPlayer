package net.romeo.sightingsofaplayer.event;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.entity.client.MythStalkerModel;
import net.romeo.sightingsofaplayer.entity.client.MythStalkerRenderer;
import net.romeo.sightingsofaplayer.entity.custom.MythStalkerEntity;
import net.romeo.sightingsofaplayer.init.SOAPEntities;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Client half of the Stalker: the renderer wiring, and the censor bars that cover it.
 * <p>
 * The censor is pure HUD. It never touches the model, the world or the network: the server already
 * tells every client where the Stalker is by spawning it normally, so this class only has to work out
 * <i>where on the screen</i> the creature is and paint over it. That keeps the effect honest - it
 * hides exactly the model the renderer just drew, because both are derived from the same bounding box.
 * <p>
 * How it works, step by step:
 * <ul>
 *     <li><b>Capture</b> - {@link #onRenderLevelStage} grabs the camera position and the exact
 *     model-view and projection matrices the level was rendered with, at the
 *     {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES AFTER_ENTITIES} stage. Using the renderer's
 *     own matrices (rather than re-deriving them) is what makes the projection line up with the
 *     world pixel for pixel, view-bob and all.</li>
 *     <li><b>Project</b> - {@link #projectToScreen} pushes the eight corners of the Stalker's bounding
 *     box through those matrices and takes the smallest rectangle containing all of them. That
 *     rectangle <i>is</i> the model on screen.</li>
 *     <li><b>Draw</b> - {@link #drawCensorBars} fills it with stacked, seamless black bars straight
 *     into the HUD pass, at the very start of {@link RenderGuiEvent.Pre}, so the world (and the
 *     Stalker in it) is covered before any GUI element is drawn on top.</li>
 * </ul>
 * It only ever draws for creatures the local player is actually looking at, using the very same
 * {@link MythStalkerEntity#isLookedAtBy(Player)} test the server uses to make them vanish - the bars
 * appear in the same instant the countdown to disappearing starts.
 */
@EventBusSubscriber(modid = SOAP.MODID, value = Dist.CLIENT)
public final class SOAPClientMythCensorEvents {

    /** Fully opaque black: the bars are meant to hide everything behind them. */
    private static final int CENSOR_COLOR = 0xFF000000;

    /** Roughly how tall, in scaled GUI pixels, one censor bar should be. */
    private static final float CENSOR_BAR_HEIGHT = 4.0F;
    private static final int CENSOR_MIN_BARS = 1;
    private static final int CENSOR_MAX_BARS = 64;

    /** Anything at or behind this clip-space depth is behind the eye and cannot be projected. */
    private static final float BEHIND_CAMERA_EPSILON = 1.0E-4F;

    // Reused every frame. The render thread is the only thread that ever touches these.
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();

    private static Vec3 cameraPosition = Vec3.ZERO;

    @Nullable
    private static Frustum frustum;

    private static boolean frameReady;

    private SOAPClientMythCensorEvents() {
    }

    // -------------------------------------------------------------------------------------------------
    // Registration - both events are mod-bus events and are routed there automatically
    // -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MythStalkerModel.LAYER_LOCATION, MythStalkerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SOAPEntities.STALKER.get(), MythStalkerRenderer::new);
    }

    // -------------------------------------------------------------------------------------------------
    // Per-frame capture of the matrices the level was actually drawn with
    // -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        MODEL_VIEW.set(event.getModelViewMatrix());
        PROJECTION.set(event.getProjectionMatrix());
        cameraPosition = event.getCamera().getPosition();
        frustum = event.getFrustum();
        frameReady = true;
    }

    /** Leaving the world drops the captured frame so a stale matrix can never leak into a new one. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        frameReady = false;
        frustum = null;
        cameraPosition = Vec3.ZERO;
    }

    // -------------------------------------------------------------------------------------------------
    // The censor bars
    // -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        drawCensors(event.getGuiGraphics());
    }

    private static void drawCensors(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player viewer = minecraft.player;

        if (!frameReady || level == null || viewer == null) {
            return;
        }

        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();

        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.getTags().contains("soap_censored")) {
                continue;
            }

            if (frustum == null || !frustum.isVisible(entity.getBoundingBox())) {
                continue;
            }

            if (viewer.hasLineOfSight(entity) && entity.isInvisible()) {
                entity.setInvisible(false);
            }

            //if (entity.isInvisible())
            //    continue;

            if (!entity.isInvisible()) {//!viewer.hasLineOfSight(entity) && !entity.isInvisible()) {
                entity.setInvisible(true);
            }

            int[] box = projectToScreen(entity.getBoundingBox().inflate(.325,.375,.325), guiWidth, guiHeight);
            if (box == null) {
                continue;
            }

            drawCensorBars(guiGraphics, box[0], box[1], box[2], box[3]);
        }
    }

    /**
     * Projects the eight corners of a world-space box onto the GUI and returns the smallest rectangle
     * containing all of them, {@code {x0, y0, x1, y1}} in scaled GUI pixels.
     * <p>
     * The rectangle is the creature's extent on screen, so bars drawn into it cover the Stalker and
     * nothing else. {@code null} means "do not draw": either a corner sits at or behind the camera,
     * where the projection would be meaningless, or the whole box lies off the screen.
     */
    @Nullable
    private static int[] projectToScreen(AABB box, int guiWidth, int guiHeight) {
        float minNdcX = Float.MAX_VALUE;
        float minNdcY = Float.MAX_VALUE;
        float maxNdcX = -Float.MAX_VALUE;
        float maxNdcY = -Float.MAX_VALUE;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;

            // World space, relative to the eye, through the view matrix and then the projection.
            SCRATCH.set(
                    (float) (x - cameraPosition.x),
                    (float) (y - cameraPosition.y),
                    (float) (z - cameraPosition.z),
                    1.0F);
            SCRATCH.mul(MODEL_VIEW);
            SCRATCH.mul(PROJECTION);

            if (SCRATCH.w <= BEHIND_CAMERA_EPSILON) {
                return null;
            }

            float ndcX = SCRATCH.x / SCRATCH.w;
            float ndcY = SCRATCH.y / SCRATCH.w;

            minNdcX = Math.min(minNdcX, ndcX);
            maxNdcX = Math.max(maxNdcX, ndcX);
            minNdcY = Math.min(minNdcY, ndcY);
            maxNdcY = Math.max(maxNdcY, ndcY);
        }

        int x0 = Mth.clamp(Math.round((minNdcX * 0.5F + 0.5F) * guiWidth), 0, guiWidth);
        int x1 = Mth.clamp(Math.round((maxNdcX * 0.5F + 0.5F) * guiWidth), 0, guiWidth);
        // NDC grows upwards while the GUI grows downwards, hence the flip before scaling.
        int y0 = Mth.clamp(Math.round((1.0F - (maxNdcY * 0.5F + 0.5F)) * guiHeight), 0, guiHeight);
        int y1 = Mth.clamp(Math.round((1.0F - (minNdcY * 0.5F + 0.5F)) * guiHeight), 0, guiHeight);

        if (x1 <= x0 || y1 <= y0) {
            return null;
        }

        return new int[] { x0, y0, x1, y1 };
    }

    /**
     * Fills the given rectangle with stacked, seamless black censor bars.
     * <p>
     * The bars share their edges, so their union is exactly the rectangle: the model is fully hidden
     * and not a pixel outside it is touched. Splitting the fill into bars rather than one block keeps
     * the effect reading as a censor overlay, with a bar height that stays legible whether the
     * Stalker is a speck on the horizon or looming right in front of the camera.
     */
    private static void drawCensorBars(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1) {
        int height = y1 - y0;
        int bars = Mth.clamp(Math.round(height / CENSOR_BAR_HEIGHT), CENSOR_MIN_BARS, CENSOR_MAX_BARS);

        for (int bar = 0; bar < bars; bar++) {
            int barTop = y0 + (int) Math.round((double) height * bar / bars);
            int barBottom = y0 + (int) Math.round((double) height * (bar + 1) / bars);
            guiGraphics.fill(x0, barTop, x1, barBottom, CENSOR_COLOR);
        }
    }

}
