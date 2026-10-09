package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

/**
 * Feature type {@code polishforests:tree} of the placeholder trees and shrubs of M2 (docs/03-m2-biomy.md §8.4): the same
 * configuration as {@code minecraft:tree} (trunk and foliage placers, providers, size, decorators) and the same shape for
 * the same random source, placed faster. The vanilla tree updates the leaf distances with a search over sets and a voxel
 * shape of the whole bounding box, and then updates the shapes of every block on the box's surface; in a forest of 8–12
 * trees per chunk that was about 40% of the decoration step (S7 profile). This one computes the leaf distances with a
 * breadth-first search over a byte array of the box, through the tree's own leaves only, and sets only the leaves whose
 * distance changes; the blocks around the box are not updated (as for other features of the generation, the chunk's
 * own block updates follow when it loads). Logs a foliage placer sets (the poplar crown) are sources of the search like
 * the trunk. A leaf the search does not reach within 6 steps (its path to the own trunk runs around a neighbor's trunk,
 * or it overwrote a neighbor's leaf) takes the distance from its neighbors in the world, as {@code LeavesBlock} computes
 * it (a log 0, a leaf its distance), repeated up to 6 times; a leaf still at distance 7 would decay (no log within 6
 * steps through leaves), so it is removed before the decorators run and the tree leaves no decaying leaves (S7 review).
 * Vines of the decorators are marked for the post-processing of the chunk, which removes a vine whose support a later
 * tree overwrote. A tree with a root placer (mangrove) takes the vanilla path.
 *
 * @param tree the vanilla tree configuration
 */
public record FastTreeFeature(TreeFeature tree) implements Feature {
	public static final MapCodec<FastTreeFeature> CODEC = TreeFeature.CODEC.xmap(FastTreeFeature::new, FastTreeFeature::tree);
	private static final int FLAGS = 19;
	private static final byte NONE = Byte.MAX_VALUE;

