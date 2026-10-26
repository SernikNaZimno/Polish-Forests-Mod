package pl.polishforests.worldgen.feature;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.RootVoxels;

/**
 * Moss at the bases of the trunks and along the root flares (step V12 of docs/04-pionowy-wycinek.md, with the root
 * flares of step I1): mosses of the forest floor grow first on the trunk bases, the root flares and the raised ground
 * around them, where the litter does not lie. After the tree and its roots are written ({@link TreeStandFeature}), the
 * own moss carpet ({@link ModBlocks#forestMossCarpet}, the feather moss of the pine floor; round 1 of the V12 review:
 * the vanilla moss carpet read as yellow-olive rugs) goes on some ground cells around the trunk base and next to the
 * written flare blocks, each with a chance of its forest ({@link #chances}) that falls off with the distance from the
 * trunk ({@link #falloff}: the cells beside the trunk walls more than its corners, so the moss forms an irregular
 * collar, not a 3 × 3 mat; at most {@link #PER_SIDE} cells on each side of the trunk), drawn from a hash of the world
 * seed and the cell (a pure function of the tree). A cell takes moss where it is air or a single-block plant of the
 * herb layer (it replaces that plant), on the sturdy top of a soil block; never on a double plant, leaves, wood or
 * water. Where a double plant stands, the result depends on the order of the chunks: a double plant written before the
 * tree stays, one whose ground layer runs after it finds the moss and is not placed (the determinism test measures this
 * within its threshold).
 *
 * <p>In the pine forests the floor is mossy anyway and moss at the bases took the place of the bilberry, so no carpets
 * are written there; instead, in the pine forests with the moss floor of step V12, the podzol under the trunk and under
 * the written flares, where no plant can stand, becomes the mossy podzol ({@link #mossyGround}; round 1 of the V12
 * review: the bare podzol read as an orange square at every trunk).
 */
public final class TreeMoss {
	/** Moss carpets written (diagnostics for the game tests). */
	public static final LongAdder MOSS = new LongAdder();
	/** Ground blocks turned into the mossy podzol (diagnostics for the game tests). */
	public static final LongAdder MOSSY_GROUND = new LongAdder();
	/** Salt of the chances ({@code feature.ground.trunk_moss}, a new salt of step V12, ZW-2). */
	static final long SALT = 0x5D2A_91C7_3E64_B0F5L;
	/** Most moss cells on each side (north, east, south, west) of a trunk. */
	static final int PER_SIDE = 2;

	private TreeMoss() {
	}

	/**
	 * Chances of a moss carpet on a cell beside a trunk wall and beside a flare, by the habitat of the tree: in the
	 * broadleaved forests (the oak-hornbeam forest: mosses 5–20% of the columns, N9) the bases are the main place of the
	 * mosses; in the pine forests none: their floor is mossy anyway (N9 60–90%, the mossy states of the ground layer), and
	 * moss at the bases took the place of the bilberry (N8, 40–70%: 31–37% in the first measurement).
	 */
	static double[] chances(HabitatBiome biome) {
		return pine(biome) ? new double[] {0, 0} : new double[] {0.32, 0.24};
	}

	/**
	 * Share of the chance of a cell at (dx, dz) from the trunk: beside a trunk wall the full chance, at a trunk corner (a
	 * diagonal cell) about a third, farther out along the flares falling off.
	 */
	static double falloff(int dx, int dz) {
		int d2 = dx * dx + dz * dz;
		return d2 <= 1 ? 1.0 : d2 == 2 ? 0.35 : d2 <= 4 ? 0.8 : d2 == 5 ? 0.5 : 0.3;
	}

	private static boolean pine(HabitatBiome biome) {
		return switch (biome) {
			case FRESH_PINE_FOREST, MIXED_PINE_FOREST, DRY_PINE_FOREST, MOIST_PINE_FOREST, COASTAL_PINE_FOREST, BOG_WOODLAND -> true;
			default -> false;
		};
	}

	/** The pine forests whose floor is the moss layer of step V12 (the mossy podzol under trunks and flares). */
	static boolean mossFloor(HabitatBiome biome) {
		return biome == HabitatBiome.FRESH_PINE_FOREST || biome == HabitatBiome.MIXED_PINE_FOREST;
	}

