package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.List;

/**
 * Access to the river network for tests outside this package (habitat tests): confluences, the geometry of a river
 * from its own segments and the number of segments in the culling frame of a column. Test code only.
 */
public final class RiverNetworkProbe {
	private RiverNetworkProbe() {
	}

	/**
	 * Confluences of small tributaries with large rivers: the downstream ends of segments of order 2, then 1 (grid
	 * cells i, j from −r to r of each order, in that order), whose end lies on the lowland floor of a valley of order
	 * 3. The first {@code count} such points, as {x, z}, each at least {@code minSpacing} (Chebyshev distance) from
	 * the points already taken, so that square windows of side {@code minSpacing} around them do not overlap and no
	 * column is counted twice (two tributaries can end in the same node or a few meters apart).
	 */
	public static List<double[]> confluences(LandscapeModel m, int r, int count, double minSpacing) {
		RiverNetwork rivers = RiverNetworkTest.networkOf(m);
		List<double[]> out = new ArrayList<>();
		for (int order = 2; order >= 1; order--) {
			for (long i = -r; i <= r; i++) {
				for (long j = -r; j <= r; j++) {
					RiverNetwork.Segment s = rivers.segment(order, i, j);
					if (s == null) {
						continue;
					}
					boolean near = out.stream().anyMatch(q -> Math.max(Math.abs(q[0] - s.x1), Math.abs(q[1] - s.z1)) < minSpacing);
					if (near) {
						continue;
					}
					ColumnSample c = m.sample(s.x1, s.z1);
					ColumnSample.Waters w = c.waters();
					if (w.streamOrder() == 3 && w.inFloor() && c.type().isLowland()) {
						out.add(new double[] {s.x1, s.z1});
						if (out.size() == count) {
							return out;
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * The order-3 river at (x, z) from its own segments, independently of the dominant valley chosen by the model and
	 * of the F2 fields: {distance from the bank of the nearest order-3 channel, its width, its water level, the gradient
	 * of its segment in ‰ (realistic scale), 1 when the column lies on the floor of some order-3 segment (floor
	 * distance below the floor half-width, head fade above 0.5, as {@code inFloor} of the model), else 0}. Null when
	 * no order-3 segment passes the culling frame.
	 */
	public static double[] river(LandscapeModel m, double x, double z) {
		LandscapeModel.Blend b = m.blend(x, z);
		double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
				+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
		List<double[]> segments = RiverNetworkTest.networkOf(m).segmentsAt(3, x, z, lowland, b.weight(LandscapeType.FOOTHILLS),
				b.weight(LandscapeType.BESKIDS));
		double[] nearest = null;
		boolean onFloor = false;
		for (double[] g : segments) {
			if (nearest == null || g[0] < nearest[0]) {
				nearest = g;
			}
			onFloor |= g[3] < g[4] && g[5] > 0.5;
		}
		return nearest == null ? null : new double[] {nearest[0], nearest[1], nearest[2], nearest[6], onFloor ? 1 : 0};
	}

	/** Number of segments in the culling frame of the column (x, z): the candidates of the F2 buffer. */
	public static int frameCandidates(LandscapeModel m, double x, double z) {
		return RiverNetworkTest.networkOf(m).frameCandidates(x, z);
	}

	/** Initial capacity of the F2 candidate buffer. */
	public static int floorCandidateCapacity() {
		return RiverNetwork.FLOOR_CANDIDATES;
	}
}
