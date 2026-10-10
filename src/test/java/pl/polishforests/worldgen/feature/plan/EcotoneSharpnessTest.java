package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLongArray;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.BiomeJsonTest;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.Material;
import pl.polishforests.worldgen.surface.SoilBlend;
import pl.polishforests.worldgen.surface.SurfaceBuilder;

/**
 * Sharpness of the biome borders (rule Z10, step S8b, docs/03-m2-biomy.md §8.8): in random areas of 5 × 5 chunks with a
 * biome border (both scales, both modes, the classifier on real samples), the tree rule, the ground layer rule (from
 * the generated palettes) and the soil (the soil type, which sets the mixture of top blocks) of every column of the
 * inner 3 × 3 chunks, before the ecotones (the columns' own codes) and after them ({@link Ecotone#effective},
 * {@link Ecotone#edges}, {@link SoilBlend}, with the salts, patches and noises of the game). Measures:
 * <ul>
 * <li><b>coincidence:</b> of the pairs of neighboring columns across a border between two land biomes that mix, the
 * share where the tree rule, the ground layer rule and the soil all change at once (before: the palettes switch on
 * the same line); the same on the land side of the waterside zones of the land (round 1 of the S8b review);</li>
 * <li><b>sharp land borders:</b> the share of the borders between land habitats (biome or zone) that mix in no layer;</li>
 * <li><b>profile across straight borders:</b> on the rows (along x and along z) that cross a border which lies within a
 * block of the same offset in the {@value #ROWS} rows either side, with one biome over {@value #SIDE} columns on each
 * side, the share of the columns taking the other side's tree rule, ground layer rule and soil block by the distance
 * from the border, and the width of the transition (offsets with a share between 10% and 90%; the window is
 * 2 · {@value #SIDE} blocks);</li>
 * <li><b>meander:</b> the RMS shift of the trees' transition at these crossings (the columns of side A's tree rule in
 * the window, averaged over the 2 · {@value #ROWS} + 1 rows, minus {@value #SIDE}).</li>
 * </ul>
 * Asserted (round 1 of the S8b review): the coincidence at most half of what it was and at most 15%, at the waterside
 * zones at most half; the transition of the trees at least 10 blocks (realistic) / 8 (gameplay) and of the soil 6 / 4
 * (about half of the values measured in round 1); the shares of the trees and the soil in the columns either side of
 * the border between 0.1 and 0.9 (a step: 0 and 1), 8 blocks away below 0.4 and above 0.6 (a ramp, not a step); the RMS shift at least 1.2
 * blocks.
 */
class EcotoneSharpnessTest {
	private static final long SEED = 20260927L;
	private static final int AREAS = 70;
	private static final int SIDE = 16;
	private static final int ROWS = 6;

	/** Tree rules of the generated palette (conditions only). */
	private static final List<HabitatMatch> TREES = conditions("tree_stand", false);
	/** Land rules of the generated ground layer palette: condition, grounds mask and edges mask. */
	private static final List<Object[]> GROUND = groundRules();

	private static List<HabitatMatch> conditions(String palette, boolean landOnly) {
		List<HabitatMatch> out = new ArrayList<>();
		for (JsonElement e : rules(palette)) {
			out.add(match(e.getAsJsonObject()));
		}
		return out;
	}

