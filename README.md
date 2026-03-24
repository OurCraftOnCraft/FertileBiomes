# Fertile Biomes

A Forge mod for **Minecraft 1.20.1** that modifies the natural growth rate of food crops based on a deterministic hash of the crop registry ID, biome registry ID, and config salt.

## Features

- **Deterministic Growth Rates**: Each crop-biome-world combination has a consistent growth multiplier
- **No Per-Block Data**: Growth rates are calculated on-the-fly using deterministic hashing
- **Tag-Based Filtering**: Uses `forge:crops` tag
- **Configurable Filtering**: Allowlist and denylist support (denylist takes precedence)
- **Bonemeal Support**: Optional bonemeal growth modification (disabled by default)
- **Works with Modded Crops**: Compatible with vanilla and modded food crops

## How It Works

### Growth Multiplier Calculation

For each growth attempt, the mod calculates a deterministic multiplier:

1. **Key Generation**: Creates a key from `"<configSalt>|<cropRL>|<biomeRL>"`
2. **Hash**: Converts the key to a float `u ∈ [0,1)` using SHA-256
3. **Curve Transformation**: Calculates `v = sign(u-0.5) * |u-0.5|^curveK`
4. **Multiplier**: `multiplier = clamp(1.0 + v * amplitude, minMultiplier, maxMultiplier)`

### Growth Behavior

- **If multiplier < 1**: Growth attempts are cancelled with probability `(1 - multiplier)`
- **If multiplier > 1**: After vanilla growth, additional age increments are applied
  - Whole increments are applied deterministically
  - Fractional increments are applied probabilistically

## Configuration

The mod generates a config file at `config/fertilebiomes-common.toml` with the following options:

### Growth Parameters

- `minMultiplier` (default: 0.1): Minimum growth multiplier (must be > 0)
- `maxMultiplier` (default: 3.0): Maximum growth multiplier
- `amplitude` (default: 0.5): Amplitude of growth variation (higher = more variation)
- `curveK` (default: 1.0): Curve exponent (1.0 = linear, <1.0 = more extreme, >1.0 = more moderate)
- `configSalt` (default: "fertilebiomes_default_salt"): Salt for deterministic hash (change to get different patterns)

### Behavior Options

- `affectsBonemeal` (default: false): If true, bonemeal usage also respects growth multipliers

### Filtering

- `allowlist`: List of crop registry IDs to explicitly allow (empty = use tags only). Format: `["modid:blockid", ...]`
- `denylist`: List of crop registry IDs to explicitly deny (overrides allowlist and tags). Format: `["modid:blockid", ...]`

## Crop Filtering

The mod affects only food crops:

1. **Primary Filter**: Blocks in the `forge:crops` tag
2. **Allowlist**: If not empty, only listed crops are affected
3. **Denylist**: Always takes precedence (denied crops are never affected)

**Note**: Non-food plants (sugar cane, cactus, bamboo, etc.) are not affected.

## Building from Source

1. Clone this repository
2. Run the Forge installer:
   ```bash
   java -jar forge-1.20.1-47.4.10-installer.jar --installServer
   ```
3. Build the mod:
   ```bash
   ./gradlew build
   ```
4. The built JAR will be in `build/libs/`

## Development

### Running in Development

- **Client**: `./gradlew runClient`
- **Server**: `./gradlew runServer`

### Project Structure

```
src/main/java/com/brooswit/fertilebiomes/
├── FertileBiomesMod.java    # Main mod class
├── FBConfig.java             # Configuration
├── CropGrowthEvents.java     # Event subscribers for crop growth
└── GrowthMultiplier.java     # Hash and multiplier calculation utility

src/main/resources/
└── META-INF/mods.toml        # Mod metadata
```

## Technical Details

- **Event-Based**: Uses `BlockEvent.CropGrowEvent.Pre` and `BlockEvent.CropGrowEvent.Post`
- **Server-Only**: All logic runs server-side for proper synchronization
- **Deterministic**: Same inputs always produce the same multiplier
- **No Block Data**: No per-block saved data required
- **Performance**: Hash calculation is fast and cached per tick

## License

MIT

## Credits

Created for the OurCraftOnCraft server.
