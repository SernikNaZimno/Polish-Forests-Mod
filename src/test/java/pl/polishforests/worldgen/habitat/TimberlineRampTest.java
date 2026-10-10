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
 * per chunk at 60 m below the timberline.
 */
class TimberlineRampTest {
	private static final long SEED = 20260927L;
	private static final int FROM = -200;
	private static final int TO = 60;
	private static final int BIN = 10;

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
			DoubleAdder[] before = new DoubleAdder[bins];
			DoubleAdder[] after = new DoubleAdder[bins];
			for (int b = 0; b < bins; b++) {
				before[b] = new DoubleAdder();
				after[b] = new DoubleAdder();
			}
			double span = sc == LandscapeScale.REALISTIC ? 3_000 : 700;
			Random r = new Random(11);
			List<int[]> chunks = new ArrayList<>();
			for (int i = 0; i < 400; i++) {
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
				int[] eff = Ecotone.effective(Ecotone.Region.of(cx, cz, codes, sc.local()), Ecotone.Layer.TREES, SEED,
						Ecotone.TREES_SALT, 1);
				for (int i = 0; i < 256; i++) {
					int x = (cx << 4) + (i >> 4);
					int z = (cz << 4) + (i & 15);
					ColumnSample s = own[i];
					HabitatBiome biome = Habitat.biome(codes[4][i]);
					if (s.hasWater() || biome != HabitatBiome.MONTANE_SPRUCE_FOREST && biome != HabitatBiome.DWARF_PINE_SCRUB) {
						continue;
					}
					HabitatClassifier.Column col = new HabitatClassifier.Column(k, s, x, z);
					if (!AltitudinalBelts.largeMassif(col) || col.wMountains <= 0.5) {
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
				}
			});
			StringBuilder rowBefore = new StringBuilder();
			StringBuilder rowAfter = new StringBuilder();
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
					+ "largest step between bins of %d m: %.1f own, %.1f with ecotones%n", sc.id(), summit[0], summit[1], summit[2],
					rowBefore, rowAfter, BIN, jumpBefore, jumpAfter));
			if (jumpAfter > 2.5) {
				failures.add(String.format(Locale.ROOT, "%s: step of %.1f trees per chunk", sc.id(), jumpAfter));
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "\n" + report);
	}
}
