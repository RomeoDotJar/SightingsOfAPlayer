package net.romeo.sightingsofaplayer.entity.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.entity.custom.MythStalkerEntity;

/**
 * Draws the Stalker as a solid black figure: the shared {@link MythStalkerModel} player mesh painted
 * with one single-colour texture.
 * <p>
 * The shadow is deliberately switched off (radius {@code 0}), because a shadow is a body-shaped hole
 * in the ground that would give the creature away long before anyone turns to look at it.
 * <p>
 * This renderer only ever draws the model. The censor bars that hide it on screen are a separate,
 * purely client-side HUD effect living in {@code SOAPClientStalkerEvents}.
 * <p>
 * It deliberately extends {@link MobRenderer} rather than {@code LivingEntityRenderer}. Every vanilla
 * mob renderer does, and that is what keeps vanilla's name-tag rules: a bare {@code LivingEntityRenderer}
 * draws a label for any living entity it renders, whereas {@link MobRenderer} additionally demands a
 * visible custom name. Without that base class the Stalker would announce itself as "Stalker" the
 * moment it came within 64 blocks - which would give the whole game away.
 */
public class MythStalkerRenderer extends MobRenderer<MythStalkerEntity, MythStalkerModel> {

    /** A single, completely black texture - the only colour the silhouette ever has. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SOAP.MODID, "textures/entity/stalker.png");

    public MythStalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new MythStalkerModel(context.bakeLayer(MythStalkerModel.LAYER_LOCATION)), 0.0F);
    }

    /**
     * Never show a name tag. {@link MobRenderer} would already keep it hidden unless the creature
     * carried a visible custom name, but a Stalker with a floating label is no Stalker at all - so
     * the door is closed completely, no matter who later names it or puts their crosshair on it.
     */
    @Override
    protected boolean shouldShowName(MythStalkerEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(MythStalkerEntity entity) {
        return TEXTURE;
    }
}

