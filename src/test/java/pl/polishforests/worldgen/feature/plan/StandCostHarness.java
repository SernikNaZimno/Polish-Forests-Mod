package pl.polishforests.worldgen.feature.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.surface.SurfaceBuilder;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.tree.RootPlan;
import pl.polishforests.worldgen.tree.RootVoxels;
import pl.polishforests.worldgen.tree.TreeForm;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeSize;
import pl.polishforests.worldgen.tree.TreeVoxels;
import pl.polishforests.worldgen.tree.Vacancies;

/**
 * The tree stand of the game without the game (step OP2 of docs/04-pionowy-wycinek.md): the planning part of
 * {@code TreeStandFeature.plant} (ecotone codes of the region with the world's wide blend, the stand plan, the vacancies,
 * the crown territories, the tree planner with the open land of the forest edge, the root planner) on the habitats of
 * the landscape model (codes, top ground block and water of the surface plan, as {@code PolandChunkGenerator.fill}
 * attaches them; no structures, no caves), chunk after chunk in rings around a site (the order in which the game
 * generates the chunks around a player). Nothing is written: the trunk base is not checked against blocks written by
 * earlier trees, and the lee side of the coast (the banner crowns of the wind belt) is unknown (no coast at the sites).
 *
 * <p>Shared by the hash of the plans ({@link StandPlanHashTest}: every voxel of every tree, its roots and its crown
 * territory, equal before and after each change of step OP2) and the cost test ({@link StandCostTest}: the time of the
 * tree stand and of its parts on the same chunks, in one JVM).
 */
final class StandCostHarness {
	static final long SEED = 20260927L;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();
	/** Salts of the noises of the tree stand ({@code TreeStandFeature}; read only, never changed). */
	private static final String RAMP_SALT = "feature.tree_stand.range_ramp";
	private static final String GAP_SALT = "feature.tree_stand.gaps";
	/** Salt of the stand map of the world type "Default + Polish Forests" ({@code VanillaForestWorld.STAND_SALT}). */
	private static final String VANILLA_STAND_SALT = "vanilla_forests.stands";
	/** Salt of the draw of a planner twin ({@code TreeStandFeature.TWIN_SALT}). */
	private static final long TWIN_SALT = 0x7B1C_4E2A_95D3L;

	/** A site of the performance game test (step F7, {@code ForestPerformance}, {@code slice_sites.txt}). */
	record Site(String name, PolandScale scale, int x, int z) {
		boolean failed() {
			return scale == PolandScale.REALISTIC ? name.equals("lowland_beech_1")
					: name.equals("oak_hornbeam_1") || name.equals("alder_carr_1");
		}
	}

	/**
	 * Four sites of each scale: the three below the norm in OP1 (realistic {@code lowland_beech_1}, gameplay
	 * {@code oak_hornbeam_1} and {@code alder_carr_1}), the same forests in the other scale, and the montane spruce forest.
	 */
	static final List<Site> SITES = List.of(
			new Site("lowland_beech_1", PolandScale.REALISTIC, -7793, -9133),
			new Site("oak_hornbeam_1", PolandScale.REALISTIC, 438, -730),
			new Site("alder_carr_1", PolandScale.REALISTIC, 2339, 29627),
			new Site("montane_spruce_1", PolandScale.REALISTIC, 75072, 73120),
			new Site("lowland_beech_1", PolandScale.GAMEPLAY, -2004, -3609),
			new Site("oak_hornbeam_1", PolandScale.GAMEPLAY, -1785, 886),
			new Site("alder_carr_1", PolandScale.GAMEPLAY, -5971, 1387),
			new Site("montane_spruce_1", PolandScale.GAMEPLAY, 3488, 22644));

	/** The world type of the stand map: "Poland" (the world seed) or "Default + Polish Forests" (a seed of its own). */
	enum StandSeed {
		POLAND, VANILLA_FORESTS
	}

	/** The landscape of a scale: model, classifier, surface builder and the world's wide blend (shared, warm). */
	static final class World {
		final PolandScale scale;
		final double k;
		final LandscapeModel model;
		final HabitatClassifier classifier;
		final SurfaceBuilder surface;
		final ForestBlend blend;
		private final ConcurrentHashMap<Long, ChunkHabitats> habitats = new ConcurrentHashMap<>();

