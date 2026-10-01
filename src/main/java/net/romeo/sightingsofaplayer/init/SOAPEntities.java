package net.romeo.sightingsofaplayer.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.entity.custom.MythStalkerEntity;

import java.util.function.Supplier;

public class SOAPEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY
            = DeferredRegister.create(Registries.ENTITY_TYPE, SOAP.MODID);

    /**
     * The Stalker, sized exactly like a player (0.6 x 1.8) so that its bounding box - and therefore
     * the censor bars the client projects from it - hugs the player-shaped model it hides.
     * <p>
     * It is registered as {@code MONSTER} for the mob-cap and difficulty bookkeeping only; the
     * entity itself refuses every kind of natural despawn (see {@link MythStalkerEntity}), so its
     * category never actually decides its fate.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MythStalkerEntity>> STALKER
            = register("stalker", () -> EntityType.Builder.of(MythStalkerEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("stalker")
    );

    private static <T extends EntityType<?>> void registerSpawnEgg(String name, DeferredHolder<EntityType<?>, T> mob) {
    }

    private static <T extends EntityType<?>> DeferredHolder<EntityType<?>, T> register(String name, Supplier<T> mob) {
        DeferredHolder<EntityType<?>, T> toReturn = REGISTRY.register(name,mob);
        registerSpawnEgg(name, toReturn);

        return toReturn;
    }

    /**
     * Hands the Stalker its attribute supplier. Without this the entity cannot be created at all,
     * since {@code Mob.createAttributes()} has no usable default.
     */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STALKER.get(), MythStalkerEntity.createAttributes().build());
    }

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
