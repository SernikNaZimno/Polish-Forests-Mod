package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.PolishForests;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.plan.ForestBlend;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.Material;

/**
 * Step TT3 (KT-1, docs/04-pionowy-wycinek.md "Projekt TT3"), part of {@code -Pgametest=tatra}: the belts of the Tatras in
 * the world of the gameplay scale and the tree stand deep in their forests.
 * <ul>
 * <li>the belt shares: the columns of the nearest massif on a {@value #STEP} m lattice by biome in the elevation bands
 * (the classifier of the world's generator; open water, the waterside zones and the valley floors left out, as in
 * {@code BeltProfileTatraTest}), at least 80% of the columns at 1300–1450 m in the montane spruce forest, 70% at
 * 1600–1750 m in the dwarf pine scrub, 70% at 1900–2200 m on the alpine grassland and 70% above 2350 m in the subnival
 * belt or on the bare rock; the first {@value #PROBES} columns of each band are generated and the biome of the level at
 * the model top ({@code VerticalScale.topBlockY}) must be the classifier's (at least 75% of them: a biome cell is 4 blocks
 * wide); the biome is read at the model top, not at the heightmap, because where a cave opens the top the highest ground
 * block is the cave floor, below the surface, and its biome is rightly a cave biome (such probes are counted apart);</li>
 * <li>KF-1: around the first lattice column of each Tatra forest (the Carpathian beech forest, the spruce forest, its
 * Abieti-Piceetum and Polysticho-Piceetum, from step TT4 its Pino cembrae-Piceetum and larch slopes, the dwarf pine)
 * deep in it (its share of the blend field at least 0.999),
 * 5 × 5 chunks: deep columns with a vanilla log trunk 0, planner trunks per chunk reported (at least one trunk in a
 * forest with {@value #MIN_DEEP} deep columns, except the creeping dwarf pine).</li>
 * <li>step C1 (CP-D.9): the top of the subnival belt around the first lattice column of the subnival rocks without a zone
 * at {@value #SUBNIVAL_FROM} m or higher on the silicate rocks and on the limestone (when the massif has one there), 5 × 5
 * chunks ({@link #subnival}): scree of the rock, turf in clumps (at most {@value #TURF_MAX} of the soil columns, most turf
 * columns with a turf neighbor), the bare rock of the steep faces apart; no herb off the turf and no plant over air.</li>
 * </ul>
 */
final class TatraBelts {
	/** Lattice step of the belt shares (m). */
	static final double STEP = 48;
	/** Generated columns per band. */
	static final int PROBES = 8;
	static final float DEEP_SHARE = 0.999F;
	static final int MIN_DEEP = 1024;
	/** Step C1: the least elevation (m) of a site of the subnival survey, and the largest share of turf on its soil columns. */
	static final double SUBNIVAL_FROM = 2_380;
	static final double TURF_MAX = 0.25;

	private TatraBelts() {
	}

	enum Band {
		UPPER_MONTANE(1_300, 1_450, 0.80), DWARF_PINE(1_600, 1_750, 0.70), ALPINE(1_900, 2_200, 0.70), SUBNIVAL(2_350, 9_000, 0.70);

		final double from;
		final double to;
		final double required;

		Band(double from, double to, double required) {
			this.from = from;
			this.to = to;
			this.required = required;
		}

		boolean counts(int code) {
			HabitatBiome b = Habitat.biome(code);
			return switch (this) {
				case UPPER_MONTANE -> b == HabitatBiome.MONTANE_SPRUCE_FOREST;
				case DWARF_PINE -> b == HabitatBiome.DWARF_PINE_SCRUB;
				case ALPINE -> b == HabitatBiome.ALPINE_GRASSLAND;
				case SUBNIVAL -> b == HabitatBiome.SUBNIVAL_ROCKS || Habitat.zone(code) == Zone.BARE_ROCK;
			};
		}

		static Band of(double h) {
			for (Band b : values()) {
				if (h >= b.from && h < b.to) {
					return b;
				}
			}
			return null;
		}
	}

	/** The Tatra forests of the KF-1 census: biome and association (null: any). */
	private static final Object[][] FORESTS = {{HabitatBiome.MONTANE_BEECH_FOREST, null},
			{HabitatBiome.MONTANE_SPRUCE_FOREST, Association.TYPICAL}, {HabitatBiome.MONTANE_SPRUCE_FOREST, Association.ABIETI_PICEETUM},
			{HabitatBiome.MONTANE_SPRUCE_FOREST, Association.POLYSTICHO_PICEETUM},
			// Step TT4: the spruce-stone pine forest and the sunny larch slopes of the granite.
			{HabitatBiome.MONTANE_SPRUCE_FOREST, Association.PINO_CEMBRAE_PICEETUM},
			{HabitatBiome.MONTANE_SPRUCE_FOREST, Association.LARCH_SLOPE},
			{HabitatBiome.DWARF_PINE_SCRUB, null}};

