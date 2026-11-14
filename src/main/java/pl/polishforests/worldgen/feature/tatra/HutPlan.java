package pl.polishforests.worldgen.feature.tatra;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Step TT6: a shepherd's hut (szalas) of the halas and the mountain glades, built only in the PRESENT_DAY mode: an A-frame
 * of larch stairs with a larch ridge beam, {@value #WIDTH} blocks wide and {@value #LENGTH} long, a closed back wall,
 * a front wall with a one-block doorway and two hay blocks inside, on a plank floor. The plan is made of the mod's
 * larch wood and a vanilla hay block only; it is built only where the ground of the site and of a block around it is
 * flat enough ({@link #MAX_RANGE}, {@link #MAX_STEP}) and dry; the cells under the floor are filled with rock, the cells
 * over it are cleared (also of trees).
 *
 * <p>Local frame: u across the roof from 0 to 4, v along the ridge from the door (0) to the back wall (5), h above the floor.
 * The roof stairs stand at (0, h1), (1, h2) and the mirror ones, the beam at (2, h3).
 */
public final class HutPlan {
	public static final int WIDTH = 5;
	public static final int LENGTH = 6;
	/** Greatest difference of the ground height over the footprint and the block around it (blocks). */
	public static final int MAX_RANGE = 3;
	/** Greatest difference of the ground height of two neighboring columns of the footprint (blocks). */
	public static final int MAX_STEP = 2;
	/** Search radius of the center around the anchor (blocks). */
	public static final int REACH = 8;

	/** A planned hut: the placements, the floor level, the direction the door faces and the range of the ground under it. */
	public record Plan(List<Placement> placements, int x, int floor, int z, int facing, int range) {
	}

	private HutPlan() {
	}

	/** The best hut within {@link #REACH} of the anchor, or null. */
	public static @Nullable Plan hut(TerrainView t, int ax, int az, long seed) {
		Plan best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int f = 0; f < 4; f++) {
			int vdir = Dirs.opposite(f);
			int pdir = Dirs.right(f);
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					int cx = ax + dx;
					int cz = az + dz;
					// The world cell of the local (0, 0): the local (2, 2) lies at (cx, cz).
					int ox = cx - 2 * Dirs.DX[pdir] - 2 * Dirs.DX[vdir];
					int oz = cz - 2 * Dirs.DZ[pdir] - 2 * Dirs.DZ[vdir];
					int lo = Integer.MAX_VALUE;
					int hi = Integer.MIN_VALUE;
					double front = 0;
					double back = 0;
					boolean ok = true;
					for (int u = -1; u <= WIDTH && ok; u++) {
						for (int v = -1; v <= LENGTH && ok; v++) {
							int x = ox + u * Dirs.DX[pdir] + v * Dirs.DX[vdir];
							int z = oz + u * Dirs.DZ[pdir] + v * Dirs.DZ[vdir];
							int top = t.top(x, z);
							if (!t.solid(x, top, z) || t.fluid(x, top, z) || t.fluid(x, top + 1, z)) {
								ok = false;
								break;
							}
							lo = Math.min(lo, top);
							hi = Math.max(hi, top);
							if (v <= 0) {
								front += top;
							} else if (v >= LENGTH - 1) {
								back += top;
							}
							if (u >= 0 && v >= 0 && u < WIDTH && v < LENGTH) {
								if (u > 0 && Math.abs(top - t.top(x - Dirs.DX[pdir], z - Dirs.DZ[pdir])) > MAX_STEP
										|| v > 0 && Math.abs(top - t.top(x - Dirs.DX[vdir], z - Dirs.DZ[vdir])) > MAX_STEP) {
									ok = false;
								}
							}
						}
					}
					if (!ok || hi - lo > MAX_RANGE) {
						continue;
					}
					// The door on the lower side; near the anchor; the flatter the better.
					double score = -(hi - lo) * 10 - Math.hypot(dx, dz) + (front <= back ? 2 : 0) + WaterPlan.unit(seed, f * 97 + dx, dz);
					if (score > bestScore) {
						Plan p = build(t, ox, oz, f, hi - lo);
						if (Fit.fits(t, p.placements())) {
							bestScore = score;
							best = p;
						}
					}
				}
			}
		}
		return best;
	}

	private static Plan build(TerrainView t, int ox, int oz, int f, int range) {
		int vdir = Dirs.opposite(f);
		int pdir = Dirs.right(f);
		int floor = Integer.MIN_VALUE;
		for (int u = 0; u < WIDTH; u++) {
			for (int v = 0; v < LENGTH; v++) {
				floor = Math.max(floor, t.top(ox + u * Dirs.DX[pdir] + v * Dirs.DX[vdir], oz + u * Dirs.DZ[pdir] + v * Dirs.DZ[vdir]));
			}
		}
		PlanBuilder plan = new PlanBuilder();
		for (int u = 0; u < WIDTH; u++) {
			for (int v = 0; v < LENGTH; v++) {
				int x = ox + u * Dirs.DX[pdir] + v * Dirs.DX[vdir];
				int z = oz + u * Dirs.DZ[pdir] + v * Dirs.DZ[vdir];
				for (int y = t.top(x, z) + 1; y < floor; y++) {
					plan.set(x, y, z, Piece.ROCK);
				}
				plan.set(x, floor, z, Piece.PLANKS);
				for (int h = 1; h <= 4; h++) {
					plan.set(x, floor + h, z, Piece.AIR);
				}
			}
		}
		int axis = vdir == 1 || vdir == 3 ? 0 : 1;
		for (int v = 0; v < LENGTH; v++) {
			put(plan, ox, oz, pdir, vdir, 0, v, floor + 1, Piece.STAIRS, pdir);
			put(plan, ox, oz, pdir, vdir, 4, v, floor + 1, Piece.STAIRS, Dirs.opposite(pdir));
			put(plan, ox, oz, pdir, vdir, 1, v, floor + 2, Piece.STAIRS, pdir);
			put(plan, ox, oz, pdir, vdir, 3, v, floor + 2, Piece.STAIRS, Dirs.opposite(pdir));
			put(plan, ox, oz, pdir, vdir, 2, v, floor + 3, Piece.LOG, axis);
		}
		// The back wall, the front wall with the doorway, the hay.
		for (int u = 1; u <= 3; u++) {
			put(plan, ox, oz, pdir, vdir, u, LENGTH - 1, floor + 1, Piece.PLANKS, 0);
		}
		put(plan, ox, oz, pdir, vdir, 2, LENGTH - 1, floor + 2, Piece.PLANKS, 0);
		put(plan, ox, oz, pdir, vdir, 1, 0, floor + 1, Piece.PLANKS, 0);
		put(plan, ox, oz, pdir, vdir, 3, 0, floor + 1, Piece.PLANKS, 0);
		put(plan, ox, oz, pdir, vdir, 1, LENGTH - 2, floor + 1, Piece.HAY, 0);
		put(plan, ox, oz, pdir, vdir, 3, LENGTH - 2, floor + 1, Piece.HAY, 0);
		int cx = ox + 2 * Dirs.DX[pdir] + 2 * Dirs.DX[vdir];
		int cz = oz + 2 * Dirs.DZ[pdir] + 2 * Dirs.DZ[vdir];
		return new Plan(plan.build(), cx, floor, cz, f, range);
	}

	private static void put(PlanBuilder plan, int ox, int oz, int pdir, int vdir, int u, int v, int y, Piece piece, int param) {
		plan.set(ox + u * Dirs.DX[pdir] + v * Dirs.DX[vdir], y, oz + u * Dirs.DZ[pdir] + v * Dirs.DZ[vdir], piece, param);
	}
}
