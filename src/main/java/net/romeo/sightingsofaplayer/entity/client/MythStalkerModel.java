package net.romeo.sightingsofaplayer.entity.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.entity.custom.MythStalkerEntity;

/**
 * The Stalker's body: an ordinary, unmodified player model.
 * <p>
 * Building it from {@link PlayerModel#createMesh(CubeDeformation, boolean)} rather than hand-rolling
 * a humanoid mesh is what guarantees the silhouette really is a player's - the exact same head,
 * torso, arms and legs the game uses for everyone else, including the outer skin layers
 * (jacket, sleeves, pants) that a hand-made {@code HumanoidModel} would be missing.
 * <p>
 * The {@code slim} flag is false, i.e. the classic four-pixel-wide arms: the censor bars the client
 * draws are derived from the creature's bounding box, and matching the vanilla player proportions
 * keeps that box hugging the model it is meant to hide.
 * <p>
 * No animation code lives here at all - {@code PlayerModel}'s own {@code setupAnim} is enough, and
 * the Stalker's slow, mostly-still behaviour means there is almost nothing to animate.
 */
public class MythStalkerModel extends PlayerModel<MythStalkerEntity> {

    /** Key this model is baked under, registered in {@code SOAPClientStalkerEvents}. */
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(SOAP.MODID, "stalker"), "main");

    /** The vanilla player skin layout; the all-black texture ships at exactly this size. */
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    public MythStalkerModel(ModelPart root) {
        super(root, false);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false),
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
    }
}

