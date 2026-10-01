package net.romeo.sightingsofaplayer.soap_event.impl.shared;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.romeo.sightingsofaplayer.event.SOAPServerTimeEvents;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Herobrine active event: replaces regular torches around the player with redstone torches.
 * Handles both floor torches and wall torches properly.
 */
public class EventReplaceTorches extends SoapEvent {

    public static final String ID = "replace_torches";
    private static final int RADIUS = 16;
    private static final int RADIUS_BIGGER = 32;

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
        return 50;
    }

    @Override
    public float selectionWeight() {
        return super.selectionWeight()*.5f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isActive(Myth.HEROBRINE) && !context.players().isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerPlayer player = context.randomPlayer();
        if (player == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        List<BlockPos> torchPositions = new ArrayList<>();
        int r = RADIUS;
        BlockPos.betweenClosedStream(playerPos.offset(-r, -r, -r), playerPos.offset(r, r, r))
                .forEach(pos -> {
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                        torchPositions.add(pos.immutable());
                    }
                });

        if (torchPositions.isEmpty()) {
            r = RADIUS_BIGGER;
            BlockPos.betweenClosedStream(playerPos.offset(-r, -r, -r), playerPos.offset(r, r, r))
                    .forEach(pos -> {
                        BlockState state = level.getBlockState(pos);
                        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                            torchPositions.add(pos.immutable());
                        }
                    });

            if (torchPositions.isEmpty())
                return false;
        }

        List<AbstractMap.SimpleEntry<BlockState,BlockPos>> torches = new ArrayList<>();

        for (BlockPos pos : torchPositions) {
            BlockState state = level.getBlockState(pos);
            torches.add(new AbstractMap.SimpleEntry<>(state,pos));
        }

        int replacedTorches = 1 + torches.size()/4;

        for (int i=0; i<replacedTorches; i++) {
            SOAPServerTimeEvents.queueWork(i*8, () -> {
                int index = randomIndex(context.random(),torches.size());
                AbstractMap.SimpleEntry<BlockState,BlockPos> torch = torches.get(index);

                BlockState state = torch.getKey();
                BlockPos pos = torch.getValue();

                if (state.is(Blocks.TORCH)) {
                    level.setBlockAndUpdate(pos, Blocks.REDSTONE_TORCH.defaultBlockState());
                } else if (state.is(Blocks.WALL_TORCH)) {
                    Direction facing = state.getValue(WallTorchBlock.FACING);
                    BlockState redstoneWallTorch = Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                            .setValue(RedstoneWallTorchBlock.FACING, facing);
                    level.setBlockAndUpdate(pos, redstoneWallTorch);
                }
                torches.remove(index);

                level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.AMBIENT,
                        1.0F, 0.8F + context.random().nextFloat() * 0.4F);
            });
        }

        return true;
    }
}
