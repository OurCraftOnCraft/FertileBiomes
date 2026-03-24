package com.brooswit.fertilebiomes;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for FertileBiomes mod.
 * Handles all configurable parameters for crop growth modification.
 */
public class FBConfig {
    public static final ForgeConfigSpec SPEC;
    
    // Multiplier bounds
    public static final ForgeConfigSpec.DoubleValue MIN_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_MULTIPLIER;
    
    // Hash curve parameters
    public static final ForgeConfigSpec.DoubleValue AMPLITUDE;
    public static final ForgeConfigSpec.DoubleValue CURVE_K;
    
    // Deterministic hash salt
    public static final ForgeConfigSpec.ConfigValue<String> CONFIG_SALT;
    
    // Bonemeal behavior
    public static final ForgeConfigSpec.BooleanValue AFFECTS_BONEMEAL;
    
    // Allowlist and denylist
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DENYLIST;
    
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.comment("FertileBiomes Configuration")
               .push("growth");
        
        MIN_MULTIPLIER = builder
                .comment("Minimum growth multiplier (must be > 0)")
                .defineInRange("minMultiplier", 0.1, 0.01, 1.0);
        
        MAX_MULTIPLIER = builder
                .comment("Maximum growth multiplier")
                .defineInRange("maxMultiplier", 3.0, 1.0, 10.0);
        
        AMPLITUDE = builder
                .comment("Amplitude of growth variation (higher = more variation)")
                .defineInRange("amplitude", 0.5, 0.0, 2.0);
        
        CURVE_K = builder
                .comment("Curve exponent (1.0 = linear, <1.0 = more extreme values, >1.0 = more moderate values)")
                .defineInRange("curveK", 1.0, 0.1, 5.0);
        
        CONFIG_SALT = builder
                .comment("Salt value for deterministic hash. Change this to get different growth patterns for the same crop-biome combinations.")
                .define("configSalt", "fertilebiomes_default_salt");
        
        AFFECTS_BONEMEAL = builder
                .comment("If true, bonemeal usage also respects growth multipliers")
                .define("affectsBonemeal", false);
        
        builder.pop();
        
        builder.comment("Crop filtering")
               .push("filtering");
        
        ALLOWLIST = builder
                .comment("List of crop registry IDs to explicitly allow (empty = use tags only). Format: 'modid:blockid'")
                .defineListAllowEmpty("allowlist", ArrayList::new, entry -> entry instanceof String);
        
        DENYLIST = builder
                .comment("List of crop registry IDs to explicitly deny (overrides allowlist and tags). Format: 'modid:blockid'")
                .defineListAllowEmpty("denylist", ArrayList::new, entry -> entry instanceof String);
        
        builder.pop();
        
        SPEC = builder.build();
    }
    
    // Helper methods for accessing config values
    public static double getMinMultiplier() {
        return MIN_MULTIPLIER.get();
    }
    
    public static double getMaxMultiplier() {
        return MAX_MULTIPLIER.get();
    }
    
    public static double getAmplitude() {
        return AMPLITUDE.get();
    }
    
    public static double getCurveK() {
        return CURVE_K.get();
    }
    
    public static String getConfigSalt() {
        return CONFIG_SALT.get();
    }
    
    public static boolean getAffectsBonemeal() {
        return AFFECTS_BONEMEAL.get();
    }
    
    @SuppressWarnings("unchecked")
    public static List<String> getAllowlist() {
        return (List<String>) ALLOWLIST.get();
    }
    
    @SuppressWarnings("unchecked")
    public static List<String> getDenylist() {
        return (List<String>) DENYLIST.get();
    }
}

