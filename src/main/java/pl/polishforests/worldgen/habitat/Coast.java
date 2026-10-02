package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Krok 2 klasyfikatora: pas wybrzeża cD &lt; B + D + 2000k (§5.2, raport ekologii §4). Plaża, wydmy,
 * bór bażynowy, klif i zaplecze zalewu; długości mnożone przez k. Dna dolin i brzegi wód stojących
 * zostawiamy strefom nadwodnym (poza plażą i wydmami).
 *
 * <p>Brzeg wydmowy i klifowy rozróżnia pole {@code teren.niskiBrzeg}, a nie podłoże: model daje glinę
 * i formę KLIF każdej kolumnie pasa nadmorskiego wyższej niż 8 m, także wysokiej wydmie przedniej. Na brzegu
 * wydmowym podłoże siedliska to piasek ({@link HabitatClassifier.Column#substrate}), a klif i jego zaplecze
 * są tylko na brzegu wysokim.
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
		// Granica boru bażynowego (2000 m·k) drga z szumem wariantów, żeby nie była linią równoległą do brzegu;
		// pas kończy się za jej najdalszym położeniem.
		double isPineForest = Calibration.COASTAL_PINE_K * k;
		if (cD < 0 || cD >= Math.max(b + d + isPineForest, isPineForest * (1 + Calibration.COASTAL_PINE_JITTER))) {
			return HabitatClassifier.Result.NONE;
		}
		Substrate sub = c.substrate;
		boolean duneShore = c.isDuneShore();
		// Klif (tylko brzeg wysoki): ściana z gołą gliną i korona z zaroślami w biomie wysoczyzny; dalej las wiatrowy.
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
		// Dna dolin i brzegi wód stojących przy morzu: strefy nadwodne.
		if (c.onValleyFloor() || c.w.s() < Calibration.LAKE_ALDER_CARR_K * k) {
			return HabitatClassifier.Result.NONE;
		}
		if (sub != Substrate.SAND) {
			return HabitatClassifier.Result.NONE;
		}
		// Zaplecze zalewu: niski brzeg za wydmami (lustro morza 0 m, h = H − 1); zalew i mierzeja powstają
		// tylko na brzegu wydmowym.
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
