package pl.polishforests.entity.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The habitat of a column for the fauna (step FA1, KFA-3): the habitat biome of the mod, in both world types ("Poland"
 * and "Default + Polish Forests" have the same biomes {@code polishforests:<habitat>} for the forests of the mod), never
 * a vanilla biome, and the forest edge (an open column within {@link AnimalProfile#EDGE_REACH} blocks of a forest of the
 * species). At generation the column codes of {@link ChunkHabitats} give the biome; later the biome of the level at the
 * surface, which is the same biome (the biome source writes the habitat biome of the column).
 */
public final class FaunaHabitat {
	private FaunaHabitat() {
	}

	/** The habitat biome of a biome holder, or null for a biome that is not one of the mod. */
	public static @Nullable HabitatBiome of(Holder<Biome> biome) {
		ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
		if (key == null || !key.identifier().getNamespace().equals(PolishForests.MOD_ID)) {
			return null;
		}
		return HabitatBiome.byId(key.identifier().getPath());
	}

	/** The habitat biome at the surface of a column (the top of {@code MOTION_BLOCKING_NO_LEAVES}), or null. */
	public static @Nullable HabitatBiome surface(LevelReader level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return of(level.getBiome(new BlockPos(x, y, z)));
	}

	/**
	 * Weight of a column for a species: the weight of its biome, or, for an open column with weight 0, the edge weight
	 * when a column {@link AnimalProfile#EDGE_REACH} blocks away in one of the four directions has a biome of weight above 0.
	 */
	public static float weight(LevelReader level, AnimalProfile profile, int x, int z) {
		float w = profile.weight(surface(level, x, z));
		if (w > 0 || profile.edgeWeight() <= 0) {
			return w;
		}
		HabitatBiome here = surface(level, x, z);
		if (here == null || here.isWater()) {
			return 0;
		}
		int r = AnimalProfile.EDGE_REACH;
		for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			if (profile.weight(surface(level, x + d[0], z + d[1])) > 0) {
				return profile.edgeWeight();
			}
		}
		return 0;
	}

	/**
	 * Weights of the 256 columns of a chunk from its habitat codes (index {@code x * 16 + z}): the biome weight, or the edge
	 * weight for an open land column within {@link AnimalProfile#EDGE_REACH} blocks of a column of weight above 0 inside the
	 * chunk (the codes of the neighbors are not at hand at the last generation stage), 0 on columns the mod does not
	 * decorate ({@link ChunkHabitats#blocked}).
	 */
	public static float[] columnWeights(ChunkHabitats habitats, AnimalProfile profile) {
		float[] w = new float[256];
		boolean[] forest = new boolean[256];
		for (int i = 0; i < 256; i++) {
			if (habitats.blocked(i)) {
				continue;
			}
			w[i] = profile.weight(habitats.biome(i >> 4, i & 15));
			forest[i] = w[i] > 0;
		}
		if (profile.edgeWeight() <= 0) {
			return w;
		}
		int r = AnimalProfile.EDGE_REACH;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int i = ChunkHabitats.index(x, z);
				HabitatBiome b = habitats.biome(x, z);
				if (forest[i] || habitats.blocked(i) || b == null || b.isWater() || habitats.hasWater(i)) {
					continue;
				}
				search:
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						int nx = x + dx;
						int nz = z + dz;
						if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && dx * dx + dz * dz <= r * r && forest[ChunkHabitats.index(nx, nz)]) {
							w[i] = profile.edgeWeight();
							break search;
						}
					}
				}
			}
		}
		return w;
	}
}
