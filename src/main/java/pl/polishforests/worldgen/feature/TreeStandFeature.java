package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.feature.plan.SpeciesRamp;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree stand dispatcher {@code polishforests:tree_stand} (docs/03-m2-biomy.md §8.2): one placed feature in step 9 of
 * every biome, which plants the trees of the whole chunk from the chunk habitats ({@link ChunkHabitats}) and the tree
 * palette ({@link TreePalette}, JSON: rules by biome, zone, forest site type and association). The plan is a pure
 * function ({@link TreeStandPlan}) with the chunk's range ramp ({@link SpeciesRamp}, from O and P of the chunk) and the
 * gap noise; the dispatcher has its own random source from (world seed, chunk, layer salt), so features of other mods do
 * not shift it. Each tree is a placed feature {@code polishforests:tree/<species>} (or a variant) placed on the top
 * ground block, or on the bottom of shallow water for willows and alders.
 */
public final class TreeStandFeature implements Feature {
	public static final MapCodec<TreeStandFeature> CODEC = TreePalette.Rule.CODEC.listOf().fieldOf("rules")
			.xmap(rules -> new TreeStandFeature(new TreePalette(rules)), f -> f.palette.rules());

	/** Time spent in the tree stand (ns), number of chunks and of trees placed (§12.3: time of each dispatcher layer). */
	public static final java.util.concurrent.atomic.LongAdder NANOS = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).nanos;
	public static final java.util.concurrent.atomic.LongAdder CHUNKS = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).chunks;
	public static final java.util.concurrent.atomic.LongAdder TREES = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).placed;

	/**
	 * Least distance (blocks) of a trunk from the footprint of a building piece of a structure: more than the crown
	 * radius of the largest trees (3–4 blocks, branches of the fancy oak), so no crown grows into a house.
	 */
	public static final int BUILDING_GAP = 5;
	/** Least distance of a trunk from any structure piece, also a street (ChunkHabitats mask, {@code StructureGround}). */
	public static final int PIECE_GAP = 2;

	/** Salts of the noises of the tree stand (new fields, new salts). */
	private static final String RAMP_SALT = "feature.tree_stand.range_ramp";
	private static final String GAP_SALT = "feature.tree_stand.gaps";

	private final TreePalette palette;
	private final List<Holder<PlacedFeature>> trees = new ArrayList<>();
	private final TreeStandPlan.Palette plan;
	private volatile @Nullable Noises noises;

	/** The noises of a world seed. */
	private record Noises(long seed, Noise ramp, Noise gaps) {
	}

	public TreeStandFeature(TreePalette palette) {
		this.palette = palette;
		List<TreeStandPlan.Rule> rules = new ArrayList<>();
		for (TreePalette.Rule r : palette.rules()) {
			int n = r.trees().size();
			int[] index = new int[n];
			int[] weights = new int[n];
			int[] flags = new int[n];
			int[] alternatives = new int[n];
			int[] alternativeFlags = new int[n];
			int[] depth = new int[n];
			for (int i = 0; i < n; i++) {
				TreePalette.Entry e = r.trees().get(i);
				index[i] = treeIndex(e.tree());
				weights[i] = e.weight();
				flags[i] = e.rangeRamp() ? e.species().flag() : 0;
				alternatives[i] = e.alternative().map(a -> treeIndex(a.tree())).orElse(-1);
				alternativeFlags[i] = e.alternative().map(a -> a.species().flag()).orElse(0);
				depth[i] = e.maxWaterDepth();
			}
			rules.add(new TreeStandPlan.Rule(r.condition().match(), r.treesPerChunk(), index, weights, flags, alternatives,
					alternativeFlags, depth));
		}
		this.plan = new TreeStandPlan.Palette(rules);
	}

	/** Index of a tree in {@link #trees}, added on first use. */
	private int treeIndex(Holder<PlacedFeature> tree) {
		int t = trees.indexOf(tree);
		if (t < 0) {
			trees.add(tree);
			t = trees.size() - 1;
		}
		return t;
	}

	public TreePalette palette() {
		return palette;
	}

	/** Mean number of trees per chunk of the rule matching a habitat code (the census of the game test). */
	public float treesPerChunk(int code) {
		return plan.treesPerChunk(code);
	}

	@Override
	public MapCodec<TreeStandFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		long t0 = System.nanoTime();
		int placed = 0;
		try {
			placed = plant(level, generator, origin);
			return placed > 0;
		} finally {
			ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).add(System.nanoTime() - t0, placed);
		}
	}

	private Noises noises(long seed) {
		Noises n = noises;
		if (n == null || n.seed() != seed) {
			Noise root = new Noise(seed);
			n = new Noises(seed, root.derive(RAMP_SALT), root.derive(GAP_SALT));
			noises = n;
		}
		return n;
	}

	private int plant(WorldGenLevel level, ChunkGenerator generator, BlockPos origin) {
		ChunkAccess chunk = level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, generator, level.getSeed());
		if (habitats == null) {
			return 0;
		}
		ChunkPos pos = chunk.getPos();
		double k = generator instanceof PolandChunkGenerator poland ? poland.settings().scale().landscape().local() : 1;
		Noises n = noises(level.getSeed());
		float[] ramp = SpeciesRamp.factors(habitats.oceanicity(), habitats.mountainInfluence(), n.ramp(),
				pos.getMiddleBlockX(), pos.getMiddleBlockZ(), k);
		long seed = TreeStandPlan.seed(level.getSeed(), pos.x(), pos.z(), TreeStandPlan.SALT);
		// Ecotones (rule Z10, step S8b): each candidate takes the rule of a column across the border within the belt of the
		// pair, so composition and density change in a ramp.
		int[] codes = ChunkEcotones.of(level, chunk, habitats, k).trees();
		int[] planned = TreeStandPlan.of(codes, VegetationColumns.waterDepth(habitats), plan, level.getSeed(),
				pos.x(), pos.z(), new TreeStandPlan.Stand(ramp, n.gaps(), k));
		WorldgenRandom treeRandom = new WorldgenRandom(new XoroshiroRandomSource(seed));
		int placed = 0;
		for (int p : planned) {
			int column = p >>> 16;
			if (habitats.buildingDistance(column) <= BUILDING_GAP || habitats.pieceDistance(column) <= PIECE_GAP) {
				// A structure nearby (round 1 of the S8 review): no trunk on a street, no crown in a house.
				continue;
			}
			BlockPos at = new BlockPos(pos.getMinBlockX() + (column >> 4), habitats.top()[column] + 1,
					pos.getMinBlockZ() + (column & 15));
			if (trees.get(p & 0xFFFF).value().place(level, generator, treeRandom, at)) {
				placed++;
			}
		}
		return placed;
	}

	@Override
	public Stream<Holder<Feature>> getSubFeatures() {
		return trees.stream().flatMap(t -> t.value().getFeatures());
	}
}
