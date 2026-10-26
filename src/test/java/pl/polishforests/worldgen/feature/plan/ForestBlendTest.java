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
		// Other forests (alder carr against oak-hornbeam forest) keep the ramp in the plant layers (their trees near the
		// slice forests: sliceForestsTakeOverTheTreesOfAStreamBeltButNotOfALargeAlderCarr).
		int alder = EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE);
		int oak = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		IntBinaryOperator other = (x, z) -> x < 0 ? alder : oak;
		ForestBlend otherBlend = blend(other, k);
		for (int cx = -1; cx <= 0; cx++) {
			int[] plain = Ecotone.effective(EcotoneTest.region(cx, 3, other, k), Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
			int[] withBlend = Ecotone.effective(EcotoneTest.region(cx, 3, other, k).withBlend(otherBlend), Ecotone.Layer.PLANTS, SEED,
					Ecotone.PLANTS_SALT, 1);
			for (int i = 0; i < 256; i++) {
				if (Habitat.biome(plain[i]) == HabitatBiome.ALDER_CARR) {
					assertEquals(plain[i], withBlend[i], "an alder carr column keeps its ramp");
				}
			}
		}
	}

	@Test
	void sliceForestsTakeOverTheTreesOfAStreamBeltButNotOfALargeAlderCarr() {
		for (double k : new double[] {1.0, 0.5}) {
			int oak = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
			int spring = EcotoneTest.code(HabitatBiome.ASH_ALDER_FOREST, Zone.SPRING_AREA);
			int alder = EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE);
			int belt = (int) Math.round(80 * k);
			// A stream belt of ash-alder forest in a spring area (|x| < belt / 2) in an oak-hornbeam forest for z < 400, a
			// large alder carr (x >= 0) next to it for z >= 400.
			IntBinaryOperator world = (x, z) -> z < 400 ? (Math.abs(x) < belt / 2 ? spring : oak) : (x < 0 ? oak : alder);
			ForestBlend blend = blend(world, k);
			long beltColumns = 0;
			long beltTaken = 0;
			long deep = 0;
			long deepTaken = 0;
			long plantsChanged = 0;
			for (int cx = -6; cx < 6; cx++) {
				for (int cz = 2; cz < 48; cz++) {
					Ecotone.Region region = EcotoneTest.region(cx, cz, world, k).withBlend(blend);
					int[] trees = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
					int[] plants = Ecotone.effective(region, Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
					int[] plain = Ecotone.effective(EcotoneTest.region(cx, cz, world, k), Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
					for (int i = 0; i < 256; i++) {
						int x = (cx << 4) + (i >> 4);
						int z = (cz << 4) + (i & 15);
						int own = world.applyAsInt(x, z);
						boolean member = ForestBlend.member(trees[i]) > 0;
						if (own == spring && z < 400 - blend.radius() - 8) {
							beltColumns++;
							beltTaken += member ? 1 : 0;
						}
						if (own == alder && z > 420 && x > 0.6 * blend.radius()) {
							deep++;
							deepTaken += member ? 1 : 0;
						}
						if (own != oak && Habitat.biome(plain[i]) != HabitatBiome.OAK_HORNBEAM_FOREST && plants[i] != plain[i]) {
							plantsChanged++;
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "[blend] k %.1f: stream belt %d columns, %.1f%% slice trees; alder carr beyond 0.6 R %.2f%%%n", k,
					beltColumns, 100.0 * beltTaken / beltColumns, 100.0 * deepTaken / Math.max(1, deep));
			assertTrue(beltTaken > 0.9 * beltColumns, "the stream belt grows the oak-hornbeam forest's trees");
			assertEquals(0, deepTaken, "deep in a large alder carr its own trees");
			assertEquals(0, plantsChanged, "the plants of the belt and the carr keep their palettes");
		}
	}

	@Test
	void provedCodesWithTheBlendEqualTheCodesOfTheColumnsOwnChunk() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator forests = EcotoneProvableTest.world(k);
			int alderCarr = EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE);
			pl.polishforests.worldgen.landscape.Noise carr = new pl.polishforests.worldgen.landscape.Noise(SEED).derive("test.blend.carr");
			// The forests of the provable test with patches of alder carr (taken over near the slice forests).
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
		List<HabitatBiome> sequence = List.of(ForestBlend.FORESTS);
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
}
