package pl.polishforests.worldgen.chunk;

import java.util.List;
import java.util.Arrays;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Registry keys of the 36 biomes of the mod ({@code polishforests:<id>}, docs/03-m2-biomy.md §2), in the order of
 * {@link HabitatBiome}. The paths {@code beach} and {@code river} are the same as in vanilla, but in another
 * namespace: they must not be confused with {@code Biomes.BEACH} and {@code Biomes.RIVER}.
 */
public final class ModBiomeKeys {
	private static final List<ResourceKey<Biome>> KEYS = Arrays.stream(HabitatBiome.values())
			.map(b -> ResourceKey.create(Registries.BIOME, PolishForests.id(b.id()))).toList();

	private ModBiomeKeys() {
	}

	/** Key of the biome. */
	public static ResourceKey<Biome> key(HabitatBiome biome) {
		return KEYS.get(biome.ordinal());
	}

	/** Keys of all biomes in the order of the enum. */
	public static List<ResourceKey<Biome>> all() {
		return KEYS;
	}
}
