package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Wilgotność siedliska z głębokości wody gruntowej (DGW, §3.3, raport ekologii §0.2):
 * GW = min(sBar − g_typ; L_w + i·r), DGW = clamp(H − GW; 0; 12).
 */
public enum Moisture {
	DRY, FRESH, MOIST, BOGGY;

	/**
	 * DGW kolumny (m). {@code L_w} i {@code r} to lustro (+0,3 m) i odległość wody: koryta, wody stojącej
	 * i morza; bierzemy najniższe zwierciadło z nich. Odległość jest w skali 1:1 (r / k, Z7), bo w skali
	 * rozgrywki odległości są ściśnięte przy podobnych wysokościach. {@code i} to spadek zwierciadła: flisz
	 * w górach, piasek przy trofii B i BM, poza tym glina. Torf daje 0, brzeg na mule ≤ 0,2. Woda zawieszona
	 * na glinie: wklęsłość terenu przed dolinami (rawSurface − sBar) ≤ −1,5 m daje DGW ≤ 1,0, a ≤ −3 m daje ≤ 0,3.
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
		// Cieki i wody stojące działają w dolinach i nieckach (wcięcie terenu), w dnie zawsze; poza nimi
		// człon dostaje karę, więc DGW nie skacze na granicy zasięgu zapytania sieci rzecznej.
		double penalty = c.onValleyFloor() ? 0
				: Calibration.OUTSIDE_VALLEY_PENALTY * (1 - Noise.smoothstep(Calibration.INCISION_FROM, Calibration.INCISION_TO,
						t.rawSurface() - c.H));
		if (w.streamOrder() > 0 && Double.isFinite(w.channelDist()) && !Double.isNaN(w.channelLevel())) {
			// Lustro najbliższego koryta bywa wyżej niż dno, przy którym leży kolumna (dopływ schodzący bystrzem
			// do dna większej doliny); dno modelu leży co najmniej 1,2 m nad lustrem jego koryta.
			double waterLevel = Math.min(w.channelLevel(), c.H - Calibration.FLOOR_ABOVE_WATER_LEVEL);
			gw = Math.min(gw, waterLevel + Calibration.WATER_LEVEL_OFFSET + i * Math.max(0, w.channelDist()) / c.k + penalty);
		}
		// Starorzecza pomijamy: leżą w dnie, gdzie zwierciadło wyznacza rzeka, a ich pierścienie (wycinki pierścienia
		// z modelu, także bez wody przy wąskich ciekach) dawały w DGW prostokątne plamy.
		if (Double.isFinite(w.s()) && w.shoreLevel() != ColumnSample.NO_WATER
				&& w.standingWaterKind() != ColumnSample.StandingWaterKind.OXBOW_LAKE) {
			gw = Math.min(gw, w.shoreLevel() + Calibration.WATER_LEVEL_OFFSET + i * Math.max(0, w.s()) / c.k + penalty);
		}
		// Morze bez ucięcia zasięgu: człon rośnie z cD, więc dalej od brzegu i tak przegrywa z terenem.
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
	 * Klasa wilgotności (§3.3): pas DGW 0,5–0,8 między bagienną a wilgotną rozstrzyga szum płatów o fali
	 * 80 m w obu skalach (przy fali 35 m·k ols strefowy wychodził w plamkach węższych niż 10 bloków).
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