	/**
	 * The ground of a trunk or a flare cell at (x, y, z) (the block below it) in a pine forest with the moss floor: podzol
	 * becomes the mossy podzol; returns 1 if changed.
	 */
	public static int mossyGround(WorldGenLevel level, int x, int y, int z, HabitatBiome biome) {
		if (!mossFloor(biome)) {
			return 0;
		}
		BlockPos below = new BlockPos(x, y - 1, z);
		if (!level.getBlockState(below).is(Blocks.PODZOL)) {
			return 0;
		}
		level.setBlock(below, ModBlocks.mossyPodzol().defaultBlockState(), 2);
		MOSSY_GROUND.increment();
		return 1;
	}

	/** Writes the moss around the trunk base and the written flares of a tree; returns the carpets written. */
	public static int write(WorldGenLevel level, RootVoxels v, TreeRoots.Written w, HabitatBiome biome, long worldSeed) {
		if (mossFloor(biome)) {
			for (int i = 0; i < v.flareCount(); i++) {
				if (w.flares()[i]) {
					mossyGround(level, v.x() + v.flareX(i), v.y() + v.flareY(i), v.z() + v.flareZ(i), biome);
				}
			}
		}
		double[] p = chances(biome);
		if (p[0] <= 0 && p[1] <= 0) {
			return 0;
		}
		BlockState moss = ModBlocks.forestMossCarpet().defaultBlockState();
		LongOpenHashSet seen = new LongOpenHashSet();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int[] sides = new int[4];
		int written = 0;
		seen.add(BlockPos.asLong(v.x(), v.y(), v.z()));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && seen.add(BlockPos.asLong(v.x() + dx, v.y(), v.z() + dz))) {
					written += moss(level, v.x() + dx, v.y(), v.z() + dz, dx, dz, p[0], sides, moss, worldSeed, at, below);
				}
			}
		}
		for (int i = 0; i < v.flareCount(); i++) {
			if (!w.flares()[i]) {
				continue;
			}
			int fx = v.x() + v.flareX(i);
			int fy = v.y() + v.flareY(i);
			int fz = v.z() + v.flareZ(i);
			seen.add(BlockPos.asLong(fx, fy, fz));
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int x = fx + d.getStepX();
				int z = fz + d.getStepZ();
				if (seen.add(BlockPos.asLong(x, fy, z))) {
					written += moss(level, x, fy, z, x - v.x(), z - v.z(), p[1], sides, moss, worldSeed, at, below);
				}
			}
		}
		MOSS.add(written);
		return written;
	}

	/**
	 * A moss carpet at (x, y, z) or, on a slope, one block lower or higher (the first of y, y − 1, y + 1 that is free over
	 * soil), with chance p times the falloff at (dx, dz) from the trunk, while its side of the trunk has fewer than
	 * {@link #PER_SIDE} chosen cells (counted from the draws alone, so the choice does not depend on the blocks of the
	 * world); returns 1 if written.
	 */
	private static int moss(WorldGenLevel level, int x, int y, int z, int dx, int dz, double p, int[] sides, BlockState moss,
			long worldSeed, BlockPos.MutableBlockPos at, BlockPos.MutableBlockPos below) {
		if ((Noise.mix(worldSeed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL + SALT)) >>> 11) * 0x1.0p-53
				>= p * falloff(dx, dz)) {
			return 0;
		}
		int side = Math.abs(dx) >= Math.abs(dz) ? dx > 0 ? 1 : 3 : dz > 0 ? 2 : 0;
		if (sides[side] >= PER_SIDE) {
			return 0;
		}
		sides[side]++;
		for (int dy : new int[] {0, -1, 1}) {
			at.set(x, y + dy, z);
			BlockState here = level.getBlockState(at);
			if (!free(here)) {
				continue;
			}
			below.setWithOffset(at, Direction.DOWN);
			BlockState ground = level.getBlockState(below);
			if (!ground.is(BlockTags.SUPPORTS_VEGETATION) || !ground.isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			if (here.is(moss.getBlock())) {
				return 0;
			}
			level.setBlock(at, moss, 2);
			return 1;
		}
		return 0;
	}

	/** Air or a single-block plant that trees replace (not leaves, not a double plant, no fluid; the own moss carpet too). */
	private static boolean free(BlockState s) {
		return s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.LEAVES)
				&& !(s.getBlock() instanceof DoublePlantBlock) && s.getFluidState().isEmpty();
	}
}
