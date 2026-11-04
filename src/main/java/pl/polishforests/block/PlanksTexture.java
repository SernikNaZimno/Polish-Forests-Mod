package pl.polishforests.block;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The planks texture of a wood set derived from a vanilla planks texture (step P2 of docs/04-pionowy-wycinek.md, decision
 * U-8: "exactly the texture of the vanilla planks, with the color of the wood and a slightly changed grain"). Plain Java on
 * ARGB pixels, so the client can run it on the texture of the game (or of a resource pack) while the atlas loads and the
 * unit tests on a texture they draw themselves; no pixel of vanilla is in the mod.
 *
 * <p>Steps, all deterministic from the input and the {@link Style}:
 * <ol>
 * <li>Layout ({@link #layout}): the seams between the boards are the rows clearly darker than the others (row mean below
 * the median minus half its distance to the darkest row); the boards are the runs of rows between them (cyclic, the
 * texture tiles); the butt joint of a board is its darkest column.</li>
 * <li>Grain: within every board, the columns other than the joint are moved cyclically by a shift of a quarter to three
 * quarters of the width and mirrored for some boards (the same for all rows of the board), so the seams and the joints stay
 * in place and the grain of each board is another one; the contrast of the grain around the board's mean is scaled by
 * {@link Style#contrast()}; {@link Style#knots()} knots (a dark pixel with darker sides) go into the middle rows of
 * boards, away from the joints.</li>
 * <li>Color: the brightness, normalized by the darkest and lightest pixel of the input, picks the color on the ramp
 * through the palette (dark to light, linear in RGB between neighbours); the alpha stays.</li>
 * </ol>
 * An animated texture (a strip of square frames) gives its first frame.
 */
public final class PlanksTexture {
	/** Below this alpha a pixel is transparent: it stays as it is and does not count in the statistics. */
	private static final int OPAQUE = 128;
	/** The seam rows are darker than the median row by at least this many brightness units (0-255), else none. */
	private static final double MIN_SEAM_DEPTH = 4.0;

	private PlanksTexture() {
	}

	/**
	 * How a species changes the planks: the palette (RGB, dark to light, at least two colors), the factor of the grain
	 * contrast (1 = as the base), the number of added knots and the seed of the per-board shifts and mirrors.
	 */
	public record Style(int[] palette, double contrast, int knots, long seed) {
		public Style {
			if (palette.length < 2) {
				throw new IllegalArgumentException("a planks palette needs at least two colors");
			}
			palette = palette.clone();
		}

		@Override
		public int[] palette() {
			return palette.clone();
		}
	}

	/**
	 * The seams and boards of a planks texture: {@code seamRows[y]} for the seam rows, {@code boards[b]} the rows of board
	 * {@code b} from top to bottom (cyclic: a board can wrap over the edge), {@code joints[b]} its joint column.
	 */
	public record Layout(int width, int height, boolean[] seamRows, int[][] boards, int[] joints) {
		public int seamCount() {
			int n = 0;
			for (boolean s : seamRows) {
				n += s ? 1 : 0;
			}
			return n;
		}
	}

	/** The height of the first frame: a strip of square frames (an animated texture) gives its first square. */
	public static int frameHeight(int width, int height) {
		return height > width && height % width == 0 ? width : height;
	}

	/** A stable seed from a name (FNV-1a over the UTF-8 bytes, then a SplitMix64 finalizer). */
	public static long seed(String name) {
		long h = 0xcbf29ce484222325L;
		for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xff;
			h *= 0x100000001b3L;
		}
		return mix(h);
	}

	/**
	 * The derived planks: {@code argb} is the base texture ({@code width × height}, row by row), the result is
	 * {@code width × frameHeight(width, height)} pixels.
	 */
	public static int[] derive(int[] argb, int width, int height, Style style) {
		int h = frameHeight(width, height);
		int[] img = Arrays.copyOf(argb, width * h);
		double[] lum = new double[img.length];
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (int i = 0; i < img.length; i++) {
			lum[i] = luminance(img[i]);
			if (opaque(img[i])) {
				lo = Math.min(lo, lum[i]);
				hi = Math.max(hi, lum[i]);
			}
		}
		int[] out = img.clone();
		if (lo > hi) {
			return out;
		}
		Layout layout = layout(img, width, h);
		double[] l = lum.clone();
		// The pixels as moved with the grain (their alpha travels with them; a transparent one stays as it is).
		int[] moved = img.clone();
		int[] alpha = new int[img.length];
		for (int i = 0; i < img.length; i++) {
			alpha[i] = img[i] >>> 24;
		}
		Rng rng = new Rng(style.seed());
		int n = width - 1;
		for (int b = 0; b < layout.boards().length; b++) {
			int[] rows = layout.boards()[b];
			int joint = layout.joints()[b];
			boolean mirror = rng.nextInt(2) == 1;
			int shift = n >= 4 ? n / 4 + rng.nextInt(Math.max(1, n / 2)) : 0;
			for (int y : rows) {
				double[] rowL = new double[n];
				int[] rowA = new int[n];
				int[] rowP = new int[n];
				for (int i = 0; i < n; i++) {
					int src = (i + shift) % n;
					if (mirror) {
						src = n - 1 - src;
					}
					int x = (joint + 1 + src) % width;
					rowL[i] = lum[y * width + x];
					rowA[i] = alpha[y * width + x];
					rowP[i] = img[y * width + x];
				}
				for (int i = 0; i < n; i++) {
					int x = (joint + 1 + i) % width;
					l[y * width + x] = rowL[i];
					alpha[y * width + x] = rowA[i];
					moved[y * width + x] = rowP[i];
				}
			}
			double sum = 0;
			int count = 0;
			for (int y : rows) {
				for (int x = 0; x < width; x++) {
					if (x != joint && alpha[y * width + x] >= OPAQUE) {
						sum += l[y * width + x];
						count++;
					}
				}
			}
			if (count > 0) {
				double mean = sum / count;
				for (int y : rows) {
					for (int x = 0; x < width; x++) {
						if (x != joint) {
							int i = y * width + x;
							l[i] = mean + style.contrast() * (l[i] - mean);
						}
					}
				}
			}
		}
		knots(layout, l, alpha, width, rng, style.knots(), seamLuminance(layout, lum, img, width, lo, hi));
		int[] palette = style.palette();
		for (int i = 0; i < img.length; i++) {
			if (alpha[i] < OPAQUE) {
				out[i] = moved[i];
				continue;
			}
			double t = hi > lo ? (l[i] - lo) / (hi - lo) : 0.5;
			out[i] = (alpha[i] << 24) | ramp(palette, t);
		}
		return out;
	}

	/** The seams, boards and joints of a planks texture ({@code width × height} pixels). */
	public static Layout layout(int[] argb, int width, int height) {
		double[] rowMean = new double[height];
		for (int y = 0; y < height; y++) {
			double sum = 0;
			int count = 0;
			for (int x = 0; x < width; x++) {
				int p = argb[y * width + x];
				if (opaque(p)) {
					sum += luminance(p);
					count++;
				}
			}
			rowMean[y] = count > 0 ? sum / count : Double.NaN;
		}
		double[] sorted = Arrays.stream(rowMean).filter(v -> !Double.isNaN(v)).sorted().toArray();
		boolean[] seam = new boolean[height];
		if (sorted.length > 0) {
			double median = sorted[sorted.length / 2];
			double darkest = sorted[0];
			if (median - darkest >= MIN_SEAM_DEPTH) {
				double limit = median - 0.5 * (median - darkest);
				for (int y = 0; y < height; y++) {
					seam[y] = !Double.isNaN(rowMean[y]) && rowMean[y] < limit;
				}
			}
		}
		List<int[]> boards = new ArrayList<>();
		int first = -1;
		for (int y = 0; y < height; y++) {
			if (seam[y]) {
				first = y;
				break;
			}
		}
		if (first < 0) {
			int[] all = new int[height];
			for (int y = 0; y < height; y++) {
				all[y] = y;
			}
			boards.add(all);
		} else {
			List<Integer> run = new ArrayList<>();
			for (int k = 1; k <= height; k++) {
				int y = (first + k) % height;
				if (seam[y]) {
					if (!run.isEmpty()) {
						boards.add(run.stream().mapToInt(Integer::intValue).toArray());
						run.clear();
					}
				} else {
					run.add(y);
				}
			}
		}
		int[][] rows = boards.toArray(new int[0][]);
		int[] joints = new int[rows.length];
		for (int b = 0; b < rows.length; b++) {
			double best = Double.MAX_VALUE;
			for (int x = 0; x < width; x++) {
				double sum = 0;
				int count = 0;
				for (int y : rows[b]) {
					int p = argb[y * width + x];
					if (opaque(p)) {
						sum += luminance(p);
						count++;
					}
				}
				double mean = count > 0 ? sum / count : Double.MAX_VALUE;
				if (mean < best) {
					best = mean;
					joints[b] = x;
				}
			}
		}
		return new Layout(width, height, seam, rows, joints);
	}

	/** The RGB color at {@code t} (0 dark … 1 light, clamped) on the ramp through the palette. */
	public static int ramp(int[] palette, double t) {
		double pos = Math.max(0, Math.min(1, t)) * (palette.length - 1);
		int i = Math.min((int) Math.floor(pos), palette.length - 2);
		double f = pos - i;
		int a = palette[i];
		int b = palette[i + 1];
		int r = (int) Math.round(((a >> 16) & 0xff) * (1 - f) + ((b >> 16) & 0xff) * f);
		int g = (int) Math.round(((a >> 8) & 0xff) * (1 - f) + ((b >> 8) & 0xff) * f);
		int bl = (int) Math.round((a & 0xff) * (1 - f) + (b & 0xff) * f);
		return (r << 16) | (g << 8) | bl;
	}

	/** Rec. 601 luma of an ARGB pixel, 0-255. */
	public static double luminance(int argb) {
		return 0.299 * ((argb >> 16) & 0xff) + 0.587 * ((argb >> 8) & 0xff) + 0.114 * (argb & 0xff);
	}

	private static boolean opaque(int argb) {
		return (argb >>> 24) >= OPAQUE;
	}

	/** The mean brightness of the seam rows (a knot is about as dark), else a dark tenth of the range. */
	private static double seamLuminance(Layout layout, double[] lum, int[] img, int width, double lo, double hi) {
		double sum = 0;
		int count = 0;
		for (int y = 0; y < layout.height(); y++) {
			if (layout.seamRows()[y]) {
				for (int x = 0; x < width; x++) {
					if (opaque(img[y * width + x])) {
						sum += lum[y * width + x];
						count++;
					}
				}
			}
		}
		return count > 0 ? sum / count : lo + 0.1 * (hi - lo);
	}

	/** Knots in the middle rows of random boards, at least two columns from the joint. */
	private static void knots(Layout layout, double[] l, int[] alpha, int width, Rng rng, int knots, double dark) {
		int n = width - 1;
		if (layout.boards().length == 0 || n < 5) {
			return;
		}
		for (int k = 0; k < knots; k++) {
			int b = rng.nextInt(layout.boards().length);
			int[] rows = layout.boards()[b];
			int y = rows.length >= 3 ? rows[1 + rng.nextInt(rows.length - 2)] : rows[rows.length / 2];
			int i = 2 + rng.nextInt(n - 4);
			int joint = layout.joints()[b];
			int x = (joint + 1 + i) % width;
			int c = y * width + x;
			if (alpha[c] < OPAQUE) {
				continue;
			}
			double core = dark + 0.2 * (l[c] - dark);
			for (int dx = -1; dx <= 1; dx += 2) {
				int s = y * width + (x + dx + width) % width;
				l[s] = l[s] - 0.45 * (l[s] - core);
			}
			l[c] = core;
		}
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/** SplitMix64: small, fast and the same everywhere. */
	private static final class Rng {
		private long state;

		Rng(long seed) {
			this.state = seed;
		}

		int nextInt(int bound) {
			state += 0x9e3779b97f4a7c15L;
			long z = mix(state);
			return (int) Math.floorMod(z >>> 1, (long) bound);
		}
	}
}
