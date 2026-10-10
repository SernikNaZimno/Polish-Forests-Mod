package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.DoubleAdder;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.BiomeJsonTest;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.feature.plan.HabitatMatch;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Landform;

/**
 * The timberline is a ramp (rule Z10, step S8b, §5.1): on the largest massif of each scale, the mean number of trees per
 * chunk of the tree palette (generated JSON) in bins of 10 m of elevation relative to the column's timberline (aspect,
 * noise and wind-exposed ridges as in {@code AltitudinalBelts}) changes by at most 2.5 trees per chunk between
 * neighboring bins, with the ecotones of the tree stand ({@link Ecotone}); before S8b the stand fell from 12 to 4 trees
 * per chunk at 60 m below the timberline. Round 1 of the S8b review: the share of the dwarf pine scrub (the understory
 * codes with their ecotones: the dwarf pines) around the timberline and the share of the alpine grassland around the
 * alpine threshold are ramps: 50 m below the border on average 0.03–0.5, 50 m above it 0.5–0.97 (before: 0 and 1), and
 * the dwarf pine share changes by at most 0.3 between bins of 10 m.
 */
class TimberlineRampTest {
	private static final long SEED = 20260927L;
	private static final int FROM = -200;
	private static final int TO = 60;
	private static final int BIN = 10;
	/** Half-span of the bins around the alpine threshold (m). */
	private static final int ALPINE_SPAN = 100;

	/** Tree rules of the generated palette: condition and trees per chunk. */
	private static final List<Object[]> RULES = new ArrayList<>();

