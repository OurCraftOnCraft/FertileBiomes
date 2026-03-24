package com.brooswit.fertilebiomes;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for calculating deterministic growth multipliers based on
 * crop registry ID, biome registry ID, and config salt.
 * 
 * The hash algorithm ensures that the same combination of inputs always
 * produces the same multiplier, making growth patterns deterministic per
 * (crop + biome) combination.
 */
public class GrowthMultiplier {

    /**
     * Calculates a deterministic growth multiplier for a given crop and biome.
     * 
     * Algorithm:
     * 1. Create key: "<configSalt>|<cropRL>|<biomeRL>"
     * 2. Hash key to get float u ∈ [0,1)
     * 3. Calculate v = sign(u-0.5) * |u-0.5|^curveK
     * 4. Calculate multiplier = clamp(1.0 + v * amplitude, minMultiplier,
     * maxMultiplier)
     * 
     * @param cropRL  The crop block's resource location
     * @param biomeRL The biome's resource location
     * @return The growth multiplier (0.0 to maxMultiplier)
     */
    public static double calculateMultiplier(ResourceLocation cropRL, ResourceLocation biomeRL) {
        // Build the deterministic key
        String key = FBConfig.getConfigSalt() + "|" + cropRL + "|" + biomeRL;

        // Hash the key to get a value in [0, 1)
        double u = hashToDouble(key);

        // Calculate v using the curve formula
        // v = sign(u-0.5) * |u-0.5|^curveK
        double offset = u - 0.5;
        double absOffset = Math.abs(offset);
        double sign = offset >= 0 ? 1.0 : -1.0;
        double curveK = FBConfig.getCurveK();
        double v = sign * Math.pow(absOffset, curveK);

        // Calculate multiplier: 1.0 + v * amplitude
        double amplitude = FBConfig.getAmplitude();
        double multiplier = 1.0 + v * amplitude;

        // Clamp to configured bounds
        double minMultiplier = FBConfig.getMinMultiplier();
        double maxMultiplier = FBConfig.getMaxMultiplier();
        return Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));
    }

    /**
     * Hashes a string to a double value in [0, 1).
     * Uses SHA-256 for deterministic hashing.
     * 
     * @param input The string to hash
     * @return A double value in [0, 1)
     */
    private static double hashToDouble(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert first 8 bytes to a long, then normalize to [0, 1)
            long hashLong = 0;
            for (int i = 0; i < 8 && i < hashBytes.length; i++) {
                hashLong = (hashLong << 8) | (hashBytes[i] & 0xFF);
            }

            // Normalize to [0, 1) by dividing by 2^63
            // Use bitwise AND to get positive value (63 bits, avoiding sign bit)
            long unsignedHash = hashLong & 0x7FFFFFFFFFFFFFFFL;
            // Divide by 2^63 to get value in [0, 1)
            return unsignedHash / (double) (1L << 63);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 is unavailable (shouldn't happen)
            return (input.hashCode() & 0x7FFFFFFF) / (double) (Integer.MAX_VALUE + 1L);
        }
    }
}
