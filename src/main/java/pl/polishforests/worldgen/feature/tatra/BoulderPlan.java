package pl.polishforests.worldgen.feature.tatra;

import java.util.List;

/**
 * Step TT6: a boulder of the Tatra rock on the moraines, the valley floors and the block fields: a rounded lump of
 * {@link Piece#ROCK} of radius 1-3 sunk a block into the ground, with rock lichens ({@link Piece#LICHEN}) on some of its
 * top blocks. Only the shape: the feature puts the blocks where the cells are free.
 */
public final class BoulderPlan {
	public static final int MAX_RADIUS = 3;

	private BoulderPlan() {
	}

	/**
	 * The boulder standing on the ground block {@code groundTop} of column (x, z).
	 *
	 * @param lichens share of the top blocks that get a lichen carpet, in percent
	 */
	public static List<Placement> boulder(long seed, int x, int groundTop, int z, int radius, int lichens) {
		PlanBuilder plan = new PlanBuilder();
		int cy = groundTop + radius - 1;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -radius; dy <= radius; dy++) {
					int y = cy + dy;
					if (y < groundTop - 1) {
						continue;
					}
					double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.44);
					double limit = radius + 0.2 + 0.5 * (WaterPlan.unit(seed, dx * 31 + dy, dz) - 0.5);
					if (d <= limit) {
						plan.set(x + dx, y, z + dz, Piece.ROCK);
					}
				}
			}
		}
		for (Placement p : plan.build()) {
			if (!plan.has(p.x(), p.y() + 1, p.z()) && WaterPlan.unit(seed ^ 0x51ED, p.x(), p.z()) * 100 < lichens) {
				int segments = 2 + (int) (WaterPlan.unit(seed ^ 0x7A11, p.x(), p.z()) * 3);
				plan.set(p.x(), p.y() + 1, p.z(), Piece.LICHEN, segments);
			}
		}
		return plan.build();
	}
}
