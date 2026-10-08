package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;

/**
 * Bank shelf, shore belt of the bed and micro-relief of step S6 (docs/03-m2-biomy.md §7.2, §7.3), on surface plans of
 * grids of 6 × 6 chunks in both scales: no water can flow in a place where the model did not already let it (every
 * water block of the plan has a solid block or water beside it at the same Y, also across chunk borders), at least 90%
 * of the shore columns of the shelf zones and lake shores have water beside their top block, the shore belt of the bed
 * is mud (gravel in streams), and puddles and hummocks keep their shares. The plans are built exactly as in
 * {@code fill()}, so the game test only confirms them in the world.
 */
class BankShelfTest {
	private static final int GRID = 6;

	/** Areas of a scale: the large river of the stage measurement and the first places of the habitats searched along a spiral. */
	private static Map<String, int[]> areas(SurfaceFixture f) {
		Map<String, int[]> out = new LinkedHashMap<>();
		out.put("river", f.scale == PolandScale.REALISTIC ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022});
		put(out, f, "tall_herbs", p -> Habitat.zone(f.code(p[0], p[1])) == Zone.TALL_HERBS);
		put(out, f, "point_bar", p -> Habitat.zone(f.code(p[0], p[1])) == Zone.POINT_BAR);
		put(out, f, "willow_scrub", p -> Habitat.zone(f.code(p[0], p[1])) == Zone.WILLOW_SCRUB);
		put(out, f, "lake", p -> f.model.sample(p[0], p[1]).waterKind().isLake());
		put(out, f, "shore_reedbed", p -> Habitat.zone(f.code(p[0], p[1])) == Zone.SHORE_REEDBED);
		put(out, f, "alder_carr", p -> patch(f, p, HabitatBiome.ALDER_CARR));
		put(out, f, "bog_woodland", p -> patch(f, p, HabitatBiome.BOG_WOODLAND));
		return out;
	}

	private static void put(Map<String, int[]> out, SurfaceFixture f, String name, Predicate<int[]> test) {
		// Narrow zones on a fine spiral, biomes (raised bogs are rare in the realistic scale) on a coarse one.
		int step = name.equals("alder_carr") || name.equals("bog_woodland") ? 160 : 40;
		int[] p = f.find(0, 0, step, 60_000, test);
		assertNotNull(p, f.scale + ": no " + name + " found");
		out.put(name, p);
	}

	/** The biome at the point and at four points 24 blocks away (a patch large enough for the micro-relief shares). */
	private static boolean patch(SurfaceFixture f, int[] p, HabitatBiome biome) {
		for (int[] d : new int[][] {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}}) {
			if (Habitat.biome(f.code(p[0] + d[0], p[1] + d[1])) != biome) {
				return false;
			}
		}
		return true;
	}

	/** Water top of a plan column, or {@link ChunkSurface#NO_WATER} when it is dry. */
	private static int water(ChunkSurface s, int i) {
		return s.wet(i) ? s.waterTop(i) : ChunkSurface.NO_WATER;
	}

	@Test
	void shelfHoldsTheWater() {
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		// Shore columns without water beside the top block, by area and by the drop that the shelf would need.
		Map<String, Integer> dry = new java.util.TreeMap<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			long shore = 0;
			long wetShore = 0;
			long lowered = 0;
			long spills = 0;
			long modelSpills = 0;
			long belt = 0;
			long beltOk = 0;
			for (Map.Entry<String, int[]> area : areas(f).entrySet()) {
				int cx0 = (area.getValue()[0] >> 4) - GRID / 2;
				int cz0 = (area.getValue()[1] >> 4) - GRID / 2;
				for (int x = cx0 * 16; x < (cx0 + GRID) * 16; x++) {
					for (int z = cz0 * 16; z < (cz0 + GRID) * 16; z++) {
						SurfaceFixture.Chunk c = f.chunk(x >> 4, z >> 4);
						ChunkSurface s = c.surface();
						int i = (x & 15) << 4 | (z & 15);
						int flags = s.flags(i);
						if ((flags & ChunkSurface.SHELF) != 0 && s.top(i) < s.modelTop(i)) {
							lowered++;
						}
						if ((flags & ChunkSurface.SHORE_BED) != 0) {
							belt++;
							Material bed = s.topMaterial(i);
							boolean stream = Habitat.biome(c.codes()[i]) == HabitatBiome.STREAM;
							beltOk += bed == (stream ? Material.GRAVEL : Material.MUD) ? 1 : 0;
						}
						boolean inner = x > cx0 * 16 && x < (cx0 + GRID) * 16 - 1 && z > cz0 * 16 && z < (cz0 + GRID) * 16 - 1;
						if (!inner) {
							continue;
						}
						int[][] nbrs = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
						if ((flags & ChunkSurface.SHORE) != 0) {
							shore++;
							int top = s.top(i);
							boolean ok = false;
							int maxW = Integer.MIN_VALUE;
							for (int[] n : nbrs) {
								SurfaceFixture.Chunk nc = f.chunk(n[0] >> 4, n[1] >> 4);
								int j = (n[0] & 15) << 4 | (n[1] & 15);
								if (nc.surface().wet(j)) {
									maxW = Math.max(maxW, nc.surface().waterTop(j));
								}
								if (nc.surface().wet(j) && nc.surface().top(j) < top && nc.surface().waterTop(j) >= top) {
									ok = true;
								}
							}
							if (ok) {
								wetShore++;
							} else {
								dry.merge(scale + " " + area.getKey() + " drop " + (s.modelTop(i) - maxW), 1, Integer::sum);
							}
						}
						int w = water(s, i);
						if (w == ChunkSurface.NO_WATER) {
							continue;
						}
						for (int[] n : nbrs) {
							SurfaceFixture.Chunk nc = f.chunk(n[0] >> 4, n[1] >> 4);
							ChunkSurface ns = nc.surface();
							int j = (n[0] & 15) << 4 | (n[1] & 15);
							// Water at Y w flows to the neighbor when the neighbor has air at w.
							boolean open = ns.top(j) < w && water(ns, j) < w;
							if (!open) {
								continue;
							}
							ColumnSample ms = c.columns()[i];
							ColumnSample mn = nc.columns()[j];
							int modelWater = f.builder.waterTopY(ms, f.maxY());
							int modelTop = s.modelTop(i);
							int modelNeighborWater = f.builder.waterTopY(mn, f.maxY());
							boolean modelOpen = modelWater > modelTop && ns.modelTop(j) < modelWater
									&& (modelNeighborWater <= ns.modelTop(j) || modelNeighborWater < modelWater);
							if (modelOpen && modelWater == w) {
								modelSpills++;
							} else {
								spills++;
								if (failures.size() < 20) {
									failures.add(String.format(Locale.ROOT, "%s %s: water at (%d, %d, %d) flows to (%d, %d), top %d, flags %d / %d",
											scale, area.getKey(), x, w, z, n[0], n[1], ns.top(j), flags, ns.flags(j)));
								}
							}
						}
					}
				}
			}
			double share = (double) wetShore / Math.max(1, shore);
			report.append(String.format(Locale.ROOT, "%s: shore columns %d, with water beside the top block %d (%.1f%%), lowered "
					+ "shelf columns %d, new open water edges %d, open water edges of the model %d, shore bed %d (%d mud or gravel); ",
					scale, shore, wetShore, 100 * share, lowered, spills, modelSpills, belt, beltOk));
			if (shore < 200 || share < 0.9) {
				failures.add(scale + ": shore share " + share + " of " + shore);
			}
			if (lowered == 0) {
				failures.add(scale + ": no shelf column was lowered");
			}
			if (belt < 100 || beltOk != belt) {
				failures.add(scale + ": shore bed " + beltOk + " of " + belt);
			}
		}
		System.out.println(report);
		System.out.println("shore columns without water beside the top: " + dry);
		assertTrue(failures.isEmpty(), failures + "; " + report);
	}

	@Test
	void microreliefShares() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			Map<String, int[]> a = areas(f);
			for (String name : List.of("alder_carr", "bog_woodland")) {
				HabitatBiome biome = name.equals("alder_carr") ? HabitatBiome.ALDER_CARR : HabitatBiome.BOG_WOODLAND;
				int cx0 = (a.get(name)[0] >> 4) - GRID / 2;
				int cz0 = (a.get(name)[1] >> 4) - GRID / 2;
				long n = 0;
				long puddles = 0;
				long mud = 0;
				long hummocks = 0;
				for (int cx = cx0; cx < cx0 + GRID; cx++) {
					for (int cz = cz0; cz < cz0 + GRID; cz++) {
						SurfaceFixture.Chunk c = f.chunk(cx, cz);
						ChunkSurface s = c.surface();
						for (int i = 0; i < 256; i++) {
							int code = c.codes()[i];
							boolean modelWet = f.builder.waterTopY(c.columns()[i], f.maxY()) > s.modelTop(i);
							if (Habitat.biome(code) != biome || Habitat.zone(code) != Zone.NONE || modelWet
									|| (s.flags(i) & (ChunkSurface.SHELF | ChunkSurface.SHORE)) != 0) {
								continue;
							}
							n++;
							int fl = s.flags(i);
							puddles += (fl & ChunkSurface.PUDDLE) != 0 ? 1 : 0;
							mud += (fl & ChunkSurface.PUDDLE_MUD) != 0 ? 1 : 0;
							if ((fl & ChunkSurface.HUMMOCK) != 0) {
								hummocks++;
								assertEquals(s.modelTop(i) + 1, s.top(i));
								assertEquals(Microrelief.hummock(biome), s.topMaterial(i));
							}
							if ((fl & ChunkSurface.PUDDLE) != 0) {
								assertEquals(s.modelTop(i) - 1, s.top(i));
								assertEquals(s.modelTop(i), s.waterTop(i));
								assertEquals(Material.MUD, s.topMaterial(i));
							}
						}
					}
				}
				double puddleShare = (double) (puddles + mud) / Math.max(1, n);
				double hummockShare = (double) hummocks / Math.max(1, n);
				double expectedPuddles = Microrelief.puddleShare(biome, Zone.NONE);
				double expectedHummocks = Microrelief.hummockShare(biome, Zone.NONE);
				report.append(String.format(Locale.ROOT, "%s %s: %d columns, puddle places %.1f%% (%.1f%% expected; %d with water, %d mud), "
						+ "hummocks %.1f%% (%.1f%% expected); ", scale, name, n, 100 * puddleShare, 100 * expectedPuddles, puddles, mud,
						100 * hummockShare, 100 * expectedHummocks));
				if (n < 300 || Math.abs(puddleShare - expectedPuddles) > 0.08 || Math.abs(hummockShare - expectedHummocks) > 0.08
						|| puddles < 0.25 * (puddles + mud)) {
					failures.add(scale + " " + name);
				}
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "; " + report);
	}

	@Test
	void planIsDeterministic() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture a = new SurfaceFixture(scale, true);
			SurfaceFixture b = new SurfaceFixture(scale, true);
			int[] p = scale == PolandScale.REALISTIC ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022};
			for (int k = 0; k < 4; k++) {
				ChunkSurface sa = a.chunk((p[0] >> 4) + k, p[1] >> 4).surface();
				ChunkSurface sb = b.chunk((p[0] >> 4) + k, p[1] >> 4).surface();
				for (int i = 0; i < 256; i++) {
					assertEquals(sa.top(i), sb.top(i));
					assertEquals(sa.waterTop(i), sb.waterTop(i));
					assertEquals(sa.flags(i), sb.flags(i));
					for (int y = sa.top(i) - 8; y <= sa.top(i) + 2; y++) {
						assertEquals(sa.material(i, y), sb.material(i, y));
					}
				}
			}
		}
	}
}
