package pl.polishforests.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Step S9: search for the nearest hit around an origin, for {@code /polishforests find biome} and {@code find zone}. A
 * lattice around the origin is split into square cells, visited in rings of cells (Chebyshev distance) outwards; the
 * points of one ring are probed in parallel, one cell row per task, so a task stays inside the grid tiles and river
 * network tiles of its cell (probing a ring of points around the whole circle, as {@link PolishForestsCommands#search}
 * does, thrashes those caches far from the origin). The spacing of the lattice grows with the distance of the cell
 * ({@link Phase}). A probe returns a hit near its lattice point (at most {@code reach} away, e.g. on a transect from a
 * watercourse) or null. The search ends when no later ring can hold a nearer hit, so the result is the nearest hit of
 * the probes (ties broken by position), independent of the thread count. A search cancelled after its first hit (S9
 * review round 1) returns that hit, which may then not be the nearest: the rings that would confirm it were not probed.
 */
final class GridSearch {
	/**
	 * Lattice spacing {@code spacing} for cells whose nearest point lies within {@code radius} of the origin.
	 *
	 * @param spacing distance of the lattice points (m)
	 * @param radius  largest distance of the phase (m)
	 */
	record Phase(double spacing, double radius) {
	}

	private GridSearch() {
	}

	/**
	 * The nearest hit {x, z}, or null within the radius of the last phase.
	 *
	 * @param cell      cell size (m); a multiple of every spacing keeps the lattice regular across cells
	 * @param phases    phases with growing radius and spacing
	 * @param reach     largest distance between a lattice point and the hit its probe returns (m)
	 * @param probe     the hit near a lattice point {x, z} (the array may be reused), or null
	 * @param cancelled checked between rings; true stops the search with the nearest hit so far (null without one)
	 */
	static double[] nearest(double cell, List<Phase> phases, double reach, Function<double[], double[]> probe, double ox,
			double oz, BooleanSupplier cancelled) {
		double max = phases.getLast().radius();
		double[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int ring = 0; ; ring++) {
			// The nearest point of the ring's cells lies at least (ring - 0.5) cells from the origin.
			double ringMin = Math.max(0, (ring - 0.5) * cell);
			if (ringMin > max || ringMin - reach > bestD) {
				break;
			}
			if (cancelled.getAsBoolean()) {
				// A hit found before the cancellation is kept; only its nearness is unconfirmed.
				return best;
			}
			List<double[]> rows = new ArrayList<>();
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					double near = Math.hypot(Math.max(0, Math.abs(i) - 0.5) * cell, Math.max(0, Math.abs(j) - 0.5) * cell);
					Phase phase = phaseAt(phases, near);
					if (phase == null || near - reach > bestD) {
						continue;
					}
					double s = phase.spacing();
					int n = Math.max(1, (int) Math.round(cell / s));
					double step = cell / n;
					double cx = ox + (i - 0.5) * cell;
					double cz = oz + (j - 0.5) * cell;
					for (int a = 0; a < n; a++) {
						rows.add(new double[] {cx + (a + 0.5) * step, cz + 0.5 * step, step, n});
					}
				}
			}
			double limit = Math.min(bestD, max);
			double[][] hits = new double[rows.size()][];
			IntStream.range(0, rows.size()).parallel().forEach(r -> hits[r] = row(rows.get(r), probe, ox, oz, limit, reach));
			for (double[] h : hits) {
				if (h != null) {
					double d = Math.hypot(h[0] - ox, h[1] - oz);
					if (d < bestD || d == bestD && (h[0] < best[0] || h[0] == best[0] && h[1] < best[1])) {
						bestD = d;
						best = h;
					}
				}
			}
		}
		return best;
	}

	/** The nearest hit of the probes of one cell row {x, z0, step, count}, or null. */
	private static double[] row(double[] row, Function<double[], double[]> probe, double ox, double oz, double limit,
			double reach) {
		double x = row[0];
		double s = row[2];
		int n = (int) row[3];
		double[] best = null;
		double bestD = limit;
		double[] p = new double[2];
		for (int b = 0; b < n; b++) {
			double z = row[1] + b * s;
			if (Math.hypot(x - ox, z - oz) - reach > bestD) {
				continue;
			}
			p[0] = x;
			p[1] = z;
			double[] h = probe.apply(p);
			if (h != null) {
				double d = Math.hypot(h[0] - ox, h[1] - oz);
				if (d < bestD || best == null && d == bestD) {
					bestD = d;
					best = new double[] {h[0], h[1]};
				}
			}
		}
		return best;
	}

	private static Phase phaseAt(List<Phase> phases, double distance) {
		for (Phase p : phases) {
			if (distance <= p.radius()) {
				return p;
			}
		}
		return null;
	}
}
