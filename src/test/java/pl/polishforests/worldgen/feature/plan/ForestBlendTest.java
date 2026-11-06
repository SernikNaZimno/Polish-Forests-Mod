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
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
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
		return profile(world, k, layer, salt, patch, meander, w, HabitatBiome.MIXED_PINE_FOREST);
	}

	/** Share of columns with biome {@code side} by world x (−w … w − 1), over the rows of many chunks. */
	private static double[] profile(IntBinaryOperator world, double k, Ecotone.Layer layer, long salt, int patch, boolean meander, int w,
			HabitatBiome side) {
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
					taken[x] += Habitat.biome(eff[i]) == side ? 1 : 0;
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
		// Other biomes (a willow scrub against the oak-hornbeam forest; the alder carr until step F2, the elm-ash forest until
		// step F5, the gray alder forest until step F6: since step F7 every forest blends) keep the ramp in the plant layers.
		int alder = EcotoneTest.code(HabitatBiome.WILLOW_SCRUB, Zone.NONE);
		int oak = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		IntBinaryOperator other = (x, z) -> x < 0 ? alder : oak;
		ForestBlend otherBlend = blend(other, k);
		for (int cx = -1; cx <= 0; cx++) {
			int[] plain = Ecotone.effective(EcotoneTest.region(cx, 3, other, k), Ecotone.Layer.PLANTS, SEED, Ecotone.PLANTS_SALT, 1);
			int[] withBlend = Ecotone.effective(EcotoneTest.region(cx, 3, other, k).withBlend(otherBlend), Ecotone.Layer.PLANTS, SEED,
					Ecotone.PLANTS_SALT, 1);
			for (int i = 0; i < 256; i++) {
				if (Habitat.biome(plain[i]) == HabitatBiome.WILLOW_SCRUB) {
					assertEquals(plain[i], withBlend[i], "a willow scrub column keeps its ramp");
				}
			}
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
	 * The forests of the blend in the order of their steps (the transect of the slice, then F1-F6), each with its index, its
	 * canonical code (the site type of the typical form) and its membership; the open land, the scrub, the zones and the
	 * unknown code are no members.
	 */
	@Test
	void forestsOfTheStepsKeepTheirIndicesAndCanonicalCodes() {
		assertEquals(List.of(HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST, HabitatBiome.MIXED_FOREST,
				HabitatBiome.OAK_HORNBEAM_FOREST), List.of(ForestBlend.TRANSECT));
		HabitatBiome[] byIndex = new HabitatBiome[ForestBlend.FORESTS.length];
		byIndex[ForestBlend.BEECH] = HabitatBiome.LOWLAND_BEECH_FOREST;
		byIndex[ForestBlend.ALDER_CARR] = HabitatBiome.ALDER_CARR;
		byIndex[ForestBlend.ASH_ALDER] = HabitatBiome.ASH_ALDER_FOREST;
		byIndex[ForestBlend.UPLAND_FIR] = HabitatBiome.UPLAND_FIR_FOREST;
		byIndex[ForestBlend.MONTANE_BEECH] = HabitatBiome.MONTANE_BEECH_FOREST;
		byIndex[ForestBlend.DRY_PINE] = HabitatBiome.DRY_PINE_FOREST;
		byIndex[ForestBlend.MOIST_PINE] = HabitatBiome.MOIST_PINE_FOREST;
		byIndex[ForestBlend.BOG_WOODLAND] = HabitatBiome.BOG_WOODLAND;
		byIndex[ForestBlend.COASTAL_PINE] = HabitatBiome.COASTAL_PINE_FOREST;
		byIndex[ForestBlend.ELM_ASH] = HabitatBiome.ELM_ASH_FOREST;
		byIndex[ForestBlend.WILLOW_POPLAR] = HabitatBiome.WILLOW_POPLAR_FOREST;
		byIndex[ForestBlend.MONTANE_SPRUCE] = HabitatBiome.MONTANE_SPRUCE_FOREST;
		byIndex[ForestBlend.GRAY_ALDER] = HabitatBiome.GRAY_ALDER_FOREST;
		byIndex[ForestBlend.DWARF_PINE] = HabitatBiome.DWARF_PINE_SCRUB;
		for (int i = ForestBlend.TRANSECT.length; i < byIndex.length; i++) {
			assertEquals(byIndex[i], ForestBlend.FORESTS[i], "index " + i);
		}
		assertEquals(19, ForestBlend.SHARES);
		assertEquals(ForestBlend.FORESTS.length, ForestBlend.OTHER);
		ForestSiteType[] sites = {ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.FRESH_MIXED_CONIFEROUS,
				ForestSiteType.FRESH_MIXED_BROADLEAVED, ForestSiteType.FRESH_BROADLEAVED, ForestSiteType.FRESH_BROADLEAVED,
				ForestSiteType.ALDER_SWAMP, ForestSiteType.ASH_ALDER_SWAMP, ForestSiteType.UPLAND_MIXED_BROADLEAVED,
				ForestSiteType.MOUNTAIN_BROADLEAVED, ForestSiteType.DRY_CONIFEROUS, ForestSiteType.MOIST_CONIFEROUS,
				ForestSiteType.BOGGY_CONIFEROUS, ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.RIPARIAN, ForestSiteType.RIPARIAN,
				ForestSiteType.MOUNTAIN_CONIFEROUS, ForestSiteType.MOUNTAIN_RIPARIAN, ForestSiteType.SUBALPINE_SCRUB};
		for (int f = 0; f < ForestBlend.FORESTS.length; f++) {
			int code = EcotoneTest.code(ForestBlend.FORESTS[f], Zone.NONE);
			String at = ForestBlend.FORESTS[f].name();
			assertEquals(f + 1, ForestBlend.member(code), at);
			int canonical = ForestBlend.code(code, f);
			assertEquals(ForestBlend.FORESTS[f], Habitat.biome(canonical), at);
			assertEquals(sites[f], Habitat.siteType(canonical), at);
			assertEquals(Association.TYPICAL, Habitat.association(canonical), at);
			assertEquals(Zone.NONE, Habitat.zone(canonical), at);
		}
		int[] others = {EcotoneTest.code(HabitatBiome.WILLOW_SCRUB, Zone.NONE), EcotoneTest.code(HabitatBiome.ALPINE_GRASSLAND, Zone.NONE),
				EcotoneTest.code(HabitatBiome.HAY_MEADOW, Zone.NONE), EcotoneTest.code(HabitatBiome.GRAY_DUNE, Zone.NONE),
				EcotoneTest.code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.RIVERSIDE_WILLOWS),
				EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.SPRING_AREA), Ecotone.UNKNOWN};
		for (int code : others) {
			assertEquals(0, ForestBlend.member(code), Integer.toHexString(code));
		}
	}

	/**
	 * Step F7 (docs/04 "Projekt F7"): every pair of forests of the blend mixes in the belt, also the pairs that met in the
	 * ramp of {@code Ecotone} until step F6 (a sharp change of the site: the beech forest and the fresh pine forest, the carr
	 * and the beech forest, the fir and the dry pine forest, the dwarf pine scrub and the lowland forests); a column with
	 * every forest at hand draws every forest, and the shares are normalized by the sum of all forests.
	 */
	@Test
	void everyPairOfForestsBlendsSinceStepF7() {
		int n = ForestBlend.FORESTS.length;
		for (int a = 0; a < n; a++) {
			assertTrue(!ForestBlend.blends(a, a), "itself " + a);
			for (int b = 0; b < n; b++) {
				if (a != b) {
					assertTrue(ForestBlend.blends(a, b), a + " " + b);
					assertTrue(ForestBlend.blendsCodes(EcotoneTest.code(ForestBlend.FORESTS[a], Zone.NONE),
							EcotoneTest.code(ForestBlend.FORESTS[b], Zone.NONE)), a + " " + b);
				}
			}
		}
		float[] s = new float[ForestBlend.SHARES];
		Arrays.fill(s, 0, n, 1F / n);
		for (int a = 0; a < n; a++) {
			int code = EcotoneTest.code(ForestBlend.FORESTS[a], Zone.NONE);
			java.util.Set<HabitatBiome> drawn = java.util.EnumSet.noneOf(HabitatBiome.class);
			for (int i = 0; i < 2000; i++) {
				drawn.add(Habitat.biome(ForestBlend.pick(code, s, (i + 0.5) / 2000)));
			}
			assertEquals(n, drawn.size(), ForestBlend.FORESTS[a] + " drew " + drawn);
		}
		float[] far = new float[ForestBlend.SHARES];
		far[1] = 0.3F;
		far[2] = 0.15F;
		far[ForestBlend.BEECH] = 0.05F;
		far[ForestBlend.GRAY_ALDER] = 0.1F;
		far[ForestBlend.OTHER] = 0.4F;
		for (int a = 0; a < n; a++) {
			assertEquals(0.6, ForestBlend.pickTotal(a, far), 1e-6, "total of " + a);
			assertEquals(ForestBlend.forestTotal(far), ForestBlend.pickTotal(a, far), 1e-6, "total of " + a);
		}
	}

	/**
	 * Step F7 (KF-4, rule Z10): a straight border between two forests that met in the ramp of {@code Ecotone} until step F6
	 * is a ramp of the blend's width in the trees and the ground layer, in both scales: about 50% on both sides of the
	 * border, pure beyond the radius, monotone; in the ground layer a forest of the density rule of §4.6 (the carr, the
	 * floodplain forests, the gray alder forest) keeps its plants and only the other side mixes (step F2).
	 */
	/** The biomes of the density rule of §4.6 among the forests ({@code Ecotone.DENSE}): their plants stay in the blend. */
	private static final java.util.Set<HabitatBiome> DENSE = java.util.EnumSet.of(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.GRAY_ALDER_FOREST);

	@Test
	void straightBordersOfThePairsOfTheRampsUntilStepF6RampOverTheBlendsWidth() {
		HabitatBiome[][] pairs = {{HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST},
				{HabitatBiome.ALDER_CARR, HabitatBiome.LOWLAND_BEECH_FOREST}, {HabitatBiome.DRY_PINE_FOREST, HabitatBiome.UPLAND_FIR_FOREST},
				{HabitatBiome.MONTANE_SPRUCE_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST},
				{HabitatBiome.BOG_WOODLAND, HabitatBiome.ELM_ASH_FOREST}, {HabitatBiome.DWARF_PINE_SCRUB, HabitatBiome.OAK_HORNBEAM_FOREST}};
		for (HabitatBiome[] pair : pairs) {
			int a = EcotoneTest.code(pair[0], Zone.NONE);
			int b = EcotoneTest.code(pair[1], Zone.NONE);
			for (double k : new double[] {1.0, 0.5}) {
				IntBinaryOperator world = (x, z) -> x < 0 ? a : b;
				int w = 96;
				double[] trees = profile(world, k, Ecotone.Layer.TREES, Ecotone.TREES_SALT, 1, false, w, pair[1]);
				double[] ground = profile(world, k, Ecotone.Layer.PLANTS, Ecotone.GROUND_SALT, Ecotone.GROUND_PATCH, false, w, pair[1]);
				int r = new ForestBlend(SEED, k, (x, z) -> 0).radius();
				for (int layer = 0; layer < 2; layer++) {
					double[] p = layer == 0 ? trees : ground;
					String at = pair[0] + " | " + pair[1] + (layer == 0 ? " trees" : " ground") + " k " + k;
					int width = width(p);
					System.out.printf(Locale.ROOT, "[blend] %s: 10-90%% %d blocks (%.0f m·k), at the border %.2f / %.2f%n", at, width,
							width / k, p[w - 1], p[w]);
					assertTrue(p[w - r - 2] < 0.01 && p[w + r + 1] > 0.99, at + ": pure beyond the radius");
					for (int i = 8; i < p.length; i += 8) {
						assertTrue(p[i] >= p[i - 8] - 0.03, at + ": monotone at " + i);
					}
					boolean denseA = layer == 1 && DENSE.contains(pair[0]);
					boolean denseB = layer == 1 && DENSE.contains(pair[1]);
					if (denseA) {
						// The floodplain forests and the carr keep their plants (rule of §4.6, step F2): only the other side mixes.
						for (int i = 0; i < w; i++) {
							assertEquals(0, p[i], 0, at + ": the dense side keeps its plants at " + i);
						}
						assertTrue(Math.abs(p[w] - 0.5) < 0.1, at + ": about 50% on the other side of the border " + p[w]);
					} else if (denseB) {
						for (int i = w; i < p.length; i++) {
							assertEquals(1, p[i], 0, at + ": the dense side keeps its plants at " + i);
						}
						assertTrue(Math.abs(p[w - 1] - 0.5) < 0.1, at + ": about 50% on the other side of the border " + p[w - 1]);
					} else {
						assertTrue(width / k >= 30 && width / k <= 70, at + ": width " + width / k + " m·k");
						assertTrue(Math.abs(p[w - 1] - 0.5) < 0.1 && Math.abs(p[w] - 0.5) < 0.1, at + ": about 50% at the border "
								+ p[w - 1] + " / " + p[w]);
					}
				}
			}
		}
	}
}
