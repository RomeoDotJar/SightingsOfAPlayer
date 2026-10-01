package net.romeo.sightingsofaplayer.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.block.NullBlock;

import java.util.function.Supplier;

public class SOAPBlocks {
    public static final DeferredRegister<Block> REGISTRY
            = DeferredRegister.create(Registries.BLOCK, SOAP.MODID);

    public static final DeferredHolder<Block, NullBlock> NULL_BLOCK
            = register("null_block", NullBlock::new);

    private static <T extends Block> void registerBlockItem(String name, DeferredHolder<Block, T> block) {
        SOAPItems.REGISTRY.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block> DeferredHolder<Block, T> register(String name, Supplier<T> block) {
        DeferredHolder<Block, T> toReturn = REGISTRY.register(name,block);
        registerBlockItem(name, toReturn);

        return toReturn;
    }

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}