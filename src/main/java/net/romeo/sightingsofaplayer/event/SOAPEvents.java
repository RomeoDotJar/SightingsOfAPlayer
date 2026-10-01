package net.romeo.sightingsofaplayer.event;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.romeo.sightingsofaplayer.SOAP;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@EventBusSubscriber(modid = SOAP.MODID, value = Dist.DEDICATED_SERVER)
public class SOAPEvents {

    @SubscribeEvent
    public static void OnLivingDamage(LivingDamageEvent.Pre event) {
        //if (event.getSource().getEntity() instanceof LivingEntity attacker) {
        //    attacker.hurt(event.getSource(), event.getNewDamage());
        //}
    }
}
