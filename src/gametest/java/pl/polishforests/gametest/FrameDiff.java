package pl.polishforests.gametest;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Pixel comparison of a screenshot with a reference frame of the same camera taken after a long wait (step T7, the check
 * of {@link ViewReady}): the share of differing pixels, the share of pixels showing sky in the screenshot where the
 * reference shows terrain (a hole: terrain not drawn yet) and the tiles of a 16 × 9 grid that differ over a large part.
 * Animated elements (particles, water, a moving mob) differ in scattered pixels or single tiles; a missing chunk or
 * section differs in a block of pixels, mostly as sky or fog in place of terrain.
 */
public final class FrameDiff {
	/** A pixel differs when a channel differs by more than this. */
	public static final int PIXEL_THRESHOLD = 40;
	/** A tile differs when more than this share of its pixels differs. */
	public static final double TILE_SHARE = 0.25;
	public static final int TILES_X = 16;
	public static final int TILES_Y = 9;
	/** Limits of a frame equal to its reference (animated elements only). */
	public static final double MAX_DIFF_SHARE = 0.01;
	public static final double MAX_HOLE_SHARE = 0.002;
	public static final int MAX_TILES = 1;

	private FrameDiff() {
	}

	/** The result: shares of all pixels, number of differing tiles, the diff image (differing pixels red over gray). */
	public record Result(double diffShare, double holeShare, int tiles, BufferedImage diff) {
		public boolean equal() {
			return diffShare <= MAX_DIFF_SHARE && holeShare <= MAX_HOLE_SHARE && tiles <= MAX_TILES;
		}

		public String describe() {
			return String.format(Locale.ROOT, "%.2f%% pixels differ, sky in place of terrain %.2f%%, %d of %d tiles differ: %s",
					100 * diffShare, 100 * holeShare, tiles, TILES_X * TILES_Y, equal() ? "EQUAL" : "DIFFERENT");
		}
	}

	/** Compares a frame with its reference (same size; a different size counts as entirely different). */
	public static Result compare(BufferedImage frame, BufferedImage reference) {
		int w = frame.getWidth();
		int h = frame.getHeight();
		if (reference.getWidth() != w || reference.getHeight() != h) {
			return new Result(1, 1, TILES_X * TILES_Y, frame);
		}
		int[] a = frame.getRGB(0, 0, w, h, null, 0, w);
		int[] b = reference.getRGB(0, 0, w, h, null, 0, w);
		int[] tileDiff = new int[TILES_X * TILES_Y];
		int[] tileAll = new int[TILES_X * TILES_Y];
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		long diff = 0;
		long holes = 0;
		for (int y = 0; y < h; y++) {
			int ty = Math.min(TILES_Y - 1, y * TILES_Y / h);
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				int t = ty * TILES_X + Math.min(TILES_X - 1, x * TILES_X / w);
				tileAll[t]++;
				int p = a[i];
				int q = b[i];
				boolean differs = channelDiff(p, q) > PIXEL_THRESHOLD;
				int gray = luma(p) / 2 + 64;
				int rgb = (gray << 16) | (gray << 8) | gray;
				if (differs) {
					diff++;
					tileDiff[t]++;
					rgb = 0xFF0000;
					if (sky(p) && !sky(q)) {
						holes++;
						rgb = 0xFF00FF;
					}
				}
				out.setRGB(x, y, rgb);
			}
		}
		int tiles = 0;
		for (int t = 0; t < tileAll.length; t++) {
			if (tileAll[t] > 0 && tileDiff[t] > TILE_SHARE * tileAll[t]) {
				tiles++;
			}
		}
		double n = (double) w * h;
		return new Result(diff / n, holes / n, tiles, out);
	}

	/** Clearly blue sky, as {@code SeasonSamples.skyShare} counts it. */
	public static boolean sky(int rgb) {
		int r = (rgb >> 16) & 255;
		int g = (rgb >> 8) & 255;
		int b = rgb & 255;
		return b >= 120 && b > r + 30 && b >= g;
	}

	private static int channelDiff(int p, int q) {
		int dr = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255));
		int dg = Math.abs(((p >> 8) & 255) - ((q >> 8) & 255));
		int db = Math.abs((p & 255) - (q & 255));
		return Math.max(dr, Math.max(dg, db));
	}

	private static int luma(int p) {
		return (299 * ((p >> 16) & 255) + 587 * ((p >> 8) & 255) + 114 * (p & 255)) / 1000;
	}
}
