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

/**
 * Column data of a chunk for the vegetation planners ({@link ColumnPlan.Columns}, docs/03-m2-biomy.md §8.2): habitat
 * codes and levels from {@link ChunkHabitats}, the water depth, the kind of the top ground block as it stands in the
 * world (trees turn the grass under their trunks into dirt) and whether a dry column has water beside its top block
 * (the bank shelf; across a chunk border the neighbor's block is read).
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

	/**
	 * Columns of the chunk.
	 *
	 * @param shore whether to find the dry columns with water beside their top block (only layers with shore rules)
	 */
	static ColumnPlan.Columns of(WorldGenLevel level, ChunkAccess chunk, ChunkHabitats habitats, boolean shore) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		int[] depth = waterDepth(habitats);
		int[] ground = new int[256];
		boolean[] beside = new boolean[256];
		short[] top = habitats.top();
		short[] water = habitats.water();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			at.set(x0 + x, top[i], z0 + z);
			ground[i] = GROUNDS.getOrDefault(chunk.getBlockState(at).getBlock(), ColumnPlan.Ground.OTHER).ordinal();
			if (!shore || depth[i] > 0) {
				continue;
			}
			for (int d = 0; d < 4 && !beside[i]; d++) {
				int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
				int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
				if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
					int j = nx << 4 | nz;
					beside[i] = water[j] != ChunkHabitats.NO_WATER && water[j] >= top[i] && top[j] < top[i];
				} else {
					at.set(x0 + nx, top[i], z0 + nz);
					beside[i] = level.getFluidState(at).is(FluidTags.WATER);
				}
			}
		}
		return new ColumnPlan.Columns(habitats.codes(), depth, ground, beside);
	}
}
