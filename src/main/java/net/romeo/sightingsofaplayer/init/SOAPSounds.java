package net.romeo.sightingsofaplayer.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.romeo.sightingsofaplayer.SOAP;

import java.util.function.Supplier;

public class SOAPSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY
            = DeferredRegister.create(Registries.SOUND_EVENT, SOAP.MODID);

    public static final Supplier<SoundEvent> CHASE_TEST
            = register("chase_test");

    private static Supplier<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SOAP.MODID, name);

        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }

    public static SoundEvent getSoundEvent(String id) {
        SoundEvent sound = SOAPSounds.CHASE_TEST.get();

        for (DeferredHolder<SoundEvent, ? extends SoundEvent> holder : SOAPSounds.REGISTRY.getEntries()) {
            if (holder.getKey().location().getPath().equals(id)) {
                sound = holder.get();
                break;
            }
        }

        return sound;
    }
}
