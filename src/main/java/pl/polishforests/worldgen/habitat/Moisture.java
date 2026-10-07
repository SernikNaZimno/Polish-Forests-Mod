package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Site moisture from the depth to groundwater (DGW, §3.3, ecology report §0.2):
 * GW = min(sBar − g_type; L_w + i·r), DGW = clamp(H − GW; 0; 12).
 */
public enum Moisture {
	DRY, FRESH, MOIST, BOGGY;

	/**
	 * DGW of a column (m). {@code L_w} and {@code r} are the water level (+0.3 m) and the distance to water: channel,
	 * standing water and sea; we take the lowest water table of them. The distance is at 1:1 scale (r / k, Z7), because
	 * at gameplay scale distances are compressed at similar heights. {@code i} is the water table gradient: flysch
	 * in the mountains, sand at fertility B and BM, till otherwise. Peat gives 0, a shore on mud ≤ 0.2. Perched water
	 * on till: a terrain concavity in front of the valleys (rawSurface − sBar) ≤ −1.5 m gives DGW ≤ 1.0, and ≤ −3 m gives ≤ 0.3.
	 */
	static double dgw(HabitatClassifier.Column c) {
		ColumnSample.Terrain t = c.t;
		ColumnSample.Waters w = c.w;
		Substrate sub = c.substrate;
		if (sub == Substrate.PEAT) {
			return 0;
		}
		double g = c.wOutwashPlain * Calibration.G_OUTWASH_PLAIN + c.wCoastland * Calibration.G_COASTLAND + c.wOldGlacialPlain * Calibration.G_OLD_GLACIAL_PLAIN
				+ c.wMorainePlateau * Calibration.G_MORAINE_PLATEAU + c.wFoothills * Calibration.G_FOOTHILLS + c.wBeskids * Calibration.G_BESKIDS;
		double gw = t.sBar() - g;
		double i = c.wMountains > 0.5 ? Calibration.I_FLYSCH : c.sandy() ? Calibration.I_SAND : Calibration.I_TILL;
		// Watercourses and standing water act in valleys and hollows (terrain incision), always on the floor; outside
		// them the term gets a penalty, so DGW does not jump at the edge of the river network query range.
		double penalty = c.onValleyFloor() ? 0
				: Calibration.OUTSIDE_VALLEY_PENALTY * (1 - Noise.smoothstep(Calibration.INCISION_FROM, Calibration.INCISION_TO,
						t.rawSurface() - c.H));
		if (w.streamOrder() > 0 && Double.isFinite(w.channelDist()) && !Double.isNaN(w.softChannelLevel())) {
			// The level of the nearest channel is sometimes higher than the floor the column lies on (a tributary
			// descending in a rapid to the floor of a larger valley); the model floor lies at least 1.2 m above its channel's water level.
			double waterLevel = Math.min(w.softChannelLevel(), c.H - Calibration.FLOOR_ABOVE_WATER_LEVEL);
			gw = Math.min(gw, waterLevel + Calibration.WATER_LEVEL_OFFSET + i * Math.max(0, w.channelDist()) / c.k + penalty);
		}
		// Oxbow lakes are skipped: they lie on the floor, where the river sets the water table, and their rings (ring
		// segments from the model, also without water at narrow watercourses) produced rectangular patches in DGW.
		if (Double.isFinite(w.s()) && w.shoreLevel() != ColumnSample.NO_WATER
				&& w.standingWaterKind() != ColumnSample.StandingWaterKind.OXBOW_LAKE) {
			gw = Math.min(gw, w.shoreLevel() + Calibration.WATER_LEVEL_OFFSET + i * Math.max(0, w.s()) / c.k + penalty);
		}
		// The sea without a range cutoff: the term grows with cD, so farther from the shore it loses to the terrain anyway.
		gw = Math.min(gw, Calibration.WATER_LEVEL_OFFSET + i * Math.max(0, t.coastD()) / c.k);
		double d = Math.clamp(c.H - gw, 0.0, Calibration.DGW_MAX);
		if (sub == Substrate.LAKE_MUD) {
			d = Math.min(d, Calibration.LAKE_MUD_DGW);
		}
		if (sub == Substrate.GLACIAL_TILL && !c.onValleyFloor()) {
			double concavity = t.rawSurface() - t.sBar();
			if (concavity <= Calibration.PERCHED_2) {
				d = Math.min(d, Calibration.PERCHED_2_DGW);
			} else if (concavity <= Calibration.PERCHED_1) {
				d = Math.min(d, Calibration.PERCHED_1_DGW);
			}
		}
		return d;
	}

	/**
	 * Moisture class (§3.3): the DGW band 0.5–0.8 between boggy and moist is decided by patch noise with a wavelength
	 * of 80 m at both scales (with a wavelength of 35 m·k the zonal alder carr came out in specks narrower than 10 blocks).
	 */
	static Moisture compute(HabitatClassifier.Column c) {
		double d = c.dgw();
		if (c.substrate == Substrate.PEAT) {
			return BOGGY;
		}
		if (d <= Calibration.DGW_BOGGY) {
			return BOGGY;
		}
		if (d <= Calibration.DGW_MOIST) {
			double limit = Calibration.DGW_BOGGY
					+ (Calibration.DGW_MOIST - Calibration.DGW_BOGGY) * c.patchQ(7, Calibration.ALDER_CARR_PATCH_WAVELENGTH / c.k);
			return d <= limit ? BOGGY : MOIST;
		}
		if (d <= Calibration.DGW_FRESH) {
			return MOIST;
		}
		if (d > Calibration.DGW_DRY && c.substrate == Substrate.SAND
				&& (c.t.has(Landform.INLAND_DUNES) || c.t.duneHeight() >= Calibration.DRY_DUNE || c.concavity() > Calibration.DRY_CONVEXITY)) {
			return DRY;
		}
		return FRESH;
	}
}
