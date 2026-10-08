package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;

/**
 * Soils of step S6 (docs/03-m2-biomy.md §7, §12.1): the top of every forest soil belongs to
 * {@code #minecraft:supports_vegetation} (the tag read from the game jar, so seedlings and {@code would_survive} work),
 * the loose cover in blocks in both scales, and at most 32 block states in a section at the surface.
 */
class SoilTest {
	/** Areas of the state count (north-west corner in blocks) per scale: lowland, Beskids, large river, coast. */
	private static final int[][] AREAS_REAL = {{-40_000, 25_000}, {154_834 - 64, 1_058_738 - 64}, {-19_484 - 64, 11_253 - 64},
			{-233_358 - 64, -136_402 - 64}};
	private static final int[][] AREAS_GAMEPLAY = {{-40_000, 25_000}, {27_609 - 64, 3_254 - 64}, {-1_851 - 64, 6_022 - 64},
			{-5_782 - 64, -1_802 - 64}, {-20_500 - 64, 10_900 - 64}};
	private static final int STATE_LIMIT = 32;

	@Test
	void forestSoilTopsSupportVegetation() {
		Set<String> supports = tag("supports_vegetation");
		assertTrue(supports.contains("minecraft:grass_block") && supports.contains("minecraft:mud")
				&& supports.contains("minecraft:moss_block") && supports.contains("minecraft:podzol"), supports.toString());
		// Exceptions of §7.4: point bars and gravel bars, and the beach and dune zones (strandline, embryo dune).
		Set<Zone> bars = EnumSet.of(Zone.POINT_BAR, Zone.GRAVEL_BAR, Zone.STRANDLINE, Zone.EMBRYO_DUNE);
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (HabitatBiome biome : HabitatBiome.values()) {
			if (!biome.isForest()) {
				continue;
			}
			for (Zone zone : Zone.values()) {
				if (bars.contains(zone)) {
					continue;
				}
				Soil soil = Soil.forBiome(biome, zone);
				for (double q1 = 0; q1 < 1; q1 += 0.05) {
					for (double q2 = 0; q2 < 1; q2 += 0.05) {
						for (double lowShore : new double[] {0, 1}) {
							Material top = SoilBlocks.top(SoilBlocks.dry(soil, zone, biome, q1, q2, lowShore));
							checked++;
							if (!supports.contains(top.id())) {
								failures.add(biome.id() + "/" + zone.id() + "/" + soil + " -> " + top.id());
							}
						}
					}
				}
			}
		}
		assertTrue(checked > 10_000);
		assertTrue(failures.isEmpty(), "forest soil tops outside #supports_vegetation: " + new HashSet<>(failures));
	}

	/** The dry tops of the beach and the white dune are sand, of the dunes and the gravel bar dry vegetation blocks. */
	@Test
	void openSoilTops() {
		Set<String> dry = tag("supports_dry_vegetation");
		for (double q = 0; q < 1; q += 0.05) {
			assertEquals(Material.SAND, SoilBlocks.top(SoilBlocks.dry(Soil.BEACH_SAND, Zone.NONE, HabitatBiome.BEACH, q, q, 1)));
			assertEquals(Material.SAND, SoilBlocks.top(SoilBlocks.dry(Soil.DUNE_SAND, Zone.NONE, HabitatBiome.WHITE_DUNE, q, q, 1)));
			assertTrue(dry.contains(SoilBlocks.top(SoilBlocks.dry(Soil.DUNE_SAND, Zone.NONE, HabitatBiome.GRAY_DUNE, q, q, 1)).id()));
		}
	}

