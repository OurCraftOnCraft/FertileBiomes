package com.brooswit.fertilebiomes;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FertileBiomesMod.MODID)
public class FertileBiomesMod {
    public static final String MODID = "fertilebiomes";

    public FertileBiomesMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        
        // Register config
        @SuppressWarnings("removal")
        var context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.COMMON, FBConfig.SPEC);
        
        // Register event handlers
        forgeEventBus.register(CropGrowthEvents.class);
    }
}

