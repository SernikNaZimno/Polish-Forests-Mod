package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Bank shelf, shore belt of the bed and micro-relief of step S6 (docs/03-m2-biomy.md §7.2, §7.3, §7.6), on surface
 * plans of grids of 6 × 6 chunks in both scales, in the areas of the shelf zones and lakes, the large river, the places
 * of the review of S6 (oxbow lakes, tunnel valley lakes, lake reedbed, a gameplay-scale stream and bog woodland) and a
 * mountain stream of the Beskids:
 * <ul>
 * <li>no water can flow in a place where the model did not already let it (every water block of the plan has a solid
 * block or water beside it at the same Y, also across chunk borders), and water flowing from the open edges of the model
 * (steps of the river level) reaches no ground that the shelf removed;</li>
 * <li>the shelf makes no step: no two neighboring dry columns, one of them lowered, differ by 2 or more blocks where the
 * model differs by less than 1 (round 1 of the review: walls of 2–3 blocks at the inner edge of the shelf), and steps of
 * the model grow by at most 1;</li>
 * <li>at least 90% of the shore columns of the shelf zones and lake shores have water beside their top block, no dry
 * ground of the plan lies below a water surface within 2 blocks, and no channel stretch without water gets a ditch;</li>
 * <li>the shore belt of the bed is mud (gravel in streams) and the beds of the mountain streams are gravel or
 * cobblestone, never bare rock;</li>
 * <li>puddles and hummocks keep their shares, also on the chunk edge.</li>
 * </ul>
 * The plans are built exactly as in {@code fill()}, so the game test only confirms them in the world.
 */
@Tag("slow")
class BankShelfTest {
	private static final int GRID = 6;
	/** Reach of water flowing from a source over air (blocks). */
	private static final int FLOW = 7;