		World(PolandScale scale) {
			this.scale = scale;
			this.k = scale.landscape().local();
			this.model = new LandscapeModel(SEED, scale.landscape(), 1.0, true);
			this.classifier = new HabitatClassifier(SEED, model, HabitatClassifier.Mode.NATURAL);
			this.surface = new SurfaceBuilder(SEED, scale.vertical(), true, LooseCover.THIN, false);
			this.blend = new ForestBlend(SEED, k, (x, z) -> classifier.classify(model.sample(x, z), x, z));
		}

		/** The habitats of chunk (cx, cz) as the generator attaches them (without structures and caves). */
		ChunkHabitats habitats(int cx, int cz) {
			return habitats.computeIfAbsent((long) cx << 32 | cz & 0xFFFF_FFFFL, key -> {
				int minX = cx << 4;
				int minZ = cz << 4;
				ColumnSample[] columns = new ColumnSample[256];
				int[] codes = new int[256];
				for (int i = 0; i < 256; i++) {
					columns[i] = model.sample(minX + (i >> 4), minZ + (i & 15));
					codes[i] = classifier.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
				}
				ChunkSurface s = surface.build(columns, codes, minX, minZ, PolandDimension.MIN_Y,
						PolandDimension.maxY(), PolandDimension.DEEP_ROCK_Y, model::sample);
				short[] tops = new short[256];
				short[] water = new short[256];
				for (int i = 0; i < 256; i++) {
					tops[i] = (short) s.top(i);
					water[i] = s.wet(i) ? (short) s.waterTop(i) : ChunkHabitats.NO_WATER;
				}
				ColumnSample center = columns[8 * 16 + 8];
				return new ChunkHabitats(codes, tops, water, (float) center.region().oceanicity(),
						(float) center.region().mountainInfluence(), ChunkHabitats.NO_STRUCTURES, ChunkHabitats.NO_CAVES);
			});
		}