	/**
	 * Cover in blocks (§7.1): with {@code cover_in_blocks} the rock starts at {@code topBlockY(surface − coverDepth)}, so
	 * in the realistic lowland a meter is a block and in the gameplay scale the cover is about 0.4 block per meter; without
	 * it the old rule (cover = ⌈coverDepth⌉ blocks). Checked on dry columns without shelf and micro-relief.
	 */
	@Test
	void coverBlocksInBothScales() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture blocks = new SurfaceFixture(scale, true);
			SurfaceFixture legacy = new SurfaceFixture(scale, false);
			double meters = 0;
			double cover = 0;
			int n = 0;
			for (int cx = 0; cx < 3; cx++) {
				for (int cz = 0; cz < 3; cz++) {
					int ccx = (-40_000 >> 4) + cx * 7;
					int ccz = (25_000 >> 4) + cz * 7;
					SurfaceFixture.Chunk a = blocks.chunk(ccx, ccz);
					SurfaceFixture.Chunk b = legacy.chunk(ccx, ccz);
					for (int i = 0; i < 256; i++) {
						ColumnSample s = a.columns()[i];
						ChunkSurface sa = a.surface();
						if (sa.wet(i) || sa.flags(i) != 0 || s.coverDepth() < 1) {
							continue;
						}
						int top = sa.top(i);
						int expected = Math.max(0, top - Math.min(top, scale.vertical().topBlockY(s.surface() - s.coverDepth())));
						assertEquals(expected, sa.coverBlocks(i), "cover in blocks at column " + i);
						assertEquals(Math.max(0, (int) Math.ceil(s.coverDepth())), b.surface().coverBlocks(i), "legacy cover");
						meters += s.coverDepth();
						cover += sa.coverBlocks(i);
						n++;
					}
				}
			}
			assertTrue(n > 1_000, scale + ": only " + n + " columns");
			double perMeter = cover / meters;
			System.out.printf(Locale.ROOT, "%s: cover %.2f blocks per meter of coverDepth (mean %.1f m, %d columns)%n", scale,
					perMeter, meters / n, n);
			if (scale == PolandScale.REALISTIC) {
				assertTrue(perMeter > 0.9 && perMeter < 1.1, "realistic cover " + perMeter + " blocks per meter");
			} else {
				// Fewer blocks than meters (also below sea level the mapping is the same power law).
				assertTrue(perMeter > 0.25 && perMeter < 0.9, "gameplay cover " + perMeter + " blocks per meter");
			}
		}
	}

	/** At most 32 block states in each section that contains the ground surface or water, in every area of both scales. */
	@Test
	void sectionsAtTheSurfaceHaveFewStates() {
		int worst = 0;
		String where = "";
		int sections = 0;
		Set<String> supports = tag("supports_vegetation");
		long forest = 0;
		long rock = 0;
		List<String> bare = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true);
			for (int[] area : scale == PolandScale.REALISTIC ? AREAS_REAL : AREAS_GAMEPLAY) {
				for (int cx = 0; cx < 6; cx++) {
					for (int cz = 0; cz < 6; cz++) {
						SurfaceFixture.Chunk chunk = f.chunk((area[0] >> 4) + cx, (area[1] >> 4) + cz);
						ChunkSurface s = chunk.surface();
						// Real columns: the dry top of a forest is a vegetation block, or rock where the model has no cover.
						for (int i = 0; i < 256; i++) {
							int code = chunk.codes()[i];
							Zone zone = Habitat.zone(code);
							if (s.wet(i) || !Habitat.biome(code).isForest() || zone == Zone.POINT_BAR || zone == Zone.GRAVEL_BAR) {
								continue;
							}
							forest++;
							Material top = s.topMaterial(i);
							if (s.coverBlocks(i) == 0) {
								rock++;
							} else if (!supports.contains(top.id()) && bare.size() < 10) {
								bare.add(scale + " " + Habitat.biome(code).id() + "/" + zone.id() + " " + top.id());
							}
						}
						int low = Integer.MAX_VALUE;
						int high = Integer.MIN_VALUE;
						for (int i = 0; i < 256; i++) {
							low = Math.min(low, Math.max(f.minY(), s.top(i) - 12));
							high = Math.max(high, Math.max(s.top(i), s.waterTop(i)));
						}
						for (int y0 = Math.floorDiv(low, 16) * 16; y0 <= high; y0 += 16) {
							Set<Material> states = EnumSet.noneOf(Material.class);
							for (int i = 0; i < 256; i++) {
								for (int dy = 0; dy < 16; dy++) {
									states.add(s.material(i, y0 + dy));
								}
							}
							sections++;
							if (states.size() > worst) {
								worst = states.size();
								where = scale + " chunk (" + ((area[0] >> 4) + cx) + ", " + ((area[1] >> 4) + cz) + ") y " + y0
										+ ": " + states;
							}
						}
					}
				}
			}
		}
		System.out.printf(Locale.ROOT, "most states in a surface section: %d of %d sections (%s)%n", worst, sections, where);
		System.out.printf(Locale.ROOT, "dry forest columns: %d, with bare rock on top (no cover in the model) %d%n", forest, rock);
		assertTrue(worst <= STATE_LIMIT, "too many states: " + where);
		assertTrue(bare.isEmpty(), "forest tops outside #supports_vegetation: " + bare);
		assertTrue(forest > 10_000 && rock == 0, "forest " + forest + ", rock tops " + rock);
	}

	// ------------------------------------------------------------------ vanilla tags from the game jar

	private static Set<String> tag(String name) {
		Set<String> out = new HashSet<>();
		collect(name, out, new HashSet<>());
		return out;
	}

	private static void collect(String name, Set<String> out, Set<String> seen) {
		if (!seen.add(name)) {
			return;
		}
		InputStream in = resource("/data/minecraft/tags/block/" + name + ".json");
		if (in == null) {
			throw new AssertionError("no vanilla tag " + name);
		}
		try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("values")) {
				String v = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
				if (v.startsWith("#")) {
					collect(v.substring("#minecraft:".length()), out, seen);
				} else {
					out.add(v);
				}
			}
		} catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	private static InputStream resource(String path) {
		return SoilTest.class.getResourceAsStream(path);
	}
}
