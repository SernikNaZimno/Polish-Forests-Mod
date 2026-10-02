package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.Substrate;

/**
 * Krok 3 klasyfikatora: strefy nadwodne (§4, raport ekologii §2). Najpierw pierścienie wód stojących
 * (jeziora, oczka, starorzecza, oczka torfowe), potem cieki według klasy A, B albo C. Strefy wyznaczamy
 * z d, u, W, rzędu i spadku (η nie używamy, bo dno doliny jest płaskie). Granice drgają o ±20% szerokości
 * i ±0,05 u. Minima w blokach (E11): szuwar 2, wiklina 3, OlJ 6, ols 10. Łęgi tylko w dnie doliny,
 * w źródliskach i w wysiękach u podnóża zbocza.
 */
final class StrefyNadwodne {
	private StrefyNadwodne() {
	}

	/** Klasa cieku (§4): A duża rzeka nizinna, B mała rzeka nizinna, C potok górski. */
	enum Klasa {
		A, B, C
	}

	/** Klasa najbliższego cieku: C w górach lub przy spadku > 3‰, A przy rzędzie 3 lub W ≥ 30 m (1:1). */
	static Klasa klasa(Klasyfikator.Kolumna c) {
		ColumnSample.Wody w = c.w;
		if (c.wG > 0.5 || w.spadek() > Kalibracja.KLASA_SPADEK) {
			return Klasa.C;
		}
		// Rząd i spadek pochodzą z doliny dominującej, a W z najbliższego koryta: mały dopływ w dnie dużej
		// doliny ma rząd 3, więc rząd 3 daje klasę A dopiero przy szerszym korycie.
		double wr = c.wr();
		if (c.wN >= Kalibracja.KLASA_WN
				&& (wr >= Kalibracja.KLASA_A_WR || w.rzad() == 3 && wr >= Kalibracja.KLASA_A_WR_RZAD3)) {
			return Klasa.A;
		}
		return Klasa.B;
	}

	static int klasyfikuj(Klasyfikator.Kolumna c) {
		ColumnSample.Wody w = c.w;
		int r = Klasyfikator.Wynik.BRAK;
		boolean waskieStarorzecze = w.rodzajStojacej() == ColumnSample.RodzajStojacej.STARORZECZE
				&& !(w.promienStojacej() >= Kalibracja.J_STARORZECZE_MIN && w.odlKoryta() > w.promienStojacej());
		if (w.rodzajStojacej() != ColumnSample.RodzajStojacej.BRAK && Double.isFinite(w.s()) && !waskieStarorzecze) {
			r = jeziora(c);
			if (Klasyfikator.Wynik.biom(r) != null) {
				return r;
			}
		}
		if (w.rzad() > 0 && Double.isFinite(w.odlKoryta())) {
			r = Klasyfikator.Wynik.dalej(r, ciek(c));
		}
		return r;
	}

	// ------------------------------------------------------------------ cieki

	private static int ciek(Klasyfikator.Kolumna c) {
		double d = Math.max(0, c.w.odlKoryta());
		double szer = c.w.szerKoryta();
		if (Double.isNaN(szer)) {
			return Klasyfikator.Wynik.BRAK;
		}
		return switch (klasa(c)) {
			case A -> klasaA(c, d, szer);
			case B -> klasaB(c, d, szer);
			case C -> klasaC(c, d, szer);
		};
	}

	/** Szerokość pasa z drganiem {@code f}, nie mniejsza niż minimum w blokach (E11). */
	private static double szerokosc(double minimum, double baza, double f) {
		return Math.max(minimum, baza * f);
	}

	/** Biom pasa przy brzegu albo null, gdy pas jest węższy niż biom (wtedy tylko strefa). */
	private static Biom pas(double szerokosc, Biom biom) {
		return szerokosc >= Kalibracja.PAS_BIOMU ? biom : null;
	}

