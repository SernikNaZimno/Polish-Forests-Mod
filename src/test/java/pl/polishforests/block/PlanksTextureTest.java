package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The planks texture derived from vanilla planks (step P2, {@link PlanksTexture}), on a planks texture this test draws
 * itself (no vanilla file): four boards of four rows, the bottom row of each a dark seam, one darker butt joint column per
 * board, staggered, and a grain of seven brightness levels. The derivation is deterministic, keeps the seams and the joints
 * where they are (they come out as the remapped colors of the input), puts every pixel on the ramp of the palette and
 * changes the grain of the boards, differently for every wood set.
 */
class PlanksTextureTest {
	/** Seven brightness levels like a planks texture (seam dark … top highlight). */
	private static final int[] LEVELS = {83, 101, 120, 134, 146, 153, 161};

	/** A planks texture of {@code size × size} px: 4 boards, seam rows at the bottom of each, staggered joints. */
	static int[] drawn(int size, long seed) {
		Random rnd = new Random(seed);
		int board = size / 4;
		int[] out = new int[size * size];
		for (int y = 0; y < size; y++) {
			int b = y / board;
			int r = y % board;
			int joint = jointOf(b, size);
			for (int x = 0; x < size; x++) {
				int level;
				if (r == board - 1) {
					level = rnd.nextInt(3);
				} else if (x == joint) {
					level = 2 + rnd.nextInt(2);
				} else if (r == 0) {
					level = 4 + rnd.nextInt(3);
				} else {
					level = 3 + rnd.nextInt(4);
				}
				out[y * size + x] = gray(LEVELS[level]);
			}
		}
		return out;
	}

	static int jointOf(int board, int size) {
		return board % 2 == 0 ? size - 1 : size / 2 - 1;
	}

	/** A warm brown of about luma {@code l}. */
	static int gray(int l) {
		return 0xff000000 | (Math.min(255, l + 20) << 16) | (l << 8) | Math.max(0, l - 30);
	}

	private static final PlanksTexture.Style STYLE = WoodSet.HORNBEAM.planksStyle();

	@Test
	void deterministicAndOfTheFrameSize() {
		int[] in = drawn(16, 1);
		int[] a = PlanksTexture.derive(in, 16, 16, STYLE);
		int[] b = PlanksTexture.derive(in.clone(), 16, 16, STYLE);
		assertArrayEquals(a, b);
		assertEquals(256, a.length);
		assertEquals(PlanksTexture.seed("hornbeam"), PlanksTexture.seed("hornbeam"));
		// A 32 px texture of a resource pack keeps its size; a strip of frames gives its first frame.
		assertEquals(32 * 32, PlanksTexture.derive(drawn(32, 2), 32, 32, STYLE).length);
		int[] strip = new int[16 * 48];
		System.arraycopy(in, 0, strip, 0, 256);
		System.arraycopy(drawn(16, 3), 0, strip, 256, 256);
		System.arraycopy(drawn(16, 4), 0, strip, 512, 256);
		assertArrayEquals(a, PlanksTexture.derive(strip, 16, 48, STYLE));
	}

	@Test
	void layoutOfTheDrawnPlanks() {
		for (int size : new int[] {16, 32}) {
			int[] in = drawn(size, 5);
			PlanksTexture.Layout l = PlanksTexture.layout(in, size, size);
			int board = size / 4;
			for (int y = 0; y < size; y++) {
				assertEquals(y % board == board - 1, l.seamRows()[y], "seam row " + y + " of " + size);
			}
			assertEquals(4, l.boards().length);
			for (int b = 0; b < 4; b++) {
				assertEquals(board - 1, l.boards()[b].length);
				int boardIndex = l.boards()[b][0] / board;
				assertEquals(jointOf(boardIndex, size), l.joints()[b], "joint of board " + boardIndex);
			}
		}
	}