	static {
		for (JsonElement e : BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve(
				"data/polishforests/worldgen/feature/tree_stand.json")).getAsJsonArray("rules")) {
			JsonObject r = e.getAsJsonObject();
			List<HabitatBiome> biomes = new ArrayList<>();
			if (r.has("biomes")) {
				r.getAsJsonArray("biomes").forEach(b -> biomes.add(HabitatBiome.byId(b.getAsString())));
			}
			List<Zone> zones = new ArrayList<>();
			if (r.has("zones")) {
				r.getAsJsonArray("zones").forEach(z -> zones.add(Zone.valueOf(z.getAsString().toUpperCase(Locale.ROOT))));
			}
			List<Association> associations = new ArrayList<>();
			if (r.has("associations")) {
				r.getAsJsonArray("associations").forEach(a -> associations.add(Association.valueOf(a.getAsString().toUpperCase(Locale.ROOT))));
			}
			List<ForestSiteType> sites = new ArrayList<>();
			if (r.has("site_types")) {
				r.getAsJsonArray("site_types").forEach(a -> sites.add(ForestSiteType.valueOf(a.getAsString().toUpperCase(Locale.ROOT))));
			}
			RULES.add(new Object[] {HabitatMatch.of(biomes, zones, sites, associations, List.of()),
					r.get("trees_per_chunk").getAsDouble()});
		}
	}

	/** Shares per bin (bins with at least 50 columns) into the row; returns the largest step between neighboring bins. */
	private static double share(AtomicLongArray count, AtomicLongArray hits, int from, StringBuilder row) {
		double last = Double.NaN;
		double jump = 0;
		for (int b = 0; b < count.length(); b++) {
			long n = count.get(b);
			if (n < 50) {
				continue;
			}
			double v = (double) hits.get(b) / n;
			row.append(String.format(Locale.ROOT, " %d:%.2f", from + b * BIN, v));
			if (!Double.isNaN(last)) {
				jump = Math.max(jump, Math.abs(v - last));
			}
			last = v;
		}
		return jump;
	}

	/** Share of the hits among the columns of the bins from {@code lo} to {@code hi} (m, relative to the border). */
	private static double mean(AtomicLongArray count, AtomicLongArray hits, int from, int lo, int hi) {
		long n = 0;
		long h = 0;
		for (int b = (lo - from) / BIN; b < (hi - from) / BIN; b++) {
			n += count.get(b);
			h += hits.get(b);
		}
		return (double) h / Math.max(1, n);
	}

	private static double trees(int code) {
		for (Object[] r : RULES) {
			if (((HabitatMatch) r[0]).matches(code)) {
				return (Double) r[1];
			}
		}
		return 0;
	}

	@Test
	void treesThinGradually() {
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder("Trees per chunk by elevation relative to the timberline:\n");
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
			double[] summit = AltitudinalBeltsTest.highestMassif(m);
			int bins = (TO - FROM) / BIN;
			AtomicLongArray count = new AtomicLongArray(bins);
			// Dwarf pine among the columns by the timberline bins, alpine grassland by bins of the alpine threshold.
			AtomicLongArray dwarf = new AtomicLongArray(bins);
			int alpineBins = 2 * ALPINE_SPAN / BIN;
			AtomicLongArray alpineCount = new AtomicLongArray(alpineBins);
			AtomicLongArray alpine = new AtomicLongArray(alpineBins);
			DoubleAdder[] before = new DoubleAdder[bins];
			DoubleAdder[] after = new DoubleAdder[bins];
			for (int b = 0; b < bins; b++) {
				before[b] = new DoubleAdder();
				after[b] = new DoubleAdder();
			}
			double span = sc == LandscapeScale.REALISTIC ? 3_000 : 700;
			Random r = new Random(11);
			List<int[]> chunks = new ArrayList<>();
			for (int i = 0; i < 1200; i++) {
				chunks.add(new int[] {(int) (summit[0] + (r.nextDouble() * 2 - 1) * span) >> 4,
						(int) (summit[1] + (r.nextDouble() * 2 - 1) * span) >> 4});
			}
			chunks.parallelStream().forEach(c -> {
				int cx = c[0];
				int cz = c[1];
				ColumnSample center = m.sample((cx << 4) + 8, (cz << 4) + 8);
				if (center.surface() < AltitudinalBelts.SUMMIT_FROM - 100) {
					return;
				}
				int[][] codes = new int[9][256];
				ColumnSample[] own = new ColumnSample[256];
				for (int a = 0; a < 9; a++) {
					for (int i = 0; i < 256; i++) {
						int x = ((cx + a / 3 - 1) << 4) + (i >> 4);
						int z = ((cz + a % 3 - 1) << 4) + (i & 15);
						ColumnSample s = m.sample(x, z);
						codes[a][i] = k.classify(s, x, z);
						if (a == 4) {
							own[i] = s;
						}
					}
				}
				Ecotone.Region region = Ecotone.Region.of(cx, cz, codes, sc.local());
				int[] eff = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
				int[] plants = Ecotone.effective(region, Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
				for (int i = 0; i < 256; i++) {
					int x = (cx << 4) + (i >> 4);
					int z = (cz << 4) + (i & 15);
					ColumnSample s = own[i];
					HabitatBiome biome = Habitat.biome(codes[4][i]);
					if (s.hasWater() || biome != HabitatBiome.MONTANE_SPRUCE_FOREST && biome != HabitatBiome.DWARF_PINE_SCRUB
							&& biome != HabitatBiome.ALPINE_GRASSLAND) {
						continue;
					}
					HabitatClassifier.Column col = new HabitatClassifier.Column(k, s, x, z);
					if (!AltitudinalBelts.largeMassif(col) || col.wMountains <= 0.5) {
						continue;
					}
					double da = col.H - AltitudinalBelts.ALPINE_THRESHOLD - AltitudinalBelts.correction(col);
					if (da >= -ALPINE_SPAN && da < ALPINE_SPAN) {
						int ab = (int) Math.floor((da + ALPINE_SPAN) / BIN);
						alpineCount.incrementAndGet(ab);
						alpine.addAndGet(ab, biome == HabitatBiome.ALPINE_GRASSLAND ? 1 : 0);
					}
					if (biome == HabitatBiome.ALPINE_GRASSLAND) {
						continue;
					}
					double limit = AltitudinalBelts.TIMBERLINE + AltitudinalBelts.correction(col)
							- (col.t.has(Landform.RIDGE) && col.slope < AltitudinalBelts.WINDY_RIDGE_SLOPE ? AltitudinalBelts.WINDY_RIDGE : 0);
					double d = col.H - limit;
					if (d < FROM || d >= TO) {
						continue;
					}
					int b = (int) Math.floor((d - FROM) / BIN);
					count.incrementAndGet(b);
					before[b].add(trees(codes[4][i]));
					after[b].add(trees(eff[i]));
					dwarf.addAndGet(b, Habitat.biome(plants[i]) == HabitatBiome.DWARF_PINE_SCRUB ? 1 : 0);
				}
			});
			StringBuilder rowBefore = new StringBuilder();
			StringBuilder rowAfter = new StringBuilder();
			StringBuilder rowDwarf = new StringBuilder();
			StringBuilder rowAlpine = new StringBuilder();
			double jumpDwarf = share(count, dwarf, FROM, rowDwarf);
			double jumpAlpine = share(alpineCount, alpine, -ALPINE_SPAN, rowAlpine);
			double jumpBefore = 0;
			double jumpAfter = 0;
			double lastBefore = Double.NaN;
			double lastAfter = Double.NaN;
			for (int b = 0; b < bins; b++) {
				long n = count.get(b);
				if (n < 50) {
					continue;
				}
				double vb = before[b].sum() / n;
				double va = after[b].sum() / n;
				rowBefore.append(String.format(Locale.ROOT, " %d:%.1f", FROM + b * BIN, vb));
				rowAfter.append(String.format(Locale.ROOT, " %d:%.1f", FROM + b * BIN, va));
				if (!Double.isNaN(lastBefore)) {
					jumpBefore = Math.max(jumpBefore, Math.abs(vb - lastBefore));
					jumpAfter = Math.max(jumpAfter, Math.abs(va - lastAfter));
				}
				lastBefore = vb;
				lastAfter = va;
			}
			report.append(String.format(Locale.ROOT, "%s (massif at %.0f, %.0f, %.0f m): own codes%s%n  with ecotones%s%n  "
					+ "largest step between bins of %d m: %.1f own, %.1f with ecotones%n  dwarf pine share by the timberline%s "
					+ "(largest step %.2f)%n  alpine grassland share by the alpine threshold%s (largest step %.2f)%n", sc.id(),
					summit[0], summit[1], summit[2], rowBefore, rowAfter, BIN, jumpBefore, jumpAfter, rowDwarf, jumpDwarf, rowAlpine,
					jumpAlpine));
			if (jumpAfter > 2.5) {
				failures.add(String.format(Locale.ROOT, "%s: step of %.1f trees per chunk", sc.id(), jumpAfter));
			}
			// The upper belt in patches on both sides of the border: its mean share 50 m below it between 0.03 and 0.5, 50 m
			// above it between 0.5 and 0.97 (a step: 0 and 1), and no step of more than 0.3 between bins of 10 m (the alpine
			// belt of the highest massif is small, so its bins are noisier).
			double[] dw = {mean(count, dwarf, FROM, -50, 0), mean(count, dwarf, FROM, 0, 50)};
			double[] al = {mean(alpineCount, alpine, -ALPINE_SPAN, -50, 0), mean(alpineCount, alpine, -ALPINE_SPAN, 0, 50)};
			report.append(String.format(Locale.ROOT, "  mean shares 50 m below / above the border: dwarf pine %.2f / %.2f, alpine "
					+ "grassland %.2f / %.2f%n", dw[0], dw[1], al[0], al[1]));
			for (double[] v : new double[][] {dw, al}) {
				if (v[0] < 0.03 || v[0] > 0.5 || v[1] < 0.5 || v[1] > 0.97) {
					failures.add(String.format(Locale.ROOT, "%s: shares below / above a belt border %.2f / %.2f", sc.id(), v[0], v[1]));
				}
			}
			if (jumpDwarf > 0.3) {
				failures.add(String.format(Locale.ROOT, "%s: step of the dwarf pine share %.2f", sc.id(), jumpDwarf));
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "\n" + report);
	}
}
