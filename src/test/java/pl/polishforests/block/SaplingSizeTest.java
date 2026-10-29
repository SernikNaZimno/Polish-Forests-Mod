package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Step S1 (the sizes of a tree grown from a sapling, decision CP-C.1): the draw of the size follows the weights (small 25%,
 * medium 55%, large 20%), a size without room falls back to the next smaller one, never to a larger one, and the three
 * sizes of every species are clearly different trees: the small one the pole stage of V7 (13–19 blocks, a trunk of 1 px),
 * the medium one taller with a trunk of at least 2 px at the base, the large one taller again but at most 28 blocks.
 */
class SaplingSizeTest {
	private static final long SEED = 20260927L;
	private static final int TREES = 40;
	private static final int Y = 64;

	@BeforeAll
	static void bootstrap() {
		GameRegistries.bootstrap();
	}

	@Test
	void drawFollowsTheWeights() {
		int n = 10_000;
		Map<SaplingSize, Integer> counts = new EnumMap<>(SaplingSize.class);
		RandomSource random = RandomSource.create(4242L);
		for (int i = 0; i < n; i++) {
			counts.merge(SaplingSize.draw(random), 1, Integer::sum);
		}
		assertEquals(25, SaplingSize.SMALL.weight());
		assertEquals(55, SaplingSize.MEDIUM.weight());
		assertEquals(20, SaplingSize.LARGE.weight());
		for (SaplingSize s : SaplingSize.values()) {
			double share = counts.getOrDefault(s, 0) / (double) n;
			// Three standard deviations of a share of 10 000 draws are below 0.015.
			assertEquals(s.share(), share, 0.015, s + ": " + share);
		}
		// Several random sources: every size comes up, medium the most often.
		for (long seed = 1; seed <= 20; seed++) {
			RandomSource r = RandomSource.create(seed);
			int[] c = new int[3];
			for (int i = 0; i < 400; i++) {
				c[SaplingSize.draw(r).ordinal()]++;
			}
			assertTrue(c[0] > 0 && c[2] > 0 && c[1] > c[0] && c[1] > c[2], "seed " + seed + ": " + Arrays.toString(c));
		}
	}

	@Test
	void chooseFallsBackToSmallerSizesOnly() {
		Set<SaplingSize> none = EnumSet.noneOf(SaplingSize.class);
		assertEquals(SaplingSize.LARGE, SaplingSize.choose(SaplingSize.LARGE, s -> true));
		assertEquals(SaplingSize.MEDIUM, SaplingSize.choose(SaplingSize.LARGE, s -> s != SaplingSize.LARGE));
		assertEquals(SaplingSize.SMALL, SaplingSize.choose(SaplingSize.LARGE, s -> s == SaplingSize.SMALL));
		assertEquals(SaplingSize.SMALL, SaplingSize.choose(SaplingSize.MEDIUM, s -> s == SaplingSize.SMALL));
		assertNull(SaplingSize.choose(SaplingSize.LARGE, none::contains));
		// A small draw never grows a larger tree, even where only the larger ones would have room.
		assertNull(SaplingSize.choose(SaplingSize.SMALL, s -> s != SaplingSize.SMALL));
		assertEquals(SaplingSize.MEDIUM, SaplingSize.choose(SaplingSize.MEDIUM, s -> s != SaplingSize.SMALL));
		assertEquals("sapling/oak", SaplingSize.SMALL.featurePath("oak"));
		assertEquals("sapling/oak/medium", SaplingSize.MEDIUM.featurePath("oak"));
		assertEquals("sapling/oak/large", SaplingSize.LARGE.featurePath("oak"));
	}

	/** The tree of a size at (x, z), as the configured feature of the size plans it. */
	private static TreeVoxels tree(Species species, SaplingSize size, int x, int z) {
		return TreeShapePlan.plan(new TreeRequest(species, CrownLayer.CANOPY, size.age(), size.factors(species), SEED, x, Y, z));
	}

