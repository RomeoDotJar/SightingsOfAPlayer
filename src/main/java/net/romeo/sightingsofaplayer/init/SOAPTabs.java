package net.romeo.sightingsofaplayer.init;

import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.tab.SOAPTab;

public class SOAPTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY
            = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SOAP.MODID);

    public static final DeferredHolder<CreativeModeTab, SOAPTab> SOAP_TAB
            = REGISTRY.register("soap_tab", SOAPTab::new);
}