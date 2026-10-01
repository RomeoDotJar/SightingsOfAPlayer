package net.romeo.sightingsofaplayer.soap_event.impl.shared;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

import java.util.function.Supplier;

/**
 * Herobrine active event: replaces regular torches around the player with redstone torches.
 * Handles both floor torches and wall torches properly.
 */
public class EventPlaceRedstoneTorchFarAway extends SoapEvent {
    public static final String ID = "place_torches_far";
    private static final int RADIUS_MIN = 64;
    private static final int RADIUS_MAX = 128;
    private static final int ATTEMPTS = Integer.MAX_VALUE;
    private static final int VERTICAL_SEARCH = 16;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Myth[] relatedMyths() { return new Myth[] {Myth.HEROBRINE}; }

    @Override
    public Categories category() {
        return Categories.Small;
    }

    @Override
    public int tensionGain() {
        return 25;
    }

    @Override
    public float nextDelayMultiplier() {
        return super.nextDelayMultiplier()*.25f;
    }

    @Override
    public float selectionWeight() {
        return super.selectionWeight();
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isActive(Myth.HEROBRINE) && !context.players().isEmpty() && super.canRun(context);
    }

    public static boolean isSolidBlock(Level level, BlockPos pos, Direction dir) {
        BlockState state = level.getBlockState(pos);
        boolean isValidSupport = state.isFaceSturdy(level, pos, dir);

        return !state.isAir() && state.getFluidState().isEmpty() && isValidSupport;
    }

    public static boolean validSpot(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir())
            return false;

        if (
                isSolidBlock(level,pos.below(),Direction.UP)
                || isSolidBlock(level,pos.east(),Direction.WEST)
                || isSolidBlock(level,pos.south(),Direction.NORTH)
                || isSolidBlock(level,pos.west(),Direction.EAST)
                || isSolidBlock(level,pos.north(),Direction.SOUTH)
        ) {
            return true;
        }


        return false;
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerPlayer player = context.randomPlayer();
        if (player == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        Supplier<Integer> generator = () -> context.random().nextIntBetweenInclusive(RADIUS_MIN,RADIUS_MAX) * (context.random().nextBoolean()?-1:1);

        int max_attempts = Math.min(ATTEMPTS,RADIUS_MAX*RADIUS_MAX-RADIUS_MIN*RADIUS_MIN);
        int attempts = max_attempts;

        while (attempts>0) {
            attempts--;

            int x = generator.get();
            int z = generator.get();

            BlockPos pos = playerPos.offset(x, VERTICAL_SEARCH +1, z);
            int y_offsets = VERTICAL_SEARCH * 2+1;

            boolean found = false;
            while (!found && y_offsets > 0) {
                y_offsets--;
                pos = pos.offset(0, -1, 0);

                if (validSpot(level, pos)) {
                    found=true;
                }
            }

            if (!found)
                continue;

            if (level.getBlockState(pos.below()).isAir()) {
                Rotation rotation = Rotation.NONE;

                if (isSolidBlock(level,pos.south(),Direction.NORTH)) {
                    rotation = Rotation.CLOCKWISE_90;
                }
                else if (isSolidBlock(level,pos.west(),Direction.EAST)) {
                    rotation = Rotation.CLOCKWISE_180;
                }
                else if (isSolidBlock(level,pos.north(),Direction.SOUTH)) {
                    rotation = Rotation.COUNTERCLOCKWISE_90;
                }

                level.setBlockAndUpdate(pos, Blocks.REDSTONE_WALL_TORCH.defaultBlockState().rotate(level, pos, rotation));
            }
            else
                level.setBlockAndUpdate(pos, Blocks.REDSTONE_TORCH.defaultBlockState());

            SOAP.LOGGER.info("EVENT SUCCESS AFTER {} ATTEMPTS",max_attempts-attempts);
            return true;
        }
        SOAP.LOGGER.info("EVENT FAIL AFTER {} ATTEMPTS",max_attempts-attempts);
        return false;
    }
}