	private static int height(TreeVoxels v) {
		int top = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			top = Math.max(top, v.woodY(i) + 1);
		}
		for (int i = 0; i < v.leafCount(); i++) {
			top = Math.max(top, v.leafY(i) + 1);
		}
		return top;
	}

	private static int woodTop(TreeVoxels v) {
		int top = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			top = Math.max(top, v.woodY(i));
		}
		return top;
	}

	private static List<int[]> columns() {
		List<int[]> out = new ArrayList<>();
		java.util.Random r = new java.util.Random(11);
		for (int k = 0; k < TREES; k++) {
			out.add(new int[] {r.nextInt(6000) - 3000, r.nextInt(6000) - 3000});
		}
		return out;
	}

	private static double median(int[] a) {
		int[] b = a.clone();
		Arrays.sort(b);
		return b.length % 2 == 1 ? b[b.length / 2] : (b[b.length / 2 - 1] + b[b.length / 2]) / 2.0;
	}

	@Test
	void sizesAreClearlyDifferentTrees() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		List<int[]> cols = columns();
		for (Species species : SpeciesShape.species()) {
			Map<SaplingSize, int[]> heights = new EnumMap<>(SaplingSize.class);
			Map<SaplingSize, int[]> bases = new EnumMap<>(SaplingSize.class);
			Map<SaplingSize, int[]> wood = new EnumMap<>(SaplingSize.class);
			Map<SaplingSize, int[]> leaves = new EnumMap<>(SaplingSize.class);
			for (SaplingSize size : SaplingSize.values()) {
				int[] h = new int[cols.size()];
				int[] b = new int[cols.size()];
				int[] w = new int[cols.size()];
				int[] l = new int[cols.size()];
				for (int k = 0; k < cols.size(); k++) {
					TreeVoxels v = tree(species, size, cols.get(k)[0], cols.get(k)[1]);
					h[k] = height(v);
					b[k] = v.radius(0);
					w[k] = v.woodCount();
					l[k] = v.leafCount();
				}
				heights.put(size, h);
				bases.put(size, b);
				wood.put(size, w);
				leaves.put(size, l);
				report.append(String.format(Locale.ROOT, "%-13s %-6s height %2d-%2d (median %4.1f), base radius %d-%d, wood %3d-%4d, leaves %4d-%4d%n",
						species.id(), size, Arrays.stream(h).min().orElseThrow(), Arrays.stream(h).max().orElseThrow(), median(h),
						Arrays.stream(b).min().orElseThrow(), Arrays.stream(b).max().orElseThrow(), Arrays.stream(w).min().orElseThrow(),
						Arrays.stream(w).max().orElseThrow(), Arrays.stream(l).min().orElseThrow(), Arrays.stream(l).max().orElseThrow()));
			}
			int[] small = heights.get(SaplingSize.SMALL);
			int[] medium = heights.get(SaplingSize.MEDIUM);
			int[] large = heights.get(SaplingSize.LARGE);
			String id = species.id();
			// Small: the pole stage of V7 (13-19 blocks with the top leaves), a trunk of 1 px.
			check(failures, Arrays.stream(small).max().orElseThrow() <= 19, id + " small too tall");
			check(failures, Arrays.stream(bases.get(SaplingSize.SMALL)).allMatch(r -> r == 1), id + ": small tree base radius not 1");
			// Medium: taller than the small tree by at least 3 blocks in the median, a trunk of at least 2 px at the base.
			check(failures, median(medium) >= median(small) + 3, id + ": medium " + median(medium) + " vs small " + median(small));
			check(failures, Arrays.stream(bases.get(SaplingSize.MEDIUM)).allMatch(r -> r >= 2), id + ": medium tree base radius below 2");
			// Large: taller than the medium tree by at least 3 blocks in the median, at most 28 blocks with the top leaves.
			check(failures, median(large) >= median(medium) + 3, id + ": large " + median(large) + " vs medium " + median(medium));
			check(failures, Arrays.stream(large).max().orElseThrow() <= 28, id + ": large tree above 28 blocks");
			check(failures, Arrays.stream(bases.get(SaplingSize.LARGE)).allMatch(r -> r >= 2), id + ": large tree base radius below 2");
		}
		try {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
			java.nio.file.Files.writeString(java.nio.file.Path.of("build/sapling-sizes.txt"), report);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		assertTrue(failures.isEmpty(), String.join("; ", failures) + " | " + report);
	}

	private static void check(List<String> failures, boolean ok, String message) {
		if (!ok) {
			failures.add(message);
		}
	}

	/**
	 * The room check sees the tree of the size: under a ceiling (a solid layer, or the build height) the size grown is the
	 * drawn one or the largest smaller one whose wood stays below it, never a larger one; under a ceiling just above the
	 * small tree's wood a large draw grows a small tree, under one just above the medium tree's wood mostly a medium tree;
	 * without room for the trunk nothing grows.
	 */
	@Test
	void roomCheckFallsBackUnderACeiling() {
		Map<SaplingSize, Integer> grownUnderMedium = new EnumMap<>(SaplingSize.class);
		int cases = 0;
		for (Species species : SpeciesShape.species()) {
			for (int[] c : columns().subList(0, 10)) {
				BlockPos origin = new BlockPos(c[0], Y, c[1]);
				Map<SaplingSize, TreeVoxels> trees = new EnumMap<>(SaplingSize.class);
				Map<SaplingSize, Integer> tops = new EnumMap<>(SaplingSize.class);
				for (SaplingSize s : SaplingSize.values()) {
					trees.put(s, tree(species, s, c[0], c[1]));
					tops.put(s, Y + woodTop(trees.get(s)));
				}
				int smallTop = tops.get(SaplingSize.SMALL);
				int mediumTop = tops.get(SaplingSize.MEDIUM);
				for (int ceiling : new int[] {Y + 2, smallTop + 1, mediumTop + 1, tops.get(SaplingSize.LARGE) + 1}) {
					for (SaplingSize drawn : SaplingSize.values()) {
						SaplingSize expected = null;
						for (SaplingSize s = drawn; s != null && expected == null; s = s.smaller()) {
							if (tops.get(s) < ceiling) {
								expected = s;
							}
						}
						int ceil = ceiling;
						assertEquals(expected, SaplingSize.choose(drawn, s -> ProceduralTreeFeature.woodFits(trees.get(s), origin,
								Integer.MAX_VALUE, p -> p.getY() < ceil)), species.id() + " at " + origin + " under " + ceiling);
						// The build height cuts alike.
						assertEquals(expected, SaplingSize.choose(drawn, s -> ProceduralTreeFeature.woodFits(trees.get(s), origin,
								ceil, p -> true)), species.id() + " at " + origin + " below the build height " + ceiling);
						cases++;
					}
				}
				// Just above the small tree's wood: a large draw grows the small tree (or nothing larger).
				if (mediumTop > smallTop && tops.get(SaplingSize.LARGE) > smallTop) {
					assertEquals(SaplingSize.SMALL, SaplingSize.choose(SaplingSize.LARGE, s -> ProceduralTreeFeature.woodFits(trees.get(s),
							origin, Integer.MAX_VALUE, p -> p.getY() <= smallTop)), species.id() + " at " + origin);
				}
				grownUnderMedium.merge(SaplingSize.choose(SaplingSize.LARGE, s -> ProceduralTreeFeature.woodFits(trees.get(s), origin,
						Integer.MAX_VALUE, p -> p.getY() <= mediumTop)), 1, Integer::sum);
				// No room for the trunk: nothing grows.
				assertNull(SaplingSize.choose(SaplingSize.LARGE, s -> ProceduralTreeFeature.woodFits(trees.get(s), origin,
						Integer.MAX_VALUE, p -> p.getY() < Y + 2)));
				// The sapling's own block counts as free.
				assertTrue(ProceduralTreeFeature.woodFits(trees.get(SaplingSize.SMALL), origin, Integer.MAX_VALUE, p -> !p.equals(origin)));
			}
		}
		int n = SpeciesShape.species().size() * 10;
		assertTrue(cases == n * 4 * 3);
		// Under a ceiling just above the medium tree's wood a large draw mostly falls back to the medium tree.
		assertTrue(grownUnderMedium.getOrDefault(SaplingSize.MEDIUM, 0) >= n * 0.7, "fallbacks to medium: " + grownUnderMedium);
	}
}
