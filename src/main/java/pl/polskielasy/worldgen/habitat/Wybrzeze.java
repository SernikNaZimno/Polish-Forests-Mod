package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.Substrate;

/**
 * Krok 2 klasyfikatora: pas wybrzeża cD &lt; B + D + 2000k (§5.2, raport ekologii §4). Plaża, wydmy,
 * bór bażynowy, klif i zaplecze zalewu; długości mnożone przez k. Dna dolin i brzegi wód stojących
 * zostawiamy strefom nadwodnym (poza plażą i wydmami).
 */
final class Wybrzeze {
	private Wybrzeze() {
	}

	static int klasyfikuj(Klasyfikator.Kolumna c) {
		ColumnSample.Teren t = c.t;
		double k = c.k;
		double cD = t.coastD();
		double b = Kalibracja.PLAZA_B * k;
		double d = Kalibracja.WYDMY_D * k;
		if (cD < 0 || cD >= b + d + Kalibracja.BOR_BAZYNOWY_K * k) {
			return Klasyfikator.Wynik.BRAK;
		}
		Substrate sub = c.s.substrate();
		// Klif: ściana z gołą gliną i korona z zaroślami w biomie wysoczyzny; dalej las wiatrowy.
		double raw = t.rawSurface();
		if (t.ma(Landform.KLIF)) {
			return Klasyfikator.Wynik.strefa(Strefa.KLIF_SCIANA);
		}
		// Wysoki brzeg z klifem leży na glinie (model daje glinę pobrzeża powyżej 8 m); wysokie wydmy i sandry
		// przy morzu idą dalej do boru bażynowego.
		if (sub == Substrate.GLACIAL_TILL && raw > Kalibracja.KLIF_H && c.H > Kalibracja.KLIF_H) {
			double krawedz = b + raw / 2.5 + Kalibracja.KLIF_KORONA_K * k;
			if (cD < krawedz + Kalibracja.KLIF_KORONA_K * k) {
				return Klasyfikator.Wynik.strefa(Strefa.KLIF_KORONA);
			}
			if (cD < krawedz + Kalibracja.KLIF_LAS_WIATROWY_K * k) {
				return Klasyfikator.Wynik.of(null, Strefa.BRAK, Zespol.WIATROWY);
			}
			return Klasyfikator.Wynik.BRAK;
		}
		if (cD < b) {
			Zespol z = raw > Kalibracja.PLAZA_KAMIENISTA_RAW ? Zespol.KAMIENISTY : Zespol.TYPOWY;
			Strefa s = cD >= Kalibracja.KIDZINA_B * b ? Strefa.KIDZINA : Strefa.BRAK;
			return Klasyfikator.Wynik.of(Biom.PLAZA, s, z);
		}
		if (sub == Substrate.BEACH_SAND) {
			Strefa s = cD < b + Kalibracja.WYDMA_INICJALNA_K * k ? Strefa.WYDMA_INICJALNA : Strefa.BRAK;
			return Klasyfikator.Wynik.of(Biom.WYDMA_BIALA, s, Zespol.TYPOWY);
		}
		// Dna dolin i brzegi wód stojących przy morzu: strefy nadwodne.
		if (c.dno() || c.w.s() < Kalibracja.J_OLS_K * k) {
			return Klasyfikator.Wynik.BRAK;
		}
		if (sub != Substrate.SAND) {
			return Klasyfikator.Wynik.BRAK;
		}
		// Zaplecze zalewu: niski brzeg za wydmami (lustro morza 0 m, h = H − 1).
		double h = c.H - 1;
		if (cD >= b + d) {
			if (h <= Kalibracja.ZALEW_TORF_H) {
				return Klasyfikator.Wynik.of(Biom.TORFOWISKO_NISKIE, Strefa.BRAK, Zespol.TYPOWY);
			}
			if (h <= Kalibracja.ZALEW_OLS_H) {
				return Klasyfikator.Wynik.of(Biom.OLS, Strefa.BRAK, Zespol.MIERZEJA);
			}
		}
		if (cD < b + d + Kalibracja.WYDMA_SZARA_K * k) {
			return Klasyfikator.Wynik.of(Biom.WYDMA_SZARA, Strefa.BRAK, Zespol.TYPOWY);
		}
		// Granica boru bażynowego (2000 m·k) drga o ±15% z szumem wariantów, żeby nie była linią równoległą do brzegu.
		double zasieg = Kalibracja.BOR_BAZYNOWY_K * k * (0.85 + 0.3 * c.wariant(4));
		if (c.H >= Kalibracja.BOR_BAZYNOWY_H || cD >= zasieg) {
			return Klasyfikator.Wynik.BRAK;
		}
		if (c.dgw() <= Kalibracja.DGW_BAGIENNA) {
			return Klasyfikator.Wynik.of(Biom.BOR_BAGIENNY, Strefa.BRAK, Zespol.MIERZEJA);
		}
		Zespol z = cD < b + d + Kalibracja.BOR_WIATROWY_K * k ? Zespol.KARLOWY : Zespol.TYPOWY;
		return Klasyfikator.Wynik.of(Biom.BOR_BAZYNOWY, Strefa.BRAK, z);
	}
}
