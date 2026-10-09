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
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.surface.Material;
import pl.polishforests.worldgen.surface.SoilBlend;
import pl.polishforests.worldgen.surface.SurfaceBuilder;

/**
 * Sharpness of the biome borders (rule Z10, step S8b, docs/03-m2-biomy.md §8.7): in random areas of 5 × 5 chunks with a
 * biome border (both scales, both modes, the classifier on real samples), the tree rule, the ground layer rule (from
 * the generated palettes) and the soil (the soil type, which sets the mixture of top blocks) of every column of the inner 3 × 3 chunks, before the ecotones (the
 * columns' own codes) and after them ({@link Ecotone#effective}, {@link Ecotone#edges}, {@link SoilBlend}, with the
 * salts and patches of the game). Two measures:
 * <ul>
 * <li><b>coincidence:</b> of the pairs of neighboring columns across a border between two land biomes that mix, the
 * share where the tree rule, the ground layer rule and the soil all change at once (before: the palettes switch on
 * the same line);</li>
 * <li><b>profile across the border:</b> on the rows that cross a border with at least 14 columns of either biome on
 * both sides, the share of the columns taking the other side's tree rule, ground layer rule and soil block by the
 * distance from the border, and the width of the transition (offsets with a share between 10% and 90%).</li>
 * </ul>
 * Asserted: after the ecotones the coincidence is at most half of what it was and below 40%, and the transition of the
 * trees and of the soil is at least 4 blocks wide in the realistic scale and 2 in the gameplay scale.
 */
class EcotoneSharpnessTest {
	private static final long SEED = 20260927L;
	private static final int AREAS = 70;
	private static final int SIDE = 14;

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

	/** Salt of a column layer of the game ({@code PlantLayerFeature}). */
	private static long layerSalt(BiomeDecoration.Dispatcher d) {
		return TreeStandPlan.SALT * 31 + d.ordinal() * 0x6A09_E667L;
	}

