package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Classifier step 2: the coastal belt cD &lt; B + D + 2000k (§5.2, ecology report §4). Beach, dunes,
 * crowberry pine forest, cliff and lagoon hinterland; lengths are multiplied by k. Valley floors and the shores
 * of standing water are left to the waterside zones (except for the beach and dunes).
 *
 * <p>Dune and cliff shores are told apart by the {@code terrain.lowShore} field, not by the substrate: the model
 * gives till and the CLIFF landform to every column of the coastal belt higher than 8 m, including a high foredune.
 * On a dune shore the habitat substrate is sand ({@link HabitatClassifier.Column#substrate}), and the cliff and its
 * hinterland exist only on a high shore.
 */
final class Coast {
	private Coast() {
	}

	static int classify(HabitatClassifier.Column c) {
		ColumnSample.Terrain t = c.t;
		double k = c.k;
		double cD = t.coastD();
		double b = Calibration.BEACH_B * k;
		double d = Calibration.DUNES_D * k;
		// The crowberry pine forest boundary (2000 m·k) jitters with the variant noise so that it is not a line
		// parallel to the shore; the belt ends beyond its farthest position.
		double isPineForest = Calibration.COASTAL_PINE_K * k;
		if (cD < 0 || cD >= Math.max(b + d + isPineForest, isPineForest * (1 + Calibration.COASTAL_PINE_JITTER))) {
			return HabitatClassifier.Result.NONE;
		}
		Substrate sub = c.substrate;
		boolean duneShore = c.isDuneShore();
		// Cliff (high shore only): a face of bare till and a top with scrub in the plateau biome; beyond it windswept forest.
		double raw = t.rawSurface();
		if (!duneShore && t.has(Landform.CLIFF)) {
			return HabitatClassifier.Result.zone(Zone.CLIFF_FACE);
		}
		if (!duneShore && sub == Substrate.GLACIAL_TILL && raw > Calibration.CLIFF_H && c.H > Calibration.CLIFF_H) {
			double edge = b + raw / 2.5 + Calibration.CLIFF_TOP_K * k;
			if (cD < edge + Calibration.CLIFF_TOP_K * k) {
				return HabitatClassifier.Result.zone(Zone.CLIFF_TOP);
			}
			if (cD < edge + Calibration.CLIFF_WINDSWEPT_FOREST_K * k) {
				return HabitatClassifier.Result.of(null, Zone.NONE, Association.WINDSWEPT);
			}
			return HabitatClassifier.Result.NONE;
		}
		if (cD < b) {
			Association z = raw > Calibration.SHINGLE_BEACH_RAW ? Association.SHINGLE : Association.TYPICAL;
			Zone s = cD >= Calibration.STRANDLINE_B * b ? Zone.STRANDLINE : Zone.NONE;
			return HabitatClassifier.Result.of(HabitatBiome.BEACH, s, z);
		}
		if (sub == Substrate.BEACH_SAND) {
			Zone s = cD < b + Calibration.EMBRYO_DUNE_K * k ? Zone.EMBRYO_DUNE : Zone.NONE;
			return HabitatClassifier.Result.of(HabitatBiome.WHITE_DUNE, s, Association.TYPICAL);
		}
		// Valley floors and shores of standing water near the sea: waterside zones.
		if (c.onValleyFloor() || c.w.s() < Calibration.LAKE_ALDER_CARR_K * k) {
			return HabitatClassifier.Result.NONE;
		}
		if (sub != Substrate.SAND) {
			return HabitatClassifier.Result.NONE;
		}
		// Lagoon hinterland: a low shore behind the dunes (sea level 0 m, h = H − 1); the lagoon and the spit form
		// only on a dune shore.
		double h = c.H - 1;
		if (duneShore && cD >= b + d) {
			if (h <= Calibration.LAGOON_PEAT_H) {
				return HabitatClassifier.Result.of(HabitatBiome.FEN, Zone.NONE, Association.TYPICAL);
			}
			if (h <= Calibration.LAGOON_ALDER_CARR_H) {
				return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, Zone.NONE, Association.SPIT);
			}
		}
		if (cD < b + d + Calibration.GRAY_DUNE_K * k) {
			return HabitatClassifier.Result.of(HabitatBiome.GRAY_DUNE, Zone.NONE, Association.TYPICAL);
		}
		double range = isPineForest * (1 + Calibration.COASTAL_PINE_JITTER * (2 * c.variant(4) - 1));
		if (c.H >= Calibration.COASTAL_PINE_H || cD >= range) {
			return HabitatClassifier.Result.NONE;
		}
		if (c.dgw() <= Calibration.DGW_BOGGY) {
			return HabitatClassifier.Result.of(HabitatBiome.BOG_WOODLAND, Zone.NONE, Association.SPIT);
		}
		Association z = cD < b + d + Calibration.WINDSWEPT_PINE_K * k ? Association.STUNTED : Association.TYPICAL;
		return HabitatClassifier.Result.of(HabitatBiome.COASTAL_PINE_FOREST, Zone.NONE, z);
	}
}
