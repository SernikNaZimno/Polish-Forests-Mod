package pl.polishforests.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * Compatibility with the tree-felling mods (step G1 of docs/04-pionowy-wycinek.md): FallingTree and Tree Harvester. Both
 * are optional, never a dependency: the mixins of {@code polishforests.compat.mixins.json} are applied only when the mod
 * is loaded ({@link FellingMixinPlugin}) and only change how the mod finds the wood of a tree of this mod; everything
 * else (tools, limits, sneaking, drops through the blocks' loot tables) stays the mod's own and its configuration.
 *
 * <ul>
 * <li><b>FallingTree</b> walks from wood to wood through all 26 neighbors and, by default, only through blocks equal to the
 * one hit. On a tree of this mod that would join the trees of a species whose branches come within a block diagonally and
 * leave the other barks of the Scots pine, the root flares and the full logs of the thickest trunks standing. The hook
 * decides each step from a thin trunk by the parent graph ({@link TreeWood#linked}) and does not walk on through the
 * crown's leaves (they fall on their own, {@link pl.polishforests.block.SpeciesLeavesBlock}).</li>
 * <li><b>Tree Harvester</b> joins logs whose names start with the same word (in the player's language) through 3 × 3 × 3
 * neighbors and two blocks up, and clears the leaves of the block at the top in a box around the tree, also the leaves of
 * the neighbors' crowns that are not within 2 blocks of wood. The hooks give it the tree of the parent graph, its count
 * of log-giving blocks (the slowdown per log of the mod) and leave the leaves of this mod's species to their own fall.</li>
 * </ul>
 * Without a hit block of this mod's trees (vanilla trees, other mods) the mods work as without this mod.
 */
public final class FellingCompat {
	public static final String FALLING_TREE = "fallingtree";
	public static final String TREE_HARVESTER = "treeharvester";
	/** Leaves next to the wood a tree needs for Tree Harvester (as the mod's own rule: more than 8). */
	public static final int TREE_HARVESTER_LEAVES = 9;
	/** Steps of FallingTree's search decided by the parent graph (game tests check that the hooks run). */
	public static final LongAdder FALLING_TREE_STEPS = new LongAdder();
	/** Trees of this mod handed to Tree Harvester. */
	public static final LongAdder TREE_HARVESTER_TREES = new LongAdder();
	/** Leaf clearings of Tree Harvester left to the leaves' own fall. */
	public static final LongAdder TREE_HARVESTER_LEAF_SKIPS = new LongAdder();
	/** Tree parts FallingTree broke with their own position known to the loot ({@link #fellingPart}; step S1). */
	public static final LongAdder FALLING_TREE_PARTS = new LongAdder();
	/** The tree part a felling mod is breaking in this thread (step S1), or null. */
	private static final ThreadLocal<@Nullable BlockPos> FELLING_PART = new ThreadLocal<>();

	private FellingCompat() {
	}

	/** Logs which felling mods are present (at start-up). */
	public static void announce() {
		for (String mod : List.of(FALLING_TREE, TREE_HARVESTER)) {
			if (FabricLoader.getInstance().isModLoaded(mod)) {
				PolishForests.LOG.info("Polish Forests: {} found, trees of the tree planner are felled by their parent graph", mod);
			}
		}
	}

	/**
	 * FallingTree: whether the search goes on from the wood at {@code parentPos} to the block at {@code checkPos};
	 * {@code null} leaves the step to the mod's own rule (neither block is a thin trunk of this mod and the step is not
	 * one along two full logs of one block). The arguments are the raw objects of the mod's wrappers.
	 */
	public static @Nullable Boolean fallingTreeStep(Object parentState, Object parentPos, Object checkState, Object checkPos,
			boolean checkIsLeaf) {
		if (!(parentState instanceof BlockState a) || !(checkState instanceof BlockState b) || !(parentPos instanceof BlockPos pa)
				|| !(checkPos instanceof BlockPos pb)) {
			return null;
		}
		TreeWood w = TreeWood.GAME;
		boolean ours = w.trunk(a) || w.trunk(b);
		if (checkIsLeaf) {
			return ours ? Boolean.FALSE : null;
		}
		if (!w.wood(a) || !w.wood(b)) {
			return null;
		}
		Direction d = TreeWood.side(pa, pb);
		if (!ours) {
			// Two full logs: the mod's own rule (same block within 26 neighbors), plus the run of a log of this mod's
			// thickest trunk parts from a hit thin trunk block (the mod only follows the block hit).
			return d != null && w.linked(a, d, b) ? Boolean.TRUE : null;
		}
		FALLING_TREE_STEPS.increment();
		return d != null && w.linked(a, d, b);
	}

	/**
	 * Step S1: the position of the tree part a felling mod is breaking in this thread, or {@code null} outside such a
	 * break. FallingTree drops the loot of every part at the hit block when {@code spawnItemsAtBreakPoint} is on (the
	 * recommended configuration), so the loot origin is not the part's position; the base condition of the trunk tables
	 * ({@link pl.polishforests.block.TrunkBaseCondition}) reads the part's own position here.
	 */
	public static @Nullable BlockPos fellingPart() {
		return FELLING_PART.get();
	}

	/** FallingTree hook: the part at {@code rawPos} (a {@link BlockPos}) is broken now; {@code null} ends the part. */
	public static void fellingPart(@Nullable Object rawPos) {
		if (rawPos instanceof BlockPos p) {
			FELLING_PART.set(p.immutable());
			FALLING_TREE_PARTS.increment();
		} else {
			FELLING_PART.remove();
		}
	}

	/** Tree Harvester: the wood to break of the tree at {@code pos}, or {@code null} for the mod's own rules. */
	public static @Nullable List<BlockPos> treeHarvesterLogs(Level level, BlockPos pos, int max) {
		TreeWood.Tree tree = TreeWood.GAME.of(level::getBlockState, pos);
		if (tree == null) {
			return null;
		}
		TREE_HARVESTER_TREES.increment();
		List<BlockPos> wood = new ArrayList<>(tree.wood());
		return max > 0 && wood.size() > max ? new ArrayList<>(wood.subList(0, max)) : wood;
	}

	/**
	 * Tree Harvester: the log count of the tree at {@code pos} (the blocks that give a log, at least 1; the mod slows the
	 * cut by it), -1 when it is not a tree (fewer than {@link #TREE_HARVESTER_LEAVES} leaves next to its wood, as dead wood,
	 * or a persistent leaf when the mod ignores player-made trees), or {@code null} for the mod's own rules.
	 */
	public static @Nullable Integer treeHarvesterLogAmount(Level level, BlockPos pos, boolean ignorePlayerMadeTrees) {
		TreeWood.Tree tree = TreeWood.GAME.of(level::getBlockState, pos);
		if (tree == null) {
			return null;
		}
		int leaves = TreeWood.leavesAround(level::getBlockState, tree.wood(), TREE_HARVESTER_LEAVES, ignorePlayerMadeTrees);
		return leaves < TREE_HARVESTER_LEAVES ? -1 : Math.max(1, tree.logDrops());
	}

	/**
	 * Tree Harvester: the places where the mod may replant a sapling after felling the tree at {@code pos} (the base it
	 * found), or {@code null} for the mod's own rules. A tree of this mod has one stem, so only its base: the mod offers
	 * every block around the base whose name starts with the same word (a shrub stem, a neighbor's trunk, the tree's own
	 * root flares) and later sets saplings there without looking.
	 */
	public static @Nullable Set<BlockPos> treeHarvesterReplant(Level level, BlockPos pos) {
		TreeWood.Tree tree = TreeWood.GAME.of(level::getBlockState, pos);
		return tree == null ? null : Set.of(pos.immutable());
	}
}
