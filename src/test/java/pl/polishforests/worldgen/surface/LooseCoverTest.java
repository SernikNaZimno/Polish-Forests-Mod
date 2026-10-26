package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Loose cover above the rock (step K1, docs/04-pionowy-wycinek.md): the thin cover of new worlds (4–10 blocks in the
 * lowland, smooth, in the layer order of the deposits, never rock in a side face) and the realistic cover of worlds saved
 * before K1, which must stay the cover those worlds had.
 */
class LooseCoverTest {
	/**
	 * Hashes of {@link #planHash} for the realistic cover, computed at commit 8d34f5e (before step K1) with
	 * {@code new SurfaceBuilder(seed, vertical, true, false)}: a world saved before K1 must keep exactly these plans.
	 */
	private static final Map<PolandScale, Long> BEFORE_K1 = Map.of(
			PolandScale.REALISTIC, 0xfa723bdceaf7efdeL,
			PolandScale.GAMEPLAY, 0x3fa8254cd71ef089L);

	/** Chunks of {@link #planHash}: 40 chunks spread over about ±72 km. */
	private static int[] hashChunk(int k) {
		return new int[] {(k * 7_919) % 9_000 - 4_500, (k * 6_007) % 9_000 - 4_500};
	}

	/** Hash of the plans of the {@link #hashChunk} chunks: tops, water, rock tops, profiles, flags and the upper blocks. */
	static long planHash(SurfaceFixture f) {
		long h = 0xcbf29ce484222325L;
		for (int k = 0; k < 40; k++) {
			int[] c = hashChunk(k);
			ChunkSurface s = f.chunk(c[0], c[1]).surface();
			for (int i = 0; i < 256; i++) {
				int[] v = {s.top(i), s.waterTop(i), s.rockTop[i], s.profile[i], s.flags(i)};
				for (int x : v) {
					h = (h ^ x) * 0x100000001b3L;
				}
				for (int y = s.rockTop[i] - 2; y <= s.top(i); y++) {
					h = (h ^ s.material(i, y).ordinal()) * 0x100000001b3L;
				}
			}
		}
		return h;
	}

