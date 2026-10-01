package net.romeo.sightingsofaplayer.soap_event.impl.herobrine.curse;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Herobrine Curse event:
 * As a Curse (active when Herobrine is Passive), Herobrine will rarely place redstone torches near the player.
 */
public class EventHerobrineCursePlaceTorch extends SoapEvent {

    public static final String ID = "herobrine_curse_place_torch";
    private static final int SEARCH_RADIUS = 32;
    private static final int ATTEMPTS = 64;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Myth[] relatedMyths() { return new Myth[] {Myth.HEROBRINE}; }

    @Override
    public Categories category() {
        return Categories.Curse;
    }

    @Override
    public int tensionGain() {
        return 5;
    }

    @Override
    public float selectionWeight() {
        // Runs rarely
        return 0.5f;
    }

    @Override
    public float nextDelayMultiplier() {
        return .01f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isCurseActive(Myth.HEROBRINE) && !context.players().isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerPlayer target = context.randomPlayer();
        if (target == null) {
            return false;
        }

        ServerLevel level = target.serverLevel();
        BlockPos center = target.blockPosition();

        BlockPos spot = findGroundSpot(level, center, context.random());
        return spot != null && level.setBlockAndUpdate(spot, Blocks.REDSTONE_TORCH.defaultBlockState());
    }

    @Nullable
    private static BlockPos findGroundSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = center.getX() + random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int z = center.getZ() + random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            
            // Check near player's vertical range first if underground/inside
            for (int dy = 5; dy >= -5; dy--) {
                BlockPos candidate = new BlockPos(x, center.getY() + dy, z);
                BlockPos below = candidate.below();
                if (level.isEmptyBlock(candidate) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return candidate;
                }
            }

            // Fallback to heightmap
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            BlockPos below = candidate.below();
            if (level.isEmptyBlock(candidate) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return candidate;
            }
        }

        return null;
    }
}