	/**
	 * The belt shares of the massif; returns the KF-1 sites (x, z, index into {@link #FORESTS}) and the probes of the level
	 * (step TT5: checked by the test in batches).
	 */
	static Probes shares(ServerLevel level, LandscapeModel.Tatra t, List<String> report, List<String> failures) {
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		long seed = level.getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		ForestBlend blend = gen.forestBlend(seed);
		List<HabitatBiome> plannerForests = List.of(ForestBlend.FORESTS);
		float[] blendShares = new float[ForestBlend.FORESTS.length + 1];
		Map<Band, long[]> counts = new EnumMap<>(Band.class);
		Map<Band, List<double[]>> probes = new EnumMap<>(Band.class);
		for (Band b : Band.values()) {
			counts.put(b, new long[2]);
			probes.put(b, new ArrayList<>());
		}
		List<int[]> sites = new ArrayList<>();
		List<int[]> subnival = new ArrayList<>();
		boolean[] subnivalFound = new boolean[2];
		boolean[] found = new boolean[FORESTS.length];
		double half = 1.2 * t.halfLength();
		// Step TT5: the lattice grows with the forms (160 m at realistic scale), so the census keeps its number of columns.
		double step = STEP * PolishForestsCommands.tatraFormScale(m);
		int n = (int) (2 * half / step);
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				double x = t.x() - half + (i + 0.5) * step;
				double z = t.z() - half + (j + 0.5) * step;
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.terrain().wTatra() < 0.9) {
					continue;
				}
				if (k.onValleyFloor(s, x, z)) {
					continue;
				}
				int code = k.classify(s, x, z);
				HabitatBiome b = Habitat.biome(code);
				Zone zone = Habitat.zone(code);
				if (b.isWater() || zone.ordinal() >= Zone.CHANNEL.ordinal() && zone.ordinal() <= Zone.SHORE_ALDERS.ordinal()) {
					continue;
				}
				// Step C1: a site of the subnival survey on the silicate rocks (0) and on the limestone and dolomite (1).
				Substrate rock = s.terrain().rock();
				if (b == HabitatBiome.SUBNIVAL_ROCKS && zone == Zone.NONE && s.surface() >= SUBNIVAL_FROM && rock != null
						&& rock.isTatraRock()) {
					int g = limestone(rock) ? 1 : 0;
					if (!subnivalFound[g]) {
						subnivalFound[g] = true;
						subnival.add(new int[] {(int) Math.floor(x), (int) Math.floor(z), g});
					}
				}
				for (int f = 0; f < FORESTS.length; f++) {
					if (!found[f] && b == FORESTS[f][0] && (FORESTS[f][1] == null || Habitat.association(code) == FORESTS[f][1])) {
						int bx = (int) Math.floor(x);
						int bz = (int) Math.floor(z);
						blend.field(bx >> 4, bz >> 4).at(bx, bz, blendShares);
						if (blendShares[plannerForests.indexOf(b)] >= DEEP_SHARE) {
							found[f] = true;
							sites.add(new int[] {bx, bz, f});
						}
					}
				}
				Band band = Band.of(s.surface());
				if (band == null) {
					continue;
				}
				long[] c = counts.get(band);
				c[0]++;
				c[1] += band.counts(code) ? 1 : 0;
				if (probes.get(band).size() < PROBES && (probes.get(band).isEmpty()
						|| i % 7 == 0 && j % 5 == 0)) {
					probes.get(band).add(new double[] {x, z, code});
				}
			}
		}
		StringBuilder line = new StringBuilder("TT3 belt shares of the massif (columns, share of the belt):");
		for (Band b : Band.values()) {
			long[] c = counts.get(b);
			double share = c[0] == 0 ? 0 : (double) c[1] / c[0];
			line.append(String.format(Locale.ROOT, " %s %d %.1f%%;", b, c[0], 100 * share));
			if (c[0] < 100 || share < b.required) {
				failures.add(String.format(Locale.ROOT, "TT3 belt %s: %.1f%% of %d columns (KT-1, at least %.0f%%)", b, 100 * share,
						c[0], 100 * b.required));
			}
		}
		report.add(line.toString());
		for (int f = 0; f < FORESTS.length; f++) {
			if (!found[f]) {
				report.add("TT3 KF-1: no deep column of " + label(f) + " in the massif");
			}
		}
		if (subnival.isEmpty()) {
			failures.add("C1 subnival survey: no column of the subnival rocks without a zone at " + SUBNIVAL_FROM + " m or higher");
		}
		Probes out = new Probes(sites);
		out.subnival.addAll(subnival);
		for (Band b : Band.values()) {
			out.points.addAll(probes.get(b));
		}
		return out;
	}

	/**
	 * Step TT5: the KF-1 sites and the probes of the level, which the test checks in batches ({@link #probe}, one server
	 * task each with ticks between them, so the chunks of the realistic Tatras unload in between), then {@link #report}.
	 */
	static final class Probes {
		final List<int[]> sites;
		/** Step C1: the sites of the subnival survey (x, z, 0 silicate / 1 limestone). */
		final List<int[]> subnival = new ArrayList<>();
		/** The probes: x, z, habitat code. */
		final List<double[]> points = new ArrayList<>();
		int probed = 0;
		int same = 0;
		int caveCut = 0;
		final StringBuilder off = new StringBuilder();

		Probes(List<int[]> sites) {
			this.sites = sites;
		}

		/** The biome of the level at the probes from {@code from} (inclusive) to {@code to} (exclusive). */
		void probe(ServerLevel level, int from, int to) {
			PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
			LandscapeModel m = gen.model(level.getSeed());
			for (double[] p : points.subList(from, to)) {
				int x = (int) Math.floor(p[0]);
				int z = (int) Math.floor(p[1]);
				level.getChunk(x >> 4, z >> 4);
				int top = gen.vertical().topBlockY(m.sample(x, z).surface());
				int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				caveCut += ground < top - 4 ? 1 : 0;
				String biome = level.getBiome(new BlockPos(x, top, z)).getRegisteredName();
				String expected = PolishForests.MOD_ID + ":" + Habitat.biome((int) p[2]).id();
				probed++;
				if (biome.equals(expected)) {
					same++;
				} else if (off.length() < 400) {
					off.append(String.format(Locale.ROOT, " (%d, %d) %s not %s;", x, z, biome, expected));
				}
			}
		}

		void report(List<String> report, List<String> failures) {
			report.add(String.format(Locale.ROOT, "TT3 biome of the level at the probes (at the model top; %d probes with the top cut "
					+ "by a cave): %d of %d as the classifier%s", caveCut, same, probed,
					off.isEmpty() ? "" : ":" + off));
			if (probed == 0 || same < 0.75 * probed) {
				failures.add("TT3 biome of the level at the probes: " + same + " of " + probed);
			}
		}
	}

	static String label(int f) {
		return ((HabitatBiome) FORESTS[f][0]).id() + (FORESTS[f][1] == null ? "" : "/" + ((Association) FORESTS[f][1]).name().toLowerCase(Locale.ROOT));
	}

	/** KF-1 around one site: 5 × 5 chunks. */
	static void kf1(ServerLevel level, int[] site, List<String> report, List<String> failures) {
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		long seed = level.getSeed();
		ForestBlend blend = gen.forestBlend(seed);
		List<HabitatBiome> plannerForests = List.of(ForestBlend.FORESTS);
		float[] shares = new float[ForestBlend.FORESTS.length + 1];
		HabitatBiome forest = (HabitatBiome) FORESTS[site[2]][0];
		int cx0 = (site[0] >> 4) - 2;
		int cz0 = (site[1] >> 4) - 2;
		for (int cx = cx0; cx < cx0 + 5; cx++) {
			for (int cz = cz0; cz < cz0 + 5; cz++) {
				level.getChunk(cx, cz);
			}
		}
		StructureReach reach = StructureReach.of(level, cx0, cz0, cx0 + 4, cz0 + 4);
		long deep = 0;
		long planner = 0;
		long vanilla = 0;
		List<String> at = new ArrayList<>();
		for (int cx = 0; cx < 5; cx++) {
			for (int cz = 0; cz < 5; cz++) {
				int[] codes = new int[256];
				ChunkSurface plan = gen.surface(new ChunkPos(cx0 + cx, cz0 + cz), level.getMinY(), level.getMaxY(), seed, codes,
						level.structureManager());
				ForestBlend.Field field = blend.field(cx0 + cx, cz0 + cz);
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int x = (cx0 + cx) * 16 + lx;
						int z = (cz0 + cz) * 16 + lz;
						int column = ChunkHabitats.index(lx, lz);
						if (plan.wet(column) || Habitat.biome(codes[column]) != forest) {
							continue;
						}
						int top = plan.top(column);
						field.at(x, z, shares);
						if (shares[plannerForests.indexOf(forest)] < DEEP_SHARE || reach.near(x, top + 1, z)) {
							continue;
						}
						deep++;
						if (!trunk(level, x, top, z)) {
							continue;
						}
						BlockState base = level.getBlockState(new BlockPos(x, top + 1, z));
						if (BuiltInRegistries.BLOCK.getKey(base.getBlock()).getNamespace().equals(PolishForests.MOD_ID)) {
							planner += TreeBases.tree(level, new BlockPos(x, top + 1, z)) ? 1 : 0;
						} else {
							vanilla++;
							if (at.size() < 4) {
								at.add(String.format(Locale.ROOT, "(%d, %d, %d) %s", x, top + 1, z, BuiltInRegistries.BLOCK.getKey(base.getBlock())));
							}
						}
					}
				}
			}
		}
		double perChunk = deep == 0 ? 0 : 256.0 * planner / deep;
		report.add(String.format(Locale.ROOT, "TT3 KF-1 %s around (%d, %d): %d deep columns, %.1f planner trunks per chunk, %d vanilla "
				+ "log trunks%s", label(site[2]), site[0], site[1], deep, perChunk, vanilla, at.isEmpty() ? "" : " " + at));
		if (vanilla > 0) {
			failures.add(String.format(Locale.ROOT, "TT3 KF-1 %s: %d vanilla log trunks deep in the forest", label(site[2]), vanilla));
		}
		// The dwarf pine is a creeping shrub, no trunk of three vertical logs: only its vanilla logs count.
		if (deep >= MIN_DEEP && planner == 0 && forest != HabitatBiome.DWARF_PINE_SCRUB) {
			failures.add(String.format(Locale.ROOT, "TT3 KF-1 %s: no planner trunk in %d deep columns", label(site[2]), deep));
		}
	}

	private static boolean limestone(Substrate rock) {
		return rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE;
	}

	/**
	 * Step C1 (CP-D.9): the top of the subnival belt around one site, 5 × 5 chunks: on the dry columns of the subnival rocks
	 * without a zone the top block of the world at the plan's top Y (scree of the rock, turf, the bare rock of the steep
	 * faces, air where a cave opens it, other: the soil of a neighbor taken across the soil ecotone); the turf at most
	 * {@value #TURF_MAX} of the soil columns (scree and turf) and gathered (most turf columns have a turf neighbor), the
	 * scree of the rock's kind, no herb off the turf and no plant over air.
	 */
	static void subnival(ServerLevel level, int[] site, List<String> report, List<String> failures) {
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		long seed = level.getSeed();
		LandscapeModel m = gen.model(seed);
		int cx0 = (site[0] >> 4) - 2;
		int cz0 = (site[1] >> 4) - 2;
		for (int cx = cx0; cx < cx0 + 5; cx++) {
			for (int cz = cz0; cz < cz0 + 5; cz++) {
				level.getChunk(cx, cz);
			}
		}
		java.util.Set<String> rocks = new java.util.HashSet<>();
		for (Substrate r : Substrate.values()) {
			Material rock = Material.tatraRock(r);
			if (rock != null) {
				rocks.add(rock.id());
			}
		}
		String granite = Material.GRANITE_SCREE.id();
		String limestone = Material.LIMESTONE_SCREE.id();
		int side = 80;
		boolean[] turf = new boolean[side * side];
		int scree = 0;
		int turfColumns = 0;
		int bare = 0;
		int cut = 0;
		int wrongScree = 0;
		int herbsOnTurf = 0;
		int herbsOffTurf = 0;
		int floating = 0;
		Map<String, Integer> other = new java.util.TreeMap<>();
		for (int cx = 0; cx < 5; cx++) {
			for (int cz = 0; cz < 5; cz++) {
				int[] codes = new int[256];
				ChunkSurface plan = gen.surface(new ChunkPos(cx0 + cx, cz0 + cz), level.getMinY(), level.getMaxY(), seed, codes,
						level.structureManager());
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int x = (cx0 + cx) * 16 + lx;
						int z = (cz0 + cz) * 16 + lz;
						int column = ChunkHabitats.index(lx, lz);
						int code = codes[column];
						if (plan.wet(column) || Habitat.biome(code) != HabitatBiome.SUBNIVAL_ROCKS || Habitat.zone(code) != Zone.NONE) {
							continue;
						}
						int top = plan.top(column);
						BlockState ground = level.getBlockState(new BlockPos(x, top, z));
						BlockState above = level.getBlockState(new BlockPos(x, top + 1, z));
						String id = BuiltInRegistries.BLOCK.getKey(ground.getBlock()).toString();
						boolean grass = ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
						if (above.getBlock() instanceof pl.polishforests.block.SeasonalHerbBlock) {
							herbsOnTurf += grass ? 1 : 0;
							herbsOffTurf += grass ? 0 : 1;
						}
						boolean plant = above.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock
								|| above.getBlock() instanceof net.minecraft.world.level.block.LeafLitterBlock;
						if (ground.isAir()) {
							cut++;
							floating += plant ? 1 : 0;
						} else if (grass) {
							turfColumns++;
							turf[(cx * 16 + lx) * side + cz * 16 + lz] = true;
						} else if (id.equals(granite) || id.equals(limestone)) {
							scree++;
							wrongScree += id.equals(limestone(m.sample(x, z).terrain().rock()) ? limestone : granite) ? 0 : 1;
						} else if (rocks.contains(id)) {
							bare++;
						} else {
							other.merge(id, 1, Integer::sum);
						}
					}
				}
			}
		}
		int gathered = 0;
		for (int i = 0; i < turf.length; i++) {
			if (!turf[i]) {
				continue;
			}
			int a = i / side;
			int b = i % side;
			gathered += a > 0 && turf[i - side] || a < side - 1 && turf[i + side] || b > 0 && turf[i - 1] || b < side - 1 && turf[i + 1]
					? 1 : 0;
		}
		int soil = scree + turfColumns;
		double share = soil == 0 ? 0 : (double) turfColumns / soil;
		double near = turfColumns == 0 ? 0 : (double) gathered / turfColumns;
		int all = soil + bare + cut + other.values().stream().mapToInt(Integer::intValue).sum();
		String label = site[2] == 1 ? "limestone" : "silicate";
		report.add(String.format(Locale.ROOT, "C1 subnival top (%s) around (%d, %d): %d columns of the subnival rocks without a zone: "
				+ "scree %d (%.1f%%), turf %d (%.1f%%; %.1f%% of the soil columns, %.1f%% of them with a turf neighbor), bare rock %d "
				+ "(%.1f%%), cave openings %d, other %s; scree of the wrong rock %d, herbs on the turf %d, herbs off the turf %d, "
				+ "plants over air %d", label, site[0], site[1], all, scree, pct(scree, all), turfColumns, pct(turfColumns, all),
				100 * share, 100 * near, bare, pct(bare, all), cut, other, wrongScree, herbsOnTurf, herbsOffTurf, floating));
		if (soil < 200) {
			report.add("C1 subnival top (" + label + "): only " + soil + " soil columns, no verdict on the turf");
		} else {
			if (share > TURF_MAX || turfColumns == 0) {
				failures.add(String.format(Locale.ROOT, "C1 subnival top (%s): turf on %.1f%% of the soil columns (0-%.0f%%)", label,
						100 * share, 100 * TURF_MAX));
			}
			if (near < 0.6) {
				failures.add(String.format(Locale.ROOT, "C1 subnival top (%s): only %.1f%% of the turf columns have a turf neighbor",
						label, 100 * near));
			}
			if (scree < soil / 2) {
				failures.add("C1 subnival top (" + label + "): scree on " + scree + " of " + soil + " soil columns");
			}
		}
		if (wrongScree > 0 || herbsOffTurf > 0 || floating > 0) {
			failures.add(String.format(Locale.ROOT, "C1 subnival top (%s): scree of the wrong rock %d, herbs off the turf %d, plants "
					+ "over air %d", label, wrongScree, herbsOffTurf, floating));
		}
	}

	private static double pct(int n, int all) {
		return all == 0 ? 0 : 100.0 * n / all;
	}

	/** A trunk of logs: three vertical log blocks above the top (as the census of {@code habitats}). */
	private static boolean trunk(ServerLevel level, int x, int top, int z) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockState s = level.getBlockState(new BlockPos(x, top + dy, z));
			if (!s.is(BlockTags.LOGS) || s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
					&& s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y) {
				return false;
			}
		}
		return true;
	}
}