	/** Counters of one scale and mode. */
	private static final class Stats {
		// Coincidence: border pairs, pairs where all three change (before, after).
		final AtomicLongArray pairs = new AtomicLongArray(3);
		// Profiles: per layer (trees, ground, soil), per offset: crossings counted and columns of the other side (after).
		final AtomicLongArray seen = new AtomicLongArray(3 * 2 * SIDE);
		final AtomicLongArray other = new AtomicLongArray(3 * 2 * SIDE);
		final AtomicLongArray otherBefore = new AtomicLongArray(3 * 2 * SIDE);
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
				report.append(String.format(Locale.ROOT, "%s %s: %d areas, %d border pairs; all three change at once: before "
						+ "%.1f%%, after %.1f%%%n", scale.getSerializedName(), mode, areas.size(), stats.pairs.get(0), 100 * before,
						100 * after));
				String[] layers = {"trees", "ground layer", "soil"};
				int[] widths = new int[3];
				for (int l = 0; l < 3; l++) {
					StringBuilder row = new StringBuilder();
					StringBuilder rowBefore = new StringBuilder();
					int width = 0;
					int widthBefore = 0;
					for (int o = 0; o < 2 * SIDE; o++) {
						long n = stats.seen.get(l * 2 * SIDE + o);
						double share = (double) stats.other.get(l * 2 * SIDE + o) / Math.max(1, n);
						double shareBefore = (double) stats.otherBefore.get(l * 2 * SIDE + o) / Math.max(1, n);
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
					report.append(String.format(Locale.ROOT, "  %s (%d crossings): transition before %d, after %d blocks; share "
							+ "of side B by offset before:%s%n    after:%s%n", layers[l], stats.seen.get(l * 2 * SIDE), widthBefore,
							width, rowBefore, row));
				}
				int least = real ? 4 : 2;
				if (stats.pairs.get(0) < 200) {
					failures.add(scale.getSerializedName() + " " + mode + ": only " + stats.pairs.get(0) + " border pairs");
				}
				if (after > 0.5 * before || after > 0.4) {
					failures.add(String.format(Locale.ROOT, "%s %s: coincidence %.1f%% after (%.1f%% before)",
							scale.getSerializedName(), mode, 100 * after, 100 * before));
				}
				if (widths[0] < least || widths[2] < least) {
					failures.add(String.format(Locale.ROOT, "%s %s: transition trees %d, soil %d blocks (at least %d)",
							scale.getSerializedName(), mode, widths[0], widths[2], least));
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

	private static void measure(LandscapeModel model, HabitatClassifier classifier, SurfaceBuilder builder, double k, int cx,
			int cz, Stats stats) {
		// Raw codes of the 5 × 5 chunks.
		int[][] raw = new int[25][256];
		for (int a = 0; a < 5; a++) {
			for (int b = 0; b < 5; b++) {
				for (int i = 0; i < 256; i++) {
					int x = ((cx + a - 2) << 4) + (i >> 4);
					int z = ((cz + b - 2) << 4) + (i & 15);
					raw[a * 5 + b][i] = classifier.classify(model.sample(x, z), x, z);
				}
			}
		}
		Noise widths = new Noise(SEED).derive("feature.ecotone.edges");
		int n = 48;
		int[] rawCode = new int[n * n];
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
				int[] trees = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, TreeStandPlan.SALT, 1);
				int[] plants = Ecotone.effective(region, Ecotone.Layer.PLANTS, SEED,
						layerSalt(BiomeDecoration.Dispatcher.GROUND_LAYER), 3);
				byte[] edges = Ecotone.edges(region, widths, plants);
				int[] soil = Ecotone.effective(region, Ecotone.Layer.SOIL, SEED, SoilBlend.SALT, SoilBlend.PATCH);
				for (int i = 0; i < 256; i++) {
					int gx = (a - 1) * 16 + (i >> 4);
					int gz = (b - 1) * 16 + (i & 15);
					int c = gx * n + gz;
					int x = (ccx << 4) + (i >> 4);
					int z = (ccz << 4) + (i & 15);
					rawCode[c] = own[i];
					Material mine = builder.soilTop(own[i], x, z);
					Material taken = builder.soilTop(soil[i], x, z);
					tBefore[c] = treeRule(own[i]);
					tAfter[c] = treeRule(trees[i]);
					sBefore[c] = Habitat.soil(own[i]).ordinal();
					sAfter[c] = Habitat.soil(soil[i]).ordinal();
					gBefore[c] = groundRule(own[i], mine, 0);
					gAfter[c] = groundRule(plants[i], taken, edges[i]);
				}
			}
		}
		// Coincidence at the border pairs.
		for (int c = 0; c < n * n; c++) {
			int x = c / n;
			int z = c % n;
			for (int d = 0; d < 2; d++) {
				if (d == 0 ? x + 1 >= n : z + 1 >= n) {
					continue;
				}
				int e = d == 0 ? c + n : c + 1;
				if (Habitat.biome(rawCode[c]) == Habitat.biome(rawCode[e])
						|| Ecotone.halfWidth(rawCode[c], rawCode[e], Ecotone.Layer.TREES) <= 0) {
					continue;
				}
				stats.pairs.incrementAndGet(0);
				if (tBefore[c] != tBefore[e] && gBefore[c] != gBefore[e] && sBefore[c] != sBefore[e]) {
					stats.pairs.incrementAndGet(1);
				}
				if (tAfter[c] != tAfter[e] && gAfter[c] != gAfter[e] && sAfter[c] != sAfter[e]) {
					stats.pairs.incrementAndGet(2);
				}
			}
		}
		// Profiles along the rows (x) that cross a clean border.
		int[][] before = {tBefore, gBefore, sBefore};
		int[][] after = {tAfter, gAfter, sAfter};
		for (int z = 0; z < n; z++) {
			for (int x = SIDE; x + SIDE <= n; x++) {
				int left = rawCode[(x - 1) * n + z];
				int right = rawCode[x * n + z];
				if (Habitat.biome(left) == Habitat.biome(right) || Ecotone.halfWidth(left, right, Ecotone.Layer.TREES) <= 0) {
					continue;
				}
				boolean clean = true;
				for (int o = 1; o <= SIDE && clean; o++) {
					clean = Habitat.biome(rawCode[(x - o) * n + z]) == Habitat.biome(left)
							&& Habitat.biome(rawCode[(x + o - 1) * n + z]) == Habitat.biome(right);
				}
				if (!clean) {
					continue;
				}
				for (int l = 0; l < 3; l++) {
					int valueA = before[l][(x - 1) * n + z];
					int valueB = before[l][x * n + z];
					if (valueA == valueB) {
						continue;
					}
					for (int o = -SIDE; o < SIDE; o++) {
						int col = (x + o) * n + z;
						stats.seen.incrementAndGet(l * 2 * SIDE + o + SIDE);
						if (after[l][col] == valueB) {
							stats.other.incrementAndGet(l * 2 * SIDE + o + SIDE);
						}
						if (before[l][col] == valueB) {
							stats.otherBefore.incrementAndGet(l * 2 * SIDE + o + SIDE);
						}
					}
				}
			}
		}
	}
}
