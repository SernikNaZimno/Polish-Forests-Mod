package pl.polishforests.worldgen.feature.tatra;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A conservative model of the flow of vanilla water (step TT6) that decides whether a planned piece of water stays where
 * it is put. It follows {@code FlowingFluid.spread}: water over a free cell (not solid, not a source block) flows only
 * down, and sideways too only beside three sources; water over a solid cell, and a source over a source, flows
 * sideways, with the amount of one less than the cell's (a source and falling water have 8, so they reach 7 cells
 * out); flowing water over water does not spread; a flowing cell between two sources over a solid or a source becomes a
 * source. It spreads into every free neighbor (vanilla prefers the direction of the nearest drop), so the set it reaches
 * contains what the game's water reaches. A plan is {@link #contained} when the cells reached are exactly its own water.
 */
public final class FluidSim {
	private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private FluidSim() {
	}

	/**
	 * Whether the water of {@code plan} stays in its own cells over {@code terrain}: the cells the sources of the plan
	 * reach are all planned water, and no planned water cell touches other water or lava of the terrain.
	 */
	public static boolean contained(TerrainView terrain, List<Placement> plan) {
		return stray(terrain, plan) == 0;
	}

	/** Number of cells the water of the plan reaches beyond its own water cells (-1: it touches other fluid). */
	public static int stray(TerrainView terrain, List<Placement> plan) {
		Set<Long> water = new HashSet<>();
		Set<Long> sources = new HashSet<>();
		Set<Long> added = new HashSet<>();
		Set<Long> carved = new HashSet<>();
		for (Placement p : plan) {
			long k = p.key();
			if (p.piece().water()) {
				water.add(k);
				if (p.piece() == Piece.SOURCE) {
					sources.add(k);
				}
			} else if (p.piece() == Piece.AIR) {
				carved.add(k);
			} else {
				added.add(k);
			}
		}
		if (water.isEmpty()) {
			return 0;
		}
		for (long k : water) {
			int x = Cells.x(k);
			int y = Cells.y(k);
			int z = Cells.z(k);
			for (int[] n : NEIGHBORS) {
				long m = Cells.pack(x + n[0], y + n[1], z + n[2]);
				if (!water.contains(m) && !carved.contains(m) && !added.contains(m) && terrain.fluid(x + n[0], y + n[1], z + n[2])) {
					return -1;
				}
			}
		}
		Map<Long, Integer> amount = new HashMap<>();
		ArrayDeque<Long> queue = new ArrayDeque<>();
		for (long s : sources) {
			amount.put(s, 8);
			queue.add(s);
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			while (!queue.isEmpty()) {
				long c = queue.poll();
				int a = amount.get(c);
				int x = Cells.x(c);
				int y = Cells.y(c);
				int z = Cells.z(c);
				long below = Cells.pack(x, y - 1, z);
				boolean belowSolid = solid(terrain, water, added, carved, x, y - 1, z);
				boolean belowSource = sources.contains(below);
				if (!belowSolid && !belowSource) {
					add(amount, queue, below, 8);
					if (sourceNeighbors(sources, x, y, z) >= 3) {
						sides(terrain, water, added, carved, amount, queue, x, y, z, a);
					}
				} else if (sources.contains(c) || belowSolid) {
					sides(terrain, water, added, carved, amount, queue, x, y, z, a);
				}
			}
			// A flowing cell between two sources, over a solid or a source, becomes a source.
			for (long c : new HashSet<>(amount.keySet())) {
				if (sources.contains(c)) {
					continue;
				}
				int x = Cells.x(c);
				int y = Cells.y(c);
				int z = Cells.z(c);
				if (sourceNeighbors(sources, x, y, z) >= 2 && (solid(terrain, water, added, carved, x, y - 1, z)
						|| sources.contains(Cells.pack(x, y - 1, z)))) {
					sources.add(c);
					amount.put(c, 8);
					queue.add(c);
					changed = true;
				}
			}
		}
		int stray = 0;
		for (long k : amount.keySet()) {
			if (!water.contains(k)) {
				stray++;
			}
		}
		return stray;
	}

	private static boolean solid(TerrainView t, Set<Long> water, Set<Long> added, Set<Long> carved, int x, int y, int z) {
		if (y < t.minY()) {
			return true;
		}
		long k = Cells.pack(x, y, z);
		if (water.contains(k)) {
			return false;
		}
		if (added.contains(k)) {
			return true;
		}
		return !carved.contains(k) && t.solid(x, y, z);
	}

	private static int sourceNeighbors(Set<Long> sources, int x, int y, int z) {
		int n = 0;
		for (int d = 0; d < 4; d++) {
			if (sources.contains(Cells.pack(x + Dirs.DX[d], y, z + Dirs.DZ[d]))) {
				n++;
			}
		}
		return n;
	}

	private static void sides(TerrainView t, Set<Long> water, Set<Long> added, Set<Long> carved, Map<Long, Integer> amount,
			ArrayDeque<Long> queue, int x, int y, int z, int a) {
		int next = a - 1;
		if (next < 1) {
			return;
		}
		for (int d = 0; d < 4; d++) {
			int nx = x + Dirs.DX[d];
			int nz = z + Dirs.DZ[d];
			if (!solid(t, water, added, carved, nx, y, nz)) {
				add(amount, queue, Cells.pack(nx, y, nz), next);
			}
		}
	}

	private static void add(Map<Long, Integer> amount, ArrayDeque<Long> queue, long cell, int a) {
		Integer old = amount.get(cell);
		if (old == null || old < a) {
			amount.put(cell, a);
			queue.add(cell);
		}
	}
}
