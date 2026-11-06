package pl.polishforests.worldgen.chunk;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.feature.plan.ForestBlend;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * The forests of the mod in a world (step W1 of docs/04-pionowy-wycinek.md): what the vegetation dispatchers, the dead wood
 * and the filters read besides the chunk habitats. Two kinds: the "Poland" generator ({@link PolandChunkGenerator}) and
 * a vanilla noise generator with the biome source of the world type "Default + Polish Forests"
 * ({@link VanillaForestsBiomeSource}, the replaced vanilla forests). Every value is a pure function of the world seed and
 * the settings.
 */
public interface ForestWorld {
	/** The scale's k (rule Z7): the size of the objects and fields of the forest. */
	double localScale();

	/** Share of managed stands (decision D9). */
	double managedForestShare();

	/**
	 * The seed of the world's stand map ({@link ForestStandMap}): the world seed in a "Poland" world, a derived seed (a new
	 * salt) in the world type "Default + Polish Forests".
	 */
	long standSeed(long worldSeed);

	/** The stand map of the world (step V9), shared by the tree stand and the dead wood. */
	ForestStandMap standMap(long worldSeed);

	/** The wide blend of the forests of the slice (step Z1). */
	ForestBlend forestBlend(long worldSeed);

	/** Whether the world has caves (the underground features of the habitat biomes, step J2). */
	boolean caveFeatures();

	/**
	 * The direction away from the sea at world column (x, z) (step F4: the lee side of the banner crowns of the coastal wind
	 * belt; radians, x = cos, z = sin), or NaN where the world has no coast model (the trees then lean to their own side).
	 */
	default double leeAzimuth(long worldSeed, int x, int z) {
		return Double.NaN;
	}

	/**
	 * Whether this is the world type "Default + Polish Forests": the chunk habitats come from the vanilla terrain, the
	 * dispatchers run from a forest column ({@code TopOfWorldPlacement}) and missing habitats are not recomputed.
	 */
	default boolean vanilla() {
		return false;
	}

	/** The forests of the world of this generator, or null for a generator without them. */
	static @Nullable ForestWorld of(ChunkGenerator generator) {
		if (generator instanceof PolandChunkGenerator poland) {
			return poland;
		}
		if (generator.getBiomeSource() instanceof VanillaForestsBiomeSource source) {
			return source.world();
		}
		return null;
	}
}
