package net.romeo.sightingsofaplayer.soap_event.impl.entity303;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Entity 303 active event: spawns primed TNT near the player that explodes after 10 seconds (200 ticks).
 */
public class EventEntity303SpawnTnt extends SoapEvent {

    public static final String ID = "entity303_spawn_tnt";
    private static final int FUSE_TICKS = 200; // 10 seconds
    private static final int HORIZONTAL_OFFSET = 5;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Big;
    }

    @Override
    public int tensionGain() {
        return 50;
    }

    @Override
    public float selectionWeight() {
        return 2.0f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isActive(Myth.ENTITY_303) && !context.players().isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerPlayer player = context.randomPlayer();
        if (player == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        double offsetX = (context.random().nextDouble() * 2 - 1) * HORIZONTAL_OFFSET;
        double offsetZ = (context.random().nextDouble() * 2 - 1) * HORIZONTAL_OFFSET;
        double x = player.getX() + offsetX;
        double y = player.getY() + 1.0;
        double z = player.getZ() + offsetZ;

        PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
        tnt.setPos(x, y, z);
        tnt.setFuse(FUSE_TICKS);
        tnt.setDeltaMovement(0, 0.1, 0);

        return level.addFreshEntity(tnt);
    }
}
