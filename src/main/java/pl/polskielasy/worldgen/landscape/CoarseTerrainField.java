package pl.polskielasy.worldgen.landscape;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Zgrubna siatka terenu: wygładzony teren {@code sBar}, nachylenie i ekspozycja (docs/03-m2-biomy.md §3.2).
 *
 * <p>Węzły leżą co {@code spacing} metrów (32 m·k: 32 m w skali rzeczywistej, 16 m w skali rozgrywki), a wartość
 * w węźle to {@link LandscapeModel#landElevation}, czyli teren bez dolin rzek i jezior. To jedyne wywołanie
 * {@code landElevation} dodane w M2 do ścieżki {@link LandscapeModel#sample} (zasada Z6); kod M1 woła je dalej
 * w oczkach, poziomie jezior i węzłach sieci rzecznej (z własną pamięcią). W węźle liczymy średnią 3 × 3 węzłów
 * ({@code sBar}, okno ok. 96 m·k) i gradient z różnic centralnych. W kolumnie wszystkie trzy wielkości są
 * interpolowane dwuliniowo z czterech węzłów oczka, więc są ciągłe, a w węźle gradient jest dokładnie różnicą
 * centralną.
 *
 * <p>Nachylenie jest liczone w układzie modelu (metry w poziomie i w pionie tej skali). W skali rozgrywki
 * odległości poziome są ściśnięte (k = 0,5, pasma 0,3) przy podobnych wysokościach w metrach, więc w modelu
 * stoki są znacznie bardziej strome niż w skali rzeczywistej; w świecie wyrównuje to odwzorowanie pionowe
 * ({@code VerticalScale}). Progi w stopniach z planu (§2, §4, §5.1) klasyfikator (S4) porównuje z nachyleniem
 * w blokach: tan(nach) · d(bloki)/d(metry) przy wysokości {@code sBar} (docs/03-m2-biomy.md, stan po S3).
 *
 * <p>Kafel ma 8 × 8 oczek: pochodne w 9 × 9 węzłach (z węzłem wspólnym z sąsiednim kaflem) z 11 × 11 węzłów
 * surowych (margines 1). Kafle są niezmienne i siedzą w {@link DirectCache}; 4096 kafli to ok. 4 MB.
 * W skali rzeczywistej kafel obejmuje 256 chunków (ok. 0,5 węzła na chunk), w skali rozgrywki 64 chunki
 * (ok. 2 węzły na chunk).
 *
 * <p>Pojedyncze, rozrzucone zapytania (komendy, testy udziałów, szukanie biomu z dużym krokiem) nie liczą
 * całego kafla (121 wywołań {@code landElevation}, ok. 0,5 ms). Pierwsze zapytanie w kaflu, którego nie ma
 * w pamięci, liczy tylko swoje oczko z 4 × 4 węzłów surowych (16 wywołań) i zaznacza kafel; dopiero drugie
 * liczy i zapamiętuje cały kafel. Oba sposoby liczą węzeł tą samą funkcją ({@link #node}) i interpolują
 * tak samo, więc wynik jest identyczny co do bitu. Znaczniki są tylko wskazówką kosztu: wyścig wątków
 * albo nadpisany znacznik zmienia najwyżej to, który sposób zostanie użyty.
 */
final class CoarseTerrainField {
	/** Oczka na bok kafla. */
	static final int TILE = 8;
	/** Węzły z pochodnymi na bok kafla. */
	private static final int N = TILE + 1;
	/** Węzły surowe na bok kafla (margines 1). */
	private static final int R = TILE + 3;
	/** Liczba miejsc w pamięci kafli. */
	static final int CACHE_SLOTS = 4096;

	/**
	 * Wynik w kolumnie.
	 *
	 * @param sBar wygładzony teren bez dolin (m n.p.m.)
	 * @param nach nachylenie (°)
	 * @param eksp ekspozycja (°): kierunek spadku stoku zgodnie z ruchem wskazówek zegara od północy (−Z),
	 *             90 = wschód (+X), 180 = południe (+Z), 270 = zachód (−X); NaN na terenie płaskim
	 */
	record Zgrubny(double sBar, double nach, double eksp) {
	}

	private final LandscapeModel model;
	private final double spacing;
	private final double inv;
	private final DirectCache<float[]> tiles;
	/** Znaczniki kafli, o które już pytano (skrót kafla, 0 = brak); po jednym miejscu na miejsce pamięci. */
	private final AtomicLongArray seen;

	CoarseTerrainField(LandscapeModel model, double spacing) {
		this(model, spacing, CACHE_SLOTS);
	}

	/** Z inną liczbą miejsc w pamięci (testy wypychania). */
	CoarseTerrainField(LandscapeModel model, double spacing, int slots) {
		this.model = model;
		this.spacing = spacing;
		this.inv = 1.0 / spacing;
		this.tiles = new DirectCache<>(slots, this::build);
		this.seen = new AtomicLongArray(slots);
	}

	/** Odstęp węzłów (m). */
	double spacing() {
		return spacing;
	}

	/** Wygładzony teren, nachylenie i ekspozycja w punkcie. */
	Zgrubny sample(double x, double z) {
		double gx = x * inv;
		double gz = z * inv;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		long tx = Math.floorDiv(cx, TILE);
		long tz = Math.floorDiv(cz, TILE);
		float[] t = tiles.peek(tx, tz);
		if (t == null) {
			if (firstTouch(tx, tz)) {
				return interpolate(cell(cx, cz), 0, 3, 6, 9, fx, fz);
			}
			t = tiles.get(tx, tz);
		}
		int i00 = ((int) (cz - tz * TILE) * N + (int) (cx - tx * TILE)) * 3;
		return interpolate(t, i00, i00 + 3, i00 + 3 * N, i00 + 3 * N + 3, fx, fz);
	}

	/**
	 * Wynik z czterech węzłów oczka (po trzy liczby: sBar, gradient x, gradient z) o indeksach i00 (−x, −z),
	 * i10 (+x), i01 (+z), i11; (fx, fz) to położenie w oczku.
	 */
	private static Zgrubny interpolate(float[] t, int i00, int i10, int i01, int i11, double fx, double fz) {
		double w00 = (1 - fx) * (1 - fz);
		double w10 = fx * (1 - fz);
		double w01 = (1 - fx) * fz;
		double w11 = fx * fz;
		double s = w00 * t[i00] + w10 * t[i10] + w01 * t[i01] + w11 * t[i11];
		double dx = w00 * t[i00 + 1] + w10 * t[i10 + 1] + w01 * t[i01 + 1] + w11 * t[i11 + 1];
		double dz = w00 * t[i00 + 2] + w10 * t[i10 + 2] + w01 * t[i01 + 2] + w11 * t[i11 + 2];
		double m = Math.sqrt(dx * dx + dz * dz);
		double nach = Math.toDegrees(m <= 1 ? atan01(m) : Math.PI / 2 - atan01(1 / m));
		// Kierunek spadku to −gradient: składowa wschodnia −dx, północna (−Z) +dz.
		double eksp = m > 0 ? azimuth(-dx, dz) : Double.NaN;
		return new Zgrubny(s, nach, eksp);
	}

	/**
	 * Czy to pierwsze zapytanie o kafel (tx, tz) spoza pamięci; zaznacza kafel. Bez blokad: wynik wpływa
	 * tylko na koszt, nie na wartości.
	 */
	private boolean firstTouch(long tx, long tz) {
		long h = Noise.mix(tx * 0x632BE59BD9B4E019L + tz) | 1;
		int i = (int) h & (seen.length() - 1);
		if (seen.get(i) == h) {
			return false;
		}
		seen.set(i, h);
		return true;
	}

	/** Kafel (tx, tz): sBar i gradient w 9 × 9 węzłach, po trzy liczby na węzeł. */
	private float[] build(long tx, long tz) {
		long ix0 = tx * TILE - 1;
		long iz0 = tz * TILE - 1;
		double[] raw = new double[R * R];
		for (int j = 0; j < R; j++) {
			for (int i = 0; i < R; i++) {
				raw[j * R + i] = model.landElevation((ix0 + i) * spacing, (iz0 + j) * spacing);
			}
		}
		float[] out = new float[3 * N * N];
		for (int b = 0; b < N; b++) {
			for (int a = 0; a < N; a++) {
				node(raw, (b + 1) * R + a + 1, R, out, (b * N + a) * 3);
			}
		}
		return out;
	}

	/**
	 * Same cztery węzły oczka (cx, cz) z 4 × 4 węzłów surowych, w kolejności (cx, cz), (cx + 1, cz),
	 * (cx, cz + 1), (cx + 1, cz + 1); wartości jak w kaflu.
	 */
	private float[] cell(long cx, long cz) {
		double[] raw = new double[16];
		for (int j = 0; j < 4; j++) {
			for (int i = 0; i < 4; i++) {
				raw[j * 4 + i] = model.landElevation((cx - 1 + i) * spacing, (cz - 1 + j) * spacing);
			}
		}
		float[] out = new float[12];
		node(raw, 5, 4, out, 0);
		node(raw, 6, 4, out, 3);
		node(raw, 9, 4, out, 6);
		node(raw, 10, 4, out, 9);
		return out;
	}

	/**
	 * Węzeł o indeksie {@code c} w tablicy węzłów surowych o szerokości wiersza {@code row}: sBar (średnia 3 × 3)
	 * i gradient z różnic centralnych, do {@code out[k..k + 2]}. Jedna funkcja dla kafla i pojedynczego oczka,
	 * więc oba dają te same liczby.
	 */
	private void node(double[] raw, int c, int row, float[] out, int k) {
		double sum = 0;
		for (int dj = -row; dj <= row; dj += row) {
			sum += raw[c + dj - 1] + raw[c + dj] + raw[c + dj + 1];
		}
		double inv2 = 0.5 * inv;
		out[k] = (float) (sum / 9);
		out[k + 1] = (float) ((raw[c + 1] - raw[c - 1]) * inv2);
		out[k + 2] = (float) ((raw[c + row] - raw[c - row]) * inv2);
	}

	/**
	 * Azymut wektora (wschód, północ) w stopniach [0, 360), zgodnie z ruchem wskazówek zegara od północy.
	 * Szybka odmiana {@code atan2} (błąd ok. 10⁻⁶°), deterministyczna jak cała arytmetyka double w Javie.
	 */
	static double azimuth(double east, double north) {
		double ae = Math.abs(east);
		double an = Math.abs(north);
		// Kąt od osi północnej w [0, π/2].
		double a = ae <= an ? atan01(ae / an) : Math.PI / 2 - atan01(an / ae);
		if (north < 0) {
			a = Math.PI - a;
		}
		double deg = Math.toDegrees(a);
		if (east >= 0) {
			return deg;
		}
		// Przy deg poniżej ok. 3·10⁻¹⁴ różnica 360 − deg zaokrągla się do 360, czyli poza [0, 360).
		double r = 360 - deg;
		return r >= 360 ? 0 : r;
	}

	/** atan(t) dla t ∈ [0, 1] (Abramowitz i Stegun 4.4.49, błąd ≤ 2·10⁻⁸ rad). */
	static double atan01(double t) {
		double t2 = t * t;
		return t * (0.9999993329 + t2 * (-0.3332985605 + t2 * (0.1994653599 + t2 * (-0.1390853351
				+ t2 * (0.0964200441 + t2 * (-0.0559098861 + t2 * (0.0218612288 - 0.0040540580 * t2)))))));
	}
}
