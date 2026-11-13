package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Step TT2 (docs/04-pionowy-wycinek.md, "Projekt TT2"): the surface zones of the Tatra rocks, before the belts (their zone
 * stays, the biome comes from the next steps).
 * <ul>
 * <li>{@link Zone#SNOW_PATCH}: perennial snow in the gullies and cirques facing north (aspect within 50° of north) above
 * about {@value #SNOW_FROM} m, in patches whose share grows with the height.</li>
 * <li>{@link Zone#SCREE}: the scree cones at the gully mouths ({@link Landform#SCREE}).</li>
 * <li>{@link Zone#BARE_ROCK}: the pinnacles and the walls steeper than about {@value #BARE_SLOPE}° in blocks, and patches
 * of the summit ridges above about {@value #BARE_FROM} m.</li>
 * </ul>
 * Every threshold jitters with a noise of a wavelength of tens of meters, so no boundary is a straight line or a contour
 * (Z10).
 */
final class TatraZones {
	static final double SNOW_FROM = 1_700;
	static final double SNOW_JITTER = 60;
	/** Aspect within this many degrees of north (the downslope direction). */
	static final double SNOW_ASPECT = 50;
	static final double BARE_SLOPE = 48;
	static final double BARE_FROM = 2_250;

	private TatraZones() {
	}

	static int classify(HabitatClassifier.Column c) {
		Substrate rock = c.t.rock();
		if (rock == null || !rock.isTatraRock() || c.t.wTatra() < 0.5) {
			return HabitatClassifier.Result.NONE;
		}
		int f = c.t.landformBits();
		double e = c.t.aspect();
		boolean north = !Double.isNaN(e) && (e < SNOW_ASPECT || e > 360 - SNOW_ASPECT);
		boolean hollow = (f & (Landform.GULLY.bit() | Landform.CIRQUE.bit())) != 0;
		if (north && hollow && c.H > SNOW_FROM + SNOW_JITTER * c.jitterNoise(41, 120)) {
			double share = 0.35 + 0.45 * smooth(SNOW_FROM, SNOW_FROM + 400, c.H);
			if (c.patchQ(42, 45) < share) {
				return HabitatClassifier.Result.zone(Zone.SNOW_PATCH);
			}
		}
		if ((f & Landform.SCREE.bit()) != 0) {
			return HabitatClassifier.Result.zone(Zone.SCREE);
		}
		if ((f & Landform.PINNACLE.bit()) != 0 || c.blockSlope() > BARE_SLOPE + 6 * c.jitterNoise(43, 90)
				|| c.H > BARE_FROM + 80 * c.jitterNoise(44, 200) && c.patchQ(45, 50) < 0.6) {
			return HabitatClassifier.Result.zone(Zone.BARE_ROCK);
		}
		return HabitatClassifier.Result.NONE;
	}

	private static double smooth(double a, double b, double v) {
		double t = Math.clamp((v - a) / (b - a), 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}
}
