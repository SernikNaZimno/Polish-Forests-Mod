package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
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
import pl.polishforests.worldgen.feature.plan.StandVacancies;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Tree stand dispatcher {@code polishforests:tree_stand} (docs/03-m2-biomy.md §8.2): one placed feature in step 9 of
 * every biome, which plants the trees of the whole chunk from the chunk habitats ({@link ChunkHabitats}) and the tree
 * palette ({@link TreePalette}, JSON: rules by biome, zone, forest site type and association). The plan is a pure
 * function ({@link TreeStandPlan}) with the chunk's range ramp ({@link SpeciesRamp}, from O and P of the chunk), the gap
 * noise and, from step V9 of docs/04-pionowy-wycinek.md, the stand map of the world ({@link ForestStandMap}) and the
 * crown territories ({@link CrownTerritories}); the dispatcher has its own random source from (world seed, chunk, layer
 * salt), so features of other mods do not shift it.
 *
 * <p><b>Placeholder trees</b> ({@code polishforests:tree_substitute/<species>}, decision PW-3): placed features placed on
 * the top ground block, or on the bottom of shallow water for willows and alders, at least {@value #BUILDING_GAP} blocks
 * from a building and {@value #PIECE_GAP} from any structure piece, as in M2.
 *
 * <p><b>Procedural trees</b> ({@code polishforests:tree/<species>}, a {@link ProceduralTreeFeature}; rule ZW-1): the
 * dispatcher calls the tree planner directly ({@link TreeShapePlan}) with the trunk's layer, the age class of its stand
 * (a young cohort in a canopy gap), its crown territory ({@link CrownTerritories.Session#territory}) without the columns
 * of the structure mask (no crown over a roof, a building's surroundings or a street), the columns of the other trunk
 * candidates, the stand map (split surfaces) and the ground of the chunks around (the split surfaces follow a slope), and
 * writes it with {@link ProceduralTreeFeature#writeCounted}. A trunk keeps a distance to a building of its crown radius
 * (at least {@value #MIN_CROWN_GAP} blocks), and {@value #PIECE_GAP} from any piece.
 *
 * <p><b>Vacancies</b> ({@link Vacancies}): a crown leans over the cells of the candidates that grow no tree. The exact
 * answer for the candidates of the chunk comes from its plan; for the candidates of the 8 neighbors the territory asks
 * for a sure vacancy: a draw at or above the highest chance of any rule at the candidate's stand, or the exact decision
 * of the neighbor's plan when the candidate's ecotone code can be proved from the codes of this chunk's region
 * ({@link Ecotone#provableTrees}, rule (i+) of decision V5-i), with the neighbor's water and range ramp.
 */
public final class TreeStandFeature implements Feature {
	public static final MapCodec<TreeStandFeature> CODEC = TreePalette.Rule.CODEC.listOf().fieldOf("rules")
			.xmap(rules -> new TreeStandFeature(new TreePalette(rules)), f -> f.palette.rules());

	/** Time spent in the tree stand (ns), number of chunks and of trees placed (§12.3: time of each dispatcher layer). */
	public static final java.util.concurrent.atomic.LongAdder NANOS = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).nanos;
	public static final java.util.concurrent.atomic.LongAdder CHUNKS = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).chunks;
	public static final java.util.concurrent.atomic.LongAdder TREES = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND).placed;

	/**
	 * Least distance (blocks) of a placeholder trunk from the footprint of a building piece of a structure: more than the
	 * crown radius of the largest placeholder trees (3–4 blocks, branches of the fancy oak), so no crown grows into a house.
	 */
	public static final int BUILDING_GAP = 5;
	/**
	 * Procedural trees of the plans not planted, by reason (step V9, diagnostics of the slice test): a structure or water
	 * at the trunk, the trunk base blocked or without ground, nothing written.
	 */
	public static final java.util.concurrent.atomic.LongAdder SKIPPED_STRUCTURE = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder SKIPPED_BASE = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder SKIPPED_WRITE = new java.util.concurrent.atomic.LongAdder();
	/** Canopy trees the planner leaves out as suppressed (a degenerate crown, {@link TreeShapePlan}; step V10). */
	public static final java.util.concurrent.atomic.LongAdder SKIPPED_SUPPRESSED = new java.util.concurrent.atomic.LongAdder();
	/** Time in the tree planner ({@link TreeShapePlan#plan}) and the planned trees, for the measurements of step V10. */
	public static final java.util.concurrent.atomic.LongAdder PLAN_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder PLANNED = new java.util.concurrent.atomic.LongAdder();
	/** Least distance of a trunk from any structure piece, also a street (ChunkHabitats mask, {@code StructureGround}). */
	public static final int PIECE_GAP = 2;
	/** Least distance of a procedural trunk from a building; more for a larger crown radius (step V9). */
	public static final int MIN_CROWN_GAP = 3;
	/**
	 * Columns of the structure mask a procedural crown leaves out: within this distance of a building piece (its footprint
	 * and the block around it) or on any structure piece (a street).
	 */
	public static final int CROWN_BUILDING_GAP = 1;

	/** Salts of the noises of the tree stand (new fields, new salts). */
	private static final String RAMP_SALT = "feature.tree_stand.range_ramp";
	private static final String GAP_SALT = "feature.tree_stand.gaps";

	private final TreePalette palette;
	private final List<Holder<PlacedFeature>> trees = new ArrayList<>();
	/** Species of each tree of {@link #trees} (for {@link ModFeatures#WOODY_PLACEMENTS}). */
	private final List<Species> species = new ArrayList<>();
	/** The procedural tree of each tree of {@link #trees}, or null for a placeholder (lazily, after the registries load). */
	private volatile @Nullable ProceduralTreeFeature[] procedural;
	private final TreeStandPlan.Palette plan;
	private volatile @Nullable Noises noises;

	/** The noises, the stand map and the crown territories of a world seed, scale and managed share. */
	private record Noises(long seed, double k, double managedShare, Noise ramp, Noise gaps, ForestStandMap stands,
			CrownTerritories crowns) {
	}

	/** Per thread: the crown territories session of the last world (memo of the candidate pattern and sites). */
	private static final ThreadLocal<SessionHolder> SESSIONS = new ThreadLocal<>();

	private record SessionHolder(CrownTerritories crowns, CrownTerritories.Session session, TreeStandPlan.Candidates candidates) {
	}

	public TreeStandFeature(TreePalette palette) {
		this.palette = palette;
		List<TreeStandPlan.Rule> rules = new ArrayList<>();
		for (TreePalette.Rule r : palette.rules()) {
			TreeStandPlan.Rule rule = rule(r.condition().match(), r.treesPerChunk(), r.trees());
			if (r.stand().isPresent()) {
				TreePalette.Stand st = r.stand().get();
				rule = rule.withLayers(new TreeStandPlan.Layers(
						st.managedCanopy().isEmpty() ? null : rule(r.condition().match(), r.treesPerChunk(), st.managedCanopy()),
						rule(r.condition().match(), r.treesPerChunk(), st.subcanopy()),
						rule(r.condition().match(), r.treesPerChunk(), st.regeneration()),
						rule(r.condition().match(), r.treesPerChunk(), st.gapRegeneration()),
						chances(st.managedChance()), chances(st.naturalChance()), st.gapChance()));
			}
			rules.add(rule);
		}
		this.plan = new TreeStandPlan.Palette(rules);
	}

	private TreeStandPlan.Rule rule(pl.polishforests.worldgen.feature.plan.HabitatMatch match, float perChunk,
			List<TreePalette.Entry> entries) {
		int n = entries.size();
		int[] index = new int[n];
		int[] weights = new int[n];
		int[] flags = new int[n];
		int[] alternatives = new int[n];
		int[] alternativeFlags = new int[n];
		int[] depth = new int[n];
		for (int i = 0; i < n; i++) {
			TreePalette.Entry e = entries.get(i);
			index[i] = treeIndex(e.tree(), e.species());
			weights[i] = e.weight();
			flags[i] = e.rangeRamp() ? e.species().flag() : 0;
			alternatives[i] = e.alternative().map(a -> treeIndex(a.tree(), a.species())).orElse(-1);
			alternativeFlags[i] = e.alternative().map(a -> a.species().flag()).orElse(0);
			depth[i] = e.maxWaterDepth();
		}
		return new TreeStandPlan.Rule(match, perChunk, index, weights, flags, alternatives, alternativeFlags, depth);
	}

	private static double[] chances(List<Float> values) {
		return new double[] {values.get(0), values.get(1), values.get(2)};
	}

	/** Index of a tree in {@link #trees}, added on first use with its species. */
	private int treeIndex(Holder<PlacedFeature> tree, Species of) {
		int t = trees.indexOf(tree);
		if (t < 0) {
			trees.add(tree);
			species.add(of);
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
		long[] counts = new long[3];
		try {
			plant(level, generator, origin, counts);
			return counts[0] > 0;
		} finally {
			ModFeatures.LayerStats stats = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND);
			stats.add(System.nanoTime() - t0, (int) counts[0]);
			stats.wood.add(counts[1]);
			stats.leaves.add(counts[2]);
		}
	}

	private Noises noises(long seed, double k, double managedShare) {
		Noises n = noises;
		if (n == null || n.seed() != seed || n.k() != k || n.managedShare() != managedShare) {
			Noise root = new Noise(seed);
			ForestStandMap stands = new ForestStandMap(seed, k, managedShare);
			n = new Noises(seed, k, managedShare, root.derive(RAMP_SALT), root.derive(GAP_SALT), stands,
					new CrownTerritories(seed, stands));
			noises = n;
		}
		return n;
	}

	private static SessionHolder session(Noises n) {
		SessionHolder h = SESSIONS.get();
		if (h == null || h.crowns() != n.crowns()) {
			h = new SessionHolder(n.crowns(), n.crowns().session(), new TreeStandPlan.Candidates(n.seed()));
			SESSIONS.set(h);
		}
		return h;
	}

	/** The procedural tree of each tree of {@link #trees} (null: a placeholder). */
	private ProceduralTreeFeature[] procedural() {
		ProceduralTreeFeature[] p = procedural;
		if (p == null) {
			p = new ProceduralTreeFeature[trees.size()];
			for (int i = 0; i < p.length; i++) {
				Feature f = trees.get(i).value().getFeatures().findFirst().map(Holder::value).orElse(null);
				p[i] = f instanceof ProceduralTreeFeature tree ? tree : null;
			}
			procedural = p;
		}
		return p;
	}

	/** Plants the trees of the chunk; adds the trees, wood blocks and leaves placed to {@code counts}. */
	private void plant(WorldGenLevel level, ChunkGenerator generator, BlockPos origin, long[] counts) {
		ChunkAccess chunk = level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, generator, level.getSeed());
		if (habitats == null) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		double k = 1;
		double managedShare = 0.85;
		if (generator instanceof PolandChunkGenerator poland) {
			k = poland.settings().scale().landscape().local();
			managedShare = poland.settings().managedForestShare();
		}
		Noises n = noises(level.getSeed(), k, managedShare);
		float[] ramp = SpeciesRamp.factors(habitats.oceanicity(), habitats.mountainInfluence(), n.ramp(),
				pos.getMiddleBlockX(), pos.getMiddleBlockZ(), k);
		long seed = TreeStandPlan.seed(level.getSeed(), pos.x(), pos.z(), TreeStandPlan.SALT);
		// Ecotones (rule Z10, step S8b): each candidate takes the rule of a column across the border within the belt of the
		// pair, so composition and density change in a ramp.
		ChunkEcotones ecotones = ChunkEcotones.of(level, chunk, habitats, k);
		int[] codes = ecotones.trees();
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(ramp, n.gaps(), k, n.stands(), n.crowns());
		int[] planned = TreeStandPlan.of(codes, VegetationColumns.waterDepth(habitats), plan, level.getSeed(), pos.x(), pos.z(),
				stand);
		if (planned.length == 0) {
			return;
		}
		ProceduralTreeFeature[] procedural = procedural();
		Around around = null;
		StandVacancies vacancies = null;
		SessionHolder session = null;
		WorldgenRandom treeRandom = new WorldgenRandom(new XoroshiroRandomSource(seed));
		// Open land around the trees of a forest edge (review of V9, round 1): the proved tree code of a column (exact, or
		// unknown and then not open), so a canopy tree there carries low branches on that side.
		Ecotone.Region region = ecotones.region();
		long worldSeed = level.getSeed();
		// Memo by column (1 open, 0 not; step O1: a fastutil map, a HashMap<Long, …> hashed the column to x ^ z).
		it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap openMemo = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
		TreeShapePlan.Open open = (wx, wz) -> {
			long key = (long) wx << 32 | wz & 0xFFFF_FFFFL;
			byte known = openMemo.getOrDefault(key, (byte) -1);
			if (known < 0) {
				known = (byte) (Ecotone.openLand(Ecotone.provableTrees(region, worldSeed, Ecotone.TREES_SALT, wx - pos.getMinBlockX(),
						wz - pos.getMinBlockZ())) ? 1 : 0);
				openMemo.put(key, known);
			}
			return known == 1;
		};
		long[] substitute = new long[2];
		for (int p : planned) {
			int column = TreeStandPlan.column(p);
			int tree = TreeStandPlan.tree(p);
			int x = pos.getMinBlockX() + (column >> 4);
			int z = pos.getMinBlockZ() + (column & 15);
			BlockPos at = new BlockPos(x, habitats.top()[column] + 1, z);
			ProceduralTreeFeature proc = procedural[tree];
			if (proc == null) {
				if (habitats.buildingDistance(column) <= BUILDING_GAP || habitats.pieceDistance(column) <= PIECE_GAP) {
					// A structure nearby (round 1 of the S8 review): no trunk on a street, no crown in a house.
					continue;
				}
				FastTreeFeature.COUNTS.set(substitute);
				try {
					if (trees.get(tree).value().place(level, generator, treeRandom, at)) {
						counts[0]++;
						ModFeatures.woodyPlaced(at, species.get(tree));
					}
				} finally {
					FastTreeFeature.COUNTS.remove();
				}
				continue;
			}
			if (habitats.pieceDistance(column) <= PIECE_GAP || habitats.hasWater(column)) {
				SKIPPED_STRUCTURE.increment();
				continue;
			}
			if (session == null) {
				session = session(n);
				around = new Around(level, pos, habitats);
				Around nb = around;
				double kk = k;
				vacancies = new StandVacancies(pos.x(), pos.z(), planned, ecotones.region(), plan, stand, new StandVacancies.Neighbors() {
					@Override
					public int waterDepth(int x, int z) {
						ChunkHabitats h = nb.at(x, z);
						if (h == null) {
							return -1;
						}
						int i = ChunkHabitats.index(x & 15, z & 15);
						return h.hasWater(i) ? h.water()[i] - h.top()[i] : 0;
					}

					@Override
					public float @Nullable [] ramp(int chunkX, int chunkZ) {
						ChunkHabitats h = nb.at(chunkX << 4, chunkZ << 4);
						return h == null ? null : SpeciesRamp.factors(h.oceanicity(), h.mountainInfluence(), n.ramp(), (chunkX << 4) + 8,
								(chunkZ << 4) + 8, kk);
					}
				}, session.candidates(), level.getSeed());
			}
			CrownTerritory territory = session.session().territory(x, z, vacancies);
			if (habitats.buildingDistance(column) <= Math.max(MIN_CROWN_GAP, (int) Math.ceil(territory.radius()))) {
				SKIPPED_STRUCTURE.increment();
				continue;
			}
			// The trunk base: air or a block trees replace (a plant, the leaves of a low crown), on ground with a sturdy top.
			BlockState base = level.getBlockState(at);
			if (!(base.isAir() || base.is(BlockTags.REPLACEABLE_BY_TREES)) || !ProceduralTreeFeature.ground(level, at.below())) {
				SKIPPED_BASE.increment();
				continue;
			}
			Around a = around;
			territory = territory.without(a::structure);
			CrownLayer layer = TreeStandPlan.layer(p);
			AgeClass standAge = n.stands().ageClass(x, z);
			// A young cohort in a canopy gap; elsewhere the trees have the age class of their stand.
			AgeClass age = layer == CrownLayer.REGENERATION && n.stands().canopyGap(x, z) ? AgeClass.POLE_STAGE : standAge;
			TreeRequest request = new TreeRequest(proc.species(), layer, age, level.getSeed(), x, at.getY(), z, territory,
					session.session().trunks(), n.stands(), a::top, open);
			long planStart = System.nanoTime();
			TreeVoxels voxels = TreeShapePlan.plan(request);
			PLAN_NANOS.add(System.nanoTime() - planStart);
			PLANNED.increment();
			if (voxels.woodCount() == 0) {
				SKIPPED_SUPPRESSED.increment();
				continue;
			}
			long written = ProceduralTreeFeature.writeCounted(level, voxels, proc.species(), layer, age);
			if (written >>> 32 > 0) {
				counts[0]++;
				counts[1] += written >>> 32;
				counts[2] += written & 0xFFFF_FFFFL;
				ModFeatures.woodyPlaced(at, proc.species());
			} else {
				SKIPPED_WRITE.increment();
			}
		}
		counts[1] += substitute[0];
		counts[2] += substitute[1];
	}

	@Override
	public Stream<Holder<Feature>> getSubFeatures() {
		return trees.stream().flatMap(t -> t.value().getFeatures());
	}

	/** The habitats of the chunk being decorated and of its 8 neighbors (null where missing), by world column. */
	private static final class Around {
		private final int chunkX;
		private final int chunkZ;
		private final ChunkHabitats[] habitats = new ChunkHabitats[9];

		Around(WorldGenLevel level, ChunkPos pos, ChunkHabitats own) {
			chunkX = pos.x();
			chunkZ = pos.z();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					habitats[(dx + 1) * 3 + dz + 1] = dx == 0 && dz == 0 ? own
							: level.getChunk(pos.x() + dx, pos.z() + dz).getAttached(ModFeatures.CHUNK_HABITATS);
				}
			}
		}

		/** The habitats of the chunk holding world column (x, z), or null (missing, or beyond the 8 neighbors). */
		@Nullable ChunkHabitats at(int x, int z) {
			int dx = (x >> 4) - chunkX;
			int dz = (z >> 4) - chunkZ;
			if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
				return null;
			}
			return habitats[(dx + 1) * 3 + dz + 1];
		}

		/** Y of the top ground block of world column (x, z), or {@link TreeShapePlan.Ground#UNKNOWN}. */
		int top(int x, int z) {
			ChunkHabitats h = at(x, z);
			return h == null ? TreeShapePlan.Ground.UNKNOWN : h.top()[ChunkHabitats.index(x & 15, z & 15)];
		}

		/** Whether world column (x, z) is in the structure mask of the crowns (by a building or on a piece). */
		boolean structure(int x, int z) {
			ChunkHabitats h = at(x, z);
			if (h == null) {
				return false;
			}
			int i = ChunkHabitats.index(x & 15, z & 15);
			return h.buildingDistance(i) <= CROWN_BUILDING_GAP || h.pieceDistance(i) == 0;
		}
	}
}
