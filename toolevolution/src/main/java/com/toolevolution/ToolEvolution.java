package com.toolevolution;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ToolEvolution.MOD_ID)
public class ToolEvolution {
    public static final String MOD_ID = "toolevolution";

    public ToolEvolution() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
    }
}