	@Test
	void seamsAndJointsStayWhereTheyAre() {
		for (WoodSet set : WoodSet.values()) {
			int[] in = drawn(16, 7);
			int[] out = PlanksTexture.derive(in, 16, 16, set.planksStyle());
			PlanksTexture.Layout before = PlanksTexture.layout(in, 16, 16);
			PlanksTexture.Layout after = PlanksTexture.layout(out, 16, 16);
			assertArrayEquals(before.seamRows(), after.seamRows(), set + ": seam rows");
			assertArrayEquals(before.joints(), after.joints(), set + ": joints");
			double lo = Arrays.stream(in).mapToDouble(PlanksTexture::luminance).min().orElseThrow();
			double hi = Arrays.stream(in).mapToDouble(PlanksTexture::luminance).max().orElseThrow();
			int[] palette = set.planksPalette();
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int i = y * 16 + x;
					boolean seam = before.seamRows()[y];
					boolean joint = !seam && x == before.joints()[boardOf(before, y)];
					if (seam || joint) {
						int expected = 0xff000000 | PlanksTexture.ramp(palette, (PlanksTexture.luminance(in[i]) - lo) / (hi - lo));
						assertEquals(expected, out[i], set + ": " + (seam ? "seam" : "joint") + " pixel " + x + "," + y);
					}
				}
			}
		}
	}

	@Test
	void everyColorOnThePaletteRamp() {
		for (WoodSet set : WoodSet.values()) {
			int[] palette = set.planksPalette();
			for (int[] in : List.of(drawn(16, 11), drawn(32, 12))) {
				int[] out = PlanksTexture.derive(in, in.length == 256 ? 16 : 32, in.length == 256 ? 16 : 32, set.planksStyle());
				for (int p : out) {
					assertEquals(0xff, p >>> 24, set + ": opaque");
					double d = distanceToRamp(palette, p & 0xffffff);
					assertTrue(d <= 1.0, set + ": color " + Integer.toHexString(p) + " is " + d + " from the palette ramp");
				}
			}
		}
	}

	@Test
	void theGrainChangesDifferentlyForEverySpecies() {
		int[] in = drawn(16, 13);
		PlanksTexture.Layout layout = PlanksTexture.layout(in, 16, 16);
		Set<List<Integer>> textures = new HashSet<>();
		for (WoodSet set : WoodSet.values()) {
			// The same palette without any change of the grain: the plain remap.
			PlanksTexture.Style plain = new PlanksTexture.Style(set.planksPalette(), 1.0, 0, 0L);
			int[] remap = plainRemap(in, plain.palette());
			int[] out = PlanksTexture.derive(in, 16, 16, set.planksStyle());
			int boardPixels = 0;
			int changed = 0;
			for (int[] rows : layout.boards()) {
				for (int y : rows) {
					for (int x = 0; x < 16; x++) {
						boardPixels++;
						changed += out[y * 16 + x] != remap[y * 16 + x] ? 1 : 0;
					}
				}
			}
			assertTrue(changed >= boardPixels / 4, set + ": only " + changed + " of " + boardPixels + " board pixels changed");
			// Same palette, another species' style: another texture (the grain is the species' own).
			int[] other = PlanksTexture.derive(in, 16, 16, new PlanksTexture.Style(set.planksPalette(), 1.0, 0, PlanksTexture.seed(set + "-x")));
			assertFalse(Arrays.equals(out, other), set + ": the style changes nothing");
			List<Integer> key = new ArrayList<>();
			for (int p : PlanksTexture.derive(in, 16, 16, new PlanksTexture.Style(WoodSet.HORNBEAM.planksPalette(), set.planksStyle().contrast(),
					set.planksStyle().knots(), set.planksStyle().seed()))) {
				key.add(p);
			}
			textures.add(key);
		}
		assertEquals(WoodSet.values().length, textures.size(), "two wood sets with the same grain");
	}

	@Test
	void flatAndTransparentInputsDoNotBreak() {
		int[] flat = new int[256];
		Arrays.fill(flat, gray(140));
		int[] out = PlanksTexture.derive(flat, 16, 16, STYLE);
		assertEquals(1, Arrays.stream(out).distinct().count());
		assertEquals(0, PlanksTexture.layout(flat, 16, 16).seamCount());
		int[] holes = drawn(16, 17);
		holes[5] = 0;
		int[] withHoles = PlanksTexture.derive(holes, 16, 16, STYLE);
		assertEquals(256, withHoles.length);
		assertEquals(1, Arrays.stream(withHoles).filter(p -> (p >>> 24) == 0).count(), "a transparent pixel stays transparent");
	}

	private static int boardOf(PlanksTexture.Layout l, int y) {
		for (int b = 0; b < l.boards().length; b++) {
			for (int r : l.boards()[b]) {
				if (r == y) {
					return b;
				}
			}
		}
		throw new IllegalArgumentException("row " + y + " is in no board");
	}

	private static int[] plainRemap(int[] in, int[] palette) {
		double lo = Arrays.stream(in).mapToDouble(PlanksTexture::luminance).min().orElseThrow();
		double hi = Arrays.stream(in).mapToDouble(PlanksTexture::luminance).max().orElseThrow();
		return Arrays.stream(in).map(p -> 0xff000000 | PlanksTexture.ramp(palette, (PlanksTexture.luminance(p) - lo) / (hi - lo))).toArray();
	}

	/** The largest channel distance of an RGB color to the nearest point of the polyline through the palette. */
	static double distanceToRamp(int[] palette, int rgb) {
		double best = Double.MAX_VALUE;
		double[] c = {(rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff};
		for (int i = 0; i + 1 < palette.length; i++) {
			double[] a = {(palette[i] >> 16) & 0xff, (palette[i] >> 8) & 0xff, palette[i] & 0xff};
			double[] b = {(palette[i + 1] >> 16) & 0xff, (palette[i + 1] >> 8) & 0xff, palette[i + 1] & 0xff};
			double num = 0;
			double den = 0;
			for (int k = 0; k < 3; k++) {
				num += (c[k] - a[k]) * (b[k] - a[k]);
				den += (b[k] - a[k]) * (b[k] - a[k]);
			}
			double f = den == 0 ? 0 : Math.max(0, Math.min(1, num / den));
			double d = 0;
			for (int k = 0; k < 3; k++) {
				d = Math.max(d, Math.abs(c[k] - (a[k] + f * (b[k] - a[k]))));
			}
			best = Math.min(best, d);
		}
		return best;
	}
}