	/** Klasa A (§4.1): łacha, wiklina, okrajek, łęg wierzbowy, topolowy, zastoiska, łęg wiązowo-jesionowy. */
	private static int klasaA(Klasyfikator.Kolumna c, double d, double szer) {
		ColumnSample.Wody w = c.w;
		double k = c.k;
		double f = c.drganie();
		boolean wypukly = w.brzegWypukly();
		double pasWik = Math.max(Kalibracja.A_WIKLINA_K * k, Kalibracja.A_WIKLINA_W * szer);
		if (wypukly) {
			pasWik = Math.max(pasWik, Math.max(Kalibracja.A_WIKLINA_WYPUKLY_K * k, Kalibracja.A_WIKLINA_WYPUKLY_W * szer));
		}
		double wik = szerokosc(Kalibracja.MIN_WIKLINA, pasWik, f);
		Strefa strefa = Strefa.BRAK;
		if (wypukly && d < Kalibracja.A_LACHA_W * szer * f && c.plat(3) > Kalibracja.A_LACHA_PLAT) {
			Biom b = pas(Kalibracja.A_LACHA_W * szer, Biom.WIKLINY);
			if (b != null) {
				return Klasyfikator.Wynik.of(b, Strefa.LACHA, Zespol.TYPOWY);
			}
			strefa = Strefa.LACHA;
		} else if (d <= wik) {
			Biom b = pas(pasWik, Biom.WIKLINY);
			if (b != null) {
				return Klasyfikator.Wynik.of(b, Strefa.WIKLINA, Zespol.TYPOWY);
			}
			strefa = Strefa.WIKLINA;
		} else {
			double okr = szerokosc(Kalibracja.MIN_OKRAJEK, Math.max(Kalibracja.A_OKRAJEK_K * k, Kalibracja.A_OKRAJEK_W * szer), f);
			if (d <= wik + okr) {
				strefa = Strefa.OKRAJEK;
			}
		}
		if (!c.dno()) {
			return zbocze(c, strefa);
		}
		if (strefa == Strefa.BRAK && c.plat(4) < Kalibracja.A_LUKI) {
			// Luki w łęgu: ziołorośla okrajka.
			strefa = Strefa.OKRAJEK;
		}
		double dwb = Math.clamp(Kalibracja.A_DWB_W * szer, Kalibracja.A_DWB_MIN * k, Kalibracja.A_DWB_MAX * k) * f;
		double dtop = Math.clamp(Kalibracja.A_DTOP_W * szer, Kalibracja.A_DTOP_MIN * k, Kalibracja.A_DTOP_MAX * k) * f;
		boolean miekki = c.H < Kalibracja.H_LEG_WIERZBOWY;
		if (d <= dwb && miekki) {
			return Klasyfikator.Wynik.of(Biom.LEG_WIERZBOWO_TOPOLOWY, strefa, Zespol.SALICETUM_ALBAE);
		}
		// Łęg topolowy według d (Odstępstwo S4: bez warunku u < 0,35, bo u pochodzi z doliny dominującej
		// i skacze prostą linią przy jej zmianie, i bez preferencji garbów, która przeplatała go z łęgiem
		// wiązowym w pasie D_top–1,15 D_top; wały brzegowe przyjdą z rzeźbą dna w M5).
		if (d <= dtop && miekki) {
			return Klasyfikator.Wynik.of(Biom.LEG_WIERZBOWO_TOPOLOWY, strefa, Zespol.POPULETUM_ALBAE);
		}
		return resztaDna(c, d, szer, strefa);
	}

