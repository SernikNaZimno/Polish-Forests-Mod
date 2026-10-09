package pl.polishforests.worldgen.feature;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.feature.plan.ColumnPlan;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Column data of a chunk for the vegetation planners ({@link ColumnPlan.Columns}, docs/03-m2-biomy.md §8.2): habitat
 * codes and levels from {@link ChunkHabitats}, the water depth, the kind of the top ground block as it stands in the
 * world (trees turn the grass under their trunks into dirt) and what is beside a dry column ({@link ColumnPlan#SIDE_WATER}
 * etc.): water beside its top block, told apart as the landscape model's water (a water biome or the reedbed zone in
 * the habitat code) or a puddle of the micro-relief (water on a column of a land habitat), and the wet beach or the sea
 * within 2 blocks. Across a chunk border the neighbor's {@link ChunkHabitats} are read (the 8 neighbors of a decorated
 * chunk are past {@code fill()}); without them the world's fluid counts as the model's water.
 */
final class VegetationColumns {
	private static final Map<Block, ColumnPlan.Ground> GROUNDS = new IdentityHashMap<>();

	static {
		GROUNDS.put(Blocks.GRASS_BLOCK, ColumnPlan.Ground.GRASS);
		GROUNDS.put(Blocks.DIRT, ColumnPlan.Ground.DIRT);
		GROUNDS.put(Blocks.COARSE_DIRT, ColumnPlan.Ground.DIRT);
		GROUNDS.put(Blocks.ROOTED_DIRT, ColumnPlan.Ground.DIRT);
		GROUNDS.put(Blocks.PODZOL, ColumnPlan.Ground.PODZOL);
		GROUNDS.put(Blocks.MUD, ColumnPlan.Ground.MUD);
		GROUNDS.put(Blocks.MOSS_BLOCK, ColumnPlan.Ground.MOSS);
		GROUNDS.put(Blocks.SAND, ColumnPlan.Ground.SAND);
		GROUNDS.put(Blocks.GRAVEL, ColumnPlan.Ground.GRAVEL);
		GROUNDS.put(Blocks.CLAY, ColumnPlan.Ground.CLAY);
	}

	/** Offsets of the columns within Manhattan distance 2 (the seaward edge). */
	private static final int[][] RING2 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {1, -1},
			{-1, 1}, {-1, -1}};

	private VegetationColumns() {
	}

	/** Water depth of each column in blocks (0: dry). */
	static int[] waterDepth(ChunkHabitats habitats) {
		int[] depth = new int[256];
		for (int i = 0; i < 256; i++) {
			depth[i] = habitats.hasWater(i) ? habitats.water()[i] - habitats.top()[i] : 0;
		}
		return depth;
	}

	/** Whether water on a column of this habitat code is the landscape model's (not a puddle of the micro-relief). */
	static boolean modelWater(int code) {
		return Habitat.biome(code).isWater() || Habitat.zone(code) == Zone.REEDBED;
	}

	/** Whether a column of this code is the wet beach (the beach below the strandline) or the sea. */
	private static boolean seaward(int code) {
		HabitatBiome b = Habitat.biome(code);
		return b == HabitatBiome.SEA || b == HabitatBiome.LAGOON || b == HabitatBiome.BEACH && Habitat.zone(code) == Zone.NONE;
	}

	/**
	 * Columns of the chunk.
	 *
	 * @param shore    whether to find the dry columns with water beside their top block (layers with shore rules)
	 * @param seaward  whether to find the dry beach columns within 2 blocks of the wet beach or the sea
	 */
	static ColumnPlan.Columns of(WorldGenLevel level, ChunkAccess chunk, ChunkHabitats habitats, boolean shore,
			boolean seaward) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		int[] depth = waterDepth(habitats);
		int[] ground = new int[256];
		byte[] side = new byte[256];
		short[] top = habitats.top();
		Neighbors around = new Neighbors(level, pos, habitats);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			at.set(x0 + x, top[i], z0 + z);
			ground[i] = GROUNDS.getOrDefault(chunk.getBlockState(at).getBlock(), ColumnPlan.Ground.OTHER).ordinal();
			if (depth[i] > 0) {
				continue;
			}
			int bits = 0;
			if (shore) {
				for (int d = 0; d < 4 && (bits & ColumnPlan.SIDE_WATER) == 0; d++) {
					int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
					int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
					ChunkHabitats h = around.at(nx, nz);
					if (h == null) {
						// No habitats of the neighbor (outside the "Poland" generation path): its fluid counts as model water.
						at.set(x0 + nx, top[i], z0 + nz);
						bits |= level.getFluidState(at).is(FluidTags.WATER) ? ColumnPlan.SIDE_WATER : 0;
						continue;
					}
					int j = ChunkHabitats.index(nx & 15, nz & 15);
					short w = h.water()[j];
					if (w != ChunkHabitats.NO_WATER && w >= top[i] && h.top()[j] < top[i]) {
						bits |= modelWater(h.codes()[j]) ? ColumnPlan.SIDE_WATER : ColumnPlan.SIDE_PUDDLE;
					}
				}
			}
			if (seaward && Habitat.biome(habitats.codes()[i]) == HabitatBiome.BEACH && Habitat.zone(habitats.codes()[i]) != Zone.NONE) {
				for (int[] o : RING2) {
					ChunkHabitats h = around.at(x + o[0], z + o[1]);
					if (h != null && seaward(h.codes()[ChunkHabitats.index(x + o[0] & 15, z + o[1] & 15)])) {
						bits |= ColumnPlan.SIDE_SEAWARD;
						break;
					}
				}
			}
			side[i] = (byte) bits;
		}
		return new ColumnPlan.Columns(habitats.codes(), depth, ground, side);
	}

	/** Habitats of the chunk and of its 8 neighbors, by local coordinates relative to the chunk (−16…31). */
	private static final class Neighbors {
		private final WorldGenLevel level;
		private final ChunkPos pos;
		private final ChunkHabitats[] cache = new ChunkHabitats[9];
		private final boolean[] loaded = new boolean[9];

		Neighbors(WorldGenLevel level, ChunkPos pos, ChunkHabitats own) {
			this.level = level;
			this.pos = pos;
			cache[4] = own;
			loaded[4] = true;
		}

		ChunkHabitats at(int x, int z) {
			int dx = x >> 4;
			int dz = z >> 4;
			int k = (dx + 1) * 3 + dz + 1;
			if (!loaded[k]) {
				loaded[k] = true;
				cache[k] = level.getChunk(pos.x() + dx, pos.z() + dz).getAttached(ModFeatures.CHUNK_HABITATS);
			}
			return cache[k];
		}
	}
}
