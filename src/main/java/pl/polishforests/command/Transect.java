package pl.polishforests.command;

import java.util.function.IntPredicate;
import pl.polishforests.worldgen.landscape.ColumnSample;

/**
 * Step S9: a transect from the bank of the nearest watercourse outwards, for the belts of a few blocks along the water
 * ({@code find zone montane_tall_herbs}: 1–2 blocks by mountain streams), which a lattice of tens of meters misses. From
 * a point near a bank it walks down the distance to the channel ({@code Waters.channelDist}, about 1 m per meter) to the
 * bank, then tests columns every {@value #STEP} m along the direction away from the channel, from a little inside the
 * channel to the given length.
 */
final class Transect {
	/** Spacing of the tested columns (m). */
	static final double STEP = 0.5;
	/** Start of the transect inside the channel (m), for belts on the bank line and in the channel. */
	static final double INSIDE = 4;
	/** Steps of the walk to the bank. */
	private static final int WALK = 8;
	/** Distance for the gradient of the distance to the channel (m). */
	private static final double H = 1;

	private Transect() {
	}

	/**
	 * The first column of the transect from the bank near {@code p} that passes the test, {x, z}, or null when {@code p}
	 * lies farther than {@code band} from a channel or no column of the transect passes.
	 */
	static double[] fromBank(HabitatTargets.World world, double[] p, double band, double length, IntPredicate test) {
		var model = world.model();
		double x = p[0];
		double z = p[1];
		double d = distance(model.sample(x, z));
		if (!(d <= band)) {
			return null;
		}
		double ux = 0;
		double uz = 0;
		for (int i = 0; i < WALK; i++) {
			double gx = distance(model.sample(x + H, z)) - distance(model.sample(x - H, z));
			double gz = distance(model.sample(x, z + H)) - distance(model.sample(x, z - H));
			double len = Math.hypot(gx, gz);
			if (!(len > 1e-9)) {
				return null;
			}
			ux = gx / len;
			uz = gz / len;
			if (Math.abs(d) < STEP) {
				break;
			}
			x -= ux * d;
			z -= uz * d;
			d = distance(model.sample(x, z));
			if (!Double.isFinite(d)) {
				return null;
			}
		}
		for (double t = -INSIDE; t <= length; t += STEP) {
			double qx = x + ux * t;
			double qz = z + uz * t;
			if (test.test(world.code(qx, qz))) {
				return new double[] {qx, qz};
			}
		}
		return null;
	}

	private static double distance(ColumnSample s) {
		return s.waters().channelDist();
	}
}