	/** Settings of a world saved before K1 (all fields of the presets of that time, no {@code loose_cover}). */
	private static PolandSettings savedBeforeK1(PolandScale scale) {
		String json = "{\"version\": 2, \"scale\": \"" + scale.getSerializedName() + "\", \"region_scale\": 1.0, "
				+ "\"agriculture\": false, \"managed_forest_share\": 0.85, \"alien_species\": true, \"cover_in_blocks\": true}";
		return PolandSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	void worldsSavedBeforeK1KeepTheirCover() {
		for (PolandScale scale : PolandScale.values()) {
			PolandSettings saved = savedBeforeK1(scale);
			assertEquals(LooseCover.REALISTIC, saved.looseCover());
			SurfaceFixture f = new SurfaceFixture(scale, saved.coverInBlocks(), saved.looseCover());
			assertEquals((long) BEFORE_K1.get(scale), planHash(f), scale + ": the realistic cover changed");
		}
	}

	/** Columns of the thin-cover belt: model cover above {@link SurfaceBuilder#THIN_FROM_METERS} (lowland, sea floor). */
	private static boolean thinBelt(ColumnSample s) {
		return s.coverDepth() > SurfaceBuilder.THIN_FROM_METERS;
	}

	private static boolean rock(Material m) {
		return m == Material.STONE || m == Material.DEEPSLATE || m == Material.ANDESITE || m == Material.BEDROCK;
	}

	/**
	 * The distribution of the cover thickness in blocks in the lowland, thin against realistic, over 120 chunks of each
	 * scale; the surface (tops, water, profiles, flags) and the blocks above the thin rock are the same in both, the
	 * foothills and mountains keep their cover, and the thin cover keeps the layer order: soil or bed profile, at least
	 * {@link SurfaceBuilder#THIN_MIN_DEPOSIT} blocks of the deposits of the substrate, then rock.
	 */
	@Test
	void thinCoverIsFourToTenBlocksInTheLowland() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture thin = new SurfaceFixture(scale, true, LooseCover.THIN);
			SurfaceFixture real = new SurfaceFixture(scale, true, LooseCover.REALISTIC);
			int[] thinHist = new int[64];
			int[] realHist = new int[256];
			int lowland = 0;
			int other = 0;
			Map<Substrate, int[]> bySubstrate = new EnumMap<>(Substrate.class);
			for (int k = 0; k < 120; k++) {
				int cx = (k * 4_831) % 7_000 - 3_500;
				int cz = (k * 2_957) % 7_000 - 3_500;
				SurfaceFixture.Chunk a = thin.chunk(cx, cz);
				ChunkSurface t = a.surface();
				ChunkSurface r = real.chunk(cx, cz).surface();
				assertArrayEquals(r.top, t.top, "tops");
				assertArrayEquals(r.waterTop, t.waterTop, "water");
				assertArrayEquals(r.profile, t.profile, "profiles");
				assertArrayEquals(r.flags, t.flags, "flags");
				for (int i = 0; i < 256; i++) {
					ColumnSample s = a.columns()[i];
					if (!thinBelt(s)) {
						assertEquals(r.rockTop[i], t.rockTop[i], "foothills and mountains keep their cover");
						other++;
						continue;
					}
					lowland++;
					int cover = t.coverBlocks(i);
					thinHist[Math.min(63, cover)]++;
					realHist[Math.min(255, r.coverBlocks(i))]++;
					int[] stats = bySubstrate.computeIfAbsent(s.substrate(), x -> new int[2]);
					stats[0]++;
					stats[1] += cover;
					assertTrue(t.rockTop[i] >= r.rockTop[i], "the thin cover is never thicker");
					int profile = SoilBlocks.depth(t.profile[i]);
					assertTrue(cover - profile >= SurfaceBuilder.THIN_MIN_DEPOSIT || cover == r.coverBlocks(i),
							"deposits under the profile: " + (cover - profile) + " at " + cx + "," + cz + "/" + i);
					for (int y = t.rockTop[i] + 1; y <= t.top(i); y++) {
						assertEquals(r.material(i, y), t.material(i, y), "blocks above the thin rock");
						assertTrue(!rock(t.material(i, y)), "rock above the rock top");
					}
					assertTrue(rock(t.material(i, t.rockTop[i])), "rock under the cover: " + t.material(i, t.rockTop[i]));
				}
			}
			int p05 = percentile(thinHist, lowland, 0.05);
			int p50 = percentile(thinHist, lowland, 0.50);
			int p95 = percentile(thinHist, lowland, 0.95);
			int realP50 = percentile(realHist, lowland, 0.50);
			int inRange = 0;
			for (int c = 4; c <= 10; c++) {
				inRange += thinHist[c];
			}
			StringBuilder subs = new StringBuilder();
			bySubstrate.forEach((sub, st) -> subs.append(String.format(Locale.ROOT, " %s %d (mean %.1f)", sub, st[0],
					(double) st[1] / st[0])));
			System.out.printf(Locale.ROOT, "%s: %d lowland columns, %d others; thin cover p5 %d, median %d, p95 %d, "
					+ "%.1f%% in 4-10 blocks; realistic median %d;%s%n", scale, lowland, other, p05, p50, p95,
					100.0 * inRange / lowland, realP50, subs);
			assertTrue(lowland > 15_000, scale + ": only " + lowland + " lowland columns");
			assertTrue(p05 >= 4 && p50 >= 5 && p50 <= 8 && p95 <= 10, scale + ": thin cover p5 " + p05 + ", median " + p50
					+ ", p95 " + p95);
			assertTrue(inRange >= 0.95 * lowland, scale + ": " + inRange + " of " + lowland + " columns in 4-10 blocks");
			for (int c = 5; c <= 10; c++) {
				assertTrue(thinHist[c] >= 0.01 * lowland, scale + ": cover " + c + " in only " + thinHist[c] + " columns");
			}
			assertTrue(realP50 >= (scale == PolandScale.REALISTIC ? 35 : 15), scale + ": realistic median " + realP50);
			for (Substrate sub : new Substrate[] {Substrate.SAND, Substrate.GLACIAL_TILL, Substrate.ALLUVIUM,
					Substrate.RIVERBED}) {
				assertTrue(bySubstrate.containsKey(sub), scale + ": no thin column on " + sub);
			}
		}
	}

	private static int percentile(int[] hist, int n, double p) {
		long need = (long) Math.ceil(p * n);
		long sum = 0;
		for (int v = 0; v < hist.length; v++) {
			sum += hist[v];
			if (sum >= need) {
				return v;
			}
		}
		return hist.length - 1;
	}

	/**
	 * No rock in a side face: the rock of a thin column lies below the top of each of its four neighbors, in the chunk
	 * (final tops) and across the chunk border (the neighbor chunk's final tops), on 6 × 6 chunk blocks in each scale
	 * around a lowland river valley, around a sea cliff (the cliff patch of the golden terrain test) and on plain lowland.
	 */
	@Test
	void noRockInSideFaces() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true, LooseCover.THIN);
			int[] valley = f.find(-30_000, 20_000, 48, 4_000, p -> {
				ColumnSample s = f.model.sample(p[0], p[1]);
				return thinBelt(s) && s.substrate() == Substrate.RIVERBED && s.hasWater();
			});
			assertTrue(valley != null, scale + ": no lowland river");
			// The center of the golden terrain patch "cliff" of the seed (golden_terrain_m2.txt).
			int[] slope = scale == PolandScale.REALISTIC ? new int[] {-181_586, -207_822} : new int[] {-3_216, 6_103};
			int faces = 0;
			int steep = 0;
			for (int[] origin : new int[][] {{(valley[0] >> 4) - 3, (valley[1] >> 4) - 3},
					{(slope[0] >> 4) - 3, (slope[1] >> 4) - 3}, {200, -300}}) {
				for (int cx = origin[0]; cx < origin[0] + 6; cx++) {
					for (int cz = origin[1]; cz < origin[1] + 6; cz++) {
						SurfaceFixture.Chunk c = f.chunk(cx, cz);
						ChunkSurface s = c.surface();
						for (int i = 0; i < 256; i++) {
							if (!thinBelt(c.columns()[i])) {
								continue;
							}
							int x = i >> 4;
							int z = i & 15;
							int[][] nbs = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
							for (int[] nb : nbs) {
								int ncx = cx + Math.floorDiv(nb[0], 16);
								int ncz = cz + Math.floorDiv(nb[1], 16);
								int ni = Math.floorMod(nb[0], 16) << 4 | Math.floorMod(nb[1], 16);
								int nTop = f.chunk(ncx, ncz).surface().top(ni);
								faces++;
								if (nTop < s.top(i) - SurfaceBuilder.THIN_MIN_COVER) {
									steep++;
								}
								assertTrue(s.rockTop[i] <= nTop, scale + ": rock in the side face of column " + i + " of "
										+ cx + "," + cz + " (rock top " + s.rockTop[i] + ", neighbor top " + nTop + ")");
							}
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s: %d side faces checked, %d of them higher than the thinnest cover; sea cliff at "
					+ "%d,%d%n", scale, faces, steep, slope[0], slope[1]);
			// Faces higher than the thinnest cover are rare in the lowland (none in these blocks at the time of K1, also at
			// the cliff: its slope in blocks is gentle), so the guard is a safety net; the count is only reported.
		}
	}

	/**
	 * Rule Z10: the thin cover thickness changes by at most one block between neighbors, takes every value of its range,
	 * has no straight edges along the axes (no boundary line longer than two chunks at one x or z), and the cover at the
	 * chunk borders is the same as inside the chunks (no seam).
	 */
	@Test
	void thinDepositIsSmoothWithoutStraightEdges() {
		SurfaceFixture f = new SurfaceFixture(PolandScale.GAMEPLAY, true, LooseCover.THIN);
		SurfaceBuilder b = f.builder;
		int n = 768;
		int[][] d = new int[n][n];
		int[] hist = new int[SurfaceBuilder.THIN_MAX_COVER + 1];
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				d[x][z] = b.thinCover(x - 5_000, z + 3_000);
				hist[d[x][z]]++;
			}
		}
		int xChanges = 0;
		int zChanges = 0;
		int longestX = 0;
		int longestZ = 0;
		int[] runX = new int[n];
		int[] runZ = new int[n];
		for (int a = 0; a < n - 1; a++) {
			for (int c = 0; c < n - 1; c++) {
				assertTrue(Math.abs(d[a + 1][c] - d[a][c]) <= 1 && Math.abs(d[a][c + 1] - d[a][c]) <= 1, "step at " + a + "," + c);
				// A boundary between x = a and a + 1 at row c continues the boundary of row c − 1 at the same x.
				if (d[a + 1][c] != d[a][c]) {
					xChanges++;
					runX[a]++;
					longestX = Math.max(longestX, runX[a]);
				} else {
					runX[a] = 0;
				}
			}
		}
		for (int c = 0; c < n - 1; c++) {
			for (int a = 0; a < n - 1; a++) {
				if (d[a][c + 1] != d[a][c]) {
					zChanges++;
					runZ[c]++;
					longestZ = Math.max(longestZ, runZ[c]);
				} else {
					runZ[c] = 0;
				}
			}
		}
		System.out.printf(Locale.ROOT, "thin cover: shares %s, boundaries x %d z %d, longest straight boundary %d / %d%n",
				java.util.Arrays.toString(hist), xChanges, zChanges, longestX, longestZ);
		for (int v = SurfaceBuilder.THIN_MIN_COVER; v <= SurfaceBuilder.THIN_MAX_COVER; v++) {
			assertTrue(hist[v] > 0.02 * n * n, "cover " + v + " in " + hist[v] + " columns");
		}
		double ratio = (double) xChanges / zChanges;
		assertTrue(ratio > 0.8 && ratio < 1.25, "boundaries along x and z: " + ratio);
		assertTrue(longestX < 32 && longestZ < 32, "straight boundary " + longestX + " / " + longestZ);

		// No seam at the chunk borders: the mean cover of the border columns equals the mean inside.
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture g = new SurfaceFixture(scale, true, LooseCover.THIN);
			double border = 0;
			double inside = 0;
			int nb = 0;
			int ni = 0;
			for (int k = 0; k < 80; k++) {
				SurfaceFixture.Chunk c = g.chunk((k * 3_571) % 5_000 - 2_500, (k * 1_931) % 5_000 - 2_500);
				for (int i = 0; i < 256; i++) {
					ChunkSurface s = c.surface();
					if (!thinBelt(c.columns()[i]) || s.wet(i) || s.flags(i) != 0) {
						continue;
					}
					int cover = s.coverBlocks(i);
					int x = i >> 4;
					int z = i & 15;
					if (x == 0 || x == 15 || z == 0 || z == 15) {
						border += cover;
						nb++;
					} else if (x >= 3 && x <= 12 && z >= 3 && z <= 12) {
						inside += cover;
						ni++;
					}
				}
			}
			double mb = border / nb;
			double mi = inside / ni;
			System.out.printf(Locale.ROOT, "%s: cover at the chunk borders %.2f, inside %.2f blocks%n", scale, mb, mi);
			assertTrue(Math.abs(mb - mi) < 0.3, scale + ": seam at the chunk borders " + mb + " vs " + mi);
		}
	}

	/** The thin plans are pure functions of the chunk: the same in any order of building the chunks. */
	@Test
	void thinPlansDoNotDependOnTheOrder() {
		SurfaceFixture a = new SurfaceFixture(PolandScale.GAMEPLAY, true, LooseCover.THIN);
		SurfaceFixture b = new SurfaceFixture(PolandScale.GAMEPLAY, true, LooseCover.THIN);
		int[][] chunks = new int[25][];
		for (int k = 0; k < 25; k++) {
			chunks[k] = new int[] {-1_890 + k % 5, 1_250 + k / 5};
		}
		for (int k = 0; k < 25; k++) {
			a.chunk(chunks[k][0], chunks[k][1]);
		}
		for (int k = 24; k >= 0; k--) {
			ChunkSurface sb = b.chunk(chunks[k][0], chunks[k][1]).surface();
			ChunkSurface sa = a.chunk(chunks[k][0], chunks[k][1]).surface();
			assertArrayEquals(sa.rockTop, sb.rockTop, "rock tops of chunk " + k);
			assertArrayEquals(sa.top, sb.top, "tops of chunk " + k);
		}
	}
}