		/** The habitats of the chunks of {@link #chunks} around a site and of the ring around them. */
		void prepare(Site site, int side) {
			int cx0 = site.x() >> 4;
			int cz0 = site.z() >> 4;
			int r = side / 2 + 1;
			List<long[]> all = new ArrayList<>();
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					all.add(new long[] {cx0 + a, cz0 + b});
				}
			}
			all.parallelStream().forEach(c -> habitats((int) c[0], (int) c[1]));
		}
	}

	/**
	 * The chunks of a square of {@code side} chunks around a site, ring after ring from the center (the order in which
	 * the game generates the chunks around a player; within a ring along its sides).
	 */
	static List<int[]> chunks(Site site, int side) {
		int cx0 = site.x() >> 4;
		int cz0 = site.z() >> 4;
		int r = side / 2;
		List<int[]> out = new ArrayList<>();
		out.add(new int[] {cx0, cz0});
		for (int ring = 1; ring <= r; ring++) {
			for (int a = -ring; a < ring; a++) {
				out.add(new int[] {cx0 + a, cz0 - ring});
			}
			for (int b = -ring; b < ring; b++) {
				out.add(new int[] {cx0 + ring, cz0 + b});
			}
			for (int a = ring; a > -ring; a--) {
				out.add(new int[] {cx0 + a, cz0 + ring});
			}
			for (int b = ring; b > -ring; b--) {
				out.add(new int[] {cx0 - ring, cz0 + b});
			}
		}
		return out;
	}

	/** Time (ns) of the tree stand and of its parts, summed over the chunks; counts of trees. */
	static final class Timers {
		/** Whether the parts inside the territories and the planner are timed (vacancies, open land). */
		final boolean fine;
		final LongAdder total = new LongAdder();
		/** The ecotone codes of the trees and the stand plan ({@code TreeStandPlan.of}). */
		final LongAdder standPlan = new LongAdder();
		final LongAdder territory = new LongAdder();
		/** {@link Vacancies#surelyVacant} asked by the territories (inside {@link #territory}; {@code StandVacancies.compute}). */
		final LongAdder vacancies = new LongAdder();
		final LongAdder shape = new LongAdder();
		/** The open land of the planner ({@code Ecotone.provableTrees}, inside {@link #shape}). */
		final LongAdder open = new LongAdder();
		final LongAdder roots = new LongAdder();
		final LongAdder chunks = new LongAdder();
		final LongAdder trees = new LongAdder();

		Timers(boolean fine) {
			this.fine = fine;
		}
	}

	/** The fields of a world for one run: a fresh stand map and fresh crown territories (cold memos, as in a new area). */
	static final class Fields {
		final World world;
		final Noise ramp;
		final Noise gaps;
		final ForestStandMap stands;
		final CrownTerritories crowns;

		Fields(World world, StandSeed seed) {
			this.world = world;
			Noise root = new Noise(SEED);
			this.ramp = root.derive(RAMP_SALT);
			this.gaps = root.derive(GAP_SALT);
			long standSeed = seed == StandSeed.POLAND ? SEED : root.derive(VANILLA_STAND_SALT).seed();
			ForestStandMap m = new ForestStandMap(standSeed, world.k, 0.85, world.blend.standProfiles());
			if (seed == StandSeed.POLAND && world.model.hasTatra()) {
				m.withPark(world.model::tatraPark);
			}
			this.stands = m;
			this.crowns = new CrownTerritories(SEED, m);
		}
	}

	/** Per thread and run: the crown territories session and the candidate pattern (as {@code TreeStandFeature.SESSIONS}). */
	static final class Session {
		final CrownTerritories.Session session;
		final TreeStandPlan.Candidates candidates;

		Session(Fields f) {
			this.session = f.crowns.session();
			this.candidates = new TreeStandPlan.Candidates(SEED);
		}
	}

	private static final int OPEN_WINDOW = 48;

	/**
	 * Plans the tree stand of chunk (cx, cz) as {@code TreeStandFeature.plant} does; returns the hash of the plans of the
	 * chunk (every procedural tree: its territory, voxels and roots, in the order of the plan).
	 */
	static long plant(Fields f, Session s, int cx, int cz, Timers t) {
		long start = System.nanoTime();
		long hashNanos = 0;
		World w = f.world;
		double k = w.k;
		ChunkHabitats habitats = w.habitats(cx, cz);
		float[] ramp = pl.polishforests.worldgen.feature.plan.SpeciesRamp.factors(habitats.oceanicity(), habitats.mountainInfluence(),
				f.ramp, (cx << 4) + 8, (cz << 4) + 8, k);
		int[][] around = new int[9][];
		ChunkHabitats[] near = new ChunkHabitats[9];
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				near[(dx + 1) * 3 + dz + 1] = w.habitats(cx + dx, cz + dz);
				around[(dx + 1) * 3 + dz + 1] = near[(dx + 1) * 3 + dz + 1].codes();
			}
		}
		Ecotone.Region region = Ecotone.Region.of(cx, cz, around, k).withBlend(w.blend);
		int[] codes = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(ramp, f.gaps, k, f.stands, f.crowns);
		int[] depth = new int[256];
		for (int i = 0; i < 256; i++) {
			depth[i] = habitats.hasWater(i) ? habitats.water()[i] - habitats.top()[i] : 0;
		}
		int[] planned = TreeStandPlan.of(codes, depth, GENERATED.palette, SEED, cx, cz, stand);
		long afterPlan = System.nanoTime();
		t.standPlan.add(afterPlan - start);
		long hash = 17;
		int minX = cx << 4;
		int minZ = cz << 4;
		TreeShapePlan.Ground ground = new TreeShapePlan.Ground() {
			@Override
			public int top(int x, int z) {
				ChunkHabitats h = at(near, cx, cz, x, z);
				return h == null ? TreeShapePlan.Ground.UNKNOWN : h.top()[ChunkHabitats.index(x & 15, z & 15)];
			}

			@Override
			public boolean water(int x, int z) {
				ChunkHabitats h = at(near, cx, cz, x, z);
				return h != null && h.hasWater(ChunkHabitats.index(x & 15, z & 15));
			}
		};
		pl.polishforests.worldgen.tree.TreeShapePlan.Ground rootGround = (x, z) -> {
			ChunkHabitats h = at(near, cx, cz, x, z);
			if (h == null) {
				return TreeShapePlan.Ground.UNKNOWN;
			}
			int i = ChunkHabitats.index(x & 15, z & 15);
			return h.hasWater(i) || h.pieceDistance(i) <= 2 || h.caveOpened(i) || h.blocked(i) ? TreeShapePlan.Ground.UNKNOWN : h.top()[i];
		};
		byte[] openWindow = new byte[OPEN_WINDOW * OPEN_WINDOW];
		Arrays.fill(openWindow, (byte) -1);
		it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap openMemo = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
		boolean fine = t.fine;
		TreeShapePlan.Open open = (wx, wz) -> {
			int ox = wx - minX + 16;
			int oz = wz - minZ + 16;
			boolean inWindow = ox >= 0 && ox < OPEN_WINDOW && oz >= 0 && oz < OPEN_WINDOW;
			long key = (long) wx << 32 | wz & 0xFFFF_FFFFL;
			byte known = inWindow ? openWindow[ox * OPEN_WINDOW + oz] : openMemo.getOrDefault(key, (byte) -1);
			if (known < 0) {
				long t0 = fine ? System.nanoTime() : 0;
				known = (byte) (Ecotone.openLand(Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, wx - minX, wz - minZ)) ? 1 : 0);
				if (fine) {
					t.open.add(System.nanoTime() - t0);
				}
				if (inWindow) {
					openWindow[ox * OPEN_WINDOW + oz] = known;
				} else {
					openMemo.put(key, known);
				}
			}
			return known == 1;
		};
		Vacancies vacancies = null;
		for (int p : planned) {
			int column = TreeStandPlan.column(p);
			int tree = TreeStandPlan.tree(p);
			int x = minX + (column >> 4);
			int z = minZ + (column & 15);
			boolean procedural = GENERATED.procedural(tree);
			if (!procedural) {
				int twin = twin(tree);
				if (twin >= 0 && TreeStandPlan.layer(p) == CrownLayer.CANOPY && !habitats.hasWater(column)
						&& region.blend() != null && twinDraw(region, column >> 4, column & 15, x, z)) {
					tree = twin;
					procedural = true;
				}
			}
			if (!procedural || habitats.pieceDistance(column) <= 2 || habitats.hasWater(column)) {
				continue;
			}
			if (vacancies == null) {
				StandVacancies v = new StandVacancies(cx, cz, planned, region, GENERATED.palette, stand, new StandVacancies.Neighbors() {
					@Override
					public int waterDepth(int x, int z) {
						ChunkHabitats h = at(near, cx, cz, x, z);
						if (h == null) {
							return -1;
						}
						int i = ChunkHabitats.index(x & 15, z & 15);
						return h.hasWater(i) ? h.water()[i] - h.top()[i] : 0;
					}

					@Override
					public float[] ramp(int chunkX, int chunkZ) {
						ChunkHabitats h = at(near, cx, cz, chunkX << 4, chunkZ << 4);
						return h == null ? null : pl.polishforests.worldgen.feature.plan.SpeciesRamp.factors(h.oceanicity(), h.mountainInfluence(),
								f.ramp, (chunkX << 4) + 8, (chunkZ << 4) + 8, k);
					}
				}, s.candidates, SEED);
				vacancies = !fine ? v : new Vacancies() {
					@Override
					public boolean vacant(int x, int z) {
						return v.vacant(x, z);
					}

					@Override
					public boolean surelyVacant(int x, int z) {
						long t0 = System.nanoTime();
						boolean out = v.surelyVacant(x, z);
						t.vacancies.add(System.nanoTime() - t0);
						return out;
					}
				};
			}
			long t0 = System.nanoTime();
			CrownTerritory territory = s.session.territory(x, z, vacancies);
			long t1 = System.nanoTime();
			t.territory.add(t1 - t0);
			territory = territory.without((wx, wz) -> false);
			Species species = GENERATED.species.get(tree);
			TreeForm.Kind kind = GENERATED.forms.get(tree);
			CrownLayer layer = TreeStandPlan.layer(p);
			AgeClass standAge = f.stands.ageClass(x, z);
			AgeClass age = layer == CrownLayer.REGENERATION && f.stands.canopyGap(x, z) ? AgeClass.POLE_STAGE : standAge;
			if (kind == TreeForm.Kind.SOLITARY && age == AgeClass.POLE_STAGE) {
				age = AgeClass.MATURE;
			}
			int y = habitats.top()[column] + 1;
			TreeRequest request = new TreeRequest(species, layer, age, SEED, x, y, z, territory, s.session.trunks(), f.stands, ground, open,
					TreeSize.ONE, TreeForm.of(kind));
			long t2 = System.nanoTime();
			TreeVoxels voxels = TreeShapePlan.plan(request);
			long t3 = System.nanoTime();
			t.shape.add(t3 - t2);
			t.trees.increment();
			RootVoxels roots = null;
			if (voxels.woodCount() > 0 && layer != CrownLayer.REGENERATION) {
				roots = RootPlan.plan(new RootPlan.Request(species, layer, age, SEED, x, y, z, voxels.radius(0), rootGround,
						s.session.trunks(), s.session.rootKinds(), Habitat.soil(codes[column]) == Soil.BOG_PEAT));
				t.roots.add(System.nanoTime() - t3);
			}
			long h0 = System.nanoTime();
			hash = 31 * hash + hash(territory);
			hash = 31 * hash + hash(voxels);
			hash = 31 * hash + (roots == null ? 0 : roots.hashCode());
			hashNanos += System.nanoTime() - h0;
		}
		t.total.add(System.nanoTime() - start - hashNanos);
		t.chunks.increment();
		return hash;
	}

	private static ChunkHabitats at(ChunkHabitats[] near, int cx, int cz, int x, int z) {
		int dx = (x >> 4) - cx;
		int dz = (z >> 4) - cz;
		if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
			return null;
		}
		return near[(dx + 1) * 3 + dz + 1];
	}

	/** The planner twin of a placeholder (as {@code TreeStandFeature.procedural}), or -1. */
	private static int twin(int tree) {
		String id = GENERATED.trees.get(tree);
		Species of = GENERATED.species.get(tree);
		if (!id.equals("polishforests:" + of.substitutePath())) {
			return -1;
		}
		for (int j = 0; j < GENERATED.trees.size(); j++) {
			if (GENERATED.procedural(j) && GENERATED.species.get(j) == of && GENERATED.forms.get(j) == TreeForm.Kind.NORMAL) {
				return j;
			}
		}
		return -1;
	}

	private static boolean twinDraw(Ecotone.Region region, int localX, int localZ, int worldX, int worldZ) {
		double chance = Math.min(1, 2 * region.blendTotal(localX, localZ));
		return chance > 0 && ForestBlend.draw(SEED, TWIN_SALT, worldX, worldZ, 1) < chance;
	}

	static long hash(TreeVoxels v) {
		long h = 1125899906842597L;
		h = 31 * h + v.x();
		h = 31 * h + v.y();
		h = 31 * h + v.z();
		h = 31 * h + Float.floatToIntBits(v.height());
		for (int i = 0; i < v.woodCount(); i++) {
			h = 31 * h + v.woodX(i);
			h = 31 * h + v.woodY(i);
			h = 31 * h + v.woodZ(i);
			h = 31 * h + v.radius(i);
			h = 31 * h + v.parent(i);
			h = 31 * h + v.bark(i);
		}
		for (int i = 0; i < v.leafCount(); i++) {
			h = 31 * h + v.leafX(i);
			h = 31 * h + v.leafY(i);
			h = 31 * h + v.leafZ(i);
			h = 31 * h + v.leafDistance(i);
		}
		return h;
	}

	static long hash(CrownTerritory t) {
		long h = 31L * t.x() + t.z();
		h = 31 * h + t.layer().ordinal();
		h = 31 * h + Float.floatToIntBits(t.radius());
		h = 31 * h + Float.floatToIntBits(t.reach());
		h = 31 * h + (t.edge() ? 1 : 0);
		int r = CrownTerritories.MAX_REACH;
		for (int dz = -r; dz <= r; dz++) {
			long row = 0;
			for (int dx = -r; dx <= r; dx++) {
				if (t.contains(t.x() + dx, t.z() + dz)) {
					row |= 1L << (dx + r);
				}
			}
			h = 31 * h + row;
		}
		return h;
	}

	/**
	 * Plans the chunks of every site of the scale in the order of the game, in {@code threads} threads (each with its
	 * own session; the chunks taken from one queue in that order); returns the hash of the plans of all chunks, combined
	 * in the order of the chunks (independent of the threads).
	 */
	static long run(World w, List<Site> sites, int side, StandSeed seed, int threads, Timers t) {
		Fields f = new Fields(w, seed);
		List<int[]> order = new ArrayList<>();
		for (Site site : sites) {
			if (site.scale() == w.scale) {
				order.addAll(chunks(site, side));
			}
		}
		long[] hashes = new long[order.size()];
		if (threads <= 1) {
			Session s = new Session(f);
			for (int i = 0; i < order.size(); i++) {
				hashes[i] = plant(f, s, order.get(i)[0], order.get(i)[1], t);
			}
		} else {
			AtomicInteger next = new AtomicInteger();
			List<Thread> pool = new ArrayList<>();
			for (int n = 0; n < threads; n++) {
				Thread th = new Thread(() -> {
					Session s = new Session(f);
					for (int i = next.getAndIncrement(); i < order.size(); i = next.getAndIncrement()) {
						hashes[i] = plant(f, s, order.get(i)[0], order.get(i)[1], t);
					}
				});
				pool.add(th);
				th.start();
			}
			for (Thread th : pool) {
				try {
					th.join();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
		}
		long h = 17;
		for (long x : hashes) {
			h = 31 * h + x;
		}
		return h;
	}

	private StandCostHarness() {
	}
}
