package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Cost of the crown territories (step V5, decision PW-9), single thread, no assertions; the result goes into
 * docs/04-pionowy-wycinek.md ("Stan po V5").
 *
 * <p>Areas of 8 × 8 chunks around the oak-hornbeam and fresh pine sites of the slice ({@code slice_sites.txt},
 * {@code oak_hornbeam_1} and {@code fresh_pine_1}) at both scales, in both vegetation modes. Per chunk of the area:
 * <ul>
 * <li><b>(a)</b> the territories of the trees of the chunk ({@link CrownTerritories.Session#chunk}) with vacancies from
 * the habitat codes (precomputed, as the {@code ChunkHabitats} of the game: open ground) and a stand-in for the palette
 * that gives a tree to {@value #TREE_SHARE} of the forest candidates ({@link PaletteVacancies}: exact vacancies of the
 * chunk's own candidates, sure ones of its neighbors): in a fresh session
 * per chunk (the worst case: the candidate pattern and the sites of a 80 × 80 block window computed anew) and in one
 * session per area in raster order (a per-thread session, as {@code ChunkEcotones}); also without vacancies (the pure
 * variant);</li>
 * <li><b>(b)</b> the habitat codes of the kept candidates in the ring of chunks at radius 2 (the 16 chunks without
 * {@code ChunkHabitats} at the FEATURES stage): enumeration of the candidates, {@link LandscapeModel#sample} and
 * {@link HabitatClassifier#classify} of each, without a cache shared between chunks (fresh, like (a) in a fresh session)
 * and with a per-thread cache of the codes of each ring chunk in raster order (like (a) in a per-thread session: about 5
 * new ring chunks per chunk). The territories of (b) would come on top of that;</li>
 * <li>for scale: sampling and classifying the 256 columns of the chunk, the core of the TERRAIN stage.</li>
 * </ul>
 * The model's tile caches are warmed on the area first, as in a world where the neighbors were generated. Rounds
 * alternate the order of the variants; the medians are reported.
 *
 * <p>Tagged {@code cost}: only {@code ./gradlew costTest} (one JVM, alone).
 */
@Tag("cost")
class CrownTerritoryCostTest {
	private static final long SEED = 20260927L;
	private static final int ROUNDS = Math.max(3, Integer.getInteger("polishforests.cost.runs", 7));
	private static final int SIZE = 8;
	/** Share of the forest candidates that grow a tree in the palette stand-in (about 11 trees per chunk, as N1). */
	private static final double TREE_SHARE = 0.65;

	private static volatile long sink;

	private record Site(String name, LandscapeScale scale, int x, int z) {
	}

	private static final Site[] SITES = {
			new Site("oak_hornbeam_1", LandscapeScale.REALISTIC, 438, -730),
			new Site("fresh_pine_1", LandscapeScale.REALISTIC, -699, 1924),
			new Site("oak_hornbeam_1", LandscapeScale.GAMEPLAY, -1785, 886),
			new Site("fresh_pine_1", LandscapeScale.GAMEPLAY, -1195, -583),
	};

	@Test
	void reportTerritoryCost() {
		for (Site site : SITES) {
			LandscapeModel model = new LandscapeModel(SEED, site.scale(), 1.0);
			for (HabitatClassifier.Mode mode : HabitatClassifier.Mode.values()) {
				HabitatClassifier classifier = mode == HabitatClassifier.Mode.NATURAL
						? new HabitatClassifier(SEED, site.scale(), mode) : new HabitatClassifier(SEED, model, mode);
				measure(site, model, classifier, mode);
			}
		}
	}

	private static void measure(Site site, LandscapeModel model, HabitatClassifier classifier, HabitatClassifier.Mode mode) {
		int cx0 = Math.floorDiv(site.x(), 16) - SIZE / 2;
		int cz0 = Math.floorDiv(site.z(), 16) - SIZE / 2;
		// Habitat codes of the area and radius 1 around it (the ChunkHabitats of the game), which also warms the tile caches.
		int span = SIZE + 2;
		int[] codes = new int[span * span * 256];
		int forest = 0;
		for (int i = 0; i < span; i++) {
			for (int j = 0; j < span; j++) {
				for (int c = 0; c < 256; c++) {
					int x = (cx0 - 1 + i) * 16 + (c >> 4);
					int z = (cz0 - 1 + j) * 16 + (c & 15);
					int code = classifier.classify(model.sample(x, z), x, z);
					codes[(i * span + j) * 256 + c] = code;
					forest += Habitat.biome(code).group() == HabitatBiome.Group.FOREST ? 1 : 0;
				}
			}
		}
		int bx = (cx0 - 1) * 16;
		int bz = (cz0 - 1) * 16;
		int side = span * 16;
		Vacancies open = new PaletteVacancies(SEED, TREE_SHARE, (x, z) -> {
			int i = x - bx;
			int j = z - bz;
			if (i < 0 || j < 0 || i >= side || j >= side) {
				throw new IllegalStateException("outside the precomputed habitats: " + x + ", " + z);
			}
			int code = codes[((i >> 4) * span + (j >> 4)) * 256 + ((i & 15) << 4 | (j & 15))];
			return Habitat.biome(code).group() != HabitatBiome.Group.FOREST;
		});
		CrownTerritories territories = new CrownTerritories(SEED, new PatchyStandMap(SEED, 0.15));
		int chunks = SIZE * SIZE;
		// Warm-up of the JIT on the variants.
		for (int r = 0; r < 2; r++) {
			coldA(territories, cx0, cz0, open);
			ringB(model, classifier, cx0, cz0, new int[1]);
			cachedRingB(model, classifier, cx0, cz0, new int[1]);
			terrain(model, classifier, cx0, cz0);
		}
		double[] coldA = new double[ROUNDS];
		double[] warmA = new double[ROUNDS];
		double[] pureA = new double[ROUNDS];
		double[] ringB = new double[ROUNDS];
		double[] cachedB = new double[ROUNDS];
		double[] terrain = new double[ROUNDS];
		int[] samples = new int[1];
		int[] cachedSamples = new int[1];
		for (int r = 0; r < ROUNDS; r++) {
			for (int v = 0; v < 6; v++) {
				int which = r % 2 == 0 ? v : 5 - v;
				long t0 = System.nanoTime();
				switch (which) {
					case 0 -> coldA(territories, cx0, cz0, open);
					case 1 -> warmA(territories, cx0, cz0, open);
					case 2 -> coldA(territories, cx0, cz0, null);
					case 3 -> {
						samples[0] = 0;
						ringB(model, classifier, cx0, cz0, samples);
					}
					case 4 -> {
						cachedSamples[0] = 0;
						cachedRingB(model, classifier, cx0, cz0, cachedSamples);
					}
					default -> terrain(model, classifier, cx0, cz0);
				}
				double us = (System.nanoTime() - t0) / 1e3 / chunks;
				switch (which) {
					case 0 -> coldA[r] = us;
					case 1 -> warmA[r] = us;
					case 2 -> pureA[r] = us;
					case 3 -> ringB[r] = us;
					case 4 -> cachedB[r] = us;
					default -> terrain[r] = us;
				}
			}
		}
		int trunks = 0;
		CrownTerritories.Session s = territories.session();
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				trunks += s.chunk(cx0 + i, cz0 + j, open).size();
			}
		}
		System.out.printf(Locale.ROOT, "[crown cost] %s %s %s (%d%% forest columns): per chunk (median of %d, µs): "
						+ "(a) fresh session %.0f, per-thread session %.0f, pure (no vacancies) %.0f, for %.1f trees; "
						+ "(b) fresh %.0f for %.0f samples of the radius-2 ring (%.2f µs/sample), per-thread cache %.0f for %.0f "
						+ "samples; 256 columns sampled and classified %.0f; (b)/(a) fresh %.1f, per-thread %.1f; "
						+ "(b fresh)/TERRAIN core %.2f%n",
				site.scale().id(), site.name(), mode.name().toLowerCase(Locale.ROOT), 100 * forest / (span * span * 256),
				ROUNDS, median(coldA), median(warmA), median(pureA), trunks / (double) chunks, median(ringB),
				samples[0] / (double) chunks, median(ringB) * chunks / samples[0], median(cachedB),
				cachedSamples[0] / (double) chunks, median(terrain), median(ringB) / median(coldA),
				median(cachedB) / median(warmA), median(ringB) / median(terrain));
		System.out.printf(Locale.ROOT, "[crown cost] rounds (a) fresh %s; (b) %s; TERRAIN core %s%n", format(coldA),
				format(ringB), format(terrain));
	}

	private static void coldA(CrownTerritories t, int cx0, int cz0, Vacancies open) {
		long acc = 0;
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				acc += t.session().chunk(cx0 + i, cz0 + j, open).size();
			}
		}
		sink += acc;
	}

	private static void warmA(CrownTerritories t, int cx0, int cz0, Vacancies open) {
		long acc = 0;
		CrownTerritories.Session s = t.session();
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				acc += s.chunk(cx0 + i, cz0 + j, open).size();
			}
		}
		sink += acc;
	}

	/** Variant (b): codes of the kept candidates in the 16 chunks at Chebyshev distance 2 of each chunk. */
	private static void ringB(LandscapeModel model, HabitatClassifier classifier, int cx0, int cz0, int[] samples) {
		long acc = 0;
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				int cx = cx0 + i;
				int cz = cz0 + j;
				TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
				for (int di = -2; di <= 2; di++) {
					for (int dj = -2; dj <= 2; dj++) {
						if (Math.max(Math.abs(di), Math.abs(dj)) != 2) {
							continue;
						}
						int minX = (cx + di) * 16;
						int minZ = (cz + dj) * 16;
						long[] local = new long[1];
						int[] count = new int[1];
						candidates.forEach(minX, minZ, minX + 15, minZ + 15, (x, z) -> {
							ColumnSample sample = model.sample(x, z);
							local[0] += classifier.classify(sample, x, z);
							count[0]++;
						});
						acc += local[0];
						samples[0] += count[0];
					}
				}
			}
		}
		sink += acc;
	}

	/**
	 * Variant (b) with a per-thread cache: the codes of the kept candidates of each ring chunk are kept between the chunks
	 * of the area (raster order, one session per area), so only the ring chunks not seen before are sampled.
	 */
	private static void cachedRingB(LandscapeModel model, HabitatClassifier classifier, int cx0, int cz0, int[] samples) {
		long acc = 0;
		TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
		Map<Long, int[]> cache = new HashMap<>();
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				int cx = cx0 + i;
				int cz = cz0 + j;
				for (int di = -2; di <= 2; di++) {
					for (int dj = -2; dj <= 2; dj++) {
						if (Math.max(Math.abs(di), Math.abs(dj)) != 2) {
							continue;
						}
						int rx = cx + di;
						int rz = cz + dj;
						int[] ring = cache.computeIfAbsent((long) rx << 32 | (rz & 0xFFFF_FFFFL), k -> {
							int[] out = new int[64];
							int[] n = new int[1];
							candidates.forEach(rx * 16, rz * 16, rx * 16 + 15, rz * 16 + 15, (x, z) -> {
								out[n[0]++] = classifier.classify(model.sample(x, z), x, z);
							});
							samples[0] += n[0];
							return Arrays.copyOf(out, n[0]);
						});
						for (int code : ring) {
							acc += code;
						}
					}
				}
			}
		}
		sink += acc;
	}

	/** The core of TERRAIN for scale: sample and classify the 256 columns of each chunk. */
	private static void terrain(LandscapeModel model, HabitatClassifier classifier, int cx0, int cz0) {
		long acc = 0;
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				for (int c = 0; c < 256; c++) {
					int x = (cx0 + i) * 16 + (c >> 4);
					int z = (cz0 + j) * 16 + (c & 15);
					acc += classifier.classify(model.sample(x, z), x, z);
				}
			}
		}
		sink += acc;
	}

	private static double median(double[] v) {
		double[] s = v.clone();
		Arrays.sort(s);
		return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
	}

	private static String format(double[] v) {
		StringBuilder b = new StringBuilder();
		for (double d : v) {
			b.append(String.format(Locale.ROOT, " %.0f", d));
		}
		return b.toString().strip();
	}
}
