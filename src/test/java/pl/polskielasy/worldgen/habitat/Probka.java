package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.LandscapeType;
import pl.polskielasy.worldgen.landscape.Substrate;
import pl.polskielasy.worldgen.landscape.WaterKind;

/**
 * Budowniczy syntetycznych próbek kolumny do testów klasyfikatora: domyślnie wnętrze wysoczyzny morenowej
 * na 120 m, bez wód, 100 km od morza, O 0,5, P 0,1. Pola jak w {@link ColumnSample}.
 */
final class Probka {
	double surface = 120;
	int waterLevel = ColumnSample.NO_WATER;
	WaterKind waterKind = WaterKind.NONE;
	LandscapeType type = LandscapeType.WYSOCZYZNA_MORENOWA;
	Substrate substrate = Substrate.GLACIAL_TILL;
	double rawSurface = Double.NaN;
	double coastD = 100_000;
	double wSandr;
	double wWysoczyzna = 1;
	double wRownina;
	double wPogorze;
	double wBeskidy;
	double wPobrzeze;
	int formy;
	double wyp;
	double wydma;
	double grzbiet = Double.NaN;
	double masyw;
	double szczyt;
	double klif;
	/** Niski brzeg morski (0 = wysoki brzeg z klifem albo poza pasem) i granica gołego piasku. */
	double niskiBrzeg;
	double golyPiasek = Double.NaN;
	double piask = 0.5;
	double sBar = Double.NaN;
	double nach = 1;
	double eksp = Double.NaN;
	int rzad;
	boolean zrodlo;
	double odlKoryta = Double.POSITIVE_INFINITY;
	double szerKoryta = Double.NaN;
	double poziomKoryta = Double.NaN;
	boolean wDnie;
	double u = Double.NaN;
	double polSzerDna = Double.NaN;
	double spadek = Double.NaN;
	boolean brzegWypukly;
	double s = Double.POSITIVE_INFINITY;
	int poziomBrzegu = ColumnSample.NO_WATER;
	ColumnSample.RodzajStojacej rodzaj = ColumnSample.RodzajStojacej.BRAK;
	boolean torfOmbro;
	long idJeziora;
	double promien = Double.NaN;
	double o = 0.5;
	double p = 0.1;

	static Probka wysoczyzna() {
		return new Probka();
	}

	static Probka sandr() {
		Probka q = new Probka();
		q.type = LandscapeType.SANDR;
		q.substrate = Substrate.SAND;
		q.wWysoczyzna = 0;
		q.wSandr = 1;
		q.surface = 140;
		return q;
	}

	static Probka beskidy(double h) {
		Probka q = new Probka();
		q.type = LandscapeType.BESKIDY;
		q.substrate = Substrate.FLYSCH;
		q.wWysoczyzna = 0;
		q.wBeskidy = 1;
		q.surface = h;
		q.p = 0.9;
		q.grzbiet = 0.5;
		return q;
	}

	static Probka wybrzeze(double cD, double h, Substrate sub) {
		Probka q = new Probka();
		q.type = LandscapeType.POBRZEZE;
		q.substrate = sub;
		q.wWysoczyzna = 0;
		q.wSandr = 1;
		q.wPobrzeze = 1;
		q.coastD = cD;
		q.surface = h;
		q.o = 0.7;
		return q;
	}

	Probka h(double v) {
		surface = v;
		return this;
	}

	Probka forma(Landform f) {
		formy |= f.bit();
		return this;
	}

	/** Woda w kolumnie: lustro i dno. */
	Probka woda(WaterKind k, int lustro, double dno) {
		waterKind = k;
		waterLevel = lustro;
		surface = dno;
		return this;
	}

	/** Ciek w pobliżu: rząd, szerokość koryta, odległość od brzegu, lustro (domyślnie dno − 1,7 m). */
	Probka ciek(int r, double szer, double d, double spadekPromil) {
		rzad = r;
		szerKoryta = szer;
		odlKoryta = d;
		spadek = spadekPromil;
		if (Double.isNaN(poziomKoryta)) {
			poziomKoryta = surface - 1.7;
		}
		return this;
	}

	Probka dno(double uDna, double polSzer) {
		wDnie = true;
		u = uDna;
		polSzerDna = polSzer;
		return this;
	}

	Probka stojaca(ColumnSample.RodzajStojacej r, double brzeg, int lustro, double prom, long id) {
		rodzaj = r;
		s = brzeg;
		poziomBrzegu = lustro;
		promien = prom;
		idJeziora = id;
		return this;
	}

	ColumnSample build() {
		double raw = Double.isNaN(rawSurface) ? surface : rawSurface;
		double sb = Double.isNaN(sBar) ? surface : sBar;
		ColumnSample.Teren t = new ColumnSample.Teren(raw, coastD, wSandr, wWysoczyzna, wRownina, wPogorze, wBeskidy,
				wPobrzeze, formy, wyp, wydma, grzbiet, masyw, szczyt, klif, niskiBrzeg, golyPiasek, piask, sb, nach, eksp);
		ColumnSample.Wody w = new ColumnSample.Wody(rzad, zrodlo, odlKoryta, szerKoryta, poziomKoryta, wDnie, u,
				polSzerDna, spadek, brzegWypukly, s, poziomBrzegu, rodzaj, torfOmbro, idJeziora, promien);
		return new ColumnSample(surface, waterLevel, waterKind, type, substrate, 5, t, w,
				new ColumnSample.Region(o, p));
	}
}