	@Override
	public MapCodec<FastTreeFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (tree.rootPlacer().isPresent()) {
			return tree.place(level, generator, random, origin);
		}
		List<BlockPos> logs = new ArrayList<>();
		List<BlockPos> leaves = new ArrayList<>();
		LongOpenHashSet leafSet = new LongOpenHashSet();
		BiConsumer<BlockPos, BlockState> trunkSetter = (pos, state) -> {
			logs.add(pos.immutable());
			level.setBlock(pos, state, FLAGS);
		};
		FoliagePlacer.FoliageSetter foliageSetter = new FoliagePlacer.FoliageSetter() {
			@Override
			public void set(BlockPos pos, BlockState state) {
				boolean first = leafSet.add(pos.asLong());
				if (state.hasProperty(BlockStateProperties.DISTANCE)) {
					if (first) {
						leaves.add(pos.immutable());
					}
				} else if (LeavesBlock.getOptionalDistanceAt(state).orElse(-1) == 0) {
					// A log of the crown (the poplar's replaceLeavesWithLog, also over its own leaf): a source of the
					// leaf distances.
					logs.add(pos.immutable());
				}
				level.setBlock(pos, state, FLAGS);
			}

			@Override
			public boolean isSet(BlockPos pos) {
				return leafSet.contains(pos.asLong());
			}
		};
		if (!grow(level, random, origin, trunkSetter, foliageSetter) || logs.isEmpty() && leaves.isEmpty()) {
			return false;
		}
		List<BlockPos> kept = updateLeaves(level, logs, leaves);
		if (!tree.decorators().isEmpty()) {
			Set<BlockPos> decorations = new HashSet<>();
			BiConsumer<BlockPos, BlockState> decorationSetter = (pos, state) -> {
				decorations.add(pos.immutable());
				level.setBlock(pos, state, FLAGS);
				if (state.getBlock() instanceof VineBlock) {
					level.getChunk(pos).markPosForPostProcessing(pos);
				}
			};
			TreeDecorator.Context context = new TreeDecorator.Context(level, decorationSetter, random, new HashSet<>(logs),
					new HashSet<>(kept), Set.of());
			tree.decorators().forEach(d -> d.place(context));
		}
		return true;
	}

	/** The vanilla {@code doPlace} without roots: trunk height, free space, trunk and foliage. */
	private boolean grow(WorldGenLevel level, RandomSource random, BlockPos origin, BiConsumer<BlockPos, BlockState> trunkSetter,
			FoliagePlacer.FoliageSetter foliageSetter) {
		int treeHeight = tree.trunkPlacer().getTreeHeight(random);
		int foliageHeight = tree.foliagePlacer().foliageHeight(random, treeHeight, tree);
		int trunkHeight = treeHeight - foliageHeight;
		int leafRadius = tree.foliagePlacer().foliageRadius(random, trunkHeight);
		int minY = origin.getY();
		int maxY = origin.getY() + treeHeight + 1;
		if (minY < level.getMinY() + 1 || maxY > level.getMaxY() + 1) {
			return false;
		}
		OptionalInt minClippedHeight = tree.minimumSize().minClippedHeight();
		int clipped = maxFreeTreeHeight(level, treeHeight, origin);
		if (clipped < treeHeight && (minClippedHeight.isEmpty() || clipped < minClippedHeight.getAsInt())) {
			return false;
		}
		List<FoliagePlacer.FoliageAttachment> attachments = tree.trunkPlacer().placeTrunk(level, trunkSetter, random, clipped,
				origin, tree);
		for (FoliagePlacer.FoliageAttachment a : attachments) {
			tree.foliagePlacer().createFoliage(level, foliageSetter, random, tree, clipped, a, foliageHeight, leafRadius);
		}
		return true;
	}

	private int maxFreeTreeHeight(WorldGenLevel level, int maxTreeHeight, BlockPos treePos) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y <= maxTreeHeight + 1; y++) {
			int r = tree.minimumSize().getSizeAtHeight(maxTreeHeight, y);
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					pos.setWithOffset(treePos, x, y, z);
					if (!tree.trunkPlacer().isFree(level, pos) || !tree.ignoreVines() && TreeFeature.isVine(level, pos)) {
						return y - 2;
					}
				}
			}
		}
		return maxTreeHeight;
	}

	/**
	 * Leaf distances (the {@code distance} property, 1–7) by a breadth-first search from the logs through the tree's own
	 * leaves within the bounding box of the tree; a leaf keeps the smaller of its current and its new distance, as in the
	 * vanilla update. Leaves the search does not reach take the distance from their neighbors in the world
	 * ({@link #fromNeighbors}); those still at 7 are removed.
	 *
	 * @return the leaves that stay
	 */
	private static List<BlockPos> updateLeaves(WorldGenLevel level, List<BlockPos> logs, List<BlockPos> leaves) {
		if (leaves.isEmpty()) {
			return leaves;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (List<BlockPos> list : List.of(logs, leaves)) {
			for (BlockPos p : list) {
				minX = Math.min(minX, p.getX());
				minY = Math.min(minY, p.getY());
				minZ = Math.min(minZ, p.getZ());
				maxX = Math.max(maxX, p.getX());
				maxY = Math.max(maxY, p.getY());
				maxZ = Math.max(maxZ, p.getZ());
			}
		}
		int sx = maxX - minX + 1;
		int sy = maxY - minY + 1;
		int sz = maxZ - minZ + 1;
		byte[] dist = new byte[sx * sy * sz];
		java.util.Arrays.fill(dist, NONE);
		boolean[] leaf = new boolean[dist.length];
		for (BlockPos p : leaves) {
			leaf[((p.getX() - minX) * sy + (p.getY() - minY)) * sz + (p.getZ() - minZ)] = true;
		}
		int[] queue = new int[dist.length];
		int head = 0;
		int tail = 0;
		for (BlockPos p : logs) {
			int i = ((p.getX() - minX) * sy + (p.getY() - minY)) * sz + (p.getZ() - minZ);
			if (dist[i] != 0) {
				dist[i] = 0;
				leaf[i] = false;
				queue[tail++] = i;
			}
		}
		int stepX = sy * sz;
		while (head < tail) {
			int i = queue[head++];
			int d = dist[i] + 1;
			if (d >= LeavesBlock.DECAY_DISTANCE) {
				continue;
			}
			int x = i / stepX;
			int y = (i / sz) % sy;
			int z = i % sz;
			for (Direction dir : DIRECTIONS) {
				int nx = x + dir.getStepX();
				int ny = y + dir.getStepY();
				int nz = z + dir.getStepZ();
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
					continue;
				}
				int j = (nx * sy + ny) * sz + nz;
				if (leaf[j] && dist[j] > d) {
					dist[j] = (byte) d;
					queue[tail++] = j;
				}
			}
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		List<BlockPos> unreached = null;
		for (BlockPos p : leaves) {
			int i = ((p.getX() - minX) * sy + (p.getY() - minY)) * sz + (p.getZ() - minZ);
			if (dist[i] == NONE) {
				if (unreached == null) {
					unreached = new ArrayList<>();
				}
				unreached.add(p);
				continue;
			}
			at.set(p);
			BlockState state = level.getBlockState(at);
			if (state.hasProperty(BlockStateProperties.DISTANCE) && state.getValue(BlockStateProperties.DISTANCE) > dist[i]) {
				level.setBlock(at, state.setValue(BlockStateProperties.DISTANCE, (int) dist[i]), FLAGS);
			}
		}
		if (unreached == null) {
			return leaves;
		}
		List<BlockPos> removed = fromNeighbors(level, unreached);
		if (removed.isEmpty()) {
			return leaves;
		}
		LongOpenHashSet gone = new LongOpenHashSet();
		removed.forEach(p -> gone.add(p.asLong()));
		List<BlockPos> kept = new ArrayList<>(leaves.size() - removed.size());
		for (BlockPos p : leaves) {
			if (!gone.contains(p.asLong())) {
				kept.add(p);
			}
		}
		return kept;
	}

	/**
	 * Distances of the leaves the search did not reach, from their neighbors in the world as {@code LeavesBlock} computes
	 * them (a log 0, a leaf its distance, plus one), repeated while any distance falls (at most 6 rounds, the longest
	 * chain); the leaves still at distance 7 are removed (water stays where a leaf was waterlogged).
	 *
	 * @return the removed leaves
	 */
	private static List<BlockPos> fromNeighbors(WorldGenLevel level, List<BlockPos> open) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		List<BlockPos> left = new ArrayList<>(open);
		for (int round = 0; round < LeavesBlock.DECAY_DISTANCE - 1 && !left.isEmpty(); round++) {
			boolean changed = false;
			for (int k = left.size() - 1; k >= 0; k--) {
				at.set(left.get(k));
				BlockState state = level.getBlockState(at);
				if (!state.hasProperty(BlockStateProperties.DISTANCE)) {
					left.remove(k);
					continue;
				}
				int d = LeavesBlock.DECAY_DISTANCE;
				for (Direction dir : DIRECTIONS) {
					next.setWithOffset(at, dir);
					d = Math.min(d, LeavesBlock.getOptionalDistanceAt(level.getBlockState(next)).orElse(LeavesBlock.DECAY_DISTANCE) + 1);
				}
				if (d < state.getValue(BlockStateProperties.DISTANCE)) {
					level.setBlock(at, state.setValue(BlockStateProperties.DISTANCE, d), FLAGS);
					changed = true;
				}
				if (d < LeavesBlock.DECAY_DISTANCE) {
					left.remove(k);
				}
			}
			if (!changed) {
				break;
			}
		}
		List<BlockPos> removed = new ArrayList<>();
		for (BlockPos p : left) {
			BlockState state = level.getBlockState(p);
			if (state.hasProperty(BlockStateProperties.DISTANCE)
					&& state.getValue(BlockStateProperties.DISTANCE) >= LeavesBlock.DECAY_DISTANCE
					&& !state.getValue(BlockStateProperties.PERSISTENT)) {
				boolean water = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
				level.setBlock(p, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
				removed.add(p);
			}
		}
		return removed;
	}

	private static final Direction[] DIRECTIONS = Direction.values();
}
