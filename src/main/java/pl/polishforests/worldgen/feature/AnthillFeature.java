package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;
import pl.polishforests.block.AnthillBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.RootFlareBlock;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.ForestWorld;
import pl.polishforests.worldgen.feature.plan.AnthillPlan;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.StandMap;

/**
 * Feature {@code polishforests:anthill} (step FA8 of docs/04-pionowy-wycinek.md): the mounds of the red wood ant of
 * {@link AnthillPlan} in the decorated chunk, of {@link AnthillBlock} layers on the ground. It runs once per chunk (placed
 * feature {@code polishforests:ground/anthills}, the last feature of step 10, after the vegetation of step 9 and the freezing
 * of the top layer: the indices and seeds of the other features do not move in either world type, {@code BiomeDecoration},
 * {@code FeatureOrder}), in both world types and at both
 * scales: the habitat codes of the chunk and its neighbors ({@code VegetationColumns.region}, as the ecotones), the stand
 * map of the world ({@link ForestWorld#standMap}) and its local scale.
 *
 * <p>The sited tries of a candidate ({@link AnthillPlan#sitedTries}) are written in order until one fits (the first is
 * the mound of the plan, the one {@code find anthill} shows; a later one lies in the same chunk). A mound is written only
 * where it fits whole: every column of the footprint on dry ground of the chunk habitats
 * (no water, no structure piece, no top opened by a cave, no blocked column), the ground at most one block uneven and the
 * top block solid; in the footprint and the ring of one column around it (inside the chunk) no wood: no trunk, root flare,
 * underground root, log or sapling of the stand plan; and up to the height of the mound only air or plants that a tree
 * would replace ({@code #minecraft:replaceable_by_trees}, not leaves) or the snow of the freezing. The plants it replaces
 * are removed with their upper halves; where the column had snow and the top of the mound is a full block, the mound gets
 * a snow layer on top. A mound is written only into its own chunk, and the block is in
 * {@code #minecraft:replaceable_by_trees}, so a
 * tree or dead wood of a neighbor written later takes its place as it takes a plant's: the order of the chunks decides
 * at most the anthill columns.
 */
public final class AnthillFeature implements Feature {
	public static final AnthillFeature INSTANCE = new AnthillFeature();
	public static final MapCodec<AnthillFeature> CODEC = MapCodec.unit(() -> INSTANCE);
	/** The least distance of a mound column to a structure piece (blocks), as the dead wood. */
	static final int PIECE_GAP = 2;

	/** Why a sited mound was not written. */
	public enum Rejection {
		/** A column on water, by a structure piece, over a cave opening, blocked or without habitats. */
		TERRAIN,
		/** The ground more than a block uneven, or its top block not solid. */
		UNEVEN,
		/** Wood in the footprint or the ring around it. */
		WOOD,
		/** A block other than air or a plant in the way of the mound. */
		OBSTACLE
	}

	/** Diagnostics of the game tests (the dispatcher line of the slice test, the habitats census). */
	public static final LongAdder NANOS = new LongAdder();
	public static final LongAdder CHUNKS = new LongAdder();
	public static final LongAdder CANDIDATES = new LongAdder();
	public static final LongAdder SITED = new LongAdder();
	public static final LongAdder PLACED = new LongAdder();
	public static final Map<AnthillPlan.Reason, LongAdder> NOT_SITED = adders(AnthillPlan.Reason.class);
	public static final Map<Rejection, LongAdder> REJECTED = adders(Rejection.class);

	/**
	 * Observer of the written mounds (the habitats census): the corner of the footprint at the ground level + 1, the mound.
	 * Null outside the game tests; called on the generation threads.
	 */
	public interface Placements {
		void placed(BlockPos corner, AnthillPlan.Anthill anthill);
	}

	public static volatile @Nullable Placements PLACEMENTS;

	private AnthillFeature() {
	}

