package net.romeo.sightingsofaplayer.tab;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.romeo.sightingsofaplayer.init.SOAPItems;

public class SOAPTab extends CreativeModeTab {
    public SOAPTab() {
        super(
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.sightingsofaplayer"))
                .icon(() -> Items.BEDROCK.getDefaultInstance())

                .displayItems((parameters, output) -> {
                    for (DeferredHolder<Item, ? extends Item> entry : SOAPItems.REGISTRY.getEntries()) {
                        output.accept(entry.get());
                    }
            }));
    }
}
