package pl.polishforests.worldgen.feature;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.block.ClimberBlock;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The hop on the trunks of the floodplain forests (step F5 of docs/04-pionowy-wycinek.md, {@link GroundPlant#HOP}): after
 * a procedural tree is written ({@link TreeStandFeature}), a tree of the elm-ash forest, the willow-poplar forest, the osier
 * scrub or the riverside willows carries hop with the chance {@link #treeChance}; then each of the four sides of its trunk
 * has a strand of 0 … {@link #MAX_STRAND} blocks from the trunk base up the vertical stem cells ({@link TreeVoxels} wood of
 * parent {@code DOWN}, a full log or a thin trunk of radius at least {@link ClimberBlock#MIN_TRUNK_RADIUS} px). The draws
 * come from a hash of the world seed, the trunk column and the side with the salt {@link DecorationRandom#HOP_SALT} (a pure
 * function of the tree, as the hanging vines of step L1).
 *
 * <p>A hop block goes only on air (never in place of a leaf, a plant or wood), and it is in the tags the trees and the
 * plants replace (the column layers count it as open, {@link PlantLayerFeature}), so an object written before or after it
 * in any order of the chunks ends the same, except where a neighbor's object already stood in the cell (contact with an
 * object, decision L1-g).
 */
public final class TreeHop {
	/** Hop blocks written on trunks (diagnostics for the game tests). */
	public static final LongAdder HOP = new LongAdder();
	/** Longest strand on one side of a trunk (blocks from the trunk base). */
	public static final int MAX_STRAND = 9;

	private TreeHop() {
	}

	/**
	 * Chance that a tree carries hop, by its habitat: the willow-poplar forest and the riverside willows most (the hop is a
	 * character species of the Salicetum albae), the elm-ash forest less; none elsewhere.
	 */
	public static double treeChance(HabitatBiome biome, Zone zone) {
		if (zone == Zone.RIVERSIDE_WILLOWS) {
			return 0.4;
		}
		return switch (biome) {
			case WILLOW_POPLAR_FOREST, WILLOW_SCRUB -> 0.35;
			case ELM_ASH_FOREST -> 0.22;
			default -> 0;
		};
	}

	/** The world-uniform draw in [0, 1) of the tree at (x, z) with the salt offset {@code k}. */
	static double unit(long worldSeed, int x, int z, long k) {
		return (Noise.mix(worldSeed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL
				+ DecorationRandom.HOP_SALT + k)) >>> 11) * 0x1.0p-53;
	}

	/**
	 * Strand lengths of the four sides (north, east, south, west) of a tree at (x, z) with the chance {@code chance}, or
	 * null when the tree carries no hop (a pure function).
	 */
	public static int @org.jspecify.annotations.Nullable [] strands(long worldSeed, int x, int z, double chance) {
		if (chance <= 0 || unit(worldSeed, x, z, 0) >= chance) {
			return null;
		}
		int[] out = new int[4];
		for (int s = 0; s < 4; s++) {
			double u = unit(worldSeed, x, z, 1 + s);
			// About a third of the sides bare, the others with strands of 2 … MAX_STRAND blocks.
			out[s] = u < 0.35 ? 0 : 2 + (int) ((u - 0.35) / 0.65 * (MAX_STRAND - 1));
		}
		return out;
	}

	/** Writes the hop of a written tree; returns the number of hop blocks. */
	public static int write(WorldGenLevel level, TreeVoxels v, HabitatBiome biome, Zone zone) {
		int[] strands = strands(level.getSeed(), v.x(), v.z(), treeChance(biome, zone));
		if (strands == null) {
			return 0;
		}
		BlockState hop = ModBlocks.ground(GroundPlant.HOP).defaultBlockState();
		BlockPos.MutableBlockPos stem = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			int y = v.woodY(i);
			if (y < 0 || y >= MAX_STRAND || v.bark(i) == TreeVoxels.ROOT || v.parent(i) != Direction.DOWN.get3DDataValue()
					&& !(i == 0 && y == 0)) {
				continue;
			}
			if (v.radius(i) < ClimberBlock.MIN_TRUNK_RADIUS) {
				continue;
			}
			stem.set(v.x() + v.woodX(i), v.y() + y, v.z() + v.woodZ(i));
			if (!ClimberBlock.support(level.getBlockState(stem))) {
				continue;
			}
			for (int s = 0; s < 4; s++) {
				if (y >= strands[s]) {
					continue;
				}
				Direction side = Direction.from2DDataValue((s + 2) % 4);
				at.setWithOffset(stem, side);
				if (level.getBlockState(at).isAir()) {
					level.setBlock(at, hop.setValue(ClimberBlock.FACING, side.getOpposite()), 2);
					placed++;
				}
			}
		}
		HOP.add(placed);
		return placed;
	}

	/**
	 * Hop on a shrub of the placeholder trees ({@link HopDecorator}): each leaf or stem block with a free side draws from
	 * its own position whether a hop block hangs on that side ({@code chance}), only on air and only below the top of the
	 * shrub, so the hop wraps the shrub's sides.
	 */
	static void decorate(long worldSeed, Iterable<BlockPos> blocks, int topY, double chance, Predicate<BlockPos> air,
			BiConsumer<BlockPos, BlockState> setter) {
		BlockState hop = ModBlocks.ground(GroundPlant.HOP).defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (BlockPos b : blocks) {
			if (b.getY() >= topY) {
				continue;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (unit(worldSeed, b.getX(), b.getZ(), 16 + b.getY() * 4L + side.get2DDataValue()) >= chance) {
					continue;
				}
				at.setWithOffset(b, side);
				if (air.test(at)) {
					setter.accept(at.immutable(), hop.setValue(ClimberBlock.FACING, side.getOpposite()));
				}
			}
		}
	}
}
