package net.romeo.sightingsofaplayer.soap_event.impl.shared;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.entity.custom.MythStalkerEntity;
import net.romeo.sightingsofaplayer.init.SOAPEntities;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;
import net.romeo.sightingsofaplayer.util.SOAPPlayerUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Something hostile appears in the dark a short distance from a player who is already underground.
 * <p>
 * Shows a conditional {@code soap_event} whose work needs real space to happen: it only becomes
 * available while someone is below ground, it looks for a floor in the cave that player is actually
 * standing in, and it gives up (returning {@code false}) if no such spot exists - in which case the
 * system quietly picks something else instead.
 * <p>
 * The creature itself is the dedicated stalking mob, {@code MythStalkerEntity}: it is placed here and
 * then left entirely to its own devices - it never despawns on its own and removes itself only when a
 * player holds it in their sights.
 */
public class EventSpawnStalker extends SoapEvent {
    private static final int MIN_DISTANCE = 32;
    private static final int MAX_DISTANCE = 80;
    private static final int ATTEMPTS = 4096;
    private static final int VERTICAL_SEARCH = 32;

    @Override
    public String id() {
        return "spawn_stalker";
    }

    @Override
    public Categories category() { return Categories.Big; }

    @Override
    public Myth[] relatedMyths() {
        return new Myth[] { Myth.HEROBRINE, Myth.NULL, Myth.ENTITY_303 };
    }

    @Override
    public int tensionGain(SoapEventContext ctx) {
        int base = 0;

        return base;
    }

    @Override
    public float selectionWeight(SoapEventContext ctx) {
        float base = 1f;

        if (SOAPMythSavedData.get(ctx.server()).isActive(Myth.HEROBRINE)) {
            base += 1f;
        }

        return base;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        return super.canRun(context) && context.tension()>=-tensionGain();
    }

    @Override
    public boolean run(SoapEventContext context) {
        List<ServerPlayer> players = context.players();

        ServerPlayer target = players.get(randomIndex(context.random(), players.size()));
        ServerLevel level = target.serverLevel();

        BlockPos spot = findSpawnSpot(context, target);
        if (spot == null) {
            return false;
        }

        MythStalkerEntity stalks = SOAPEntities.STALKER.get().spawn(level, spot, MobSpawnType.EVENT);
        if (stalks == null) {
            return false;
        }

        // A chance to be silent
        if (context.random().nextInt(3)==0) {
            SoundEvent sound = SoundEvents.AMBIENT_CAVE.value();
            level.playSound(null, spot, sound, SoundSource.AMBIENT,
                    32.0F, 0.6F + context.random().nextFloat() * 0.3F);
        }

        return true;
    }

    /**
     * Looks for a standable spot.
     */
    @Nullable
    private static BlockPos findSpawnSpot(SoapEventContext context, ServerPlayer target) {
        Supplier<Vec3> generator = () -> new Vec3(context.random().nextIntBetweenInclusive(MIN_DISTANCE,MAX_DISTANCE),0,0).yRot(context.random().nextIntBetweenInclusive(-180,180));

        BlockPos center = target.blockPosition();
        ServerLevel level = target.serverLevel();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Vec3 vec = generator.get();
            double dx = vec.x();
            double dz = vec.z();

            double x = center.getX() + dx;
            double z = center.getZ() + dz;

            vec = new Vec3(x,center.getY(),z);

            boolean valid = true;
            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                if (player.position().distanceTo(vec) <= MIN_DISTANCE) {
                    valid=false;
                    break;
                }
            }
            if (!valid)
                continue;

            boolean sees = SOAPPlayerUtils.lookingAt(target, vec);

            if (sees)
                continue;

            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos candidate = new BlockPos((int)x, center.getY() + dy, (int)z);
                BlockPos below = candidate.below();

                if (level.isEmptyBlock(candidate)
                        && level.isEmptyBlock(candidate.above())
                        && level.isEmptyBlock(candidate.above(2))
                        && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
