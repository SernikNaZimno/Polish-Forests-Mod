package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * The wide blend of the forests of the slice ({@link ForestBlend}, step Z1 of docs/04-pionowy-wycinek.md, rule Z10, N15):
 * the ramp across a straight border (width, symmetry, the 50% at the border), the meander, the soils and the other
 * biomes untouched, and the transitions of the tree stand and ground layer palettes on the transects of the slice
 * (slice_sites.txt, the classifier on real samples, both scales and modes) within the norm of 20–100 m·k.
 */
class ForestBlendTest {
	private static final long SEED = 20260927L;
	private static final int FRESH = EcotoneTest.code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
	private static final int MIXED_PINE = EcotoneTest.code(HabitatBiome.MIXED_PINE_FOREST, Zone.NONE);

	private static ForestBlend blend(IntBinaryOperator world, double k) {
		return new ForestBlend(SEED, k, world::applyAsInt);
	}

	/** Share of columns with the biome of side b by world x (−w … w − 1), over the rows of many chunks. */
	private static double[] profile(IntBinaryOperator world, double k, Ecotone.Layer layer, long salt, int patch, boolean meander, int w) {
		ForestBlend blend = blend(world, k);
		long[] taken = new long[2 * w];
		long[] all = new long[2 * w];
		int chunks = w / 16;
		for (int cx = -chunks; cx < chunks; cx++) {
			for (int cz = 0; cz < 40; cz++) {
				Ecotone.Region region = EcotoneTest.region(cx, cz, world, k).withBlend(blend);
				int[] eff = Ecotone.effective(region, layer, SEED, salt, patch, meander);
				for (int i = 0; i < 256; i++) {
					int x = (cx << 4) + (i >> 4) + w;
					all[x]++;
					taken[x] += Habitat.biome(eff[i]) == HabitatBiome.MIXED_PINE_FOREST ? 1 : 0;
				}
			}
		}
		double[] out = new double[2 * w];
		for (int i = 0; i < out.length; i++) {
			out[i] = (double) taken[i] / all[i];
		}
		return out;
	}

	/** 10–90% width (blocks) of a rising profile, from the first index at or above 0.1 to the first at or above 0.9. */
	private static int width(double[] p) {
		int a = -1;
		int b = -1;
		for (int i = 0; i < p.length; i++) {
			if (a < 0 && p[i] >= 0.1) {
				a = i;
			}
			if (b < 0 && p[i] >= 0.9) {
				b = i;
			}
		}
		return b - a;
	}

