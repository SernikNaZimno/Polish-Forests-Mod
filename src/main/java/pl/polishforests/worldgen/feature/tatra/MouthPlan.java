package pl.polishforests.worldgen.feature.tatra;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Step TT6: the mouth of a cave in a limestone or dolomite slope of the Tatras. From a column P0 at the foot, whose ground
 * is the floor of the mouth (one walks in at the ground level in front of it), a passage 3 wide and 3 high (rounded top
 * corners) is cut straight into the slope, uphill. Where the ground is still lower than the passage and its roof
 * ({@value #ROOF} blocks of rock) the passage is an open cut to the surface (at most {@value #MAX_PORTAL} blocks long: the
 * portal); from where the rock covers it the passage is a tunnel of {@value #MIN_TUNNEL} to {@value #MAX_TUNNEL} blocks,
 * winding a little, ending in a chamber about 5 blocks across. The tunnel is cut only through rock with a solid floor
 * under it, never within a block of water or lava, so it opens neither the surface above it nor a cave below it.
 */
public final class MouthPlan {
	public static final int MAX_PORTAL = 6;
	public static final int MIN_TUNNEL = 6;
	public static final int MAX_TUNNEL = 7;
	/** Rock over the roof of the tunnel (blocks). */
	public static final int ROOF = 2;
	/** Search radius of the foot column around the anchor (blocks). */
	public static final int REACH = 3;
	/** Height of the passage over its floor in the middle (blocks). */
	private static final int HEIGHT = 3;

	/** A planned mouth: the placements, the floor cell of the entrance, the direction the mouth faces, the tunnel length and blocks cut. */
	public record Plan(List<Placement> placements, int x, int y, int z, int facing, int length, int cut) {
	}

	private MouthPlan() {
	}

	/** The best mouth within {@link #REACH} of the anchor, or null. */
	public static @Nullable Plan mouth(TerrainView t, int ax, int az, long seed) {
		Plan best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int d = 0; d < 4; d++) {
					Plan p = mouthAt(t, ax + dx, az + dz, d, seed);
					if (p == null) {
						continue;
					}
					double score = p.cut() * 0.2 - Math.hypot(dx, dz) + 3 * WaterPlan.unit(seed, (ax + dx) * 4 + d, az + dz);
					if (score > bestScore) {
						bestScore = score;
						best = p;
					}
				}
			}
		}
		return best;
	}

	/** The mouth with its foot at column (px, pz) looking outward in direction {@code d} (the passage runs the other way), or null. */
	static @Nullable Plan mouthAt(TerrainView t, int px, int pz, int d, long seed) {
		int u = Dirs.opposite(d);
		int ux = Dirs.DX[u];
		int uz = Dirs.DZ[u];
		int sx = Dirs.DX[Dirs.right(u)];
		int sz = Dirs.DZ[Dirs.right(u)];
		int fy = t.top(px, pz);
		if (!t.solid(px, fy, pz) || t.fluid(px, fy + 1, pz)) {
			return null;
		}
		// The portal: columns along the passage where the rock does not yet cover it.
		int j0 = 0;
		while (!covered(t, px + ux * j0, pz + uz * j0, sx, sz, fy)) {
			j0++;
			if (j0 > MAX_PORTAL) {
				return null;
			}
		}
		PlanBuilder plan = new PlanBuilder();
		for (int j = 0; j < j0; j++) {
			int x = px + ux * j;
			int z = pz + uz * j;
			if (!t.solid(x, fy, z)) {
				return null;
			}
			for (int b = -1; b <= 1; b++) {
				int cx = x + b * sx;
				int cz = z + b * sz;
				for (int y = fy + 1; y <= t.top(cx, cz); y++) {
					if (!cut(t, plan, cx, y, cz)) {
						return null;
					}
				}
			}
		}
		int tunnel = MIN_TUNNEL + (int) (WaterPlan.unit(seed, px * 4 + d, pz + 5) * (MAX_TUNNEL - MIN_TUNNEL + 1));
		int lateral = 0;
		int cx = px + ux * j0;
		int cz = pz + uz * j0;
		for (int i = 0; i < tunnel; i++) {
			if (i > 0) {
				cx += ux;
				cz += uz;
				if (i % 3 == 0) {
					double r = WaterPlan.unit(seed, i * 17 + px, pz + d);
					int step = r < 0.3 ? -1 : r > 0.7 ? 1 : 0;
					if (Math.abs(lateral + step) <= 1) {
						lateral += step;
						cx += step * sx;
						cz += step * sz;
					}
				}
			}
			if (!column(t, plan, cx, cz, fy + 1, fy + HEIGHT) || !column(t, plan, cx + sx, cz + sz, fy + 1, fy + HEIGHT - 1)
					|| !column(t, plan, cx - sx, cz - sz, fy + 1, fy + HEIGHT - 1)) {
				return null;
			}
		}
		// The chamber at the end: an ellipsoid 5 across and 3 high.
		int chx = cx + 2 * ux;
		int chz = cz + 2 * uz;
		PlanBuilder withChamber = copy(plan);
		boolean chamberOk = true;
		for (int dx = -2; dx <= 2 && chamberOk; dx++) {
			for (int dz = -2; dz <= 2 && chamberOk; dz++) {
				int lo = 2;
				int hi = -2;
				for (int dy = -1; dy <= 1; dy++) {
					if (dx * dx + dz * dz + dy * dy * 3 <= 6) {
						lo = Math.min(lo, dy);
						hi = Math.max(hi, dy);
					}
				}
				if (lo <= hi) {
					chamberOk = column(t, withChamber, chx + dx, chz + dz, fy + 2 + lo, fy + 2 + hi);
				}
			}
		}
		PlanBuilder use = chamberOk ? withChamber : plan;
		int cutCount = 0;
		for (Placement p : use.build()) {
			cutCount += p.piece() == Piece.AIR ? 1 : 0;
		}
		if (cutCount < 25) {
			return null;
		}
		List<Placement> placements = use.build();
		return Fit.fits(t, placements, true) ? new Plan(placements, px, fy + 1, pz, d, tunnel, cutCount) : null;
	}

	/** Whether the rock covers the passage at the column: {@value #ROOF} blocks over its top, also beside it. */
	private static boolean covered(TerrainView t, int x, int z, int sx, int sz, int fy) {
		return t.top(x, z) >= fy + HEIGHT + ROOF && t.top(x + sx, z + sz) >= fy + HEIGHT - 1 + ROOF
				&& t.top(x - sx, z - sz) >= fy + HEIGHT - 1 + ROOF;
	}

	private static PlanBuilder copy(PlanBuilder plan) {
		PlanBuilder out = new PlanBuilder();
		for (Placement p : plan.build()) {
			out.set(p.x(), p.y(), p.z(), p.piece(), p.param());
		}
		return out;
	}

	/** Cuts the cell of the open portal: a solid cell becomes air; no fluid next to it. */
	private static boolean cut(TerrainView t, PlanBuilder plan, int x, int y, int z) {
		for (int[] n : NEIGHBORS) {
			if (t.fluid(x + n[0], y + n[1], z + n[2])) {
				return false;
			}
		}
		if (t.solid(x, y, z)) {
			plan.set(x, y, z, Piece.AIR);
		}
		return true;
	}

	/**
	 * Cuts the cells from {@code lo} to {@code hi} of a column: rock under the lowest one (the floor) and over the highest
	 * ({@value #ROOF} blocks of roof), no fluid next to any of them. A cell that is already air stays as it is.
	 */
	private static boolean column(TerrainView t, PlanBuilder plan, int x, int z, int lo, int hi) {
		if (!t.solid(x, lo - 1, z)) {
			return false;
		}
		for (int r = 1; r <= ROOF; r++) {
			if (!t.solid(x, hi + r, z) && !plan.has(x, hi + r, z)) {
				return false;
			}
		}
		for (int y = lo; y <= hi; y++) {
			for (int[] n : NEIGHBORS) {
				if (t.fluid(x + n[0], y + n[1], z + n[2])) {
					return false;
				}
			}
			if (t.solid(x, y, z)) {
				plan.set(x, y, z, Piece.AIR);
			}
		}
		return true;
	}

	private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
}
