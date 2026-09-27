package net.romeo.sightingsofaplayer.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.block.NullBlock;
import net.romeo.sightingsofaplayer.item.AmuletItem;

public class SOAPItems {
    public static final DeferredRegister<Item> REGISTRY
            = DeferredRegister.create(Registries.ITEM, SOAP.MODID);

    public static final DeferredHolder<Item, AmuletItem> AMULET
            = REGISTRY.register("amulet", AmuletItem::new);
}