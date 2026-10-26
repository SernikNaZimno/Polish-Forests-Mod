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
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.RootVoxels;

/**
 * Moss at the bases of the trunks and along the root flares (step V12 of docs/04-pionowy-wycinek.md, with the root
 * flares of step I1): mosses of the forest floor grow first on the trunk bases, the root flares and the raised ground
 * around them, where the litter does not lie. After the tree and its roots are written ({@link TreeStandFeature}), the
 * vanilla moss carpet goes on the ground cells around the trunk base (the 8 neighbors) and next to the written flare
 * blocks (their 4 neighbors), each with a chance of its forest ({@link #chances}), drawn from a hash of the world seed
 * and the cell (a pure function, no order of the chunks). A cell takes moss where it is air or a single-block plant of
 * the herb layer (it replaces that plant: the ground layer of the chunk may have run before or after the tree, and
 * either way the cell ends with moss), on the sturdy top of a soil block; never on a double plant, leaves, wood or water.
 */
public final class TreeMoss {
	/** Moss carpets written (diagnostics for the game tests). */
	public static final LongAdder MOSS = new LongAdder();
	/** Salt of the chances ({@code feature.ground.trunk_moss}, a new salt of step V12, ZW-2). */
	static final long SALT = 0x5D2A_91C7_3E64_B0F5L;

	private TreeMoss() {
	}

	/**
	 * Chances of a moss carpet on a cell beside the trunk base and beside a flare, by the habitat of the tree: in the
	 * broadleaved forests (the oak-hornbeam forest: mosses 5–20% of the columns, N9) the bases are the main place of the
	 * mosses (with these chances about 8–12% of the columns, the ground layer 1%); in the pine forests none: their floor
	 * is mossy anyway (N9 60–90%, the mossy states of the ground layer), and moss at the bases took the place of the
	 * bilberry (N8, 40–70%: 31–37% in the first measurement).
	 */
	static double[] chances(HabitatBiome biome) {
		return pine(biome) ? new double[] {0, 0} : new double[] {0.2, 0.3};
	}

	private static boolean pine(HabitatBiome biome) {
		return switch (biome) {
			case FRESH_PINE_FOREST, MIXED_PINE_FOREST, DRY_PINE_FOREST, MOIST_PINE_FOREST, COASTAL_PINE_FOREST, BOG_WOODLAND -> true;
			default -> false;
		};
	}

	/** Writes the moss around the trunk base and the written flares of a tree; returns the carpets written. */
	public static int write(WorldGenLevel level, RootVoxels v, TreeRoots.Written w, HabitatBiome biome, long worldSeed) {
		double[] p = chances(biome);
		if (p[0] <= 0 && p[1] <= 0) {
			return 0;
		}
		BlockState moss = Blocks.MOSS_CARPET.defaultBlockState();
		LongOpenHashSet seen = new LongOpenHashSet();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int written = 0;
		seen.add(BlockPos.asLong(v.x(), v.y(), v.z()));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && seen.add(BlockPos.asLong(v.x() + dx, v.y(), v.z() + dz))) {
					written += moss(level, v.x() + dx, v.y(), v.z() + dz, p[0], moss, worldSeed, at, below);
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
					written += moss(level, x, fy, z, p[1], moss, worldSeed, at, below);
				}
			}
		}
		MOSS.add(written);
		return written;
	}

	/**
	 * A moss carpet at (x, y, z) or, on a slope, one block lower or higher (the first of y, y − 1, y + 1 that is free over
	 * soil), with chance p; returns 1 if written.
	 */
	private static int moss(WorldGenLevel level, int x, int y, int z, double p, BlockState moss, long worldSeed,
			BlockPos.MutableBlockPos at, BlockPos.MutableBlockPos below) {
		if ((Noise.mix(worldSeed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL + SALT)) >>> 11) * 0x1.0p-53 >= p) {
			return 0;
		}
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
			if (here.is(Blocks.MOSS_CARPET)) {
				return 0;
			}
			level.setBlock(at, moss, 2);
			return 1;
		}
		return 0;
	}

	/** Air or a single-block plant that trees replace (not leaves, not a double plant, no fluid), or a moss carpet. */
	private static boolean free(BlockState s) {
		return s.isAir() || s.is(Blocks.MOSS_CARPET) || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.LEAVES)
				&& !(s.getBlock() instanceof DoublePlantBlock) && s.getFluidState().isEmpty();
	}
}
