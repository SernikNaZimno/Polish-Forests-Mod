package pl.polishforests.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import pl.polishforests.block.TrunkBlock;

/**
 * The wood of one tree of the tree planner as the world holds it (step G1 of docs/04-pionowy-wycinek.md, the tree-felling
 * mods): every thin trunk block ({@link TrunkBlock}, root flares included) knows its parent, the neighbor towards the
 * root, so the wood of one tree is exactly the connected part of the "parent" graph around a block. Neighboring trees of
 * the same species, shrubs on thin stems, dead wood and the underground roots ({@code tree_root}, not a log) are never in
 * it, however close they stand, while the four barks of the Scots pine, the root flares and the full vanilla logs of the
 * thickest trunk parts are.
 *
 * <p>Links between two face neighbors {@code a} and {@code b = a + d} that are both wood ({@code #minecraft:logs}):
 * <ul>
 * <li>a thin trunk is linked to the block its parent points to (in either direction of the pair);</li>
 * <li>two full logs (blocks with an axis, as the planner writes trunk parts of 8 px and more) of the same block are linked
 * when the axis of either of them runs along {@code d} (a log's parent and its children lie along its axis);</li>
 * <li>nothing else: never diagonally, never a full log to a thin trunk but by the trunk's parent.</li>
 * </ul>
 * A group of wood without any thin trunk (a vanilla tree, a log cabin) is not a tree of this mod: {@link #of} returns
 * {@code null} and the felling mods keep their own rules.
 */
public final class TreeWood {
	/** Largest tree followed, wood blocks; a larger group (a build of trunk blocks) is left to the felling mod's own rules. */
	public static final int MAX_BLOCKS = 4096;
	/** The wood of the game: the blocks of {@code #minecraft:logs} (the underground root block is not in it). */
	public static final TreeWood GAME = new TreeWood(s -> s.is(BlockTags.LOGS));

	/** A view of the blocks of a world. */
	@FunctionalInterface
	public interface Blocks {
		BlockState state(BlockPos pos);
	}

	/** The wood of one tree and what it is made of. */
	public record Tree(List<BlockPos> wood, int trunks, int logDrops) {
		public int size() {
			return wood.size();
		}
	}

	private final Predicate<BlockState> log;

	/** A wood rule; {@code log} tells the wood blocks (in the game {@code #minecraft:logs}). */
	public TreeWood(Predicate<BlockState> log) {
		this.log = log;
	}

	/** Whether a block is wood: a log, a thin trunk or a root flare. */
	public boolean wood(BlockState s) {
		return log.test(s) && (s.getBlock() instanceof TrunkBlock || s.hasProperty(RotatedPillarBlock.AXIS));
	}

	/** Whether a block is a thin trunk or root flare of this mod (wood with a parent). */
	public boolean trunk(BlockState s) {
		return s.getBlock() instanceof TrunkBlock && log.test(s);
	}

	/**
	 * Whether wood {@code a} and wood {@code b}, its neighbor on side {@code d}, belong to one tree (the rules of the class
	 * comment). Both must be {@link #wood}.
	 */
	public boolean linked(BlockState a, Direction d, BlockState b) {
		boolean ta = a.getBlock() instanceof TrunkBlock;
		boolean tb = b.getBlock() instanceof TrunkBlock;
		if (ta && a.getValue(TrunkBlock.PARENT) == d || tb && b.getValue(TrunkBlock.PARENT) == d.getOpposite()) {
			return true;
		}
		if (ta || tb) {
			return false;
		}
		return a.getBlock() == b.getBlock() && (a.getValue(RotatedPillarBlock.AXIS) == d.getAxis()
				|| b.getValue(RotatedPillarBlock.AXIS) == d.getAxis());
	}

	/**
	 * The tree of the wood block at {@code start}: its wood in the order found (the start first), or {@code null} when the
	 * block is not wood, the group has no thin trunk (not a tree of this mod) or more than {@link #MAX_BLOCKS} blocks.
	 */
	public @Nullable Tree of(Blocks level, BlockPos start) {
		BlockState first = level.state(start);
		if (!wood(first)) {
			return null;
		}
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		BlockPos s0 = start.immutable();
		seen.add(s0);
		open.add(s0);
		int trunks = 0;
		int drops = 0;
		while (!open.isEmpty()) {
			BlockPos p = open.poll();
			BlockState a = level.state(p);
			found.add(p);
			if (found.size() > MAX_BLOCKS) {
				return null;
			}
			if (a.getBlock() instanceof TrunkBlock) {
				trunks++;
			}
			if (dropsLog(a)) {
				drops++;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (seen.contains(q)) {
					continue;
				}
				BlockState b = level.state(q);
				if (wood(b) && linked(a, d, b)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		return trunks == 0 ? null : new Tree(List.copyOf(found), trunks, drops);
	}

	/** The side of {@code a} on which {@code b} lies, or {@code null} when they are not face neighbors. */
	public static @Nullable Direction side(BlockPos a, BlockPos b) {
		int dx = b.getX() - a.getX();
		int dy = b.getY() - a.getY();
		int dz = b.getZ() - a.getZ();
		if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
			return null;
		}
		for (Direction d : Direction.values()) {
			if (d.getStepX() == dx && d.getStepY() == dy && d.getStepZ() == dz) {
				return d;
			}
		}
		return null;
	}

	/**
	 * Whether a wood block gives a log when broken (the loot tables of {@link TrunkBlock}, datagen {@code ModBlockLoot}): a
	 * full log, a thin trunk or flare of {@value #LOG_RADIUS} px and more, and an upright segment (parent below or above)
	 * of {@value #STEM_LOG_RADIUS} px, the stem of a young tree (decisions G1-b and G1-e); thinner wood gives sticks.
	 */
	public static boolean dropsLog(BlockState s) {
		if (!(s.getBlock() instanceof TrunkBlock)) {
			return true;
		}
		int r = s.getValue(TrunkBlock.RADIUS);
		return r >= LOG_RADIUS || r == STEM_LOG_RADIUS && s.getValue(TrunkBlock.PARENT).getAxis() == Direction.Axis.Y;
	}

	/** Least radius of a thin trunk that gives a log, px (G1-b). */
	public static final int LOG_RADIUS = 3;
	/** Radius of an upright stem segment that gives a log, px (G1-e). */
	public static final int STEM_LOG_RADIUS = 2;

	/**
	 * The leaves next to the wood of a tree (distinct blocks on the six sides of its wood), counted up to {@code limit};
	 * -1 when {@code persistentAborts} and one of them is a player's (persistent) leaf.
	 */
	public static int leavesAround(Blocks level, List<BlockPos> wood, int limit, boolean persistentAborts) {
		Set<BlockPos> leaves = new HashSet<>();
		for (BlockPos p : wood) {
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				BlockState b = level.state(q);
				if (!(b.getBlock() instanceof LeavesBlock)) {
					continue;
				}
				if (persistentAborts && b.getValue(LeavesBlock.PERSISTENT)) {
					return -1;
				}
				leaves.add(q.immutable());
				if (leaves.size() >= limit) {
					return leaves.size();
				}
			}
		}
		return leaves.size();
	}
}
