package pl.polishforests.worldgen.tree;

import pl.polishforests.worldgen.norms.CensusGrid;

/**
 * Writes a dead wood plan into a census grid as {@code DeadwoodFeature.write} writes it into the world (step V11): the wood
 * on air or leaves in the planner's order (a block whose parent is missing is left out with its descendants), the soil of
 * a root plate as other blocks on air, moss and leaf litter as the plant of free columns, and the base column of a
 * standing object marked dead (as the game test records it). The pits are left out (the grid keeps its ground).
 */
public final class DeadwoodGrid {
	private DeadwoodGrid() {
	}

	/** Writes the plan; returns the wood blocks written. */
	public static int write(CensusGrid g, DeadwoodVoxels v) {
		TreeVoxels t = v.wood();
		int n = t.woodCount();
		boolean[] ok = new boolean[n];
		int count = 0;
		for (int i = 0; i < n; i++) {
			int p = t.parentIndex(i);
			if (p >= 0 && !ok[p]) {
				continue;
			}
			int x = t.x() + t.woodX(i);
			int z = t.z() + t.woodZ(i);
			if (!g.contains(x, z) || g.ground(x, z) == CensusGrid.NO_GROUND) {
				continue;
			}
			int dy = t.y() + t.woodY(i) - g.ground(x, z);
			if (dy < 1 || dy > g.height()) {
				continue;
			}
			byte c = g.cell(x, dy, z);
			if (c != CensusGrid.AIR && c != CensusGrid.LEAVES) {
				continue;
			}
			int parent = t.parent(i);
			g.setWood(x, dy, z, parent <= TreeVoxels.UP, CensusGrid.trunk(t.radius(i), v.species(), "polishforests:" + v.species().id()
					+ "_trunk", parent));
			ok[i] = true;
			count++;
		}
		if (count == 0) {
			return 0;
		}
		for (int s : v.soil()) {
			int x = t.x() + v.x(s);
			int z = t.z() + v.z(s);
			if (!g.contains(x, z) || g.ground(x, z) == CensusGrid.NO_GROUND) {
				continue;
			}
			int dy = t.y() + v.y(s) - g.ground(x, z);
			if (dy >= 1 && dy <= g.height() && g.cell(x, dy, z) == CensusGrid.AIR) {
				g.set(x, dy, z, CensusGrid.OTHER);
			}
		}
		for (int i = 0; i < v.cover().length; i++) {
			int x = t.x() + v.x(v.cover()[i]);
			int z = t.z() + v.z(v.cover()[i]);
			if (g.contains(x, z) && g.ground(x, z) == t.y() - 1 && g.plant(x, z) == null && g.cell(x, 1, z) == CensusGrid.AIR) {
				g.setPlant(x, z, v.coverType()[i] == DeadwoodPlan.COVER_MOSS ? "minecraft:moss_carpet" : "minecraft:leaf_litter");
			}
		}
		if (v.kind().standing() && g.contains(t.x(), t.z())) {
			g.setDead(t.x(), t.z(), true);
		}
		return count;
	}
}
