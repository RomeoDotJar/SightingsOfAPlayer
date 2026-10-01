package net.romeo.sightingsofaplayer.soap_event.impl.nullmyth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.romeo.sightingsofaplayer.init.SOAPBlocks;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Null active event: spawns NullBlocks near the player in place of air blocks.
 */
public class EventNullSpawnBlocks extends SoapEvent {

    public static final String ID = "null_spawn_blocks";
    private static final int RADIUS = 6;
    private static final int BLOCKS_TO_SPAWN = 3;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Small;
    }

    @Override
    public int tensionGain() {
        return 25;
    }

    @Override
    public float selectionWeight() {
        return 2.0f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isActive(Myth.NULL) && !context.players().isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerPlayer player = context.randomPlayer();
        if (player == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        List<BlockPos> airCandidates = new ArrayList<>();
        BlockPos.betweenClosedStream(playerPos.offset(-RADIUS, -2, -RADIUS), playerPos.offset(RADIUS, 3, RADIUS))
                .forEach(pos -> {
                    // Make sure it doesn't spawn exactly on the player's body
                    if (pos.equals(playerPos) || pos.equals(playerPos.above())) {
                        return;
                    }
                    if (level.isEmptyBlock(pos)) {
                        airCandidates.add(pos.immutable());
                    }
                });

        if (airCandidates.isEmpty()) {
            return false;
        }

        Collections.shuffle(airCandidates);
        int spawned = 0;
        int targetCount = Math.min(BLOCKS_TO_SPAWN, airCandidates.size());

        for (int i = 0; i < targetCount; i++) {
            BlockPos pos = airCandidates.get(i);
            if (level.setBlockAndUpdate(pos, SOAPBlocks.NULL_BLOCK.get().defaultBlockState())) {
                spawned++;
            }
        }

        return spawned > 0;
    }
}
