package pl.polishforests.worldgen.feature.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.norms.CensusGrid;
import pl.polishforests.worldgen.norms.ForestCensus;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Calibration of the stands of the vertical slice against the norms card without the game (step V9 of
 * docs/04-pionowy-wycinek.md §6): an area of uniform oak-hornbeam or fresh pine forest in one stand (managed or natural,
 * one age class, the canopy gaps of the stand map in a natural stand) is planned and written as the tree stand writes it
 * (procedural trees only; the placeholder shrubs and small trees of the regeneration, the understory, the ground layer
 * and the dead wood are not simulated), read into a census grid and counted on many 1 ha plots: the spread of N1–N6 over
 * the plots. A report ({@code -Dpolishforests.slice.calibrate=true}); the in-game census of {@code slice} is the measure.
 */
@EnabledIfSystemProperty(named = "polishforests.slice.calibrate", matches = "true")
class StandCensusSimulationTest {
	private static final int CHUNKS = 24;
	private static final int GROUND = SimulatedStands.GROUND;
	private static final String[] KEYS = {ForestNorms.TREES_PER_CHUNK, ForestNorms.A1_PER_CHUNK, ForestNorms.A2_PER_CHUNK,
			ForestNorms.A1_HEIGHT, ForestNorms.A2_HEIGHT, ForestNorms.A2_SHARE, ForestNorms.COMPOSITION + "oak",
			ForestNorms.COMPOSITION + "linden", ForestNorms.COMPOSITION + "maple_ash", ForestNorms.COMPOSITION + "scots_pine",
			ForestNorms.COMPOSITION + "birch", ForestNorms.COMPOSITION + "hornbeam", ForestNorms.CANOPY, ForestNorms.GAP_SHARE, ForestNorms.GAP_DIAMETER,
			ForestNorms.REGENERATION_RATIO, ForestNorms.OAK_REGENERATION, ForestNorms.PINE_REGENERATION_UNDER_CANOPY,
			ForestNorms.TOP_HEIGHT, ForestNorms.BARE_WOOD, ForestNorms.EXPOSED_WOOD, ForestNorms.BARE_ARMS};

	@Test
	void reportNormsOfSimulatedStands() {
		String only = System.getProperty("polishforests.slice.calibrate.only", "");
		for (HabitatBiome biome : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
			for (boolean natural : new boolean[] {true, false}) {
				for (AgeClass age : AgeClass.values()) {
					if (only.isBlank() || only.contains(biome.id() + (natural ? ":natural" : ":managed"))) {
						report(biome, natural, age);
					}
				}
			}
		}
	}

	private static void report(HabitatBiome biome, boolean natural, AgeClass age) {
		int side = CHUNKS * 16;
		CensusGrid grid = new CensusGrid(0, 0, side, side, 48);
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				grid.setGround(x, z, GROUND, "minecraft:grass_block");
			}
		}
		SimulatedStands.plant(biome, natural, age, CHUNKS, (vox, species, layer) -> write(grid, vox, species));
		ForestCensus.Census census = ForestCensus.of(grid);
		ForestNorms.Forest forest = biome == HabitatBiome.OAK_HORNBEAM_FOREST
				? natural ? ForestNorms.Forest.OAK_HORNBEAM_NATURAL : ForestNorms.Forest.OAK_HORNBEAM_MANAGED
				: natural ? ForestNorms.Forest.FRESH_PINE_NATURAL : ForestNorms.Forest.FRESH_PINE_MANAGED;
		Map<String, List<Double>> values = new LinkedHashMap<>();
		for (int px = 16; px + 100 <= side - 16; px += 40) {
			for (int pz = 16; pz + 100 <= side - 16; pz += 40) {
				Map<String, Double> m = census.measure(ForestCensus.rectangle(grid, px, pz, 100, 100));
				for (String k : KEYS) {
					values.computeIfAbsent(k, key -> new ArrayList<>()).add(m.get(k));
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (String k : KEYS) {
			List<Double> v = values.get(k);
			double min = v.stream().mapToDouble(Double::doubleValue).filter(d -> !Double.isNaN(d)).min().orElse(Double.NaN);
			double max = v.stream().mapToDouble(Double::doubleValue).filter(d -> !Double.isNaN(d)).max().orElse(Double.NaN);
			double mean = v.stream().mapToDouble(Double::doubleValue).filter(d -> !Double.isNaN(d) && !Double.isInfinite(d)).average()
					.orElse(Double.NaN);
			Optional<ForestNorms.Norm> norm = ForestNorms.of(k, forest, ForestNorms.Aspect.SUMMER, age);
			long out = norm.map(n -> v.stream().filter(d -> ForestNorms.verdict(d, n) == ForestNorms.Verdict.OUT).count()).orElse(0L);
			sb.append(String.format(Locale.ROOT, "%n    %-36s %7s..%7s mean %7s%s", k, ForestNorms.number(min), ForestNorms.number(max),
					ForestNorms.number(mean), norm.map(n -> "  norm " + n.range() + (out > 0 ? "  OUT on " + out + " of " + v.size() : ""))
							.orElse("")));
		}
		System.out.printf(Locale.ROOT, "[calibrate] %s %s %s:%s%n", biome.id(), natural ? "natural" : "managed", age, sb);
	}

	/** Writes a tree into the grid as {@code ProceduralTreeFeature.write} does (wood on air or leaves, leaves on air). */
	private static void write(CensusGrid g, TreeVoxels v, Species species) {
		int n = v.woodCount();
		boolean[] ok = new boolean[n];
		int count = 0;
		for (int i = 0; i < n; i++) {
			int p = v.parentIndex(i);
			if (p >= 0 && !ok[p]) {
				continue;
			}
			int x = v.x() + v.woodX(i);
			int z = v.z() + v.woodZ(i);
			int dy = v.y() + v.woodY(i) - GROUND;
			if (!g.contains(x, z) || dy < 1 || dy > g.height() || g.cell(x, dy, z) == CensusGrid.WOOD_UP
					|| g.cell(x, dy, z) == CensusGrid.WOOD_SIDE) {
				continue;
			}
			int parent = v.parent(i);
			g.setWood(x, dy, z, parent <= 1, CensusGrid.trunk(Math.min(7, v.radius(i)), species, "polishforests:" + species.id()
					+ "_trunk", parent));
			ok[i] = true;
			count++;
		}
		int leaves = v.leafCount();
		boolean[] placeable = new boolean[leaves];
		boolean all = count == n;
		for (int i = 0; i < leaves; i++) {
			int x = v.x() + v.leafX(i);
			int z = v.z() + v.leafZ(i);
			int dy = v.y() + v.leafY(i) - GROUND;
			placeable[i] = g.contains(x, z) && dy >= 1 && dy <= g.height() && g.cell(x, dy, z) == CensusGrid.AIR;
			all &= placeable[i];
		}
		byte[] d = all ? null : v.leafDistances(ok, placeable);
		for (int i = 0; i < leaves; i++) {
			int dist = d == null ? v.leafDistance(i) : d[i];
			if (dist < 1 || !placeable[i]) {
				continue;
			}
			g.set(v.x() + v.leafX(i), v.y() + v.leafY(i) - GROUND, v.z() + v.leafZ(i), CensusGrid.LEAVES);
		}
	}
}
