package pl.polishforests.worldgen.feature.tatra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step TT6 (review fixes): whether a finished plan may be built on the ground the region holds. A plan fits when every cell
 * it writes lies in a column the builder may write ({@link TerrainView#writable}: its own chunk, so the result does not
 * depend on the order of the chunks) and no tree trunk stands in a column the plan carves or fills, from its lowest cell
 * to the cell above its highest one: a trunk cut or buried by the plan would leave its crown floating. A plan of a tunnel
 * ({@code buried}: the mouth of a cave) may also carve air under the ground of a neighbor chunk: no tree, plant or snow of
 * the neighbor lands in rock, so the order of the chunks does not matter there.
 */
final class Fit {
	private Fit() {
	}

	static boolean fits(TerrainView t, List<Placement> plan) {
		return fits(t, plan, false);
	}

	static boolean fits(TerrainView t, List<Placement> plan, boolean buried) {
		// The lowest and the highest planned cell of each column.
		Map<Long, int[]> columns = new HashMap<>();
		for (Placement p : plan) {
			if (!t.writable(p.x(), p.z()) && !(buried && p.piece() == Piece.AIR && p.y() < t.top(p.x(), p.z()))) {
				return false;
			}
			columns.merge(Cells.pack(p.x(), 0, p.z()), new int[] {p.y(), p.y(), p.x(), p.z()}, (a, b) -> {
				a[0] = Math.min(a[0], b[0]);
				a[1] = Math.max(a[1], b[1]);
				return a;
			});
		}
		for (int[] c : columns.values()) {
			for (int y = c[0]; y <= c[1] + 1; y++) {
				if (t.log(c[2], y, c[3])) {
					return false;
				}
			}
		}
		return true;
	}
}