	/** Reszta dna dużej doliny: zastoiska (ols, torfowisko niskie) albo łęg wiązowo-jesionowy. */
	private static int resztaDna(Klasyfikator.Kolumna c, double d, double szer, Strefa strefa) {
		ColumnSample.Wody w = c.w;
		double k = c.k;
		double odZastoisk = Math.max(Kalibracja.A_ZASTOISKO_D_K * k, Kalibracja.A_ZASTOISKO_D_W * szer);
		if (c.uDrgane() > Kalibracja.A_ZASTOISKO_U && c.hKoryta() < Kalibracja.A_ZASTOISKO_H && d > odZastoisk
				&& (1 - Kalibracja.A_ZASTOISKO_U) * w.polSzerDna() >= 2 * Kalibracja.MIN_OLS
				&& c.platQ(10, Kalibracja.FALA_ZASTOISK / k) < Kalibracja.ZASTOISKA_UDZIAL) {
			boolean torf = w.polSzerDna() > Kalibracja.A_TORF_POLSZER_K * k && w.spadek() < Kalibracja.A_TORF_SPADEK
					&& c.platQ(5, Kalibracja.FALA_ZASTOISK / k) < Kalibracja.A_TORF_UDZIAL;
			Strefa s = strefa == Strefa.OKRAJEK ? Strefa.BRAK : strefa;
			return Klasyfikator.Wynik.of(torf ? Biom.TORFOWISKO_NISKIE : Biom.OLS, s, Zespol.ZASTOISKO);
		}
		return Klasyfikator.Wynik.of(Biom.LEG_WIAZOWO_JESIONOWY, strefa, Zespol.TYPOWY);
	}

