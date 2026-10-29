package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Placement filter {@code polishforests:lake_clearance} (step J2 of docs/04-pionowy-wycinek.md): the vanilla rule of the
 * underground lava lake (its origin at least {@value #DEPTH} blocks below the ground) checked over the whole footprint of
 * the lake instead of only at its origin. The vanilla lake ({@code LakeFeature}) spans x and z from {@value #FROM} to
 * {@value #TO} around the origin and opens air up to 2 blocks above it, so on a slope or a bank the lower ground beside the
 * origin broke open, under trees and logs placed before (the {@code caves} game test found a log over such a hole).
 *
 * <p>The ground is the planned top of each column ({@link ChunkHabitats#top}), not the {@code OCEAN_FLOOR_WG} heightmap:
 * that heightmap counts leaves and logs, and while a chunk is decorated it already holds the crowns of its own trees and
 * of the trees of neighbors decorated before, so it depended on the order of generation (J2 review round 1). A neighbor
 * whose habitats are gone (it reached its last stage) gets its surface plan computed again; the columns a cave entrance
 * opened keep their planned top (the lake there opens into the cave, not under the forest), so the result does not depend
 * on which neighbor still holds its attachment. Only outside the "Poland" generator the heightmap is used. The filter
 * never draws a random number, so the features after it do not change.
 */
public final class LakeClearanceFilter implements PlacementFilter {
	public static final LakeClearanceFilter INSTANCE = new LakeClearanceFilter();
	public static final MapCodec<LakeClearanceFilter> CODEC = MapCodec.unit(() -> INSTANCE);
	/** Least depth of the origin below the ground of every column of the footprint (the vanilla threshold). */
	static final int DEPTH = 5;
	/** Footprint of the lake around its origin (vanilla: grid cells 1 … 14 from the origin offset by -8). */
	static final int FROM = -7;
	static final int TO = 6;

	private LakeClearanceFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		int limit = origin.getY() + DEPTH;
		ChunkGenerator generator = context.generator();
		if (!(generator instanceof PolandChunkGenerator poland)) {
			return heightmapClear(context, origin, limit);
		}
		WorldGenLevel level = context.getLevel();
		long seed = level.getSeed();
		int cx0 = (origin.getX() + FROM) >> 4;
		int cx1 = (origin.getX() + TO) >> 4;
		int cz0 = (origin.getZ() + FROM) >> 4;
		int cz1 = (origin.getZ() + TO) >> 4;
		int centerX = origin.getX() >> 4;
		int centerZ = origin.getZ() >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				short[] tops = tops(level, poland, seed, cx, cz, cx == centerX && cz == centerZ);
				if (tops == null) {
					return false;
				}
				int x0 = Math.max(origin.getX() + FROM, cx << 4);
				int x1 = Math.min(origin.getX() + TO, (cx << 4) + 15);
				int z0 = Math.max(origin.getZ() + FROM, cz << 4);
				int z1 = Math.min(origin.getZ() + TO, (cz << 4) + 15);
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (tops[ChunkHabitats.index(x & 15, z & 15)] < limit) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * The planned ground tops of chunk (cx, cz) (index {@code x * 16 + z}): its habitats attachment, or, when it is gone
	 * (the neighbor reached its last stage), the surface plan computed again exactly as {@code fill()} computes it, with
	 * the ground of the structure pieces. Null when the chunk is outside the region, which the footprint never is (it
	 * reaches 7 blocks from an origin in the center chunk).
	 */
	private static short @Nullable [] tops(WorldGenLevel level, PolandChunkGenerator generator, long seed, int cx, int cz,
			boolean center) {
		if (!level.hasChunk(cx, cz)) {
			return null;
		}
		ChunkAccess chunk = level.getChunk(cx, cz);
		ChunkHabitats h = center ? ModFeatures.habitats(chunk, generator, seed) : chunk.getAttached(ModFeatures.CHUNK_HABITATS);
		if (h != null) {
			return h.top();
		}
		ChunkPos pos = new ChunkPos(cx, cz);
		short[] tops = new short[256];
		if (level instanceof WorldGenRegion region) {
			ChunkSurface surface = generator.surface(pos, chunk.getMinY(), chunk.getMaxY(), seed, null,
					level.getLevel().structureManager().forWorldGenRegion(region));
			for (int i = 0; i < 256; i++) {
				tops[i] = (short) surface.top(i);
			}
			return tops;
		}
		return generator.computeHabitats(pos, chunk.getMinY(), chunk.getMaxY(), seed).top();
	}

	/** The vanilla rule over the footprint against {@code OCEAN_FLOOR_WG}, for a generator other than the "Poland" one. */
	private static boolean heightmapClear(PlacementContext context, BlockPos origin, int limit) {
		for (int dx = FROM; dx <= TO; dx++) {
			for (int dz = FROM; dz <= TO; dz++) {
				if (context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz) < limit) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public MapCodec<LakeClearanceFilter> codec() {
		return CODEC;
	}
}
