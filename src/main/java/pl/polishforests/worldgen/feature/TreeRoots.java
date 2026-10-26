package pl.polishforests.worldgen.feature;

import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.RootFlare;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.RootPlan;
import pl.polishforests.worldgen.tree.RootVoxels;

/**
 * Writes the roots of a tree (step I1 of docs/04-pionowy-wycinek.md; the plan: {@link RootPlan}) after the tree stand has
 * written the tree ({@link TreeStandFeature}).
 *
 * <p><b>Root flares</b>: the flare block of the species ({@link RootFlare}) with the planned radius and the parent towards
 * the trunk, from the trunk outwards, only on air or a block that trees replace (plants, leaves; as the tree's own wood)
 * over ground with a sturdy top (no flare over a pit); a block whose parent could not be written is left out with the
 * rest of its flare.
 *
 * <p><b>Roots in the soil</b> ({@code tree_root}): only in place of soil ({@code #minecraft:dirt}, {@code #minecraft:sand},
 * clay; not stone, gravel, water or what other features put there), each root cell on its own: the parents in the soil
 * do not matter (the model's reach into a parent hides in the ground). Over an open space below (a cave of another mod) a
 * root cell becomes rooted dirt with vanilla hanging roots under it (they need the sturdy face of a full block above).
 * The cells the planner marks exposed (a wall of the ground) and the rooted dirt around the roots turn dirt or coarse dirt
 * into rooted dirt; a protruding root goes only into air. Nothing is written above the ground but the flares and the
 * protruding roots, and the top ground block of every column stays as it is.
 */
public final class TreeRoots {
	/** Blocks written (diagnostics for the game tests and the measurements). */
	public static final LongAdder FLARES = new LongAdder();
	public static final LongAdder ROOTS = new LongAdder();
	public static final LongAdder DIRT = new LongAdder();
	public static final LongAdder HANGING = new LongAdder();
	/** Time in the root planner and the writing (ns), and the trees planned, for the measurements. */
	public static final LongAdder NANOS = new LongAdder();
	public static final LongAdder TREES = new LongAdder();
	/** Block update flags of the generation, as the trees. */
	private static final int FLAGS = 19;

	private TreeRoots() {
	}

	/**
	 * Which planned blocks were written: per flare whether it was, per root {@link #ROOT}, {@link #DIRT} or 0, the cells
	 * of rooted dirt around the roots and the hanging roots.
	 */
	public record Written(boolean[] flares, byte[] roots, int dirt, int hanging) {
		/** A root written as a root block. */
		public static final byte ROOT = 1;
		/** A root cell written as rooted dirt (exposed, or over an open space). */
		public static final byte DIRT = 2;

		public int flareCount() {
			int n = 0;
			for (boolean f : flares) {
				n += f ? 1 : 0;
			}
			return n;
		}

		public int rootCount() {
			int n = 0;
			for (byte r : roots) {
				n += r == ROOT ? 1 : 0;
			}
			return n;
		}
	}

	/** Writes the roots of a tree of a species; returns what was written. */
	public static Written write(WorldGenLevel level, RootVoxels v, Species species) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		RootFlare flare = RootFlare.of(species);
		boolean[] flares = new boolean[v.flareCount()];
		for (int i = 0; i < v.flareCount() && flare != null; i++) {
			int p = v.flareParentIndex(i);
			if (p >= 0 && !flares[p]) {
				continue;
			}
			pos.set(v.x() + v.flareX(i), v.y() + v.flareY(i), v.z() + v.flareZ(i));
			below.setWithOffset(pos, Direction.DOWN);
			BlockState at = level.getBlockState(pos);
			if (!(at.isAir() || at.is(BlockTags.REPLACEABLE_BY_TREES)) || !at.getFluidState().isEmpty()
					|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			level.setBlock(pos, ModBlocks.flare(flare).state(v.flareRadius(i), Direction.from3DDataValue(v.flareParent(i))), FLAGS);
			flares[i] = true;
		}
		byte[] roots = new byte[v.rootCount()];
		int dirt = 0;
		int hanging = 0;
		BlockState rooted = Blocks.ROOTED_DIRT.defaultBlockState();
		for (int i = 0; i < v.rootCount(); i++) {
			pos.set(v.x() + v.rootX(i), v.y() + v.rootY(i), v.z() + v.rootZ(i));
			BlockState at = level.getBlockState(pos);
			switch (v.rootKind(i)) {
				case RootVoxels.PROTRUDING -> {
					if (at.isAir()) {
						level.setBlock(pos, ModBlocks.treeRoot().state(v.rootRadius(i), Direction.from3DDataValue(v.rootParent(i))), FLAGS);
						roots[i] = Written.ROOT;
					}
				}
				case RootVoxels.EXPOSED -> {
					if (dirtLike(at)) {
						level.setBlock(pos, rooted, FLAGS);
						roots[i] = Written.DIRT;
						hanging += hang(level, pos, below);
					}
				}
				default -> {
					if (!soil(at)) {
						continue;
					}
					below.setWithOffset(pos, Direction.DOWN);
					if (level.getBlockState(below).isAir()) {
						// The ceiling of an open space: rooted dirt with hanging roots instead of a root hanging in the air.
						level.setBlock(pos, rooted, FLAGS);
						roots[i] = Written.DIRT;
						hanging += hang(level, pos, below);
					} else {
						level.setBlock(pos, ModBlocks.treeRoot().state(v.rootRadius(i), Direction.from3DDataValue(v.rootParent(i))), FLAGS);
						roots[i] = Written.ROOT;
					}
				}
			}
		}
		for (int i = 0; i < v.dirtCount(); i++) {
			pos.set(v.x() + v.dirtX(i), v.y() + v.dirtY(i), v.z() + v.dirtZ(i));
			if (dirtLike(level.getBlockState(pos))) {
				level.setBlock(pos, rooted, FLAGS);
				dirt++;
				hanging += hang(level, pos, below);
			}
		}
		Written w = new Written(flares, roots, dirt, hanging);
		FLARES.add(w.flareCount());
		ROOTS.add(w.rootCount());
		DIRT.add(dirt);
		HANGING.add(hanging);
		return w;
	}

	/** Vanilla hanging roots under rooted dirt at {@code pos} when the block below is air; returns 1 if placed. */
	private static int hang(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos below) {
		below.setWithOffset(pos, Direction.DOWN);
		if (!level.getBlockState(below).isAir()) {
			return 0;
		}
		level.setBlock(below, Blocks.HANGING_ROOTS.defaultBlockState(), FLAGS);
		return 1;
	}

	/** Soil a root may take the place of. */
	static boolean soil(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.CLAY);
	}

	/** Soil that turns into rooted dirt. */
	static boolean dirtLike(BlockState s) {
		return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT);
	}
}
