package net.romeo.sightingsofaplayer.soap_event.impl;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.romeo.sightingsofaplayer.event.SOAPServerTimeEvents;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Something hostile appears in the dark a short distance from a player who is already underground.
 * <p>
 * Shows a conditional {@code soap_event} whose work needs real space to happen: it only becomes
 * available while someone is below ground, it looks for a floor in the cave that player is actually
 * standing in, and it gives up (returning {@code false}) if no such spot exists - in which case the
 * system quietly picks something else instead.
 * <p>
 * The creature itself is a plain cave spider standing in for a dedicated stalking mob: a real one
 * would simply replace the spawn call below without the scheduling around it changing at all.
 */
public class EventDebugSpawnCaveStalkingEntity extends SoapEvent {
    private static final int MIN_DISTANCE = 8;
    private static final int MAX_DISTANCE = 16;
    private static final int ATTEMPTS = 16;
    private static final int VERTICAL_SEARCH = 4;

    @Override
    public String id() {
        return "debug_spawn_cave_stalking_entity";
    }

    @Override
    public Categories category() { return Categories.Big; }

    @Override
    public int tensionGain() {
        return -200;
    }

    @Override
    public float selectionWeight() {
        return 4f;
    }

    /** Only ever while at least one player is below ground. */
    @Override
    public boolean canRun(SoapEventContext context) {
        return !context.playersUnderground().isEmpty() && context.tension()>=-tensionGain();
    }

    @Override
    public boolean run(SoapEventContext context) {
        List<ServerPlayer> underground = context.playersUnderground();
        if (underground.isEmpty()) {
            return false;
        }

        ServerPlayer target = underground.get(randomIndex(context.random(), underground.size()));
        ServerLevel level = context.overworld();

        BlockPos spot = findCaveFloor(level, target.blockPosition(), context.random());
        if (spot == null) {
            return false;
        }

        CaveSpider stalks = EntityType.CAVE_SPIDER.spawn(level, spot, MobSpawnType.EVENT);
        if (stalks == null) {
            return false;
        }

        //stalks.setCustomName(Component.translatable(nameKey() + ".entity"));
        //stalks.setCustomNameVisible(true);

        SOAPServerTimeEvents.queueWork(200, () -> {
            if (stalks != null) {
                stalks.discard();
            }
        });

        level.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.HOSTILE, 0.8F, 0.6F);
        return true;
    }

    /**
     * Looks for a standable spot in the cave the player is in.
     * <p>
     * Deliberately searched around the player's own height rather than through the heightmap, since
     * the heightmap would answer with the surface, dozens of blocks above a player in a cave.
     */
    @Nullable
    private static BlockPos findCaveFloor(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int dx = random.nextInt(MAX_DISTANCE * 2 + 1) - MAX_DISTANCE;
            int dz = random.nextInt(MAX_DISTANCE * 2 + 1) - MAX_DISTANCE;

            if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) {
                continue;   // too close to the player to be a surprise
            }

            int x = center.getX() + dx;
            int z = center.getZ() + dz;

            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos candidate = new BlockPos(x, center.getY() + dy, z);
                BlockPos below = candidate.below();

                if (level.isEmptyBlock(candidate)
                        && level.isEmptyBlock(candidate.above())
                        && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