	/** Klasa B (§4.2): ziołorośla brzegu, wierzby, łęg jesionowo-olszowy, ols w szerokich dnach, źródliska. */
	private static int klasaB(Klasyfikator.Kolumna c, double d, double szer) {
		ColumnSample.Wody w = c.w;
		double k = c.k;
		double f = c.drganie();
		double ziol = szerokosc(Kalibracja.MIN_ZIOLOROSLA, Math.max(Kalibracja.B_ZIOL_K * k, Kalibracja.B_ZIOL_W * szer), f);
		Strefa strefa = Strefa.BRAK;
		if (d <= ziol) {
			strefa = Strefa.ZIOLOROSLA;
		} else if (c.wr() >= Kalibracja.B_WIERZBY_WR && c.piaszczyste() && d <= Kalibracja.B_WIERZBY_K * k * f) {
			strefa = Strefa.WIERZBY;
		} else if (c.kl.tryb == Klasyfikator.Tryb.D && d >= Kalibracja.B_SZPALER_OD_K * k
				&& d <= Kalibracja.B_SZPALER_DO_K * k * f) {
			strefa = Strefa.SZPALER;
		}
		if (c.zrodlisko() && c.H < Kalibracja.H_LEG_OLJ) {
			return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa == Strefa.BRAK ? Strefa.ZRODLISKO : strefa,
					Zespol.ZRODLISKOWY);
		}
		if (!c.dno()) {
			// Wąskie dno (albo jego brak): łęg przy brzegu w pasie co najmniej 6 bloków (E11), nisko nad ciekiem.
			if (d <= Kalibracja.MIN_OLJ && c.H < Kalibracja.H_LEG_OLJ && !Double.isNaN(c.w.poziomKoryta())
					&& c.H - c.w.poziomKoryta() <= Kalibracja.WYSIEK_HL) {
				return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa, Zespol.TYPOWY);
			}
			return zbocze(c, strefa);
		}
		double olj = Math.min(Kalibracja.B_OLJ_MAX_K * k, Math.max(Kalibracja.B_OLJ_MIN_K * k, Kalibracja.B_OLJ_W * szer));
		if (c.wS > 0.5) {
			olj = Math.clamp(olj, Kalibracja.B_OLJ_SANDR_MIN_K * k, Kalibracja.B_OLJ_SANDR_MAX_K * k);
		}
		olj = szerokosc(Kalibracja.MIN_OLJ, olj, f);
		if (d <= olj && c.H < Kalibracja.H_LEG_OLJ) {
			return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa, Zespol.TYPOWY);
		}
		double odOls = Math.max(Kalibracja.B_OLS_D_K * k, Kalibracja.B_OLS_D_W * szer) * f;
		// Ols tylko w dnie z miejscem na płaty szersze niż 10 bloków za łęgiem (E11): pas co najmniej 2 × 10.
		if (w.polSzerDna() > Kalibracja.B_OLS_POLSZER_K * k && w.polSzerDna() - odOls >= 2 * Kalibracja.MIN_OLS && d > odOls
				&& c.H < Kalibracja.H_OLS
				&& (c.podloze == Substrate.PEAT || (c.hKoryta() < Kalibracja.B_OLS_H
						|| c.uDrgane() > Kalibracja.B_OLS_U) && c.platQ(6, Kalibracja.FALA_PLATOW_OLSU / k) < Kalibracja.B_OLS_UDZIAL)) {
			Strefa s = strefa == Strefa.BRAK && d < odOls + Kalibracja.B_LOZOWISKO_K * k ? Strefa.LOZOWISKO : strefa;
			return Klasyfikator.Wynik.of(Biom.OLS, s, Zespol.TYPOWY);
		}
		// Mały ciek w szerokim dnie dużej doliny: dalej reszta jej dna.
		if (w.polSzerDna() > Kalibracja.SZEROKIE_DNO_K * k && c.wN >= Kalibracja.KLASA_WN
				&& w.rzad() >= Kalibracja.SZEROKIE_DNO_RZAD) {
			return resztaDna(c, d, szer, strefa);
		}
		// Skraj dna: siedlisko strefowe (glina: grąd niski, LMw; piasek: bór wilgotny, dalej świeży).
		return Klasyfikator.Wynik.strefa(strefa);
	}

	/** Klasa C (§4.3): kamieniec, wiklina górska, olszyna górska, ziołorośla nadpotokowe, młaki. */
	private static int klasaC(Klasyfikator.Kolumna c, double d, double szer) {
		ColumnSample.Wody w = c.w;
		double k = c.k;
		double f = c.drganie();
		double h = c.H;
		double wr = c.wr();
		Strefa strefa = Strefa.BRAK;
		if (w.brzegWypukly() && wr >= Kalibracja.C_KAMIENIEC_WR && d <= szer * f && h >= Kalibracja.C_KAMIENIEC_H_OD
				&& h <= Kalibracja.C_KAMIENIEC_H_DO) {
			Biom b = pas(szer, Biom.WIKLINY);
			if (b != null) {
				return Klasyfikator.Wynik.of(b, Strefa.KAMIENIEC, Zespol.TYPOWY);
			}
			strefa = Strefa.KAMIENIEC;
		} else if (wr >= Kalibracja.C_WIKLINA_WR && d <= szerokosc(Kalibracja.MIN_WIKLINA, szer, f)
				&& h <= Kalibracja.C_WIKLINA_H) {
			Biom b = pas(Math.max(Kalibracja.MIN_WIKLINA, szer), Biom.WIKLINY);
			if (b != null) {
				return Klasyfikator.Wynik.of(b, Strefa.WIKLINA, Zespol.TYPOWY);
			}
			strefa = Strefa.WIKLINA;
		}
		boolean olsza = Zasiegi.olszaSzara(c.O, c.P);
		boolean zrodlo = c.zrodlisko();
		if ((zrodlo || c.zboczeDoliny() && c.dgw() <= Kalibracja.C_MLAKA_DGW) && h >= Kalibracja.C_MLAKA_H_OD
				&& h <= Kalibracja.C_MLAKA_H_DO && olsza) {
			return Klasyfikator.Wynik.of(Biom.OLSZYNA_GORSKA, strefa == Strefa.BRAK && zrodlo ? Strefa.ZRODLISKO : strefa,
					Zespol.MLAKA);
		}
		if (zrodlo && h < Kalibracja.H_LEG_OLJ) {
			return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa == Strefa.BRAK ? Strefa.ZRODLISKO : strefa,
					Zespol.ZRODLISKOWY);
		}
		if (!c.dno()) {
			return zbocze(c, strefa);
		}
		double polSzer = w.polSzerDna();
		if (h > Kalibracja.C_OLSZYNA_H || polSzer < Kalibracja.C_WASKIE_DNO_K * k) {
			// Wysoko i w wąskich dolinach V: las strefowy do brzegu, przy wodzie ziołorośla.
			if (strefa == Strefa.BRAK && d <= szerokosc(Kalibracja.C_ZIOL_MIN, Kalibracja.C_ZIOL_W * szer, f)) {
				strefa = Strefa.ZIOLOROSLA_GORSKIE;
			}
			return Klasyfikator.Wynik.strefa(strefa);
		}
		double olsz = Math.min(Kalibracja.C_OLSZYNA_MAX_K * k, Math.max(Kalibracja.C_OLSZYNA_MIN_K * k,
				Kalibracja.C_OLSZYNA_W * szer)) * f;
		double hMax = c.ekspozycjaN() ? Kalibracja.C_OLSZYNA_H_N : Kalibracja.C_OLSZYNA_H;
		if ((d <= olsz || polSzer < Kalibracja.C_CALE_DNO_K * k) && h <= hMax) {
			if (w.rzad() == 1 && h < Kalibracja.C_CARICI_H && c.P >= Kalibracja.C_CARICI_P
					&& polSzer < Kalibracja.C_CALE_DNO_K * k && c.dgw() <= Kalibracja.WYSIEK_DGW) {
				return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa, Zespol.CARICI_REMOTAE_FRAXINETUM);
			}
			return Klasyfikator.Wynik.of(olsza ? Biom.OLSZYNA_GORSKA : Biom.LEG_JESIONOWO_OLSZOWY, strefa, Zespol.TYPOWY);
		}
		return Klasyfikator.Wynik.strefa(strefa);
	}

	/** Poza dnem: wysięki u podnóża zbocza (DGW ≤ 0,5) to łęg jesionowo-olszowy, dalej strefowe. */
	private static int zbocze(Klasyfikator.Kolumna c, Strefa strefa) {
		if (c.wysiek()) {
			return Klasyfikator.Wynik.of(Biom.LEG_JESIONOWO_OLSZOWY, strefa, Zespol.TYPOWY);
		}
		return Klasyfikator.Wynik.strefa(strefa);
	}

	// ------------------------------------------------------------------ wody stojące (§4.4)

	private static int jeziora(Klasyfikator.Kolumna c) {
		ColumnSample.Wody w = c.w;
		double k = c.k;
		double f = c.drganie();
		double s = w.s();
		double h = c.H - w.poziomBrzegu() - 1;
		Substrate sub = c.podloze;
		ColumnSample.RodzajStojacej rodzaj = w.rodzajStojacej();
		if (rodzaj == ColumnSample.RodzajStojacej.OCZKO_TORFOWE) {
			double promien = w.promienStojacej();
			boolean duze = promien > Kalibracja.OCZKO_TORF_PROMIEN_K * k;
			if (sub == Substrate.PEAT || s < 0) {
				if (w.torfOmbro()) {
					return Klasyfikator.Wynik.of(duze ? Biom.TORFOWISKO_WYSOKIE : Biom.BOR_BAGIENNY, Strefa.BRAK,
							Zespol.TYPOWY);
				}
				// Torf minerotroficzny: duże oczko na glinie to torfowisko niskie, inne ols.
				boolean glina = c.wW + c.wR > 0.5;
				return Klasyfikator.Wynik.of(duze && glina ? Biom.TORFOWISKO_NISKIE : Biom.OLS, Strefa.BRAK, Zespol.TYPOWY);
			}
			if (w.torfOmbro() && s <= Kalibracja.OCZKO_PIERSCIEN) {
				return Klasyfikator.Wynik.of(Biom.BOR_BAGIENNY, Strefa.BRAK, Zespol.TYPOWY);
			}
			if (!w.torfOmbro() && s <= Kalibracja.J_OLS_OCZKO_K * k * f && h <= Kalibracja.J_OLS_H) {
				return Klasyfikator.Wynik.of(Biom.OLS, Strefa.BRAK, Zespol.TYPOWY);
			}
			return Klasyfikator.Wynik.BRAK;
		}
		s = Math.max(0, s);
		if (rodzaj == ColumnSample.RodzajStojacej.STARORZECZE) {
			double szuwar = Math.max(Kalibracja.MIN_SZUWAR, Kalibracja.J_STARORZECZE_SZUWAR_K * k);
			if (s <= szerokosc(Kalibracja.MIN_SZUWAR, Kalibracja.J_STARORZECZE_SZUWAR_K * k, f)) {
				Biom b = pas(szuwar, Biom.SZUWAR);
				return b != null ? Klasyfikator.Wynik.of(b, Strefa.SZUWAR_LADOWY, Zespol.STARORZECZE)
						: Klasyfikator.Wynik.strefa(Strefa.SZUWAR_LADOWY);
			}
			if (s <= Kalibracja.J_STARORZECZE_LOZ_K * k * f) {
				return Klasyfikator.Wynik.of(Biom.OLS, Strefa.LOZOWISKO, Zespol.STARORZECZE);
			}
			if (c.dno() && c.uDrgane() > Kalibracja.J_STARORZECZE_OLS_U) {
				return Klasyfikator.Wynik.of(Biom.OLS, Strefa.BRAK, Zespol.STARORZECZE);
			}
			return Klasyfikator.Wynik.BRAK;
		}
		Woda.Troficznosc tr = Woda.troficznosc(c);
		if (tr == Woda.Troficznosc.DYSTROFICZNA) {
			if (s <= Kalibracja.J_DYSTROF_TORF_K * k * f) {
				return Klasyfikator.Wynik.of(Biom.TORFOWISKO_WYSOKIE, Strefa.BRAK, Zespol.TYPOWY);
			}
			if (s <= Kalibracja.J_DYSTROF_BB) {
				return Klasyfikator.Wynik.of(Biom.BOR_BAGIENNY, Strefa.BRAK, Zespol.TYPOWY);
			}
			return Klasyfikator.Wynik.BRAK;
		}
		if (tr == Woda.Troficznosc.OLIGOTROFICZNA) {
			// Jezioro lobeliowe: bór dochodzi do wody z wąskim pasem olszy lub brzozy.
			if (s <= Math.max(1, Kalibracja.J_OLSZA_BRZEG_K * k) * f) {
				return Klasyfikator.Wynik.strefa(Strefa.OLSZA_BRZEG);
			}
			return Klasyfikator.Wynik.BRAK;
		}
		boolean podloze = sub == Substrate.GLACIAL_TILL || sub == Substrate.PEAT || sub == Substrate.LAKE_MUD;
		Strefa strefa = Strefa.BRAK;
		if (s <= szerokosc(Kalibracja.MIN_SZUWAR, Kalibracja.J_SZUWAR_LAD_K * k, f) && h <= Kalibracja.J_SZUWAR_LAD_H) {
			strefa = Strefa.SZUWAR_LADOWY;
		}
		if (s <= Kalibracja.J_LOZOWISKO_K * k * f && h <= Kalibracja.J_LOZOWISKO_H && podloze) {
			return Klasyfikator.Wynik.of(Biom.OLS, strefa == Strefa.BRAK ? Strefa.LOZOWISKO : strefa, Zespol.TYPOWY);
		}
		double olsMax = (rodzaj == ColumnSample.RodzajStojacej.OCZKO ? Kalibracja.J_OLS_OCZKO_K : Kalibracja.J_OLS_K) * k;
		if (s <= szerokosc(Kalibracja.MIN_OLS, olsMax, f) && h <= Kalibracja.J_OLS_H && c.nach < Kalibracja.J_OLS_NACH
				&& podloze) {
			return Klasyfikator.Wynik.of(Biom.OLS, strefa, Zespol.TYPOWY);
		}
		return Klasyfikator.Wynik.strefa(strefa);
	}
}
