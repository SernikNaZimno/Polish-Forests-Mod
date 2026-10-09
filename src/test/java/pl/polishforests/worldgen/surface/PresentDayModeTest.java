package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * The PRESENT_DAY mode (step S8, docs/03-m2-biomy.md §3.4 step 6, §4.5) keeps the terrain and the potential habitat of
 * the NATURAL mode for the same seed: in the chunks of the soil test areas (both scales) the model terrain and water of
 * the surface plan are identical, the final top differs only where the one-block micro-relief of §7.3 (puddles and
 * hummocks, which follow the biome) differs, and every column keeps its STL, association and zone (a zone of the NATURAL
 * mode is kept; only the alder tree row TREE_ROW by class B watercourses is added); a forested column of the PRESENT_DAY
 * mode has the biome of the NATURAL mode. The only exception are the heath openings on dunes of the NATURAL mode (§2.2:
 * a dry pine forest site, no STL in the NATURAL mode). Both modes are reachable: the PRESENT_DAY areas hold forest,
 * arable land, hay or wet meadows and the tree row.
 */
public class PresentDayModeTest {
	private static final int[][] AREAS_REAL = {{-40_000, 25_000}, {154_834 - 64, 1_058_738 - 64}, {-19_484 - 64, 11_253 - 64},
			{-233_358 - 64, -136_402 - 64}, {800 - 64, -4_304 - 64}};
	private static final int[][] AREAS_GAMEPLAY = {{-40_000, 25_000}, {27_609 - 64, 3_254 - 64}, {-1_851 - 64, 6_022 - 64},
			{-5_782 - 64, -1_802 - 64}, {-1_400 - 64, -296 - 64}};
	private static final int MICRO_RELIEF = ChunkSurface.PUDDLE | ChunkSurface.PUDDLE_MUD | ChunkSurface.HUMMOCK;

	@Test
	void sameTerrainAndPotentialHabitatInBothModes() {
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture natural = new SurfaceFixture(scale, true, HabitatClassifier.Mode.NATURAL);
			SurfaceFixture today = new SurfaceFixture(scale, true, HabitatClassifier.Mode.PRESENT_DAY);
			long columns = 0;
			long microRelief = 0;
			long forest = 0;
			long open = 0;
			long treeRow = 0;
			Set<HabitatBiome> openBiomes = EnumSet.noneOf(HabitatBiome.class);
			for (int[] area : scale == PolandScale.REALISTIC ? AREAS_REAL : AREAS_GAMEPLAY) {
				for (int cx = 0; cx < 8; cx++) {
					for (int cz = 0; cz < 8; cz++) {
						SurfaceFixture.Chunk a = natural.chunk((area[0] >> 4) + cx, (area[1] >> 4) + cz);
						SurfaceFixture.Chunk b = today.chunk((area[0] >> 4) + cx, (area[1] >> 4) + cz);
						ChunkSurface sa = a.surface();
						ChunkSurface sb = b.surface();
						for (int i = 0; i < 256; i++) {
							columns++;
							String at = String.format(Locale.ROOT, "%s (%d, %d)", scale, (a.cx() << 4) + (i >> 4), (a.cz() << 4) + (i & 15));
							if (sa.modelTop(i) != sb.modelTop(i) || sa.modelWater(i) != sb.modelWater(i)) {
								failures.add("model terrain differs at " + at);
							}
							boolean micro = ((sa.flags(i) | sb.flags(i)) & MICRO_RELIEF) != 0;
							microRelief += micro && (sa.top(i) != sb.top(i) || sa.waterTop(i) != sb.waterTop(i)) ? 1 : 0;
							if (micro ? Math.abs(sa.top(i) - sb.top(i)) > 1 : sa.top(i) != sb.top(i) || sa.waterTop(i) != sb.waterTop(i)) {
								failures.add("top differs outside the micro-relief at " + at + ": " + sa.top(i) + " / " + sb.top(i));
							}
							int n = a.codes()[i];
							int d = b.codes()[i];
							HabitatBiome bn = Habitat.biome(n);
							HabitatBiome bd = Habitat.biome(d);
							boolean heathOpening = bn == HabitatBiome.HEATH && Habitat.siteType(d) == ForestSiteType.DRY_CONIFEROUS;
							if (Habitat.siteType(n) != Habitat.siteType(d) && !heathOpening) {
								failures.add("STL " + Habitat.siteType(n) + " / " + Habitat.siteType(d) + " at " + at);
							}
							if (Habitat.association(n) != Habitat.association(d)) {
								failures.add("association differs at " + at);
							}
							Zone zn = Habitat.zone(n);
							Zone zd = Habitat.zone(d);
							if (zn != zd && !(zn == Zone.NONE && zd == Zone.TREE_ROW)) {
								failures.add("zone " + zn + " / " + zd + " at " + at);
							}
							if (bd.isForest() && bd != bn && !heathOpening) {
								failures.add("forest biome " + bn + " / " + bd + " at " + at);
							}
							if (bd != bn && !bd.isForest() && !heathOpening) {
								open++;
								openBiomes.add(bd);
							}
							forest += bd.isForest() ? 1 : 0;
							treeRow += zd == Zone.TREE_ROW ? 1 : 0;
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s: %d columns, PRESENT_DAY forest %.1f%%, unforested %.1f%% %s, tree row %d columns, "
					+ "micro-relief differences %d columns%n", scale, columns, 100.0 * forest / columns, 100.0 * open / columns,
					openBiomes, treeRow, microRelief);
			assertTrue(openBiomes.containsAll(List.of(HabitatBiome.ARABLE_LAND, HabitatBiome.HAY_MEADOW, HabitatBiome.WET_MEADOW)),
					scale + ": non-forest biomes " + openBiomes);
			assertTrue(forest > columns / 10, scale + ": forest " + forest);
			assertTrue(treeRow > 0, scale + ": no tree row");
		}
		assertEquals(List.of(), failures.size() > 20 ? failures.subList(0, 20) : failures);
	}
}
