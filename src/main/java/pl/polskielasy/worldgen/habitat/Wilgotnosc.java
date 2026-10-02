package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.Noise;
import pl.polskielasy.worldgen.landscape.Substrate;

/**
 * Wilgotność siedliska z głębokości wody gruntowej (DGW, §3.3, raport ekologii §0.2):
 * GW = min(sBar − g_typ; L_w + i·r), DGW = clamp(H − GW; 0; 12).
 */
public enum Wilgotnosc {
	SUCHA, SWIEZA, WILGOTNA, BAGIENNA;

	/**
	 * DGW kolumny (m). {@code L_w} i {@code r} to lustro (+0,3 m) i odległość wody w zasięgu 2 km·k: koryta,
	 * wody stojącej i morza; bierzemy najniższe zwierciadło z nich. {@code i} to spadek zwierciadła: flisz
	 * w górach, piasek przy trofii B i BM, poza tym glina. Torf daje 0, brzeg na mule ≤ 0,2. Woda zawieszona
	 * na glinie: wklęsłość terenu przed dolinami (rawSurface − sBar) ≤ −1,5 m daje DGW ≤ 1,0, a ≤ −3 m daje ≤ 0,3.
	 */
	static double dgw(Klasyfikator.Kolumna c) {
		ColumnSample s = c.s;
		ColumnSample.Teren t = c.t;
		ColumnSample.Wody w = c.w;
		Substrate sub = s.substrate();
		if (sub == Substrate.PEAT) {
			return 0;
		}
		double g = c.wS * Kalibracja.G_SANDR + c.wPob * Kalibracja.G_POBRZEZE + c.wR * Kalibracja.G_ROWNINA
				+ c.wW * Kalibracja.G_WYSOCZYZNA + c.wPg * Kalibracja.G_POGORZE + c.wBs * Kalibracja.G_BESKIDY;
		double gw = t.sBar() - g;
		double i = c.wG > 0.5 ? Kalibracja.I_FLISZ : c.piaszczyste() ? Kalibracja.I_PIASEK : Kalibracja.I_GLINA;
		// Cieki i wody stojące działają w dolinach i nieckach (wcięcie terenu), w dnie zawsze; poza nimi
		// człon dostaje karę, więc DGW nie skacze na granicy zasięgu zapytania sieci rzecznej.
		double kara = c.dno() ? 0
				: Kalibracja.KARA_POZA_DOLINA * (1 - Noise.smoothstep(Kalibracja.WCIECIE_OD, Kalibracja.WCIECIE_DO,
						t.rawSurface() - c.H));
		if (w.rzad() > 0 && Double.isFinite(w.odlKoryta()) && !Double.isNaN(w.poziomKoryta())) {
			// Lustro najbliższego koryta bywa wyżej niż dno, przy którym leży kolumna (dopływ schodzący bystrzem
			// do dna większej doliny); dno modelu leży co najmniej 1,2 m nad lustrem jego koryta.
			double lustro = Math.min(w.poziomKoryta(), c.H - 1.2);
			gw = Math.min(gw, lustro + Kalibracja.LUSTRO_PLUS + i * Math.max(0, w.odlKoryta()) + kara);
		}
		// Starorzecza pomijamy: leżą w dnie, gdzie zwierciadło wyznacza rzeka, a ich pierścienie (wycinki pierścienia
		// z modelu, także bez wody przy wąskich ciekach) dawały w DGW prostokątne plamy.
		if (Double.isFinite(w.s()) && w.poziomBrzegu() != ColumnSample.NO_WATER
				&& w.rodzajStojacej() != ColumnSample.RodzajStojacej.STARORZECZE) {
			gw = Math.min(gw, w.poziomBrzegu() + Kalibracja.LUSTRO_PLUS + i * Math.max(0, w.s()) + kara);
		}
		// Morze bez ucięcia zasięgu: człon rośnie z cD, więc dalej od brzegu i tak przegrywa z terenem.
		gw = Math.min(gw, Kalibracja.LUSTRO_PLUS + i * Math.max(0, t.coastD()));
		double d = Math.clamp(c.H - gw, 0.0, Kalibracja.DGW_MAX);
		if (sub == Substrate.LAKE_MUD) {
			d = Math.min(d, Kalibracja.MUL_DGW);
		}
		if (sub == Substrate.GLACIAL_TILL && !c.dno()) {
			double wkl = t.rawSurface() - t.sBar();
			if (wkl <= Kalibracja.ZAWIESZONA_2) {
				d = Math.min(d, Kalibracja.ZAWIESZONA_2_DGW);
			} else if (wkl <= Kalibracja.ZAWIESZONA_1) {
				d = Math.min(d, Kalibracja.ZAWIESZONA_1_DGW);
			}
		}
		return d;
	}

	/**
	 * Klasa wilgotności (§3.3): pas DGW 0,5–0,8 między bagienną a wilgotną rozstrzyga szum płatów o fali
	 * 80 m w obu skalach (przy fali 35 m·k ols strefowy wychodził w plamkach węższych niż 10 bloków).
	 */
	static Wilgotnosc klasa(Klasyfikator.Kolumna c) {
		double d = c.dgw();
		if (c.s.substrate() == Substrate.PEAT) {
			return BAGIENNA;
		}
		if (d <= Kalibracja.DGW_BAGIENNA) {
			return BAGIENNA;
		}
		if (d <= Kalibracja.DGW_WILGOTNA) {
			double granica = Kalibracja.DGW_BAGIENNA
					+ (Kalibracja.DGW_WILGOTNA - Kalibracja.DGW_BAGIENNA) * c.platQ(7, Kalibracja.FALA_PLATOW_OLSU / c.k);
			return d <= granica ? BAGIENNA : WILGOTNA;
		}
		if (d <= Kalibracja.DGW_SWIEZA) {
			return WILGOTNA;
		}
		if (d > Kalibracja.DGW_SUCHA && c.s.substrate() == Substrate.SAND
				&& (c.t.ma(Landform.WYDMY) || c.t.wydma() >= Kalibracja.WYDMA_SUCHA || c.wkl() > Kalibracja.WYP_SUCHA)) {
			return SUCHA;
		}
		return SWIEZA;
	}
}
