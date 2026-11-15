package pl.polishforests.worldgen.vanilla;

import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import pl.polishforests.climate.BiomeClimate;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.ColumnClimate;
import pl.polishforests.climate.ColumnClimates;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.climate.VanillaClimate;

/**
 * Step C2 (CP-D.11), stage 2: snow and ice of {@code freeze_top_layer} also on the habitat columns of the neighbor chunks
 * that were decorated before this chunk, in the world type "Default + Polish Forests".
 *
 * <p>{@code freeze_top_layer} runs at the end of a chunk's own decoration and only on its own 16 × 16 columns. The crowns
 * of the trees of a chunk decorated later reach up to its neighbors (the decoration writes into the 3 × 3 chunks), so a
 * crown leaning over a neighbor decorated earlier stayed without snow, a cut along the chunk border (rule Z10; in the game
 * test 168 of 3227 cold columns of the former snowy taiga and 60 of 388 of the former windswept forest, all with leaves at
 * the top). After the decoration of a chunk this pass applies the vanilla rule of {@code SnowAndFreezeFeature} once more to
 * the columns of every neighbor already past FEATURES that holds a column climate: ice on cold water, snow on a cold dry top
 * with air above. The rule is idempotent (a column with snow stays as it is), so a column ends with the snow of its final
 * top whichever chunk is decorated last. Only habitat biomes with the column profile are touched; vanilla biomes and the
 * "Poland" world keep the vanilla behavior. Cheap: a neighbor without a column climate or not yet decorated is skipped, and
 * a column is tested against the biome only when its column temperature at the top is below the snow threshold.
 */
public final class BorderFreeze {
	/** Snow layers and ice blocks laid by this pass (diagnostics of the game test). */
	public static final LongAdder SNOW = new LongAdder();
	public static final LongAdder ICE = new LongAdder();

	private BorderFreeze() {
	}

	/**
	 * Runs the pass for the neighbors of {@code chunk} in its generation region; the calling thread must be marked as
	 * decorating that region ({@link ColumnClimates#decorating}), so the biome temperature reads the column climates.
	 */
	public static void apply(WorldGenLevel level, ChunkAccess chunk) {
		int cx = chunk.getPos().x();
		int cz = chunk.getPos().z();
		int seaLevel = level.getSeaLevel();
		BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx | dz) == 0 || !level.hasChunk(cx + dx, cz + dz)) {
					continue;
				}
				ChunkAccess n = level.getChunk(cx + dx, cz + dz, ChunkStatus.EMPTY, false);
				if (n == null || !n.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
					continue;
				}
				ColumnClimate climate = n.getAttached(ColumnClimates.TYPE);
				if (climate != null) {
					freeze(level, cx, cz, n, climate, seaLevel, top, below);
				}
			}
		}
	}

	private static void freeze(WorldGenLevel level, int cx, int cz, ChunkAccess n, ColumnClimate climate, int seaLevel,
			BlockPos.MutableBlockPos top, BlockPos.MutableBlockPos below) {
		int x0 = n.getPos().getMinBlockX();
		int z0 = n.getPos().getMinBlockZ();
		for (int i = 0; i < 256; i++) {
			int x = x0 + (i >> 4);
			int z = z0 + (i & 15);
			int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
			// The cheap test first: the column's vanilla temperature at its top (a frozen column below it is colder still).
			if (VanillaClimate.temperature(climate.base(x, z), x, y - 1, z, seaLevel) >= PolandClimate.SNOW_THRESHOLD) {
				continue;
			}
			if (!zoomInside(cx, cz, x, z)) {
				continue;
			}
			top.set(x, y, z);
			below.set(x, y - 1, z);
			Biome biome = level.getBiome(top).value();
			BiomeClimate profile = BiomeClimateAccess.climate(biome);
			if (profile == null || !profile.vanillaColumns()) {
				continue;
			}
			if (biome.shouldFreeze(level, below, false)) {
				level.setBlock(below, Blocks.ICE.defaultBlockState(), 2);
				ICE.increment();
			}
			if (!level.getBlockState(top).is(Blocks.SNOW) && biome.shouldSnow(level, top)) {
				level.setBlock(top, Blocks.SNOW.defaultBlockState(), 2);
				SNOW.increment();
				BlockState belowState = level.getBlockState(below);
				if (belowState.hasProperty(SnowyBlock.SNOWY)) {
					level.setBlock(below, belowState.setValue(SnowyBlock.SNOWY, true), 2);
				}
			}
		}
	}

	/**
	 * Whether the vanilla biome zoom of column (x, z) reads only the 3 × 3 chunks around the decorated chunk (cx, cz) (it
	 * may pick the quart after the one holding {@code x - 2}): the far edge of a neighbor would need a chunk two away,
	 * which the generation region holds at most at an earlier status than BIOMES. Such columns lie 14 or more blocks from
	 * the decorated chunk, out of reach of its crowns, and are skipped.
	 */
	static boolean zoomInside(int cx, int cz, int x, int z) {
		int qx = (x - 2) >> 2;
		int qz = (z - 2) >> 2;
		return Math.abs((qx >> 2) - cx) <= 1 && Math.abs(((qx + 1) >> 2) - cx) <= 1 && Math.abs((qz >> 2) - cz) <= 1
				&& Math.abs(((qz + 1) >> 2) - cz) <= 1;
	}
}
