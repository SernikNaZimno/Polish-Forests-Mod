package pl.polishforests.worldgen.feature.tatra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects the placements of one plan, one per cell (a later piece replaces an earlier one in the same cell). */
final class PlanBuilder {
	private final Map<Long, Placement> cells = new LinkedHashMap<>();

	void set(int x, int y, int z, Piece piece) {
		set(x, y, z, piece, 0);
	}

	void set(int x, int y, int z, Piece piece, int param) {
		Placement p = new Placement(x, y, z, piece, param);
		cells.put(p.key(), p);
	}

	/** Puts the piece only where the cell has none yet. */
	void setIfAbsent(int x, int y, int z, Piece piece, int param) {
		cells.putIfAbsent(Cells.pack(x, y, z), new Placement(x, y, z, piece, param));
	}

	boolean has(int x, int y, int z) {
		return cells.containsKey(Cells.pack(x, y, z));
	}

	Placement get(int x, int y, int z) {
		return cells.get(Cells.pack(x, y, z));
	}

	int size() {
		return cells.size();
	}

	List<Placement> build() {
		return new ArrayList<>(cells.values());
	}
}
