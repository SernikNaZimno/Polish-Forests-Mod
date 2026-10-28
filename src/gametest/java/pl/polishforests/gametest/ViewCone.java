package pl.polishforests.gametest;

/**
 * The geometry of the view of a screenshot (step T7, plain Java so that unit tests can check it): a grid of rays through
 * the frame of a camera and the distance up to which the terrain must be there for the frame to be complete.
 *
 * <p>Minecraft angles: yaw 0 looks south (+z), 90 west (-x); pitch 90 looks straight down. The field of view of the
 * options is the vertical one.
 */
public final class ViewCone {
	/** The reach of an eye-height view (8 chunks): a forest closes the view much nearer, an open field shows this far. */
	public static final double EYE_REACH = 128;
	/** Each block of height above the ground adds this much reach (views from above and overviews see farther). */
	public static final double REACH_PER_HEIGHT = 2;
	/** The height of the eyes over the ground below which a camera counts as at eye height (no extra reach). */
	public static final double EYE_HEIGHT = 2;
	/** Rays stop this many chunks inside the render distance: sections at the edge lack neighbors and never mesh. */
	public static final double EDGE_CHUNKS = 1.5;

	private ViewCone() {
	}

	/**
	 * Unit directions {x, y, z} of {@code cols} × {@code rows} rays spread evenly over the frame (corners included),
	 * widened by {@code margin} (1.1 = 10% beyond the frame on each side).
	 */
	public static double[][] rays(double yawDeg, double pitchDeg, double verticalFovDeg, double aspect, int cols, int rows,
			double margin) {
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		double[] f = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
		// Horizontal right-hand vector of the yaw (well defined also when looking straight up or down).
		double[] r = {Math.cos(yaw), 0, Math.sin(yaw)};
		double[] u = cross(f, r);
		double ty = Math.tan(Math.toRadians(verticalFovDeg) / 2) * margin;
		double tx = ty * aspect;
		double[][] out = new double[cols * rows][];
		int k = 0;
		for (int j = 0; j < rows; j++) {
			double v = rows == 1 ? 0 : -1 + 2.0 * j / (rows - 1);
			for (int i = 0; i < cols; i++) {
				double h = cols == 1 ? 0 : -1 + 2.0 * i / (cols - 1);
				double x = f[0] + u[0] * ty * v + r[0] * tx * h;
				double y = f[1] + u[1] * ty * v + r[1] * tx * h;
				double z = f[2] + u[2] * ty * v + r[2] * tx * h;
				double n = Math.sqrt(x * x + y * y + z * z);
				out[k++] = new double[] {x / n, y / n, z / n};
			}
		}
		return out;
	}

	/**
	 * How far the rays look for terrain, in blocks: {@link #EYE_REACH} for a camera near the ground, more for a camera
	 * high above it, never beyond the render distance less {@link #EDGE_CHUNKS}; a positive {@code override} replaces the
	 * computed reach (still capped by the render distance).
	 */
	public static double reach(double heightAboveGround, int renderDistanceChunks, double override) {
		double cap = Math.max(16, (renderDistanceChunks - EDGE_CHUNKS) * 16);
		double wanted = override > 0 ? override : EYE_REACH + REACH_PER_HEIGHT * Math.max(0, heightAboveGround - EYE_HEIGHT);
		return Math.min(cap, wanted);
	}

	/** The horizontal limit of the rays (the render distance less {@link #EDGE_CHUNKS}), in blocks. */
	public static double horizontalLimit(int renderDistanceChunks) {
		return Math.max(16, (renderDistanceChunks - EDGE_CHUNKS) * 16);
	}

	private static double[] cross(double[] a, double[] b) {
		double[] c = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
		double n = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
		return new double[] {c[0] / n, c[1] / n, c[2] / n};
	}
}
