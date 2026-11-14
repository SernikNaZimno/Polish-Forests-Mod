package pl.polishforests.worldgen.feature.tatra;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Step TT6 (docs/04-pionowy-wycinek.md, "Projekt TT6"): the water of the Tatra details, planned over the ground the
 * region holds ({@link TerrainView}) and kept only if the model of the flow ({@link FluidSim}) says it stays in its own
 * cells.
 *
 * <p><b>A waterfall</b> falls from a source block in a notch of the lip of a drop into a plunge pool at its foot. At the lip
 * L (the top ground block, Y T) the ground block of L becomes the source, lined behind and on both sides with rock; the
 * cell beside it over the drop (O, one column out, level with the source) is flowing water of level 7, and the cells
 * under it down to the pool are falling water. Water over a free cell flows only down, so the column does not spread
 * sideways; the pool is the {@code 3 x 2} columns in front of the drop, cut down to the lowest ground of the pool and its
 * ring so that the ring is never lower than the water, two sources deep: the falling water ends on a source, which does
 * not spread it. The drop is the height between the lip and the pool surface, at least {@value #MIN_FALL} blocks.
 *
 * <p><b>A spring</b> (a karst spring, wywierzysko) is a small closed pool, {@code 2 x 2} to {@code 3 x 3} sources two
 * deep, cut down to the lowest ground around it.
 */
public final class WaterPlan {
	/** Search radius around the anchor (blocks): the plan reaches 4 further. */
	public static final int REACH = 8;
	/** Least and greatest height of the drop of a waterfall (blocks). */
	public static final int MIN_FALL = 5;
	public static final int MAX_FALL = 24;
	/** Least natural drop from the lip to the lowest ground around the pool, and most the pool is cut deeper (blocks). */
	static final int MIN_NATURAL = 2;
	static final int MAX_EXTRA = 3;
	/** Greatest depth a spring pool is cut into the ground and greatest height of the ground in its footprint (blocks). */
	static final int SPRING_RANGE = 2;
	/** Greatest height of lining under or beside the source of a waterfall (blocks). */
	static final int LINING = 3;

	/** A planned piece of water: the placements, the anchor cell (the lip or the pool), its fall (0 for a spring) and sources. */
	public record Plan(List<Placement> placements, int x, int y, int z, int fall, int sources) {
	}

	private WaterPlan() {
	}

	/** The best waterfall within {@link #REACH} of the anchor, or null. */
	public static @Nullable Plan waterfall(TerrainView t, int ax, int az, long seed) {
		// The candidates by score first (cheap), the flow model only on the best few (it costs a search of the water's reach).
		List<Scored> scored = new ArrayList<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				if (dx * dx + dz * dz > REACH * REACH) {
					continue;
				}
				int lx = ax + dx;
				int lz = az + dz;
				for (int d = 0; d < 4; d++) {
					Plan p = waterfallAt(t, lx, lz, d);
					if (p != null) {
						double score = Math.min(p.fall(), 14) * 10 - 0.4 * p.placements().size() + 3 * unit(seed, lx * 4 + d, lz);
						scored.add(new Scored(p, score));
					}
				}
			}
		}
		scored.sort((a, b) -> Double.compare(b.score, a.score));
		// The flow model only for the candidates that fit (inside the chunk, no trunk cut), at most TRIES of them.
		int tries = 0;
		for (Scored s : scored) {
			if (!Fit.fits(t, s.plan.placements())) {
				continue;
			}
			if (FluidSim.contained(t, s.plan.placements())) {
				return s.plan;
			}
			if (++tries >= TRIES) {
				break;
			}
		}
		return null;
	}

	/** How many of the best candidates of a waterfall the flow model is asked about. */
	static final int TRIES = 8;

	private record Scored(Plan plan, double score) {
	}

	/** The waterfall with its lip at column (lx, lz) over the drop in direction {@code d} (not yet checked with {@link Fit} and the flow model), or null. */
	static @Nullable Plan waterfallAt(TerrainView t, int lx, int lz, int d) {
		int top = t.top(lx, lz);
		int ox = lx + Dirs.DX[d];
		int oz = lz + Dirs.DZ[d];
		int px = Dirs.DX[Dirs.right(d)];
		int pz = Dirs.DZ[Dirs.right(d)];
		if (top - t.top(ox, oz) < 1) {
			return null;
		}
		// The pool: 3 columns across and 2 along the direction of the drop, from the column O; the ring around it.
		int[][] pool = new int[6][];
		int n = 0;
		for (int a = 0; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				pool[n++] = new int[] {ox + a * Dirs.DX[d] + b * px, oz + a * Dirs.DZ[d] + b * pz};
			}
		}
		int ground = Integer.MAX_VALUE;
		for (int a = -1; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				ground = Math.min(ground, t.top(ox + a * Dirs.DX[d] + b * px, oz + a * Dirs.DZ[d] + b * pz));
			}
		}
		// The pool is cut deeper than the lowest ground around it where the drop is short (a pit with the ground all round).
		int natural = top - ground;
		int extra = Math.max(0, MIN_FALL - natural);
		int level = ground - extra;
		int fall = top - level;
		if (natural < MIN_NATURAL || extra > MAX_EXTRA || fall > MAX_FALL) {
			return null;
		}
		if (!t.solid(lx, top, lz) || !t.solid(lx, top - 1, lz)) {
			return null;
		}
		PlanBuilder plan = new PlanBuilder();
		// The shaft of the falling water beside the face: free cells from the foot to the lip.
		for (int y = level + 1; y <= top; y++) {
			if (y > t.top(ox, oz) && t.solid(ox, y, oz)) {
				return null;
			}
		}
		// The notch of the source: the ground block of the lip, lined behind and on both sides.
		int[][] lining = {{lx - Dirs.DX[d], lz - Dirs.DZ[d]}, {lx + px, lz + pz}, {lx - px, lz - pz}};
		for (int[] c : lining) {
			int ct = t.top(c[0], c[1]);
			if (top - ct > LINING) {
				return null;
			}
			for (int y = Math.max(ct, level) + 1; y <= top; y++) {
				if (!t.solid(c[0], y, c[1])) {
					plan.set(c[0], y, c[1], Piece.ROCK);
				}
			}
			if (!t.solid(c[0], top, c[1])) {
				plan.set(c[0], top, c[1], Piece.ROCK);
			}
		}
		plan.set(lx, top, lz, Piece.SOURCE);
		plan.set(ox, top, oz, Piece.FLOW_LIP);
		for (int y = level + 1; y < top; y++) {
			plan.set(ox, y, oz, Piece.FALL);
		}
		int sources = 1;
		for (int[] c : pool) {
			int ct = t.top(c[0], c[1]);
			for (int y = level + 1; y <= ct; y++) {
				if (!(c[0] == ox && c[1] == oz)) {
					plan.set(c[0], y, c[1], Piece.AIR);
				}
			}
			plan.set(c[0], level, c[1], Piece.SOURCE);
			plan.set(c[0], level - 1, c[1], Piece.SOURCE);
			sources += 2;
		}
		return new Plan(plan.build(), lx, top, lz, fall, sources);
	}

	/** The best spring pool within {@link #REACH} of the anchor, or null. */
	public static @Nullable Plan spring(TerrainView t, int ax, int az, long seed) {
		Plan best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		int shape = (int) (unit(seed, 11, 13) * 3);
		int w = shape == 0 ? 2 : 3;
		int h = shape == 2 ? 3 : 2;
		for (int dx = -REACH; dx <= REACH; dx += 2) {
			for (int dz = -REACH; dz <= REACH; dz += 2) {
				int x0 = ax + dx;
				int z0 = az + dz;
				int level = Integer.MAX_VALUE;
				int high = Integer.MIN_VALUE;
				for (int x = x0 - 1; x <= x0 + w; x++) {
					for (int z = z0 - 1; z <= z0 + h; z++) {
						int ct = t.top(x, z);
						level = Math.min(level, ct);
						if (x >= x0 && x < x0 + w && z >= z0 && z < z0 + h) {
							high = Math.max(high, ct);
						}
					}
				}
				if (high - level > SPRING_RANGE) {
					continue;
				}
				PlanBuilder plan = new PlanBuilder();
				int sources = 0;
				for (int x = x0; x < x0 + w; x++) {
					for (int z = z0; z < z0 + h; z++) {
						for (int y = level + 1; y <= t.top(x, z); y++) {
							plan.set(x, y, z, Piece.AIR);
						}
						plan.set(x, level, z, Piece.SOURCE);
						plan.set(x, level - 1, z, Piece.SOURCE);
						sources += 2;
					}
				}
				List<Placement> placements = plan.build();
				double score = -(high - level) * 4 - 0.1 * (dx * dx + dz * dz) / REACH + unit(seed, dx, dz);
				if (score > bestScore && Fit.fits(t, placements) && FluidSim.contained(t, placements)) {
					bestScore = score;
					best = new Plan(placements, x0, level, z0, 0, sources);
				}
			}
		}
		return best;
	}

	/** A number in [0, 1) from the seed and two integers. */
	public static double unit(long seed, int a, int b) {
		return (Noise.mix(seed ^ Noise.mix(a * 0x632BE59BD9B4E019L + b * 0x85157AF5L)) >>> 11) * 0x1.0p-53;
	}
}
