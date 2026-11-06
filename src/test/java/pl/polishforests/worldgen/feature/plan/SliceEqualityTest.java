package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
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
 * The equality test of step F1 (docs/04-pionowy-wycinek.md §3, "Kroki F", F1): the tree stand of the four forests of
 * the vertical slice far from the lowland beech forest (farther than the radius of {@link ForestBlend}, the reach of the
 * canopy gaps and of the crown territories) is the one of the build before F1. A world of the slice forests, meadows,
 * herb fringes and lakes (as {@code SliceStandTest}) lies west of x = 0, a mosaic of the lowland beech forest (fertile and
 * acid) with the oak-hornbeam and the mixed forest east of it; the chunks at least {@value #FAR} blocks west of x = 0 are
 * planned as the tree stand plans them (the effective tree codes of the ecotones with the wide blend, the plan of the
 * stand with the world's stand map and crown territories, the territory and the voxels of each procedural tree) and
 * hashed per chunk. The hashes of the build before F1 are in {@code src/test/resources/f1_slice_equality.txt}
 * ({@code -Dpolishforests.f1.equality.write=<file>} writes them). The palette rules of the generated tree stand, dead
 * wood, understory and ground layer palettes that name a slice forest and not the beech forest are hashed too.
 *
 * <p>Step F6 (choice F-i, docs/00 G.47 F6-t): the planner rowan of the subcanopy replaced the thin placeholder
 * {@code tree_substitute/rowan_thin} also in the slice forests, so the file was rewritten once by the F6 build. Before
 * that, exactly the 24 chunks (11 realistic, 13 gameplay) whose tree stand plans a rowan and the 4 palette rules that
 * named the placeholder (tree stand of the fresh pine, mixed pine and mixed forest, understory of the fresh pine forest)
 * differed from the build before F1; every chunk without a rowan was still equal. Since then the file guards the slice
 * forests against any other change.
 *
 * <p>Step F7 (docs/00 G.48 F7-l): the lone trees of the hay meadow of this world are trees of the planner (form
 * {@code SOLITARY}) instead of the M2 placeholders, so the file was rewritten once more by the F7 build. Before that,
 * exactly the 5 chunks (1 realistic, 4 gameplay) whose plan has a meadow tree differed; every palette rule and every
 * chunk of the slice forests without a meadow tree was equal.
 */
@Tag("slow")
class SliceEqualityTest {
	private static final long SEED = 20260927L;
	private static final String RESOURCE = "/f1_slice_equality.txt";
	/** Least distance of a planned chunk from the beech mosaic, blocks. */
	static final int FAR = 176;
	private static final float[] RAMP = {1, 1, 1, 1};
	private static final int GROUND = 64;
	private static final HabitatBiome[] SLICE = {HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST,
			HabitatBiome.MIXED_FOREST, HabitatBiome.OAK_HORNBEAM_FOREST};

	/** The world: the slice forests west of x = 0, the beech mosaic east of it. */
	static IntBinaryOperator world(double k) {
		Noise n = new Noise(SEED).derive("test.slice_equality");
		int[] forests = {code(HabitatBiome.OAK_HORNBEAM_FOREST, ForestSiteType.NONE), code(HabitatBiome.MIXED_FOREST, ForestSiteType.NONE),
				code(HabitatBiome.MIXED_PINE_FOREST, ForestSiteType.NONE), code(HabitatBiome.FRESH_PINE_FOREST, ForestSiteType.NONE)};
		int[] beech = {code(HabitatBiome.LOWLAND_BEECH_FOREST, ForestSiteType.FRESH_BROADLEAVED),
				code(HabitatBiome.LOWLAND_BEECH_FOREST, ForestSiteType.FRESH_MIXED_BROADLEAVED),
				code(HabitatBiome.OAK_HORNBEAM_FOREST, ForestSiteType.NONE), code(HabitatBiome.MIXED_FOREST, ForestSiteType.NONE)};
		int meadow = code(HabitatBiome.HAY_MEADOW, ForestSiteType.NONE);
		int fringe = Habitat.pack(HabitatBiome.HAY_MEADOW, Zone.HERB_FRINGE, ForestSiteType.NONE, Association.TYPICAL,
				LandCover.forBiome(HabitatBiome.HAY_MEADOW), 0, Soil.forBiome(HabitatBiome.HAY_MEADOW, Zone.HERB_FRINGE));
		int lake = code(HabitatBiome.LAKE, ForestSiteType.NONE);
		return (x, z) -> {
			if (x >= 0) {
				double b = n.at(x + 2_000, z - 7_000, 100 * k);
				return b > 0.25 ? beech[2] : b > 0.1 ? beech[3] : n.at(x, z + 3_000, 50 * k) > 0 ? beech[0] : beech[1];
			}
			double c = n.at(x - 9_000, z + 1_000, 60 * k);
			if (c > 0.6) {
				return lake;
			}
			if (c > 0.52) {
				return fringe;
			}
			if (n.at(x + 5_000, z - 3_000, 80 * k) > 0.45) {
				return meadow;
			}
			double a = n.at(x, z, 120 * k);
			return forests[Math.clamp((int) Math.floor((a + 0.6) / 1.2 * 4), 0, 3)];
		};
	}

	private static int code(HabitatBiome b, ForestSiteType site) {
		return Habitat.pack(b, Zone.NONE, site, Association.TYPICAL, LandCover.forBiome(b), 0, Soil.forBiome(b, Zone.NONE));
	}

	/** The hashes of the far chunks, "k cx cz hash" per line, then the palette rules. */
	static List<String> hashes() {
		GeneratedTreePalette generated = GeneratedTreePalette.load();
		List<String> out = new ArrayList<>();
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator world = world(k);
			ForestBlend blend = new ForestBlend(SEED, k, world::applyAsInt);
			ForestStandMap map = standMap(k, blend);
			CrownTerritories crowns = new CrownTerritories(SEED, map);
			CrownTerritories.Session session = crowns.session();
			TreeStandPlan.Stand stand = new TreeStandPlan.Stand(RAMP, new Noise(SEED).derive("feature.tree_stand.gaps"), k, map, crowns);
			int last = Math.floorDiv(-FAR, 16) - 1;
			for (int cx = last - 10; cx <= last; cx++) {
				for (int cz = -6; cz < 6; cz++) {
					Ecotone.Region region = EcotoneTest.region(cx, cz, world, k).withBlend(blend);
					int[] codes = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
					long h = java.util.Arrays.hashCode(codes);
					int[] plan = TreeStandPlan.of(codes, new int[256], generated.palette, SEED, cx, cz, stand);
					for (int p : plan) {
						int t = TreeStandPlan.tree(p);
						int c = TreeStandPlan.column(p);
						CrownLayer layer = TreeStandPlan.layer(p);
						h = mix(h, c * 31L + layer.ordinal());
						h = mix(h, generated.trees.get(t).hashCode());
						if (!generated.procedural(t)) {
							continue;
						}
						int x = (cx << 4) + (c >> 4);
						int z = (cz << 4) + (c & 15);
						CrownTerritories.Site site = crowns.site(x, z);
						h = mix(h, site.layer().ordinal() * 7L + site.age().ordinal() * 3L + (site.natural() ? 1 : 0) + (site.gap() ? 2 : 0));
						h = mix(h, Float.floatToIntBits(site.radius()));
						CrownTerritory territory = session.territory(x, z, null);
						h = mix(h, territory.hashCode());
						h = mix(h, Float.floatToIntBits(territory.reach()));
						Species species = generated.species.get(t);
						AgeClass age = layer == CrownLayer.REGENERATION && map.canopyGap(x, z) ? AgeClass.POLE_STAGE : map.ageClass(x, z);
						TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species, layer, age, SEED, x, GROUND + 1, z, territory,
								session::candidate, map, (wx, wz) -> GROUND));
						h = mix(h, v.woodCount() * 65_537L + v.leafCount());
						h = mix(h, v.hashCode());
					}
					out.add(String.format(Locale.ROOT, "%s %d %d %016x", k == 1.0 ? "real" : "gameplay", cx, cz, h));
				}
			}
		}
		for (String palette : List.of("tree_stand", "deadwood", "understory", "ground_layer")) {
			Map<String, Integer> seen = new java.util.HashMap<>();
			for (JsonElement e : EcotoneSharpnessTest.rules(palette)) {
				JsonObject r = e.getAsJsonObject();
				Set<String> biomes = new java.util.HashSet<>();
				if (r.has("biomes")) {
					r.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
				}
				boolean slice = false;
				for (HabitatBiome b : SLICE) {
					slice |= biomes.contains(b.id());
				}
				if (slice && !biomes.contains(HabitatBiome.LOWLAND_BEECH_FOREST.id())) {
					String key = palette + " " + String.join("+", new java.util.TreeSet<>(biomes));
					int nth = seen.merge(key, 1, Integer::sum);
					out.add(String.format(Locale.ROOT, "rule %s #%d %08x", key, nth, r.toString().hashCode()));
				}
			}
		}
		return out;
	}

	/** The stand map of the tree stand (the world's, with the stand profiles of its blend; step F1). */
	static ForestStandMap standMap(double k, ForestBlend blend) {
		return new ForestStandMap(SEED, k, 0.85, blend.standProfiles());
	}

	private static long mix(long h, long v) {
		return Noise.mix(h * 0x9E37_79B9_7F4A_7C15L ^ v);
	}

	@Test
	void sliceForestsFarFromTheBeechForestAreAsBeforeF1() throws IOException {
		List<String> now = hashes();
		String write = System.getProperty("polishforests.f1.equality.write", "");
		if (!write.isBlank()) {
			Files.writeString(Path.of(write), String.join("\n", now) + "\n", StandardCharsets.UTF_8);
			return;
		}
		List<String> before = new ArrayList<>();
		try (InputStream in = SliceEqualityTest.class.getResourceAsStream(RESOURCE)) {
			assertTrue(in != null, "missing " + RESOURCE);
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (!line.isBlank()) {
					before.add(line.strip());
				}
			}
		}
		Map<String, String> want = new LinkedHashMap<>();
		for (String line : before) {
			want.put(line.substring(0, line.lastIndexOf(' ')), line);
		}
		List<String> diff = new ArrayList<>();
		int chunks = 0;
		for (String line : now) {
			String key = line.substring(0, line.lastIndexOf(' '));
			chunks += line.startsWith("rule ") ? 0 : 1;
			String old = want.remove(key);
			if (!line.equals(old)) {
				diff.add(line + " (before: " + old + ")");
			}
		}
		diff.addAll(want.values().stream().map(l -> "missing: " + l).toList());
		assertTrue(chunks > 200, "chunks hashed: " + chunks);
		assertEquals(List.of(), diff, "slice forests far from the beech forest differ from the build before F1");
	}
}
