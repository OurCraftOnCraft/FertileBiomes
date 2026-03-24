package com.brooswit.fertilebiomes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Event subscriber for crop growth events.
 * Handles both natural growth and bonemeal-triggered growth.
 * 
 * Growth Logic:
 * - If multiplier < 1: Cancel growth attempts with probability (1 - multiplier)
 * - If multiplier > 1: Apply extra age increments after vanilla growth
 */
@Mod.EventBusSubscriber(modid = FertileBiomesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CropGrowthEvents {

    // Tag keys for crop filtering
    private static final TagKey<Block> CROPS_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.parse("minecraft:crops"));

    /**
     * Handles pre-growth event to potentially cancel slow growth attempts.
     * When multiplier < 1, we cancel the growth with probability (1 - multiplier).
     */
    @SubscribeEvent
    public static void onCropGrowPre(BlockEvent.CropGrowEvent.Pre event) {
        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        // Check if this block is a food crop we should affect
        if (!isAffectedCrop(state.getBlock())) {
            return;
        }

        // Get biome
        ResourceLocation biomeRL = level.getBiome(pos).unwrapKey()
                .map(key -> key.location())
                .orElse(null);

        if (biomeRL == null) {
            return; // Biome has no registry key, skip processing
        }

        ResourceLocation cropRL = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (cropRL == null) {
            return;
        }

        // Calculate growth multiplier
        double multiplier = GrowthMultiplier.calculateMultiplier(cropRL, biomeRL);

        // If multiplier < 1, cancel growth with probability (1 - multiplier)
        if (multiplier < 1.0) {
            double cancelProbability = 1.0 - multiplier;
            RandomSource random = level.getRandom();

            if (random.nextDouble() < cancelProbability) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    /**
     * Handles post-growth event to apply extra growth when multiplier > 1.
     * After vanilla growth occurs, we apply additional age increments.
     * Also shows particle effects based on the growth multiplier.
     */
    @SubscribeEvent
    public static void onCropGrowPost(BlockEvent.CropGrowEvent.Post event) {
        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        // Check if this block is a food crop we should affect
        if (!isAffectedCrop(state.getBlock())) {
            return;
        }

        // Get biome
        ResourceLocation biomeRL = level.getBiome(pos).unwrapKey()
                .map(key -> key.location())
                .orElse(null);

        if (biomeRL == null) {
            return; // Biome has no registry key, skip processing
        }

        ResourceLocation cropRL = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (cropRL == null) {
            return;
        }

        // Calculate growth multiplier
        double multiplier = GrowthMultiplier.calculateMultiplier(cropRL, biomeRL);

        // If multiplier > 1, apply extra growth
        if (multiplier > 1.0) {
            applyExtraGrowth(level, pos, state, multiplier);
        }

        // Show particle effects during growth (always)
        spawnGrowthParticles(level, pos, multiplier);
    }

    // Note: Bonemeal-triggered growth is already handled by CropGrowEvent.Post
    // when bonemeal successfully grows a crop. The affectsBonemeal config option
    // is kept for potential future implementation if a proper bonemeal event
    // becomes available.

    /**
     * Handles client-side visual effects when planting seeds.
     * Shows particles based on the growth multiplier for the crop+biome
     * combination.
     * - High multiplier (>=1.0): Happy villager particles (green sparkles)
     * - Low multiplier (<1.0): Smoke particles (gray)
     * 
     * This fires when a player right-clicks farmland with a seed item.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack itemStack = event.getItemStack();
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        BlockState clickedBlock = level.getBlockState(pos);

        // Check if player is holding a seed item
        if (itemStack.isEmpty() || !isSeedItem(itemStack.getItem())) {
            return;
        }

        // Check if clicking on farmland
        if (!(clickedBlock.getBlock() instanceof FarmBlock)) {
            return;
        }

        // Get the block that would be placed (one block above the farmland)
        BlockPos cropPos = pos.above();

        // Determine what crop would be planted from this seed
        Block cropBlock = getCropBlockFromSeed(itemStack.getItem());
        if (cropBlock == null || !isAffectedCrop(cropBlock)) {
            return;
        }

        // Get biome at the crop position
        ResourceLocation biomeRL = level.getBiome(cropPos).unwrapKey()
                .map(key -> key.location())
                .orElse(null);

        if (biomeRL == null) {
            return;
        }

        ResourceLocation cropRL = ForgeRegistries.BLOCKS.getKey(cropBlock);
        if (cropRL == null) {
            return;
        }

        // Calculate growth multiplier
        double multiplier = GrowthMultiplier.calculateMultiplier(cropRL, biomeRL);

        // Spawn particles based on multiplier
        spawnGrowthParticles(level, cropPos, multiplier);
    }

    /**
     * Gets the crop block that would be planted from a seed item.
     * 
     * @param seedItem The seed item
     * @return The crop block, or null if not a known seed
     */
    private static Block getCropBlockFromSeed(Item seedItem) {
        if (seedItem == Items.WHEAT_SEEDS) {
            return Blocks.WHEAT;
        } else if (seedItem == Items.BEETROOT_SEEDS) {
            return Blocks.BEETROOTS;
        } else if (seedItem == Items.CARROT) {
            return Blocks.CARROTS;
        } else if (seedItem == Items.POTATO) {
            return Blocks.POTATOES;
        } else if (seedItem == Items.MELON_SEEDS) {
            return Blocks.MELON_STEM;
        } else if (seedItem == Items.PUMPKIN_SEEDS) {
            return Blocks.PUMPKIN_STEM;
        }
        return null;
    }

    /**
     * Checks if an item is a seed item (for filtering placement events).
     * 
     * @param item The item to check
     * @return true if the item is a seed
     */
    private static boolean isSeedItem(Item item) {
        // Check if item is a known seed item
        return item == Items.WHEAT_SEEDS ||
                item == Items.BEETROOT_SEEDS ||
                item == Items.CARROT ||
                item == Items.POTATO ||
                item == Items.MELON_SEEDS ||
                item == Items.PUMPKIN_SEEDS;
    }

    /**
     * Spawns particle effects based on growth multiplier with tiered effects.
     * Works for both client-side (Level) and server-side (ServerLevel) levels.
     * 
     * Tier levels:
     * - >= 2.0: Heart particles (excellent fertility)
     * - >= 1.5: Happy villager + heart particles (very good)
     * - >= 1.0: Happy villager particles (good)
     * - >= 0.5: Smoke particles (poor)
     * - < 0.5: Large smoke + soul fire particles (very poor)
     * 
     * @param level      The level (client or server)
     * @param pos        The position to spawn particles at
     * @param multiplier The growth multiplier
     */
    private static void spawnGrowthParticles(Level level, BlockPos pos, double multiplier) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;

        int clientCount, serverCount;
        boolean isClient = level.isClientSide();

        if (multiplier >= 2.0) {
            // Excellent fertility - heart particles
            clientCount = 12;
            serverCount = 8;
            for (int i = 0; i < (isClient ? clientCount : serverCount); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.6;
                double offsetY = random.nextDouble() * 0.4;
                double offsetZ = (random.nextDouble() - 0.5) * 0.6;
                spawnParticle(level, ParticleTypes.HEART, x + offsetX, y + offsetY, z + offsetZ);
            }
        } else if (multiplier >= 1.5) {
            // Very good fertility - happy villager + hearts
            clientCount = 10;
            serverCount = 6;
            for (int i = 0; i < (isClient ? clientCount : serverCount); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.5;
                double offsetY = random.nextDouble() * 0.3;
                double offsetZ = (random.nextDouble() - 0.5) * 0.5;
                spawnParticle(level, ParticleTypes.HAPPY_VILLAGER, x + offsetX, y + offsetY, z + offsetZ);
            }
            // Add a few hearts
            for (int i = 0; i < (isClient ? 3 : 2); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.4;
                double offsetY = random.nextDouble() * 0.3;
                double offsetZ = (random.nextDouble() - 0.5) * 0.4;
                spawnParticle(level, ParticleTypes.HEART, x + offsetX, y + offsetY, z + offsetZ);
            }
        } else if (multiplier >= 1.0) {
            // Good fertility - happy villager particles
            clientCount = 8;
            serverCount = 5;
            for (int i = 0; i < (isClient ? clientCount : serverCount); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.5;
                double offsetY = random.nextDouble() * 0.3;
                double offsetZ = (random.nextDouble() - 0.5) * 0.5;
                spawnParticle(level, ParticleTypes.HAPPY_VILLAGER, x + offsetX, y + offsetY, z + offsetZ);
            }
        } else if (multiplier >= 0.5) {
            // Poor fertility - smoke particles
            clientCount = 5;
            serverCount = 3;
            for (int i = 0; i < (isClient ? clientCount : serverCount); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.4;
                double offsetY = random.nextDouble() * 0.2;
                double offsetZ = (random.nextDouble() - 0.5) * 0.4;
                spawnParticle(level, ParticleTypes.SMOKE, x + offsetX, y + offsetY, z + offsetZ);
            }
        } else {
            // Very poor fertility - large smoke + soul fire particles
            clientCount = 8;
            serverCount = 5;
            for (int i = 0; i < (isClient ? clientCount : serverCount); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.5;
                double offsetY = random.nextDouble() * 0.3;
                double offsetZ = (random.nextDouble() - 0.5) * 0.5;
                spawnParticle(level, ParticleTypes.LARGE_SMOKE, x + offsetX, y + offsetY, z + offsetZ);
            }
            // Add soul fire particles
            for (int i = 0; i < (isClient ? 4 : 2); i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.4;
                double offsetY = random.nextDouble() * 0.2;
                double offsetZ = (random.nextDouble() - 0.5) * 0.4;
                spawnParticle(level, ParticleTypes.SOUL_FIRE_FLAME, x + offsetX, y + offsetY, z + offsetZ);
            }
        }
    }

    /**
     * Helper method to spawn a particle on client or server.
     */
    private static void spawnParticle(Level level, net.minecraft.core.particles.ParticleOptions particleOptions,
            double x, double y, double z) {
        if (level.isClientSide()) {
            level.addParticle(particleOptions, true, x, y, z, 0, 0, 0);
        } else if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particleOptions, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Checks if a block is a food crop that should be affected by this mod.
     * 
     * Filtering logic:
     * 1. Check denylist (denylist wins)
     * 2. Check allowlist (if not empty, only allow listed crops)
     * 3. Check tags (forge:crops)
     * 
     * @param block The block to check
     * @return true if the block should be affected
     */
    private static boolean isAffectedCrop(Block block) {
        ResourceLocation blockRL = ForgeRegistries.BLOCKS.getKey(block);
        if (blockRL == null) {
            return false;
        }

        String blockId = blockRL.toString();

        // Denylist always wins
        List<String> denylist = FBConfig.getDenylist();
        if (denylist.contains(blockId)) {
            return false;
        }

        // Check allowlist (if not empty, only allow listed crops)
        List<String> allowlist = FBConfig.getAllowlist();
        if (!allowlist.isEmpty()) {
            return allowlist.contains(blockId);
        }

        // Check tags (minecraft:crops)
        BlockState state = block.defaultBlockState();
        boolean inTag = state.is(CROPS_TAG);

        // Fallback: check if it's a known vanilla crop block
        if (!inTag) {
            return block == Blocks.WHEAT ||
                    block == Blocks.CARROTS ||
                    block == Blocks.POTATOES ||
                    block == Blocks.BEETROOTS ||
                    block == Blocks.MELON_STEM ||
                    block == Blocks.PUMPKIN_STEM;
        }

        return inTag;
    }

    /**
     * Applies extra growth to a crop block when multiplier > 1.
     * 
     * Handles fractional and whole parts cleanly:
     * - Whole part: Apply that many age increments
     * - Fractional part: Apply with probability equal to the fraction
     * 
     * @param level      The server level
     * @param pos        The block position
     * @param state      The current block state
     * @param multiplier The growth multiplier (> 1.0)
     */
    private static void applyExtraGrowth(ServerLevel level, BlockPos pos, BlockState state, double multiplier) {
        // multiplier > 1.0, so extraGrowth is positive
        // If multiplier is 1.5, we want 0.5 extra growth (50% chance of one extra
        // increment)
        // If multiplier is 2.0, we want 1.0 extra growth (guaranteed one extra
        // increment)
        double extraGrowth = multiplier - 1.0;

        // Find the age property for this crop block
        IntegerProperty ageProperty = findAgeProperty(state);
        if (ageProperty == null) {
            return; // Can't apply growth if we can't find age property
        }

        int currentAge = state.getValue(ageProperty);
        int maxAge = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(currentAge);

        // Don't grow beyond max age
        if (currentAge >= maxAge) {
            return;
        }

        RandomSource random = level.getRandom();

        // Calculate whole and fractional parts
        int wholeIncrements = (int) Math.floor(extraGrowth);
        double fractionalPart = extraGrowth - wholeIncrements;

        // Apply whole increments
        for (int i = 0; i < wholeIncrements && currentAge < maxAge; i++) {
            currentAge++;
        }

        // Apply fractional increment with probability
        if (fractionalPart > 0.0 && currentAge < maxAge) {
            if (random.nextDouble() < fractionalPart) {
                currentAge++;
            }
        }

        // Update the block state if age changed
        if (currentAge != state.getValue(ageProperty)) {
            BlockState newState = state.setValue(ageProperty, Math.min(currentAge, maxAge));
            level.setBlock(pos, newState, 2);
        }
    }

    /**
     * Finds the age property for a crop block.
     * Most crops use "age" property, but some might use different names.
     * 
     * @param state The block state
     * @return The age property, or null if not found
     */
    private static IntegerProperty findAgeProperty(BlockState state) {
        // Try common age property names
        Set<String> agePropertyNames = new HashSet<>();
        agePropertyNames.add("age");
        agePropertyNames.add("growth");
        agePropertyNames.add("stage");

        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty intProp) {
                String name = property.getName();
                if (agePropertyNames.contains(name.toLowerCase())) {
                    return intProp;
                }
            }
        }

        // Fallback: find any IntegerProperty that might be age-related
        // (look for properties with small value ranges, likely age properties)
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty intProp) {
                var possibleValues = intProp.getPossibleValues();
                int min = possibleValues.stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
                int max = possibleValues.stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);

                // Age properties typically have small ranges (0-7, 0-3, etc.)
                if (min == 0 && max > 0 && max <= 15) {
                    return intProp;
                }
            }
        }

        return null;
    }
}