	private static JsonArray rules(String palette) {
		return BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve("data/polishforests/worldgen/feature/" + palette + ".json"))
				.getAsJsonArray("rules");
	}

	private static HabitatMatch match(JsonObject r) {
		List<HabitatBiome> biomes = new ArrayList<>();
		for (String id : strings(r, "biomes")) {
			biomes.add(HabitatBiome.byId(id));
		}
		List<Zone> zones = new ArrayList<>();
		for (String id : strings(r, "zones")) {
			zones.add(Zone.valueOf(id.toUpperCase(Locale.ROOT)));
		}
		List<ForestSiteType> sites = new ArrayList<>();
		for (String id : strings(r, "site_types")) {
			sites.add(ForestSiteType.valueOf(id.toUpperCase(Locale.ROOT)));
		}
		List<Association> associations = new ArrayList<>();
		for (String id : strings(r, "associations")) {
			associations.add(Association.valueOf(id.toUpperCase(Locale.ROOT)));
		}
		List<Soil> soils = new ArrayList<>();
		for (String id : strings(r, "soils")) {
			soils.add(Soil.valueOf(id.toUpperCase(Locale.ROOT)));
		}
		return HabitatMatch.of(biomes, zones, sites, associations, soils);
	}

	private static List<String> strings(JsonObject r, String key) {
		List<String> out = new ArrayList<>();
		if (r.has(key)) {
			for (JsonElement e : r.getAsJsonArray(key)) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static List<Object[]> groundRules() {
		List<Object[]> out = new ArrayList<>();
		for (JsonElement e : rules("ground_layer")) {
			JsonObject r = e.getAsJsonObject();
			String medium = r.has("medium") ? r.get("medium").getAsString() : "land";
			if (!medium.equals("land")) {
				continue;
			}
			int grounds = 0;
			for (String g : strings(r, "grounds")) {
				grounds |= 1 << ColumnPlan.Ground.valueOf(g.toUpperCase(Locale.ROOT)).ordinal();
			}
			int edges = 0;
			for (String g : strings(r, "edges")) {
				edges |= 1 << Ecotone.Edge.valueOf(g.toUpperCase(Locale.ROOT)).ordinal();
			}
			out.add(new Object[] {match(r), grounds, edges});
		}
		return out;
	}

	private static int treeRule(int code) {
		for (int r = 0; r < TREES.size(); r++) {
			if (TREES.get(r).matches(code)) {
				return r;
			}
		}
		return -1;
	}

	private static int groundRule(int code, Material top, int edge) {
		int ground = ground(top).ordinal();
		for (int r = 0; r < GROUND.size(); r++) {
			Object[] g = GROUND.get(r);
			int grounds = (Integer) g[1];
			int edges = (Integer) g[2];
			if ((grounds == 0 || (grounds >>> ground & 1) != 0) && (edges == 0 || (edges >>> edge & 1) != 0)
					&& ((HabitatMatch) g[0]).matches(code)) {
				return r;
			}
		}
		return -1;
	}

	private static ColumnPlan.Ground ground(Material m) {
		return switch (m) {
			case GRASS_BLOCK -> ColumnPlan.Ground.GRASS;
			case DIRT, COARSE_DIRT, ROOTED_DIRT -> ColumnPlan.Ground.DIRT;
			case PODZOL -> ColumnPlan.Ground.PODZOL;
			case MUD -> ColumnPlan.Ground.MUD;
			case MOSS_BLOCK -> ColumnPlan.Ground.MOSS;
			case SAND -> ColumnPlan.Ground.SAND;
			case GRAVEL -> ColumnPlan.Ground.GRAVEL;
			case CLAY -> ColumnPlan.Ground.CLAY;
			default -> ColumnPlan.Ground.OTHER;
		};
	}

	/** Counters of one scale and mode. */
	private static final class Stats {
		// Coincidence at biome borders: border pairs, pairs where all three change (before, after); the same at the borders
		// of the waterside zones of the land with land columns of the same biome (indices 3-5).
		final AtomicLongArray pairs = new AtomicLongArray(6);
		// Land habitat borders (biome or zone differs, no water): all, and sharp in all layers before and after the fix of
		// round 1 of the S8b review (the zones did not mix).
		final AtomicLongArray land = new AtomicLongArray(2);
		// The sharp land borders by kind (zone or biome of each side).
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> sharpKinds =
				new java.util.concurrent.ConcurrentHashMap<>();
		// Profiles: per layer (trees, ground, soil), per offset: crossings counted and columns of the other side (after).
		final AtomicLongArray seen = new AtomicLongArray(3 * 2 * SIDE);
		final AtomicLongArray other = new AtomicLongArray(3 * 2 * SIDE);
		final AtomicLongArray otherBefore = new AtomicLongArray(3 * 2 * SIDE);
		// Meander: crossings of straight borders and the sum of the squared shifts of the trees' transition (blocks², ×100).
		final AtomicLongArray shift = new AtomicLongArray(2);
	}

	@Test
	void bordersAreTransitions() {
		StringBuilder report = new StringBuilder("Sharpness of the biome borders (rule Z10):\n");
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			LandscapeModel model = new LandscapeModel(SEED, scale.landscape(), 1.0);
			SurfaceBuilder builder = new SurfaceBuilder(SEED, scale.vertical(), true, false);
			double k = scale.landscape().local();
			for (HabitatClassifier.Mode mode : HabitatClassifier.Mode.values()) {
				HabitatClassifier classifier = new HabitatClassifier(SEED, scale.landscape(), mode);
				Stats stats = new Stats();
				List<int[]> areas = areas(model, classifier, scale);
				areas.parallelStream().forEach(a -> measure(model, classifier, builder, k, a[0], a[1], stats));
				boolean real = scale == PolandScale.REALISTIC;
				double before = (double) stats.pairs.get(1) / Math.max(1, stats.pairs.get(0));
				double after = (double) stats.pairs.get(2) / Math.max(1, stats.pairs.get(0));
				double zoneBefore = (double) stats.pairs.get(4) / Math.max(1, stats.pairs.get(3));
				double zoneAfter = (double) stats.pairs.get(5) / Math.max(1, stats.pairs.get(3));
				double sharp = (double) stats.land.get(1) / Math.max(1, stats.land.get(0));
				double meander = Math.sqrt(stats.shift.get(1) / 100.0 / Math.max(1, stats.shift.get(0)));
				report.append(String.format(Locale.ROOT, "%s %s: %d areas, %d border pairs; all three change at once: before "
						+ "%.1f%%, after %.1f%%; land side of the waterside zones (%d pairs): before %.1f%%, after %.1f%%; land "
						+ "habitat borders sharp in all layers: %.1f%% of %d; shift of the trees' transition at %d straight "
						+ "crossings: RMS %.2f blocks%n", scale.getSerializedName(), mode, areas.size(), stats.pairs.get(0),
						100 * before, 100 * after, stats.pairs.get(3), 100 * zoneBefore, 100 * zoneAfter, 100 * sharp,
						stats.land.get(0), stats.shift.get(0), meander));
				report.append("  most frequent sharp land borders:");
				stats.sharpKinds.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue().get(), x.getValue().get())).limit(6)
						.forEach(e -> report.append(String.format(Locale.ROOT, " %s %.1f%%;", e.getKey(),
								100.0 * e.getValue().get() / Math.max(1, stats.land.get(0)))));
				report.append(System.lineSeparator());
				String[] layers = {"trees", "ground layer", "soil"};
				int[] widths = new int[3];
				double[][] shares = new double[3][2 * SIDE];
				for (int l = 0; l < 3; l++) {
					StringBuilder row = new StringBuilder();
					StringBuilder rowBefore = new StringBuilder();
					int width = 0;
					int widthBefore = 0;
					for (int o = 0; o < 2 * SIDE; o++) {
						long n = stats.seen.get(l * 2 * SIDE + o);
						double share = (double) stats.other.get(l * 2 * SIDE + o) / Math.max(1, n);
						double shareBefore = (double) stats.otherBefore.get(l * 2 * SIDE + o) / Math.max(1, n);
						shares[l][o] = share;
						row.append(String.format(Locale.ROOT, " %d:%.2f", o - SIDE, share));
						rowBefore.append(String.format(Locale.ROOT, " %d:%.2f", o - SIDE, shareBefore));
						if (shareBefore > 0.1 && shareBefore < 0.9) {
							widthBefore++;
						}
						// Share of side B's value: on side A (o < SIDE) "other" is B, on side B it is B too.
						if (share > 0.1 && share < 0.9) {
							width++;
						}
					}
					widths[l] = width;
					report.append(String.format(Locale.ROOT, "  %s (%d crossings): transition before %d, after %s blocks; share "
							+ "of side B by offset before:%s%n    after:%s%n", layers[l], stats.seen.get(l * 2 * SIDE), widthBefore,
							width >= 2 * SIDE ? "≥ " + width + " (window)" : String.valueOf(width), rowBefore, row));
				}
				// Limits: about half of the measured values of round 1 of the S8b review (widths) and twice the coincidence.
				int trees = real ? 10 : 8;
				int soil = real ? 6 : 4;
				if (stats.pairs.get(0) < 200 || stats.seen.get(0) < 20) {
					failures.add(scale.getSerializedName() + " " + mode + ": only " + stats.pairs.get(0) + " border pairs, "
							+ stats.seen.get(0) + " straight crossings");
				}
				if (after > 0.5 * before || after > 0.15) {
					failures.add(String.format(Locale.ROOT, "%s %s: coincidence %.1f%% after (%.1f%% before)",
							scale.getSerializedName(), mode, 100 * after, 100 * before));
				}
				if (stats.pairs.get(3) >= 100 && zoneAfter > 0.5 * zoneBefore) {
					failures.add(String.format(Locale.ROOT, "%s %s: coincidence at the waterside zones %.1f%% after (%.1f%% before)",
							scale.getSerializedName(), mode, 100 * zoneAfter, 100 * zoneBefore));
				}
				if (widths[0] < trees || widths[2] < soil) {
					failures.add(String.format(Locale.ROOT, "%s %s: transition trees %d, soil %d blocks (at least %d, %d)",
							scale.getSerializedName(), mode, widths[0], widths[2], trees, soil));
				}
				for (int l = 0; l < 3; l += 2) {
					// A ramp, not a step: in the columns either side of the border the share lies between 0.1 and 0.9, and 8
					// blocks away it is still on its side's half.
					double nearA = shares[l][SIDE - 1];
					double nearB = shares[l][SIDE];
					double farA = shares[l][SIDE - 8];
					double farB = shares[l][SIDE + 7];
					if (nearA < 0.1 || nearA > 0.9 || nearB < 0.1 || nearB > 0.9 || farA > 0.4 || farB < 0.6) {
						failures.add(String.format(Locale.ROOT, "%s %s: %s share at −8, −1, 0, +7 blocks %.2f / %.2f / %.2f / %.2f",
								scale.getSerializedName(), mode, layers[l], farA, nearA, nearB, farB));
					}
				}
				if (meander < 1.2) {
					failures.add(String.format(Locale.ROOT, "%s %s: the transition does not meander at straight borders (RMS %.2f)",
							scale.getSerializedName(), mode, meander));
				}
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "\n" + report);
	}

	/** Centers (chunk coordinates) of areas of 5 × 5 chunks with at least two land biomes at a few points. */
	private static List<int[]> areas(LandscapeModel model, HabitatClassifier classifier, PolandScale scale) {
		Random r = new Random(scale.ordinal() * 31 + 5);
		double span = scale == PolandScale.REALISTIC ? 200_000 : 20_000;
		List<int[]> out = new ArrayList<>();
		for (int tries = 0; tries < 20 * AREAS && out.size() < AREAS; tries++) {
			int cx = (int) ((r.nextDouble() * 2 - 1) * span) >> 4;
			int cz = (int) ((r.nextDouble() * 2 - 1) * span) >> 4;
			java.util.Set<HabitatBiome> seen = java.util.EnumSet.noneOf(HabitatBiome.class);
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					int x = (cx << 4) - 16 + i * 16;
					int z = (cz << 4) - 16 + j * 16;
					HabitatBiome b = Habitat.biome(classifier.classify(model.sample(x, z), x, z));
					if (!b.isWater()) {
						seen.add(b);
					}
				}
			}
			if (seen.size() >= 2) {
				out.add(new int[] {cx, cz});
			}
		}
		return out;
	}

	/** The zone of a code, or its biome when it has none. */
	private static String side(int code) {
		Zone z = Habitat.zone(code);
		return z == Zone.NONE ? Habitat.biome(code).id() : z.id();
	}

	private static boolean softZone(int code) {
		return (Ecotone.SOFT_ZONES >>> Habitat.zone(code).ordinal() & 1) != 0;
	}

	private static boolean landZone(int code) {
		Zone z = Habitat.zone(code);
		return z == Zone.NONE || z == Zone.TIMBERLINE;
	}

	private static void measure(LandscapeModel model, HabitatClassifier classifier, SurfaceBuilder builder, double k, int cx,
			int cz, Stats stats) {
		// Raw codes of the 5 × 5 chunks.
		int[][] raw = new int[25][256];
		boolean[][] wet = new boolean[25][256];
		for (int a = 0; a < 5; a++) {
			for (int b = 0; b < 5; b++) {
				for (int i = 0; i < 256; i++) {
					int x = ((cx + a - 2) << 4) + (i >> 4);
					int z = ((cz + b - 2) << 4) + (i & 15);
					var s = model.sample(x, z);
					raw[a * 5 + b][i] = classifier.classify(s, x, z);
					wet[a * 5 + b][i] = s.hasWater();
				}
			}
		}
		Ecotone.Noises noises = Ecotone.Noises.of(SEED);
		int n = 48;
		int[] rawCode = new int[n * n];
		boolean[] water = new boolean[n * n];
		int[] tBefore = new int[n * n];
		int[] tAfter = new int[n * n];
		int[] gBefore = new int[n * n];
		int[] gAfter = new int[n * n];
		int[] sBefore = new int[n * n];
		int[] sAfter = new int[n * n];
		for (int a = 1; a <= 3; a++) {
			for (int b = 1; b <= 3; b++) {
				int[][] chunks = new int[9][];
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						chunks[(dx + 1) * 3 + dz + 1] = raw[(a + dx) * 5 + b + dz];
					}
				}
				int ccx = cx + a - 2;
				int ccz = cz + b - 2;
				Ecotone.Region region = Ecotone.Region.of(ccx, ccz, chunks, k);
				int[] own = region.own();
				int[] trees = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
				int[] shrubs = Ecotone.effective(region, Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
				int[] ground = Ecotone.effective(region, Ecotone.Layer.PLANTS, SEED, Ecotone.GROUND_SALT, Ecotone.GROUND_PATCH);
				Ecotone.Edges edges = Ecotone.edges(region, noises, shrubs, ground);
				int[] soil = Ecotone.effective(region, Ecotone.Layer.SOIL, SEED, SoilBlend.SALT, SoilBlend.PATCH);
				for (int i = 0; i < 256; i++) {
					int gx = (a - 1) * 16 + (i >> 4);
					int gz = (b - 1) * 16 + (i & 15);
					int c = gx * n + gz;
					int x = (ccx << 4) + (i >> 4);
					int z = (ccz << 4) + (i & 15);
					rawCode[c] = own[i];
					water[c] = wet[a * 5 + b][i];
					Material mine = builder.soilTop(own[i], x, z);
					Material taken = builder.soilTop(soil[i], x, z);
					tBefore[c] = treeRule(own[i]);
					tAfter[c] = treeRule(trees[i]);
					sBefore[c] = Habitat.soil(own[i]).ordinal();
					sAfter[c] = Habitat.soil(soil[i]).ordinal();
					gBefore[c] = groundRule(own[i], mine, 0);
					gAfter[c] = groundRule(ground[i], taken, edges.ground()[i]);
				}
			}
		}
		// Coincidence at the border pairs, and the land habitat borders that stay sharp.
		for (int c = 0; c < n * n; c++) {
			int x = c / n;
			int z = c % n;
			for (int d = 0; d < 2; d++) {
				if (d == 0 ? x + 1 >= n : z + 1 >= n) {
					continue;
				}
				int e = d == 0 ? c + n : c + 1;
				int p = rawCode[c];
				int q = rawCode[e];
				if (p == q) {
					continue;
				}
				boolean land = !water[c] && !water[e] && !Habitat.biome(p).isWater() && !Habitat.biome(q).isWater();
				boolean habitatBorder = Habitat.biome(p) != Habitat.biome(q) || Habitat.zone(p) != Habitat.zone(q);
				if (land && habitatBorder) {
					stats.land.incrementAndGet(0);
					boolean mixes = false;
					for (Ecotone.Layer layer : Ecotone.Layer.values()) {
						mixes |= Ecotone.halfWidth(p, q, layer) > 0 || Ecotone.halfWidth(q, p, layer) > 0;
					}
					stats.land.addAndGet(1, mixes ? 0 : 1);
					if (!mixes) {
						String sp = side(p);
						String sq = side(q);
						stats.sharpKinds.computeIfAbsent(sp.compareTo(sq) < 0 ? sp + " | " + sq : sq + " | " + sp,
								key -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
					}
				}
				int slot;
				if (Habitat.biome(p) != Habitat.biome(q) && Ecotone.halfWidth(p, q, Ecotone.Layer.TREES) > 0) {
					slot = 0;
				} else if (land && Habitat.biome(p) == Habitat.biome(q)
						&& (softZone(p) && landZone(q) || softZone(q) && landZone(p))) {
					slot = 3;
				} else {
					continue;
				}
				stats.pairs.incrementAndGet(slot);
				if (tBefore[c] != tBefore[e] && gBefore[c] != gBefore[e] && sBefore[c] != sBefore[e]) {
					stats.pairs.incrementAndGet(slot + 1);
				}
				if (tAfter[c] != tAfter[e] && gAfter[c] != gAfter[e] && sAfter[c] != sAfter[e]) {
					stats.pairs.incrementAndGet(slot + 2);
				}
			}
		}
		// Profiles across straight borders, along x and along z: the border lies within one block of the same offset in
		// the 2 · ROWS + 1 neighboring rows, and each side holds one biome over SIDE blocks.
		int[][] before = {tBefore, gBefore, sBefore};
		int[][] after = {tAfter, gAfter, sAfter};
		for (int dir = 0; dir < 2; dir++) {
			final int axis = dir;
			java.util.function.IntBinaryOperator at = (u, v) -> axis == 0 ? u * n + v : v * n + u;
			for (int v = ROWS; v + ROWS < n; v++) {
				for (int u = SIDE; u + SIDE <= n; u++) {
					int left = rawCode[at.applyAsInt(u - 1, v)];
					int right = rawCode[at.applyAsInt(u, v)];
					HabitatBiome bl = Habitat.biome(left);
					HabitatBiome br = Habitat.biome(right);
					if (bl == br || Ecotone.halfWidth(left, right, Ecotone.Layer.TREES) <= 0) {
						continue;
					}
					boolean clean = true;
					for (int w = v - ROWS; w <= v + ROWS && clean; w++) {
						for (int o = 2; o <= SIDE && clean; o++) {
							clean = Habitat.biome(rawCode[at.applyAsInt(u - o, w)]) == bl;
						}
						for (int o = 1; o < SIDE && clean; o++) {
							clean = Habitat.biome(rawCode[at.applyAsInt(u + o, w)]) == br;
						}
					}
					if (!clean) {
						continue;
					}
					for (int l = 0; l < 3; l++) {
						int valueA = before[l][at.applyAsInt(u - 1, v)];
						int valueB = before[l][at.applyAsInt(u, v)];
						if (valueA == valueB) {
							continue;
						}
						for (int o = -SIDE; o < SIDE; o++) {
							int col = at.applyAsInt(u + o, v);
							stats.seen.incrementAndGet(l * 2 * SIDE + o + SIDE);
							if (after[l][col] == valueB) {
								stats.other.incrementAndGet(l * 2 * SIDE + o + SIDE);
							}
							if (before[l][col] == valueB) {
								stats.otherBefore.incrementAndGet(l * 2 * SIDE + o + SIDE);
							}
						}
					}
					// The shift of the trees' transition: the columns of side A's tree rule in the window, averaged over the
					// neighboring rows (whose border lies at the same offset within a block), minus SIDE.
					int valueA = tBefore[at.applyAsInt(u - 1, v)];
					if (valueA != tBefore[at.applyAsInt(u, v)]) {
						double sum = 0;
						for (int w = v - ROWS; w <= v + ROWS; w++) {
							for (int o = -SIDE; o < SIDE; o++) {
								sum += tAfter[at.applyAsInt(u + o, w)] == valueA ? 1 : 0;
							}
						}
						double s = sum / (2 * ROWS + 1) - SIDE;
						stats.shift.incrementAndGet(0);
						stats.shift.addAndGet(1, Math.round(100 * s * s));
					}
				}
			}
		}
	}
}
