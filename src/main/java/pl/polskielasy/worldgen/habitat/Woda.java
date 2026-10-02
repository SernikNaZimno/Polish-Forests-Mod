package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Noise;
import pl.polskielasy.worldgen.landscape.WaterKind;

/**
 * Krok 1 klasyfikatora: kolumny z wodą (§2.3, §4.4). Morze, zalew, rzeka lub potok, jezioro albo jezioro
 * dystroficzne; w płytkiej wodzie eutroficznej szuwar (biom w pasie ≥ 12 m, węższy jako strefa SZUWAR),
 * w jeziorach strefy ELODEIDY i NYMFEIDY, w jeziorze dystroficznym pło.
 */
final class Woda {
	private Woda() {
	}

	/** Troficzność wody stojącej. */
	enum Troficznosc {
		EUTROFICZNA, OLIGOTROFICZNA, DYSTROFICZNA
	}

	static int klasyfikuj(Klasyfikator.Kolumna c) {
		ColumnSample s = c.s;
		if (!s.hasWater()) {
			return Klasyfikator.Wynik.BRAK;
		}
		double z = s.waterLevel() - s.surface();
		return switch (s.waterKind()) {
			case SEA -> {
				if (c.t.coastD() < 0) {
					yield Klasyfikator.Wynik.of(Biom.MORZE, Strefa.BRAK, Zespol.TYPOWY);
				}
				if (z > Kalibracja.ZALEW_Z) {
					yield Klasyfikator.Wynik.of(Biom.ZALEW, z <= Kalibracja.J_GLEBIA ? Strefa.ELODEIDY : Strefa.BRAK,
							Zespol.TYPOWY);
				}
				yield Klasyfikator.Wynik.of(Biom.SZUWAR, Strefa.SZUWAR, Zespol.TYPOWY);
			}
			case RIVER -> Klasyfikator.Wynik.of(StrefyNadwodne.klasa(c) == StrefyNadwodne.Klasa.C ? Biom.POTOK : Biom.RZEKA,
					Strefa.KORYTO, Zespol.TYPOWY);
			case LAKE, KETTLE, OXBOW -> jezioro(c, z);
			case NONE -> Klasyfikator.Wynik.BRAK;
		};
	}

	private static int jezioro(Klasyfikator.Kolumna c, double z) {
		boolean starorzecze = c.s.waterKind() == WaterKind.OXBOW;
		Troficznosc tr = troficznosc(c);
		Biom biom = tr == Troficznosc.DYSTROFICZNA ? Biom.JEZIORO_DYSTROFICZNE : Biom.JEZIORO;
		Zespol zes = tr == Troficznosc.OLIGOTROFICZNA ? Zespol.LOBELIOWY : Zespol.TYPOWY;
		// Odległość od brzegu w głąb wody (m); s jest ujemne w wodzie.
		double wGlab = Math.max(0, -c.w.s());
		if (!Double.isFinite(wGlab)) {
			wGlab = 0;
		}
		if (tr == Troficznosc.DYSTROFICZNA) {
			if (wGlab <= Math.max(1, Kalibracja.J_PLO_K * c.k * c.drganie())) {
				return Klasyfikator.Wynik.of(biom, Strefa.PLO, zes);
			}
			return Klasyfikator.Wynik.of(biom, z <= Kalibracja.J_GLEBIA ? Strefa.ELODEIDY : Strefa.BRAK, zes);
		}
		// Starorzecze: szuwar tylko w pasie przy brzegu (2–15 m), dalej nymfeidy na 60–90% lustra.
		boolean przyBrzegu = !starorzecze
				|| wGlab <= Math.max(Kalibracja.MIN_SZUWAR, Kalibracja.J_STARORZECZE_SZUWAR_K * c.k) * c.drganie();
		if (z <= Kalibracja.J_SZUWAR_Z && przyBrzegu) {
			if (tr == Troficznosc.EUTROFICZNA) {
				// Biom w zbiornikach z miejscem na pas ≥ 12 m (całą płyciznę, bez odwróconych pierścieni przy brzegu);
				// w starorzeczach pas ma 15 m·k, w małych oczkach tylko strefa.
				double promien = c.w.promienStojacej();
				double pas = starorzecze ? Kalibracja.J_STARORZECZE_SZUWAR_K * c.k
						: Double.isNaN(promien) || promien >= Kalibracja.J_SZUWAR_BIOM_PROMIEN_K * c.k ? Kalibracja.PAS_BIOMU : 0;
				return pas >= Kalibracja.PAS_BIOMU ? Klasyfikator.Wynik.of(Biom.SZUWAR, Strefa.SZUWAR, zes)
						: Klasyfikator.Wynik.of(biom, Strefa.SZUWAR, zes);
			}
			// Jezioro lobeliowe: szuwar tylko w płatach.
			if (c.platQ(1) > Kalibracja.J_LOBELIOWE_SZUWAR) {
				return Klasyfikator.Wynik.of(biom, Strefa.SZUWAR, zes);
			}
		}
		double glebia = starorzecze ? Kalibracja.J_GLEBIA_STARORZECZE : Kalibracja.J_GLEBIA;
		if (z > glebia) {
			return Klasyfikator.Wynik.of(biom, Strefa.BRAK, zes);
		}
		if (z >= Kalibracja.J_NYMFEIDY_OD && z <= Kalibracja.J_NYMFEIDY_DO && tr == Troficznosc.EUTROFICZNA) {
			double udzial = starorzecze ? Kalibracja.J_NYMFEIDY_STARORZECZE : Kalibracja.J_NYMFEIDY_JEZIORO;
			if (c.platQ(2) < udzial) {
				return Klasyfikator.Wynik.of(biom, Strefa.NYMFEIDY, zes);
			}
		}
		return Klasyfikator.Wynik.of(biom, z >= Kalibracja.J_ELODEIDY ? Strefa.ELODEIDY : Strefa.BRAK, zes);
	}

	/**
	 * Troficzność najbliższej wody stojącej: starorzecza i wody poza sandrem eutroficzne; jeziora rynnowe
	 * i oczka na sandrze według skrótu zbiornika: dystroficzne (&lt; 0,3), oligotroficzne (lobeliowe) albo
	 * eutroficzne.
	 */
	static Troficznosc troficznosc(Klasyfikator.Kolumna c) {
		ColumnSample.RodzajStojacej r = c.w.rodzajStojacej();
		if (c.wS <= 0.5 || r != ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE && r != ColumnSample.RodzajStojacej.OCZKO) {
			return Troficznosc.EUTROFICZNA;
		}
		double h = (Noise.mix(c.w.idJeziora() ^ 0x7A3E_11C5_2B9DL) >>> 11) * 0x1.0p-53;
		if (h < Kalibracja.J_DYSTROF) {
			return Troficznosc.DYSTROFICZNA;
		}
		return h < Kalibracja.J_OLIGO ? Troficznosc.OLIGOTROFICZNA : Troficznosc.EUTROFICZNA;
	}
}