	@Test
	void straightBorderRampsOverTheBlendsWidthInBothScales() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator world = (x, z) -> x < 0 ? FRESH : MIXED_PINE;
			int w = 96;
			double[] trees = profile(world, k, Ecotone.Layer.TREES, Ecotone.TREES_SALT, 1, false, w);
			double[] ground = profile(world, k, Ecotone.Layer.PLANTS, Ecotone.GROUND_SALT, Ecotone.GROUND_PATCH, false, w);
			int r = new ForestBlend(SEED, k, (x, z) -> 0).radius();
			for (double[] p : new double[][] {trees, ground}) {
				int width = width(p);
				System.out.printf(Locale.ROOT, "[blend] k %.1f: 10-90%% %d blocks (%.0f m·k), at the border %.2f / %.2f, %d blocks out %.3f / %.3f%n",
						k, width, width / k, p[w - 1], p[w], r, p[w - r - 1], p[w + r]);
				assertTrue(width / k >= 30 && width / k <= 70, "width " + width / k + " m·k");
				assertTrue(Math.abs(p[w - 1] - 0.5) < 0.08 && Math.abs(p[w] - 0.5) < 0.08, "about 50% on both sides of the border");
				assertTrue(p[w - r - 2] < 0.01 && p[w + r + 1] > 0.99, "pure beyond the radius");
				for (int i = 8; i < p.length; i += 8) {
					assertTrue(p[i] >= p[i - 8] - 0.03, "monotone at " + i);
				}
			}
		}
	}

	@Test
	void sharedNodeTableGivesTheSameSharesAsTheKernelSumInAnyOrderAndThread() {
		int[] codes = {FRESH, MIXED_PINE, EcotoneTest.code(HabitatBiome.MIXED_FOREST, Zone.NONE),
				EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE), EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE)};
		// Patches of 40 blocks of the four forests and an alder carr: every node mixes several categories.
		IntBinaryOperator world = (x, z) -> codes[(int) Math.floorMod(
				pl.polishforests.worldgen.landscape.Noise.mix(Math.floorDiv(x, 40) * 31L + Math.floorDiv(z, 40) * 1_000_003L), 5L)];
		for (double k : new double[] {1.0, 0.5}) {
			ForestBlend shared = blend(world, k);
			ForestBlend fresh = blend(world, k);
			List<int[]> chunks = new ArrayList<>();
			for (int cx = -12; cx < 12; cx++) {
				for (int cz = -12; cz < 12; cz++) {
					chunks.add(new int[] {cx, cz});
				}
			}
			java.util.Collections.shuffle(chunks, new java.util.Random(7));
			long before = ForestBlend.NODES.sum();
			// Each chunk's field twice (in parallel, in shuffled order): from the shared table and from the kernel sum.
			List<String> diffs = java.util.Collections.synchronizedList(new ArrayList<>());
			chunks.parallelStream().forEach(c -> {
				ForestBlend.Field f = shared.field(c[0], c[1]);
				float[] a = new float[ForestBlend.SHARES];
				float[] b = new float[ForestBlend.SHARES];
				int s = shared.spacing();
				for (int x = (c[0] << 4) - 20; x < (c[0] << 4) + 36; x += 3) {
					for (int z = (c[1] << 4) - 20; z < (c[1] << 4) + 36; z += 5) {
						f.at(x, z, a);
						int i = Math.floorDiv(x - s / 2, s);
						int j = Math.floorDiv(z - s / 2, s);
						float fx = (x - s / 2 - i * s) / (float) s;
						float fz = (z - s / 2 - j * s) / (float) s;
						float[] n = new float[4 * ForestBlend.SHARES];
						fresh.node(i, j, n, 0);
						fresh.node(i + 1, j, n, ForestBlend.SHARES);
						fresh.node(i, j + 1, n, 2 * ForestBlend.SHARES);
						fresh.node(i + 1, j + 1, n, 3 * ForestBlend.SHARES);
						for (int q = 0; q < ForestBlend.SHARES; q++) {
							float top = n[q] + (n[ForestBlend.SHARES + q] - n[q]) * fx;
							float bottom = n[2 * ForestBlend.SHARES + q] + (n[3 * ForestBlend.SHARES + q] - n[2 * ForestBlend.SHARES + q]) * fx;
							b[q] = top + (bottom - top) * fz;
						}
						if (!Arrays.equals(a, b)) {
							diffs.add(x + "," + z + ": " + Arrays.toString(a) + " vs " + Arrays.toString(b));
						}
					}
				}
			});
			long computed = ForestBlend.NODES.sum() - before;
			int n = (int) Math.ceil((16.0 + 2 * (Ecotone.REACH + Ecotone.WARP)) / shared.spacing()) + 2;
			System.out.printf(Locale.ROOT, "[blend] k %.1f: %d chunks, kernel sums %d (%.1f per chunk, without the table up to about %d), stencil %d%n",
					k, chunks.size(), computed, (double) computed / chunks.size(), n * n, shared.stencilSize());
			assertTrue(diffs.isEmpty(), diffs.size() + " columns differ, e.g. " + (diffs.isEmpty() ? "" : diffs.get(0)));
			assertTrue(computed < chunks.size() * (long) n * n / 3, "the table shares the nodes between chunks: " + computed);
		}
	}

	@Test
	void meanderMovesTheBlendAndOtherBiomesAndSoilsKeepTheirRamps() {
		double k = 1;
		IntBinaryOperator world = (x, z) -> x < 0 ? FRESH : MIXED_PINE;
		ForestBlend blend = blend(world, k);
		Ecotone.Region a = EcotoneTest.region(0, 3, world, k).withBlend(blend);
		Ecotone.Region b = EcotoneTest.region(0, 3, world, k).withBlend(blend);
		assertNotEquals(Arrays.toString(Ecotone.effective(a, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1, true)),
				Arrays.toString(Ecotone.effective(b, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1, false)), "the meander moves the blend");
		// The soils of the forests keep the ramp of Ecotone (8 m·k), with or without a blend.
		Ecotone.Region c = EcotoneTest.region(0, 3, world, k);
		assertEquals(Arrays.toString(Ecotone.effective(c, Ecotone.Layer.SOIL, SEED, 77, 1)),
				Arrays.toString(Ecotone.effective(EcotoneTest.region(0, 3, world, k).withBlend(blend), Ecotone.Layer.SOIL, SEED, 77, 1)));
		// Other forests (a gray alder forest against the oak-hornbeam forest; the alder carr until step F2, the elm-ash forest
		// until step F5) keep the ramp in the plant layers.
		int alder = EcotoneTest.code(HabitatBiome.GRAY_ALDER_FOREST, Zone.NONE);
		int oak = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		IntBinaryOperator other = (x, z) -> x < 0 ? alder : oak;
		ForestBlend otherBlend = blend(other, k);
		for (int cx = -1; cx <= 0; cx++) {
			int[] plain = Ecotone.effective(EcotoneTest.region(cx, 3, other, k), Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
			int[] withBlend = Ecotone.effective(EcotoneTest.region(cx, 3, other, k).withBlend(otherBlend), Ecotone.Layer.PLANTS, SEED,
					Ecotone.PLANTS_SALT, 1);
			for (int i = 0; i < 256; i++) {
				if (Habitat.biome(plain[i]) == HabitatBiome.GRAY_ALDER_FOREST) {
					assertEquals(plain[i], withBlend[i], "a gray alder forest column keeps its ramp");
				}
			}
		}
	}

	/**
	 * The takeover of a neighbor woodland's trees near the slice forests (step Z1). Until step F5 the example was a belt and
	 * a large woodland of the elm-ash forest; since F5 both floodplain forests are forests of the blend and no lowland
	 * woodland is left in {@code NEIGHBORS} (decision F5-g), so no code is taken over, and the takeover's draw itself (a pure
	 * function of the shares) is checked: none below {@value ForestBlend#TAKEOVER_FROM} of the slice forests' share, all at
	 * {@value ForestBlend#TAKEOVER_TO} and above, and the forest taken by the shares.
	 */
	@Test
	void noWoodlandIsTakenOverSinceStepF5AndTheTakeoverDrawFollowsTheShares() {
		for (HabitatBiome b : HabitatBiome.values()) {
			for (Zone z : new Zone[] {Zone.NONE, Zone.SPRING_AREA}) {
				assertTrue(!ForestBlend.takenOver(EcotoneTest.code(b, z)), b + " " + z + " taken over");
			}
		}
		int other = EcotoneTest.code(HabitatBiome.GRAY_ALDER_FOREST, Zone.NONE);
		float[] near = new float[ForestBlend.SHARES];
		near[3] = 0.9F;
		near[ForestBlend.OTHER] = 0.1F;
		float[] far = new float[ForestBlend.SHARES];
		far[3] = 0.4F;
		far[ForestBlend.OTHER] = 0.6F;
		for (int i = 0; i < 1000; i++) {
			double v = i / 1000.0;
			assertEquals(HabitatBiome.OAK_HORNBEAM_FOREST, Habitat.biome(ForestBlend.takeover(other, near, v)), "near " + v);
			assertEquals(other, ForestBlend.takeover(other, far, v), "far " + v);
		}
	}

	@Test
	void provedCodesWithTheBlendEqualTheCodesOfTheColumnsOwnChunk() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator forests = EcotoneProvableTest.world(k);
			int alderCarr = EcotoneTest.code(HabitatBiome.ELM_ASH_FOREST, Zone.NONE);
			pl.polishforests.worldgen.landscape.Noise carr = new pl.polishforests.worldgen.landscape.Noise(SEED).derive("test.blend.carr");
			// The forests of the provable test with patches of a floodplain forest (taken over near the slice forests; the
			// alder carr until step F2, now a forest of the blend).
			IntBinaryOperator w = (x, z) -> carr.at(x, z, 60 * k) > 0.35 ? alderCarr : forests.applyAsInt(x, z);
			ForestBlend blend = blend(w, k);
			for (int cx = -3; cx < 3; cx++) {
				for (int cz = -3; cz < 3; cz++) {
					Ecotone.Region region = EcotoneTest.region(cx, cz, w, k).withBlend(blend);
					int[] own = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
					for (int i = 0; i < 256; i++) {
						assertEquals(own[i], Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, i >> 4, i & 15), "own chunk column " + i);
					}
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx == 0 && dz == 0) {
								continue;
							}
							int[] theirs = Ecotone.effective(EcotoneTest.region(cx + dx, cz + dz, w, k).withBlend(blend), Ecotone.Layer.TREES,
									SEED, Ecotone.TREES_SALT, 1);
							for (int i = 0; i < 256; i++) {
								int lx = dx * 16 + (i >> 4);
								int lz = dz * 16 + (i & 15);
								int p = Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, lx, lz);
								if (p != Ecotone.UNKNOWN) {
									assertEquals(theirs[i], p, "column " + lx + ", " + lz + " of chunk " + cx + ", " + cz);
								}
							}
						}
					}
				}
			}
		}
	}

	/** A transect of slice_sites.txt: scale and line. */
	private record Line(PolandScale scale, int x0, int z0, int x1, int z1) {
	}

	private static List<Line> transects() throws IOException {
		List<Line> out = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(ForestBlendTest.class.getResourceAsStream("/slice_sites.txt"),
				StandardCharsets.UTF_8))) {
			for (String line = r.readLine(); line != null; line = r.readLine()) {
				String[] f = line.trim().split("\\s+");
				if (line.startsWith("#") || f.length < 8 || !f[2].equals("transect")) {
					continue;
				}
				out.add(new Line(PolandScale.valueOf(f[0].toUpperCase(Locale.ROOT)), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
						Integer.parseInt(f[6]), Integer.parseInt(f[7])));
			}
		}
		return out;
	}

	@Test
	void paletteTransitionsOfTheSliceTransectsAreWithinTheNorm() throws IOException {
		List<HabitatBiome> sequence = List.of(ForestBlend.TRANSECT);
		List<Line> lines = transects();
		assertEquals(2, lines.size(), "a transect per scale");
		StringBuilder report = new StringBuilder();
		List<String> outside = new ArrayList<>();
		for (Line t : lines) {
			double k = t.scale().landscape().local();
			LandscapeModel model = new LandscapeModel(SEED, t.scale().landscape(), 1.0);
			for (HabitatClassifier.Mode mode : HabitatClassifier.Mode.values()) {
				HabitatClassifier classifier = new HabitatClassifier(SEED, model, mode);
				ForestBlend.Sampler sampler = (x, z) -> classifier.classify(model.sample(x, z), x, z);
				int steps = (int) Math.ceil(Math.hypot(t.x1() - t.x0(), t.z1() - t.z0()));
				int[] rank = new int[steps + 1];
				for (int i = 0; i <= steps; i++) {
					double x = Math.floor(t.x0() + (t.x1() - t.x0()) * (double) i / steps) + 0.5;
					double z = Math.floor(t.z0() + (t.z1() - t.z0()) * (double) i / steps) + 0.5;
					rank[i] = sequence.indexOf(Habitat.biome(classifier.classify(model.sample(x, z), x, z)));
				}
				for (boolean blended : new boolean[] {false, true}) {
					ForestBlend blend = blended ? new ForestBlend(SEED, k, sampler) : null;
					TransectPalettes.Result r = TransectPalettes.of(sampler, blend, SEED, k, sequence, t.x0() + 0.5, t.z0() + 0.5, t.x1() + 0.5,
							t.z1() + 0.5, steps, rank, 8);
					report.append(String.format(Locale.ROOT, "%n[blend] %s %s %s: trees %s, ground layer %s, field %s m·k", t.scale(), mode,
							blended ? "blend" : "ramps of M2", fmt(r.treeWidths()), fmt(r.groundWidths()), fmt(r.fieldWidths())));
					if (blended) {
						for (double[] widths : new double[][] {r.treeWidths(), r.groundWidths(), r.fieldWidths()}) {
							for (int p = 0; p < widths.length; p++) {
								if (!(widths[p] >= 20 && widths[p] <= 100)) {
									outside.add(t.scale() + " " + mode + " transition " + p + ": " + fmt(widths));
								}
							}
						}
					}
				}
			}
		}
		System.out.println(report);
		assertTrue(outside.isEmpty(), "transitions outside 20-100 m·k: " + outside);
	}

	private static String fmt(double[] w) {
		StringBuilder s = new StringBuilder();
		for (double v : w) {
			s.append(s.isEmpty() ? "" : " / ").append(String.format(Locale.ROOT, "%.1f", v));
		}
		return s.toString();
	}

	/**
	 * Step F1: the lowland beech forest is a forest of the blend; it blends with the oak-hornbeam, the mixed and the mixed
	 * pine-oak forest, not with the fresh pine forest (that pair keeps the ramp of Ecotone); the forests of the transect
	 * keep their indices and all blend with each other, and the beech forest is no longer a neighbor taken over.
	 */
	@Test
	void beechForestBlendsWithItsNeighborsOfStepF1() {
		assertEquals(List.of(HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST, HabitatBiome.MIXED_FOREST,
				HabitatBiome.OAK_HORNBEAM_FOREST), List.of(ForestBlend.TRANSECT));
		assertEquals(HabitatBiome.LOWLAND_BEECH_FOREST, ForestBlend.FORESTS[ForestBlend.BEECH]);
		assertEquals(ForestBlend.FORESTS.length + 1, ForestBlend.SHARES);
		for (int a = 0; a < 4; a++) {
			for (int b = 0; b < 4; b++) {
				assertEquals(a != b, ForestBlend.blends(a, b), a + " " + b);
			}
		}
		assertTrue(ForestBlend.blends(ForestBlend.BEECH, 3) && ForestBlend.blends(2, ForestBlend.BEECH)
				&& ForestBlend.blends(ForestBlend.BEECH, 1));
		assertTrue(!ForestBlend.blends(ForestBlend.BEECH, 0) && !ForestBlend.blends(0, ForestBlend.BEECH));
		int beech = EcotoneTest.code(HabitatBiome.LOWLAND_BEECH_FOREST, Zone.NONE);
		assertTrue(!ForestBlend.takenOver(beech));
		assertEquals(5, ForestBlend.member(beech));
		// A beech column with every forest at hand never draws the fresh pine forest; a fresh pine column never the beech.
		float[] s = {0.2F, 0.2F, 0.2F, 0.2F, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		for (int i = 0; i < 1000; i++) {
			double v = i / 1000.0;
			assertNotEquals(HabitatBiome.FRESH_PINE_FOREST, Habitat.biome(ForestBlend.pick(beech, s, v)));
			assertNotEquals(HabitatBiome.LOWLAND_BEECH_FOREST, Habitat.biome(ForestBlend.pick(FRESH, s, v)));
		}
		// Far from the beech forest (its share 0) the normalization of a forest of the transect is the sum of step Z1.
		float[] far = {0.1F, 0.3F, 0.15F, 0.05F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.4F};
		assertEquals(Math.max(ForestBlend.MIN_TOTAL, far[0] + far[1] + far[2] + far[3]), ForestBlend.pickTotal(1, far));
		// The canonical code of the beech forest is its fertile form.
		assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.FRESH_BROADLEAVED,
				Habitat.siteType(ForestBlend.code(beech, ForestBlend.BEECH)));
	}
	/**
	 * Step F2: the alder carr and the ash-alder forest are forests of the blend after the beech forest; the carr blends with
	 * the ash-alder forest and the four forests of the transect, not with the beech forest; the ash-alder forest with the
	 * carr, the mixed pine-oak, the mixed, the oak-hornbeam and the beech forest, not with the fresh pine forest; neither is
	 * a neighbor taken over any more, and their canonical codes are the alder swamp (Ol) and the ash-alder swamp (OlJ).
	 */
	@Test
	void alderForestsBlendWithTheirNeighborsOfStepF2() {
		assertEquals(HabitatBiome.ALDER_CARR, ForestBlend.FORESTS[ForestBlend.ALDER_CARR]);
		assertEquals(HabitatBiome.ASH_ALDER_FOREST, ForestBlend.FORESTS[ForestBlend.ASH_ALDER]);
		// Steps F3, F4, F5 and F6 appended two, four, two and three forests (their shares after the ash-alder forest's).
		assertEquals(19, ForestBlend.SHARES);
		for (int b = 0; b < 4; b++) {
			assertTrue(ForestBlend.blends(ForestBlend.ALDER_CARR, b) && ForestBlend.blends(b, ForestBlend.ALDER_CARR), "carr " + b);
			assertEquals(b != 0, ForestBlend.blends(ForestBlend.ASH_ALDER, b), "ash-alder " + b);
			assertEquals(b != 0, ForestBlend.blends(b, ForestBlend.ASH_ALDER), "ash-alder " + b);
		}
		assertTrue(ForestBlend.blends(ForestBlend.ALDER_CARR, ForestBlend.ASH_ALDER));
		assertTrue(ForestBlend.blends(ForestBlend.ASH_ALDER, ForestBlend.BEECH) && ForestBlend.blends(ForestBlend.BEECH, ForestBlend.ASH_ALDER));
		assertTrue(!ForestBlend.blends(ForestBlend.ALDER_CARR, ForestBlend.BEECH) && !ForestBlend.blends(ForestBlend.BEECH, ForestBlend.ALDER_CARR));
		int carr = EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE);
		int ashAlder = EcotoneTest.code(HabitatBiome.ASH_ALDER_FOREST, Zone.NONE);
		assertTrue(!ForestBlend.takenOver(carr) && !ForestBlend.takenOver(ashAlder));
		assertEquals(6, ForestBlend.member(carr));
		assertEquals(7, ForestBlend.member(ashAlder));
		assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.ALDER_SWAMP,
				Habitat.siteType(ForestBlend.code(carr, ForestBlend.ALDER_CARR)));
		assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.ASH_ALDER_SWAMP,
				Habitat.siteType(ForestBlend.code(ashAlder, ForestBlend.ASH_ALDER)));
		// A carr column with every forest at hand never draws the beech forest; an ash-alder column never the fresh pine forest.
		float[] s = {0.1F, 0.1F, 0.1F, 0.1F, 0.2F, 0.2F, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		for (int i = 0; i < 1000; i++) {
			double v = i / 1000.0;
			assertNotEquals(HabitatBiome.LOWLAND_BEECH_FOREST, Habitat.biome(ForestBlend.pick(carr, s, v)));
			assertNotEquals(HabitatBiome.FRESH_PINE_FOREST, Habitat.biome(ForestBlend.pick(ashAlder, s, v)));
		}
		// Far from the alder forests (their shares 0) the beech forest's normalization is the one of step F1.
		float[] far = {0, 0.3F, 0.15F, 0.05F, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1F};
		assertEquals(Math.max(ForestBlend.MIN_TOTAL, far[1] + far[2] + far[3] + far[4]), ForestBlend.pickTotal(ForestBlend.BEECH, far));
	}

	/**
	 * Step F3 (docs/04 "Projekt F3"): the upland fir forest blends with the lowland beech forest and the forests of the
	 * transect but the fresh pine forest; the Carpathian beech forest with the lowland beech, the fir and the oak-hornbeam
	 * forest; the other pairs (the alder forests, the fresh pine forest) meet in the ramp of {@code Ecotone}.
	 */
	@Test
	void mountainForestsBlendWithTheirNeighborsOfStepF3() {
		assertEquals(HabitatBiome.UPLAND_FIR_FOREST, ForestBlend.FORESTS[ForestBlend.UPLAND_FIR]);
		assertEquals(HabitatBiome.MONTANE_BEECH_FOREST, ForestBlend.FORESTS[ForestBlend.MONTANE_BEECH]);
		int fir = ForestBlend.UPLAND_FIR;
		int montane = ForestBlend.MONTANE_BEECH;
		for (int b : new int[] {1, 2, 3, ForestBlend.BEECH, montane}) {
			assertTrue(ForestBlend.blends(fir, b) && ForestBlend.blends(b, fir), "fir " + b);
		}
		for (int b : new int[] {0, ForestBlend.ALDER_CARR, ForestBlend.ASH_ALDER}) {
			assertTrue(!ForestBlend.blends(fir, b) && !ForestBlend.blends(b, fir), "fir " + b);
		}
		for (int b : new int[] {3, ForestBlend.BEECH, fir}) {
			assertTrue(ForestBlend.blends(montane, b) && ForestBlend.blends(b, montane), "montane " + b);
		}
		for (int b : new int[] {0, 1, 2, ForestBlend.ALDER_CARR, ForestBlend.ASH_ALDER}) {
			assertTrue(!ForestBlend.blends(montane, b) && !ForestBlend.blends(b, montane), "montane " + b);
		}
		int firCode = EcotoneTest.code(HabitatBiome.UPLAND_FIR_FOREST, Zone.NONE);
		int montaneCode = EcotoneTest.code(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE);
		assertTrue(!ForestBlend.takenOver(firCode) && !ForestBlend.takenOver(montaneCode));
		assertEquals(8, ForestBlend.member(firCode));
		assertEquals(9, ForestBlend.member(montaneCode));
		// The canonical codes: the mesotrophic fir forest (LMwyż), the fertile Carpathian beech forest (LG, typical).
		assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.UPLAND_MIXED_BROADLEAVED,
				Habitat.siteType(ForestBlend.code(firCode, fir)));
		assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_BROADLEAVED,
				Habitat.siteType(ForestBlend.code(montaneCode, montane)));
		assertEquals(pl.polishforests.worldgen.habitat.Association.TYPICAL, Habitat.association(ForestBlend.code(montaneCode, montane)));
		// With every forest at hand a Carpathian beech column never draws the pine or alder forests, a fir column never
		// the fresh pine or the alder forests, and the lowland beech forest's column may draw both mountain forests.
		float[] s = {0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.1F, 0.15F, 0.15F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		boolean beechDrawsFir = false;
		boolean beechDrawsMontane = false;
		int lowland = EcotoneTest.code(HabitatBiome.LOWLAND_BEECH_FOREST, Zone.NONE);
		for (int i = 0; i < 1000; i++) {
			double v = i / 1000.0;
			HabitatBiome m = Habitat.biome(ForestBlend.pick(montaneCode, s, v));
			assertTrue(m == HabitatBiome.MONTANE_BEECH_FOREST || m == HabitatBiome.UPLAND_FIR_FOREST || m == HabitatBiome.LOWLAND_BEECH_FOREST
					|| m == HabitatBiome.OAK_HORNBEAM_FOREST, "montane drew " + m);
			HabitatBiome f = Habitat.biome(ForestBlend.pick(firCode, s, v));
			assertTrue(f != HabitatBiome.FRESH_PINE_FOREST && f != HabitatBiome.ALDER_CARR && f != HabitatBiome.ASH_ALDER_FOREST, "fir drew " + f);
			HabitatBiome l = Habitat.biome(ForestBlend.pick(lowland, s, v));
			beechDrawsFir |= l == HabitatBiome.UPLAND_FIR_FOREST;
			beechDrawsMontane |= l == HabitatBiome.MONTANE_BEECH_FOREST;
		}
		assertTrue(beechDrawsFir && beechDrawsMontane);
		// Far from the mountain forests (their shares 0) the lowland beech forest's normalization is the one of step F1.
		float[] far = {0, 0.3F, 0.15F, 0.05F, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1F};
		assertEquals(Math.max(ForestBlend.MIN_TOTAL, far[1] + far[2] + far[3] + far[4]), ForestBlend.pickTotal(ForestBlend.BEECH, far));
	}

	/**
	 * Step F4 (docs/04 "Projekt F4"): the dry, the moist pine forest, the bog woodland and the coastal pine forest are
	 * forests of the blend after the Carpathian beech forest, no longer neighbors taken over; the dry pine forest blends
	 * with the fresh and the mixed pine-oak forest, the moist one with them, the alder carr and the bog woodland, the bog
	 * woodland with the fresh pine forest and the alder carr, the coastal pine forest with the fresh and the mixed pine-oak
	 * forest, the bog woodland and the dry pine forest; with the broadleaved and the mountain forests they meet in the ramp.
	 */
	@Test
	void pineForestsAndBogWoodlandBlendWithTheirNeighborsOfStepF4() {
		assertEquals(List.of(HabitatBiome.DRY_PINE_FOREST, HabitatBiome.MOIST_PINE_FOREST, HabitatBiome.BOG_WOODLAND,
				HabitatBiome.COASTAL_PINE_FOREST), List.of(ForestBlend.FORESTS[ForestBlend.DRY_PINE], ForestBlend.FORESTS[ForestBlend.MOIST_PINE],
						ForestBlend.FORESTS[ForestBlend.BOG_WOODLAND], ForestBlend.FORESTS[ForestBlend.COASTAL_PINE]));
		int[][] pairs = {{ForestBlend.DRY_PINE, 0}, {ForestBlend.DRY_PINE, 1}, {ForestBlend.MOIST_PINE, 0}, {ForestBlend.MOIST_PINE, 1},
				{ForestBlend.MOIST_PINE, ForestBlend.ALDER_CARR}, {ForestBlend.MOIST_PINE, ForestBlend.BOG_WOODLAND},
				{ForestBlend.BOG_WOODLAND, 0}, {ForestBlend.BOG_WOODLAND, ForestBlend.ALDER_CARR}, {ForestBlend.COASTAL_PINE, 0},
				{ForestBlend.COASTAL_PINE, 1}, {ForestBlend.COASTAL_PINE, ForestBlend.BOG_WOODLAND},
				{ForestBlend.COASTAL_PINE, ForestBlend.DRY_PINE}};
		java.util.Set<String> expected = new java.util.HashSet<>();
		for (int[] p : pairs) {
			expected.add(Math.min(p[0], p[1]) + "-" + Math.max(p[0], p[1]));
		}
		for (int a = ForestBlend.DRY_PINE; a <= ForestBlend.COASTAL_PINE; a++) {
			for (int b = 0; b < ForestBlend.FORESTS.length; b++) {
				boolean pair = expected.contains(Math.min(a, b) + "-" + Math.max(a, b));
				assertEquals(pair, ForestBlend.blends(a, b), a + " " + b);
				assertEquals(pair, ForestBlend.blends(b, a), b + " " + a);
			}
		}
		pl.polishforests.worldgen.habitat.ForestSiteType[] sites = {pl.polishforests.worldgen.habitat.ForestSiteType.DRY_CONIFEROUS,
				pl.polishforests.worldgen.habitat.ForestSiteType.MOIST_CONIFEROUS, pl.polishforests.worldgen.habitat.ForestSiteType.BOGGY_CONIFEROUS,
				pl.polishforests.worldgen.habitat.ForestSiteType.FRESH_CONIFEROUS};
		for (int f = ForestBlend.DRY_PINE; f <= ForestBlend.COASTAL_PINE; f++) {
			int code = EcotoneTest.code(ForestBlend.FORESTS[f], Zone.NONE);
			assertTrue(!ForestBlend.takenOver(code), ForestBlend.FORESTS[f] + " taken over");
			assertEquals(f + 1, ForestBlend.member(code));
			assertEquals(sites[f - ForestBlend.DRY_PINE], Habitat.siteType(ForestBlend.code(code, f)));
		}
		// A dry pine column with every forest at hand draws only itself and its partners.
		float[] s = new float[ForestBlend.SHARES];
		java.util.Arrays.fill(s, 0, ForestBlend.FORESTS.length, 1F / ForestBlend.FORESTS.length);
		int dry = EcotoneTest.code(HabitatBiome.DRY_PINE_FOREST, Zone.NONE);
		for (int i = 0; i < 1000; i++) {
			HabitatBiome d = Habitat.biome(ForestBlend.pick(dry, s, i / 1000.0));
			assertTrue(d == HabitatBiome.DRY_PINE_FOREST || d == HabitatBiome.FRESH_PINE_FOREST || d == HabitatBiome.MIXED_PINE_FOREST
					|| d == HabitatBiome.COASTAL_PINE_FOREST, "dry drew " + d);
		}
	}

	/**
	 * Step F5 (docs/04 "Projekt F5"): the elm-ash and the willow-poplar floodplain forest are forests of the blend after the
	 * coastal pine forest; the elm-ash forest blends with the ash-alder forest, the alder carr, the oak-hornbeam and the mixed
	 * forest and the willow-poplar forest, the willow-poplar forest with the ash-alder forest, the alder carr and the
	 * oak-hornbeam forest; their canonical codes are the riparian site (Lł).
	 */
	@Test
	void floodplainForestsBlendWithTheirNeighborsOfStepF5() {
		assertEquals(List.of(HabitatBiome.ELM_ASH_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST),
				List.of(ForestBlend.FORESTS[ForestBlend.ELM_ASH], ForestBlend.FORESTS[ForestBlend.WILLOW_POPLAR]));
		int[][] pairs = {{ForestBlend.ELM_ASH, ForestBlend.ASH_ALDER}, {ForestBlend.ELM_ASH, ForestBlend.ALDER_CARR},
				{ForestBlend.ELM_ASH, 3}, {ForestBlend.ELM_ASH, 2}, {ForestBlend.ELM_ASH, ForestBlend.WILLOW_POPLAR},
				{ForestBlend.WILLOW_POPLAR, ForestBlend.ASH_ALDER}, {ForestBlend.WILLOW_POPLAR, ForestBlend.ALDER_CARR},
				{ForestBlend.WILLOW_POPLAR, 3}};
		java.util.Set<String> expected = new java.util.HashSet<>();
		for (int[] p : pairs) {
			expected.add(Math.min(p[0], p[1]) + "-" + Math.max(p[0], p[1]));
		}
		for (int a = ForestBlend.ELM_ASH; a <= ForestBlend.WILLOW_POPLAR; a++) {
			for (int b = 0; b < ForestBlend.FORESTS.length; b++) {
				boolean pair = expected.contains(Math.min(a, b) + "-" + Math.max(a, b));
				assertEquals(pair, ForestBlend.blends(a, b), a + " " + b);
				assertEquals(pair, ForestBlend.blends(b, a), b + " " + a);
			}
			int code = EcotoneTest.code(ForestBlend.FORESTS[a], Zone.NONE);
			assertEquals(a + 1, ForestBlend.member(code));
			assertEquals(pl.polishforests.worldgen.habitat.ForestSiteType.RIPARIAN, Habitat.siteType(ForestBlend.code(code, a)));
		}
		// The osier scrub is no forest of the blend (it meets them in the ramp), and its zone and the riverside willows
		// keep their own codes.
		assertEquals(0, ForestBlend.member(EcotoneTest.code(HabitatBiome.WILLOW_SCRUB, Zone.NONE)));
		assertEquals(0, ForestBlend.member(EcotoneTest.code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.RIVERSIDE_WILLOWS)));
		// A willow-poplar column with every forest at hand draws only itself and its partners.
		float[] s = new float[ForestBlend.SHARES];
		java.util.Arrays.fill(s, 0, ForestBlend.FORESTS.length, 1F / ForestBlend.FORESTS.length);
		int willow = EcotoneTest.code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE);
		for (int i = 0; i < 1000; i++) {
			HabitatBiome d = Habitat.biome(ForestBlend.pick(willow, s, i / 1000.0));
			assertTrue(d == HabitatBiome.WILLOW_POPLAR_FOREST || d == HabitatBiome.ELM_ASH_FOREST || d == HabitatBiome.ASH_ALDER_FOREST
					|| d == HabitatBiome.ALDER_CARR || d == HabitatBiome.OAK_HORNBEAM_FOREST, "willow-poplar drew " + d);
		}
	}

	/**
	 * Step F6 (docs/04 "Projekt F6"): the montane spruce forest, the gray alder forest and the dwarf pine scrub are forests
	 * of the blend after the willow-poplar forest; the montane spruce forest blends with the Carpathian beech forest, the fir
	 * forest and the dwarf pine scrub, the gray alder forest with the montane spruce, the Carpathian beech and the
	 * ash-alder forest; their canonical codes are the poor mountain coniferous site (BG), the mountain riparian site (LłG)
	 * and the subalpine scrub; the alpine grassland is no forest of the blend.
	 */
	@Test
	void mountainForestsBlendWithTheirNeighborsOfStepF6() {
		assertEquals(List.of(HabitatBiome.MONTANE_SPRUCE_FOREST, HabitatBiome.GRAY_ALDER_FOREST, HabitatBiome.DWARF_PINE_SCRUB),
				List.of(ForestBlend.FORESTS[ForestBlend.MONTANE_SPRUCE], ForestBlend.FORESTS[ForestBlend.GRAY_ALDER],
						ForestBlend.FORESTS[ForestBlend.DWARF_PINE]));
		int[][] pairs = {{ForestBlend.MONTANE_SPRUCE, ForestBlend.MONTANE_BEECH}, {ForestBlend.MONTANE_SPRUCE, ForestBlend.UPLAND_FIR},
				{ForestBlend.MONTANE_SPRUCE, ForestBlend.DWARF_PINE}, {ForestBlend.GRAY_ALDER, ForestBlend.MONTANE_SPRUCE},
				{ForestBlend.GRAY_ALDER, ForestBlend.MONTANE_BEECH}, {ForestBlend.GRAY_ALDER, ForestBlend.ASH_ALDER}};
		java.util.Set<String> expected = new java.util.HashSet<>();
		for (int[] p : pairs) {
			expected.add(Math.min(p[0], p[1]) + "-" + Math.max(p[0], p[1]));
		}
		pl.polishforests.worldgen.habitat.ForestSiteType[] sites = {pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_CONIFEROUS,
				pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_RIPARIAN, pl.polishforests.worldgen.habitat.ForestSiteType.SUBALPINE_SCRUB};
		for (int a = ForestBlend.MONTANE_SPRUCE; a <= ForestBlend.DWARF_PINE; a++) {
			for (int b = 0; b < ForestBlend.FORESTS.length; b++) {
				boolean pair = expected.contains(Math.min(a, b) + "-" + Math.max(a, b));
				assertEquals(pair, ForestBlend.blends(a, b), a + " " + b);
				assertEquals(pair, ForestBlend.blends(b, a), b + " " + a);
			}
			int code = EcotoneTest.code(ForestBlend.FORESTS[a], Zone.NONE);
			assertEquals(a + 1, ForestBlend.member(code));
			assertEquals(sites[a - ForestBlend.MONTANE_SPRUCE], Habitat.siteType(ForestBlend.code(code, a)));
		}
		assertEquals(0, ForestBlend.member(EcotoneTest.code(HabitatBiome.ALPINE_GRASSLAND, Zone.NONE)));
		// A dwarf pine column with every forest at hand draws only itself and the montane spruce forest.
		float[] s = new float[ForestBlend.SHARES];
		java.util.Arrays.fill(s, 0, ForestBlend.FORESTS.length, 1F / ForestBlend.FORESTS.length);
		int dwarf = EcotoneTest.code(HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE);
		for (int i = 0; i < 1000; i++) {
			HabitatBiome d = Habitat.biome(ForestBlend.pick(dwarf, s, i / 1000.0));
			assertTrue(d == HabitatBiome.DWARF_PINE_SCRUB || d == HabitatBiome.MONTANE_SPRUCE_FOREST, "dwarf pine drew " + d);
		}
	}
}