	@Override
	public MapCodec<AnthillFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		long start = System.nanoTime();
		try {
			return placeChunk(level, generator, origin.getX() >> 4, origin.getZ() >> 4);
		} finally {
			NANOS.add(System.nanoTime() - start);
		}
	}

	private static boolean placeChunk(WorldGenLevel level, ChunkGenerator generator, int chunkX, int chunkZ) {
		CHUNKS.increment();
		long seed = level.getSeed();
		List<AnthillPlan.Anthill> candidates = AnthillPlan.candidatesInChunk(seed, chunkX, chunkZ);
		if (candidates.isEmpty()) {
			return false;
		}
		ForestWorld world = ForestWorld.of(generator);
		if (world == null) {
			return false;
		}
		ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, generator, seed);
		if (habitats == null) {
			return false;
		}
		double k = world.localScale();
		Ecotone.Region region = VegetationColumns.region(level, chunk, habitats, k);
		StandMap stands = world.standMap(seed);
		int minX = chunkX << 4;
		int minZ = chunkZ << 4;
		AnthillPlan.Site site = new AnthillPlan.Site() {
			@Override
			public int code(int x, int z) {
				return region.at(x - minX, z - minZ);
			}

			@Override
			public double gapDepth(int x, int z) {
				return stands.gapDepth(x, z);
			}

			@Override
			public boolean lightStand(int x, int z) {
				return stands.ageClass(x, z) != AgeClass.POLE_STAGE;
			}
		};
		int i = AnthillPlan.cellOfChunk(chunkX);
		int j = AnthillPlan.cellOfChunk(chunkZ);
		boolean any = false;
		for (AnthillPlan.Anthill a : candidates) {
			CANDIDATES.increment();
			AnthillPlan.Reason reason = AnthillPlan.whyNot(seed, i, j, a, site, k);
			if (reason != null) {
				NOT_SITED.get(reason).increment();
				continue;
			}
			SITED.increment();
			// The sited tries in order: the first is the mound of the plan; where it does not fit (wood, ground), the next.
			Rejection rejection = null;
			for (AnthillPlan.Anthill p : AnthillPlan.sitedTries(seed, i, j, a, site, k)) {
				ModFeatures.AnthillPlans plans = ModFeatures.ANTHILL_PLANS;
				if (plans != null) {
					for (int dx = 0; dx < p.width(); dx++) {
						for (int dz = 0; dz < p.width(); dz++) {
							plans.planned(p.x() + dx, p.z() + dz);
						}
					}
				}
				rejection = write(level, habitats, p);
				if (rejection == null) {
					break;
				}
			}
			if (rejection != null) {
				REJECTED.get(rejection).increment();
				continue;
			}
			PLACED.increment();
			any = true;
		}
		return any;
	}

	/** Writes the mound, or returns why it does not fit (then nothing is written). */
	static @Nullable Rejection write(WorldGenLevel level, ChunkHabitats habitats, AnthillPlan.Anthill a) {
		int w = a.width();
		int[] tops = new int[w * w];
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				int x = a.x() + dx;
				int z = a.z() + dz;
				int i = ChunkHabitats.index(x & 15, z & 15);
				if (habitats.hasWater(i) || habitats.pieceDistance(i) <= PIECE_GAP || habitats.caveOpened(i) || habitats.blocked(i)) {
					return Rejection.TERRAIN;
				}
				int top = habitats.top()[i];
				BlockState ground = level.getBlockState(pos.set(x, top, z));
				if (!Block.isFaceFull(ground.getCollisionShape(level, pos), Direction.UP) || wood(ground)) {
					return Rejection.UNEVEN;
				}
				tops[dx * w + dz] = top;
				low = Math.min(low, top);
				high = Math.max(high, top);
			}
		}
		if (high - low > 1) {
			return Rejection.UNEVEN;
		}
		// No wood in the footprint or the ring of one column around it (inside the chunk), from the ground to the mound's
		// height above the highest ground.
		int chunkX = a.x() >> 4;
		int chunkZ = a.z() >> 4;
		int blocks = (a.height() + AnthillBlock.MAX_LAYERS - 1) / AnthillBlock.MAX_LAYERS;
		for (int x = a.x() - 1; x <= a.x() + w; x++) {
			for (int z = a.z() - 1; z <= a.z() + w; z++) {
				if (x >> 4 != chunkX || z >> 4 != chunkZ) {
					continue;
				}
				for (int y = low; y <= high + blocks + 1; y++) {
					if (wood(level.getBlockState(pos.set(x, y, z)))) {
						return Rejection.WOOD;
					}
				}
			}
		}
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				int n = (a.layers(dx, dz) + AnthillBlock.MAX_LAYERS - 1) / AnthillBlock.MAX_LAYERS;
				for (int y = 1; y <= n; y++) {
					BlockState s = level.getBlockState(pos.set(a.x() + dx, tops[dx * w + dz] + y, a.z() + dz));
					if (!s.isAir() && !plant(s)) {
						return Rejection.OBSTACLE;
					}
				}
			}
		}
		Block anthill = ModBlocks.anthill();
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				int x = a.x() + dx;
				int z = a.z() + dz;
				int y = tops[dx * w + dz] + 1;
				int layers = a.layers(dx, dz);
				boolean snow = level.getBlockState(pos.set(x, y, z)).is(Blocks.SNOW);
				while (layers > 0) {
					int here = Math.min(layers, AnthillBlock.MAX_LAYERS);
					level.setBlock(pos.set(x, y, z), anthill.defaultBlockState().setValue(AnthillBlock.LAYERS, here), Block.UPDATE_CLIENTS);
					layers -= here;
					y++;
				}
				// The upper halves of the replaced tall plants above the mound.
				int above = y;
				while (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof DoublePlantBlock) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					y++;
				}
				// The snow of the freezing on the mound, where a snow layer can stand (on a full mound block).
				if (snow && level.getBlockState(pos.set(x, above, z)).isAir()) {
					BlockState s = Blocks.SNOW.defaultBlockState();
					if (s.canSurvive(level, pos)) {
						level.setBlock(pos, s, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		Placements p = PLACEMENTS;
		if (p != null) {
			p.placed(new BlockPos(a.x(), low + 1, a.z()), a);
		}
		return null;
	}

	/** A plant the mound may replace: what a tree replaces, but no leaves (and no other mound), or a snow layer. */
	static boolean plant(BlockState s) {
		return s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.LEAVES) && !(s.getBlock() instanceof AnthillBlock)
				|| s.is(Blocks.SNOW);
	}

	/** Wood of the stand: trunks, root flares, the underground root, logs and saplings. */
	static boolean wood(BlockState s) {
		Block b = s.getBlock();
		return b instanceof TrunkBlock || b instanceof RootFlareBlock || b instanceof SaplingBlock || s.is(BlockTags.LOGS)
				|| s.is(BlockTags.SAPLINGS);
	}

	/** The counters since the start in one line (the slice and habitats game tests). */
	public static String stats() {
		long chunks = Math.max(1, CHUNKS.sum());
		StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT,
				"anthills since the start (step FA8): %d chunks, %.4f ms/chunk, %d candidates, %d sited, %d placed", CHUNKS.sum(),
				NANOS.sum() / 1e6 / chunks, CANDIDATES.sum(), SITED.sum(), PLACED.sum()));
		NOT_SITED.forEach((r, n) -> b.append(", not sited ").append(r.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(n.sum()));
		REJECTED.forEach((r, n) -> b.append(", rejected ").append(r.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(n.sum()));
		return b.toString();
	}

	private static <E extends Enum<E>> Map<E, LongAdder> adders(Class<E> type) {
		Map<E, LongAdder> m = new EnumMap<>(type);
		for (E e : type.getEnumConstants()) {
			m.put(e, new LongAdder());
		}
		return m;
	}
}