	/**
	 * Areas of a scale: the large river of the stage measurement, the places of the review of S6 and the first places of
	 * the habitats searched along a spiral.
	 */
	private static Map<String, int[]> areas(SurfaceFixture f) {
		Map<String, int[]> out = new LinkedHashMap<>();
		boolean real = f.scale == PolandScale.REALISTIC;
		out.put("river", real ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022});
		if (real) {
			out.put("oxbow_lake", new int[] {-4_389, 2_169});
			out.put("lake_reedbed", new int[] {-4_160, 4_096});
			out.put("tunnel_lake", new int[] {4_301, -23_367});
			// Review of S6, round 2: a stepped mountain stream (levels 202-212 every 10-15 blocks, open edges across chunk
			// borders) and a seam between channels.
			out.put("cascade", new int[] {59_458, 136_152});
			out.put("channel_seam", new int[] {87_432, -42_036});
			// Step S6b (walls of 2 blocks found by the review of S6, round 2): the fade of a stepped stream changed by a water
			// entering the ramp's window at Chebyshev 3, and an oxbow lake beyond a chunk border that only one of two
			// neighbors saw (its fields end a few blocks from the shore).
			out.put("fade_step", new int[] {74_601, -33_251});
			out.put("oxbow_border", new int[] {-27_778, -138_754});
		} else {
			out.put("oxbow_lake", new int[] {1_643, -452});
			out.put("tunnel_lake", new int[] {-20_280, 10_620});
			out.put("willow_poplar", new int[] {-640, 3_200});
			out.put("stream", new int[] {-134, -52});
			out.put("bog_woodland_stream", new int[] {210, 54});
			// Review of S6, round 2: a confluence with 3-block pillars, oxbow lake banks near a channel, a stepped stream.
			out.put("confluence", new int[] {17_085, 16_600});
			out.put("oxbow_near_channel", new int[] {-14_271, 22_788});
			out.put("stepped_stream", new int[] {20_028, -8_612});
			// Step S6b, as in the realistic scale.
			out.put("fade_step", new int[] {7_350, -19_466});
			out.put("fade_step_b", new int[] {13_574, -5_053});
			out.put("oxbow_border", new int[] {-16_353, -19_489});
		}
		put(out, f, "tall_herbs", 0, 0, p -> Habitat.zone(f.code(p[0], p[1])) == Zone.TALL_HERBS);
		put(out, f, "point_bar", 0, 0, p -> Habitat.zone(f.code(p[0], p[1])) == Zone.POINT_BAR);
		put(out, f, "willow_scrub", 0, 0, p -> Habitat.zone(f.code(p[0], p[1])) == Zone.WILLOW_SCRUB);
		put(out, f, "lake", 0, 0, p -> f.model.sample(p[0], p[1]).waterKind().isLake());
		put(out, f, "shore_reedbed", 0, 0, p -> Habitat.zone(f.code(p[0], p[1])) == Zone.SHORE_REEDBED);
		put(out, f, "alder_carr", 0, 0, p -> patch(f, p, HabitatBiome.ALDER_CARR));
		put(out, f, "bog_woodland", 0, 0, p -> patch(f, p, HabitatBiome.BOG_WOODLAND));
		// Review of S6, round 2: puddles on the chunk edge of raised bogs (rare in the realistic scale: start at a known one).
		int[] bog = real ? new int[] {-230_158, -137_746} : new int[] {0, 0};
		put(out, f, "raised_bog", bog[0], bog[1], p -> patch(f, p, HabitatBiome.RAISED_BOG));
		// A mountain stream in the Beskids of the stage measurement (review of S6: bare rock in the gameplay-scale beds).
		int[] beskids = real ? new int[] {154_834, 1_058_738} : new int[] {27_609, 3_254};
		put(out, f, "mountain_stream", beskids[0], beskids[1], p -> Habitat.biome(f.code(p[0], p[1])) == HabitatBiome.STREAM);
		return out;
	}

	private static void put(Map<String, int[]> out, SurfaceFixture f, String name, int x0, int z0, Predicate<int[]> test) {
		// Narrow zones on a fine spiral, biomes (raised bogs are rare in the realistic scale) on a coarse one.
		int step = name.equals("alder_carr") || name.equals("bog_woodland") || name.equals("raised_bog") ? 160 : name.equals("mountain_stream") ? 16 : 40;
		int[] p = f.find(x0, z0, step, 60_000, test);
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

	/** Plans of a grid of chunks with lookups by block coordinates. */
	static final class Grid {
		final SurfaceFixture f;
		final int x0;
		final int z0;
		final int size;

		Grid(SurfaceFixture f, int[] center) {
			this.f = f;
			this.x0 = ((center[0] >> 4) - GRID / 2) << 4;
			this.z0 = ((center[1] >> 4) - GRID / 2) << 4;
			this.size = GRID << 4;
		}

		boolean in(int x, int z) {
			return x >= x0 && z >= z0 && x < x0 + size && z < z0 + size;
		}

		SurfaceFixture.Chunk chunk(int x, int z) {
			return f.chunk(x >> 4, z >> 4);
		}

		ChunkSurface plan(int x, int z) {
			return chunk(x, z).surface();
		}

		static int index(int x, int z) {
			return (x & 15) << 4 | (z & 15);
		}

		int top(int x, int z) {
			return plan(x, z).top(index(x, z));
		}

		/** Top after the shelf, before the micro-relief (puddles −1, hummocks +1). */
		int shelfTop(int x, int z) {
			ChunkSurface s = plan(x, z);
			int i = index(x, z);
			int fl = s.flags(i);
			return s.top(i) + ((fl & ChunkSurface.PUDDLE) != 0 ? 1 : 0) - ((fl & ChunkSurface.HUMMOCK) != 0 ? 1 : 0);
		}

		int water(int x, int z) {
			ChunkSurface s = plan(x, z);
			int i = index(x, z);
			return s.wet(i) ? s.waterTop(i) : ChunkSurface.NO_WATER;
		}

		int modelTop(int x, int z) {
			return plan(x, z).modelTop(index(x, z));
		}

		int modelWater(int x, int z) {
			return plan(x, z).modelWater(index(x, z));
		}

		int flags(int x, int z) {
			return plan(x, z).flags(index(x, z));
		}
	}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	@Test
	void shelfHoldsTheWater() {
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		// Shore columns without water beside the top block, by area and by the drop that the shelf would need.
		Map<String, Integer> dry = new TreeMap<>();
		Map<String, String> perArea = new TreeMap<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			long samples0 = SurfaceBuilder.OUTSIDE_SAMPLES.sum();
			long[] total = new long[15];
			// Shore share and grown model steps without the areas of round 2 (stepped water, where the guard keeps the
			// banks at the upper water level on purpose): [shore, with water, lowered, grown].
			long[] shares = new long[4];
			for (Map.Entry<String, int[]> area : areas(f).entrySet()) {
				Grid g = new Grid(f, area.getValue());
				long[] n = area(g, area.getKey(), failures, dry);
				for (int k = 0; k < n.length; k++) {
					total[k] += n[k];
				}
				if (!ROUND2_AREAS.contains(area.getKey())) {
					shares[0] += n[0];
					shares[1] += n[1];
					shares[2] += n[2];
					shares[3] += n[5];
				}
				perArea.put(scale + " " + area.getKey(), String.format(Locale.ROOT, "shore %d/%d, lowered %d, walls %d/%d/%d, "
						+ "flow into removed ground %d, ditch %d, below water %d, stream bed %d/%d", n[1], n[0], n[2], n[4], n[5],
						n[6], n[7], n[8], n[9], n[12], n[11]));
			}
			double share = (double) shares[1] / Math.max(1, shares[0]);
			report.append(String.format(Locale.ROOT, "%s: %.1f neighbor samples outside the chunk per chunk in %d chunks; ", scale,
					(double) (SurfaceBuilder.OUTSIDE_SAMPLES.sum() - samples0) / Math.max(1, f.chunks()), f.chunks()));
			report.append(String.format(Locale.ROOT, "%s: shore columns %d, with water beside the top block %d (%.1f%%), lowered "
					+ "columns %d, new open water edges %d, steps from the shelf: >= 2 on flat model ground %d, model step 1 grown "
					+ "to >= 2 %d, model step >= 2 grown by more than 1 %d; water from the model's open edges flowing over removed "
					+ "ground %d columns; lowered columns without water within 4 blocks %d; dry ground below water within 2 blocks "
					+ "%d; shore bed %d (%d mud or gravel); mountain stream bed %d (%d gravel or cobblestone); ",
					scale, total[0], total[1], 100.0 * total[1] / Math.max(1, total[0]), total[2], total[3], total[4], total[5], total[6], total[7], total[8],
					total[9], total[13], total[14], total[11], total[12]));
			report.append(String.format(Locale.ROOT, "%s without the areas of round 2: shore columns %d, with water beside the "
					+ "top block %.1f%%, model step 1 grown to >= 2 %d at %d lowered columns; ", scale, shares[0], 100 * share,
					shares[3], shares[2]));
			if (shares[0] < 200 || share < 0.9) {
				failures.add(scale + ": shore share " + share + " of " + shares[0]);
			}
			if (total[2] == 0) {
				failures.add(scale + ": no column was lowered");
			}
			if (total[3] > 0 || total[4] > 0 || total[6] > 0 || total[7] > 0 || total[8] > 0 || total[9] > 0) {
				failures.add(scale + ": new open edges " + total[3] + ", walls on flat ground " + total[4]
						+ ", grown model steps " + total[6] + ", flow over removed ground " + total[7] + ", ditches " + total[8]
						+ ", ground below water " + total[9]);
			}
			// A model step of 1 block may grow to 2 where the ramp goes down a bank rising away from the water (rare but on the
			// steep banks of the stepped mountain stream).
			if (shares[3] > 0.01 * shares[2]) {
				failures.add(scale + ": model steps of 1 grown to 2 or more: " + shares[3] + " at " + shares[2] + " lowered columns");
			}
			if (total[13] < 100 || total[14] != total[13]) {
				failures.add(scale + ": shore bed " + total[14] + " of " + total[13]);
			}
			if (total[11] < 30 || total[12] != total[11]) {
				failures.add(scale + ": mountain stream bed " + total[12] + " of " + total[11]);
			}
		}
		System.out.println(report);
		System.out.println("per area: " + perArea);
		System.out.println("shore columns without water beside the top: " + dry);
		assertTrue(failures.isEmpty(), failures + "; " + report);
	}

	/**
	 * Areas of the review of S6, round 2 (stepped water, a seam, a confluence) and of step S6b (stepped water), left out
	 * of the shore share.
	 */
	private static final java.util.Set<String> ROUND2_AREAS = java.util.Set.of("cascade", "channel_seam", "confluence",
			"oxbow_near_channel", "stepped_stream", "fade_step", "fade_step_b");

	/** Number of random water areas per scale of {@link #shelfOverRandomWaterAreas}. */
	private static final int RANDOM_AREAS = 120;

	/**
	 * Random areas of rivers and lakes (review of S6, round 2: confluences, braided channels, seams between two streams,
	 * oxbow lakes near a channel and stepped mountain streams, which the named areas lack): no step of 2 or more on flat
	 * model ground, no water flowing from an open edge of the model over removed ground, no new open water edges, no
	 * ditch and no dry ground below nearby water.
	 */
	@Test
	void shelfOverRandomWaterAreas() {
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			long samples0 = SurfaceBuilder.OUTSIDE_SAMPLES.sum();
			boolean real = scale == PolandScale.REALISTIC;
			int range = real ? 150_000 : 25_000;
			long[] total = new long[15];
			List<String> worst = new ArrayList<>();
			int areas = 0;
			for (int k = 0; areas < RANDOM_AREAS && k < 50_000; k++) {
				long h = pl.polishforests.worldgen.landscape.Noise.mix(k * 0x9E3779B97F4A7C15L + (real ? 1007 : 1013));
				int x = (int) Math.floorMod(h, 2L * range) - range;
				int z = (int) Math.floorMod(h >>> 32, 2L * range) - range;
				pl.polishforests.worldgen.landscape.ColumnSample s = f.model.sample(x, z);
				if (!s.hasWater() || !(s.waterKind() == pl.polishforests.worldgen.landscape.WaterKind.RIVER
						|| s.waterKind().isLake())) {
					continue;
				}
				areas++;
				List<String> local = new ArrayList<>();
				long[] n = area(new Grid(f, new int[] {x, z}), "a" + areas + "@" + x + "," + z, local, new TreeMap<>());
				for (int j = 0; j < n.length; j++) {
					total[j] += n[j];
				}
				if (n[3] + n[4] + n[6] + n[7] + n[8] + n[9] > 0) {
					worst.add(String.format(Locale.ROOT, "(%d, %d): open %d, walls %d, grown %d, flow %d, ditch %d, below %d; %s",
							x, z, n[3], n[4], n[6], n[7], n[8], n[9], local.subList(0, Math.min(3, local.size()))));
				}
			}
			double share = (double) total[1] / Math.max(1, total[0]);
			report.append(String.format(Locale.ROOT, "%s: %d areas, %d chunks, %.1f neighbor samples outside the chunk per chunk, "
					+ "shore columns %d (%.1f%% with water beside the top), lowered %d, new open edges %d, steps >= 2 on flat model "
					+ "ground %d, model step 1 grown to >= 2 %d, grown by more than 1 %d, flow over removed ground %d, ditches %d, "
					+ "ground below water %d; ", scale, areas, f.chunks(),
					(double) (SurfaceBuilder.OUTSIDE_SAMPLES.sum() - samples0) / Math.max(1, f.chunks()), total[0], 100 * share,
					total[2], total[3], total[4], total[5], total[6], total[7], total[8], total[9]));
			if (areas < RANDOM_AREAS || total[3] + total[4] + total[6] + total[7] + total[8] + total[9] > 0
					|| total[5] > 0.01 * total[2] || share < 0.9) {
				failures.add(scale + ": " + worst.subList(0, Math.min(10, worst.size())));
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "; " + report);
	}

	/**
	 * Counts of one area: 0 shore columns, 1 with water beside the top, 2 lowered columns, 3 new open water edges,
	 * 4 steps ≥ 2 by the shelf on flat model ground, 5 model steps of 1 grown to ≥ 2, 6 model steps ≥ 2 grown by more than
	 * 1, 7 columns of removed ground reached by water flowing from an open edge, 8 lowered columns without model water within
	 * 4 blocks, 9 dry columns below a plan water surface within 2 blocks, 10 shore bed columns not mud or gravel (failures
	 * added), 11 mountain stream bed columns, 12 of them gravel or cobblestone, 13 shore bed columns, 14 of them mud
	 * (gravel in streams).
	 */
	static long[] area(Grid g, String name, List<String> failures, Map<String, Integer> dry) {
		long[] n = new long[15];
		SurfaceFixture f = g.f;
		String tag = f.scale + " " + name;
		for (int x = g.x0; x < g.x0 + g.size; x++) {
			for (int z = g.z0; z < g.z0 + g.size; z++) {
				SurfaceFixture.Chunk c = g.chunk(x, z);
				ChunkSurface s = c.surface();
				int i = Grid.index(x, z);
				int flags = s.flags(i);
				boolean wet = s.wet(i);
				if ((flags & ChunkSurface.SHELF) != 0 && g.shelfTop(x, z) < s.modelTop(i)) {
					n[2]++;
					// No ditch: a lowered column has model water within 4 blocks (the reach of the ramp).
					boolean water = false;
					for (int dx = -4; dx <= 4 && !water; dx++) {
						for (int dz = -4; dz <= 4 && !water; dz++) {
							water = g.modelWater(x + dx, z + dz) != ChunkSurface.NO_WATER;
						}
					}
					if (!water) {
						n[8]++;
						if (failures.size() < 30) {
							failures.add(String.format(Locale.ROOT, "%s: lowered (%d, %d) without water nearby", tag, x, z));
						}
					}
				}
				if ((flags & ChunkSurface.SHORE_BED) != 0) {
					boolean stream = Habitat.biome(c.codes()[i]) == HabitatBiome.STREAM;
					n[13]++;
					n[14] += s.topMaterial(i) == (stream ? Material.GRAVEL : Material.MUD) ? 1 : 0;
					if (s.topMaterial(i) != (stream ? Material.GRAVEL : Material.MUD)) {
						n[10]++;
						if (failures.size() < 30) {
							failures.add(String.format(Locale.ROOT, "%s: shore bed (%d, %d) is %s", tag, x, z, s.topMaterial(i)));
						}
					}
				}
				if (name.equals("mountain_stream") && wet && Habitat.biome(c.codes()[i]) == HabitatBiome.STREAM) {
					n[11]++;
					Material bed = s.topMaterial(i);
					n[12] += bed == Material.GRAVEL || bed == Material.COBBLESTONE ? 1 : 0;
				}
				boolean inner = x > g.x0 && x < g.x0 + g.size - 1 && z > g.z0 && z < g.z0 + g.size - 1;
				if (!inner) {
					continue;
				}
				if ((flags & ChunkSurface.SHORE) != 0) {
					n[0]++;
					int top = s.top(i);
					boolean ok = false;
					int maxW = Integer.MIN_VALUE;
					for (int[] d : DIRS) {
						int w = g.water(x + d[0], z + d[1]);
						if (w != ChunkSurface.NO_WATER) {
							maxW = Math.max(maxW, w);
							ok |= g.top(x + d[0], z + d[1]) < top && w >= top;
						}
					}
					if (ok) {
						n[1]++;
					} else {
						dry.merge(tag + " drop " + (s.modelTop(i) - maxW), 1, Integer::sum);
					}
				}
				if (!wet) {
					// Dry ground of the plan below a water surface within 2 blocks (a floor that a dug block would flood).
					int top = g.shelfTop(x, z);
					if (top < s.modelTop(i)) {
						int high = Integer.MIN_VALUE;
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								if (g.in(x + dx, z + dz) && (g.flags(x + dx, z + dz) & ChunkSurface.PUDDLE) == 0) {
									high = Math.max(high, g.water(x + dx, z + dz));
								}
							}
						}
						if (high != ChunkSurface.NO_WATER && high > top) {
							n[9]++;
							if (failures.size() < 30) {
								failures.add(String.format(Locale.ROOT, "%s: dry (%d, %d) at %d below water %d nearby", tag, x, z, top,
										high));
							}
						}
					}
					// Steps between neighboring dry columns, one of them lowered by the shelf.
					for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
						int nx = x + d[0];
						int nz = z + d[1];
						if (!g.in(nx, nz) || g.water(nx, nz) != ChunkSurface.NO_WATER) {
							continue;
						}
						boolean lowered = top < s.modelTop(i) || g.shelfTop(nx, nz) < g.modelTop(nx, nz);
						if (!lowered) {
							continue;
						}
						int plan = Math.abs(top - g.shelfTop(nx, nz));
						int model = Math.abs(s.modelTop(i) - g.modelTop(nx, nz));
						if (plan >= 2 && model == 0) {
							n[4]++;
						} else if (plan >= 2 && model == 1) {
							n[5]++;
						} else if (model >= 2 && plan > model + 1) {
							n[6]++;
						}
						if (plan >= 2 && plan > model && failures.size() < 30 && model == 0) {
							failures.add(String.format(Locale.ROOT, "%s: step %d at (%d, %d)-(%d, %d), model %d", tag, plan, x, z, nx,
									nz, model));
						}
					}
					continue;
				}
				int w = s.waterTop(i);
				for (int[] d : DIRS) {
					int nx = x + d[0];
					int nz = z + d[1];
					// Water at Y w flows to the neighbor when the neighbor has air at w.
					boolean open = g.top(nx, nz) < w && g.water(nx, nz) < w;
					if (!open) {
						continue;
					}
					int modelWater = s.modelWater(i);
					boolean modelOpen = modelWater != ChunkSurface.NO_WATER && g.modelTop(nx, nz) < modelWater
							&& g.modelWater(nx, nz) < modelWater;
					if (!(modelOpen && modelWater == w)) {
						n[3]++;
						if (failures.size() < 30) {
							failures.add(String.format(Locale.ROOT, "%s: water at (%d, %d, %d) flows to (%d, %d), top %d, flags %d / %d",
									tag, x, w, z, nx, nz, g.top(nx, nz), flags, g.flags(nx, nz)));
						}
					} else {
						n[7] += flow(g, nx, nz, w, failures, tag);
					}
				}
			}
		}
		return n;
	}

	/**
	 * Water flowing at Y {@code w} from an open edge of the model into the column (x, z): spreads over the columns with
	 * air at {@code w} (ground and water below it) up to {@link #FLOW} blocks; returns the number of reached columns whose
	 * model top was at {@code w} or higher, i.e. ground that the shelf or the micro-relief removed.
	 */
	private static int flow(Grid g, int x, int z, int w, List<String> failures, String tag) {
		int removed = 0;
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		queue.add(new int[] {x, z, 1});
		seen.add((long) x << 32 | (z & 0xFFFFFFFFL));
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			if (g.modelTop(p[0], p[1]) >= w && (g.flags(p[0], p[1]) & ChunkSurface.PUDDLE) == 0) {
				removed++;
				if (failures.size() < 30) {
					failures.add(String.format(Locale.ROOT, "%s: water flowing at %d reaches removed ground at (%d, %d)", tag, w,
							p[0], p[1]));
				}
			}
			if (p[2] >= FLOW) {
				continue;
			}
			for (int[] d : DIRS) {
				int nx = p[0] + d[0];
				int nz = p[1] + d[1];
				if (!g.in(nx, nz) || g.top(nx, nz) >= w || g.water(nx, nz) >= w) {
					continue;
				}
				if (seen.add((long) nx << 32 | (nz & 0xFFFFFFFFL))) {
					queue.add(new int[] {nx, nz, p[2] + 1});
				}
			}
		}
		return removed;
	}

	@Test
	void microreliefShares() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			Map<String, int[]> a = areas(f);
			for (String name : List.of("alder_carr", "bog_woodland", "raised_bog")) {
				HabitatBiome biome = switch (name) {
					case "alder_carr" -> HabitatBiome.ALDER_CARR;
					case "bog_woodland" -> HabitatBiome.BOG_WOODLAND;
					default -> HabitatBiome.RAISED_BOG;
				};
				int cx0 = (a.get(name)[0] >> 4) - GRID / 2;
				int cz0 = (a.get(name)[1] >> 4) - GRID / 2;
				long n = 0;
				long puddles = 0;
				long mud = 0;
				long hummocks = 0;
				// Puddle places on the chunk edge and inside: with water, of all.
				long[] edge = new long[2];
				long[] inside = new long[2];
				for (int cx = cx0; cx < cx0 + GRID; cx++) {
					for (int cz = cz0; cz < cz0 + GRID; cz++) {
						SurfaceFixture.Chunk c = f.chunk(cx, cz);
						ChunkSurface s = c.surface();
						for (int i = 0; i < 256; i++) {
							int code = c.codes()[i];
							boolean modelWet = s.modelWater(i) != ChunkSurface.NO_WATER;
							if (Habitat.biome(code) != biome || Habitat.zone(code) != Zone.NONE || modelWet
									|| (s.flags(i) & (ChunkSurface.SHELF | ChunkSurface.SHORE)) != 0) {
								continue;
							}
							n++;
							int fl = s.flags(i);
							boolean puddle = (fl & ChunkSurface.PUDDLE) != 0;
							puddles += puddle ? 1 : 0;
							mud += (fl & ChunkSurface.PUDDLE_MUD) != 0 ? 1 : 0;
							if ((fl & (ChunkSurface.PUDDLE | ChunkSurface.PUDDLE_MUD)) != 0) {
								int x = i >> 4;
								int z = i & 15;
								long[] k = x == 0 || x == 15 || z == 0 || z == 15 ? edge : inside;
								k[0] += puddle ? 1 : 0;
								k[1]++;
							}
							if ((fl & ChunkSurface.HUMMOCK) != 0) {
								hummocks++;
								assertEquals(s.modelTop(i) + 1, s.top(i));
								assertEquals(Microrelief.hummock(biome), s.topMaterial(i));
							}
							if (puddle) {
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
				double edgeHold = (double) edge[0] / Math.max(1, edge[1]);
				double insideHold = (double) inside[0] / Math.max(1, inside[1]);
				report.append(String.format(Locale.ROOT, "%s %s: %d columns, puddle places %.1f%% (%.1f%% expected; %d with water, %d mud), "
						+ "hummocks %.1f%% (%.1f%% expected), puddle places holding water on the chunk edge %.0f%% of %d, inside "
						+ "%.0f%% of %d; ", scale, name, n, 100 * puddleShare, 100 * expectedPuddles, puddles, mud,
						100 * hummockShare, 100 * expectedHummocks, 100 * edgeHold, edge[1], 100 * insideHold, inside[1]));
				if (n < 300 || Math.abs(puddleShare - expectedPuddles) > 0.08 || Math.abs(hummockShare - expectedHummocks) > 0.08
						|| puddles < 0.25 * (puddles + mud)) {
					failures.add(scale + " " + name);
				}
				// No chunk grid: the edge holds water about as often as the inside (review of S6, round 1).
				if (edge[1] < 30 || edgeHold < 0.6 * insideHold) {
					failures.add(scale + " " + name + ": puddles on the chunk edge " + edgeHold + " vs " + insideHold);
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
			// The second fixture builds the chunks in another order, after their neighbors, so its plans read the
			// neighbors' summaries from the shared cache (ColumnCache) instead of sampling them: the same plans.
			for (int k = 4; k >= -1; k--) {
				b.chunk((p[0] >> 4) + k, (p[1] >> 4) - 1);
				b.chunk((p[0] >> 4) + k, (p[1] >> 4) + 1);
			}
			for (int k = 3; k >= 0; k--) {
				b.chunk((p[0] >> 4) + k, p[1] >> 4);
			}
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
