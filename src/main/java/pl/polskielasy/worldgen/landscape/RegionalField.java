package pl.polskielasy.worldgen.landscape;

/**
 * Pola regionalne: oceaniczność O i podgórskość P, wariant V3 z raportu ekologii (docs/03-m2-biomy.md §3.2, §9).
 *
 * <p>O = clamp(0,15 + 0,45·N + 0,25·Wz + 0,15·S; 0; 1), gdzie
 * <ul>
 * <li>N – prowincje klimatyczne: smoothstep kwantyla szumu o fali 900 km·zs (kwantyl ma rozkład jednostajny
 * na [0, 1], smoothstep zagęszcza wartości przy 0 i 1);</li>
 * <li>Wz – udział morza w 8 punktach na zachód (−X, strona zachodu słońca) co 60 km·zs, z wagami 1/k
 * (wiatry zachodnie); morze to {@link LandscapeModel#seaField} &lt; 0 z miękką granicą ±0,02;</li>
 * <li>S = 1 − smoothstep(0; 150 km·zs; {@link LandscapeModel#coastDistance}) – bliskość morza.</li>
 * </ul>
 * P to średnia z okna 5 × 5 węzłów (ok. 80 km·zs) wartości smoothstep(0,465; 0,915; liniowe pole pasm), gdzie
 * liniowe pole to {@link LandscapeModel#mountainLinear}: pole pasm górskich bez sześcianu z
 * {@link LandscapeModel#mountainField}, więc sięga dalej od osi pasma niż góry i pogórza. Progi dają granicę
 * P = 0,5 w medianie ok. 220 km·zs od osi pasma (linia zerowa {@link LandscapeModel#mountainRaw}; 10 ziaren
 * razem, mediany ziaren 180–280 km·zs; plan: 150–250 km·zs). Uśrednianie ogranicza spadek P do
 * 1/80 na km·zs: bez niego maski pasm (koniec łańcucha, odsunięcie od morza) dawały do 0,05 na km·zs.
 *
 * <p>Węzły leżą co 16 km·zs (w skali rozgrywki ok. 350 m), wartość w kolumnie to interpolacja dwuliniowa.
 * Kafel ma 8 × 8 oczek (9 × 9 węzłów) i jest niezmienny; węzeł kosztuje kilka µs, a kafel w skali rzeczywistej
 * obejmuje 128 km. Wynik nie zależy od kolejności zapytań ani od stanu pamięci.
 */
final class RegionalField {
	/** Oczka na bok kafla. */
	static final int TILE = 8;
	private static final int N = TILE + 1;
	/** Liczba miejsc w pamięci kafli. */
	static final int CACHE_SLOTS = 1024;
	/** Odstęp węzłów przy zs = 1 (m). */
	static final double SPACING = 16_000;
	/** Fala szumu prowincji klimatycznych przy zs = 1 (m). */
	private static final double PROWINCJE_FALA = 900_000;
	/** Krok punktów zachodnich przy zs = 1 (m) i ich liczba. */
	private static final double ZACHOD_KROK = 60_000;
	private static final int ZACHOD_PUNKTY = 8;
	/** Suma wag 1/k punktów zachodnich. */
	private static final double ZACHOD_SUMA_WAG;
	/** Zasięg członu bliskości morza przy zs = 1 (m). */
	private static final double MORZE_ZASIEG = 150_000;
	/** Progi P na liniowym polu pasm. */
	static final double P0 = 0.465;
	static final double P1 = 0.915;
	/** Promień uśredniania P w węzłach (okno 5 × 5 węzłów, ok. 80 km·zs). */
	private static final int P_PROMIEN = 2;

	static {
		double s = 0;
		for (int k = 1; k <= ZACHOD_PUNKTY; k++) {
			s += 1.0 / k;
		}
		ZACHOD_SUMA_WAG = s;
	}

	private final LandscapeModel model;
	private final Noise prowincje;
	private final double zs;
	private final double spacing;
	private final double inv;
	private final DirectCache<float[]> tiles;

	/**
	 * @param prowincje szum prowincji klimatycznych ({@code habitat.*}, więc teren się nie zmienia)
	 * @param zs        mnożnik skali stref (rozmiar regionu / 64 km)
	 */
	RegionalField(LandscapeModel model, Noise prowincje, double zs) {
		this(model, prowincje, zs, CACHE_SLOTS);
	}

	private RegionalField(LandscapeModel model, Noise prowincje, double zs, int slots) {
		this.model = model;
		this.prowincje = prowincje;
		this.zs = zs;
		this.spacing = SPACING * zs;
		this.inv = 1.0 / spacing;
		this.tiles = new DirectCache<>(slots, this::build);
	}

	/** Te same pola z inną liczbą miejsc w pamięci kafli (testy wypychania). */
	RegionalField withSlots(int slots) {
		return new RegionalField(model, prowincje, zs, slots);
	}

	/** Odstęp węzłów (m). */
	double spacing() {
		return spacing;
	}

	/** O i P w punkcie (interpolacja dwuliniowa z węzłów). */
	ColumnSample.Region sample(double x, double z) {
		double gx = x * inv;
		double gz = z * inv;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		long tx = Math.floorDiv(cx, TILE);
		long tz = Math.floorDiv(cz, TILE);
		float[] t = tiles.get(tx, tz);
		int i00 = ((int) (cz - tz * TILE) * N + (int) (cx - tx * TILE)) * 2;
		int i10 = i00 + 2;
		int i01 = i00 + 2 * N;
		int i11 = i01 + 2;
		double w00 = (1 - fx) * (1 - fz);
		double w10 = fx * (1 - fz);
		double w01 = (1 - fx) * fz;
		double w11 = fx * fz;
		double o = w00 * t[i00] + w10 * t[i10] + w01 * t[i01] + w11 * t[i11];
		double p = w00 * t[i00 + 1] + w10 * t[i10 + 1] + w01 * t[i01 + 1] + w11 * t[i11 + 1];
		// Wagi sumują się do 1 z dokładnością do zaokrągleń; clamp trzyma zakres [0, 1] dokładnie.
		return new ColumnSample.Region(Math.clamp(o, 0.0, 1.0), Math.clamp(p, 0.0, 1.0));
	}

	/** Oceaniczność w węźle (bez interpolacji). */
	double oceanicznosc(double x, double z) {
		// Kwantyl daje rozkład jednostajny; smoothstep rozsuwa prowincje ku biegunom (oceaniczny, kontynentalny),
		// co zwęża pas bez buka i świerka do ok. 14% lądu (przy samym kwantylu ok. 18–21%).
		double n = Noise.smoothstep(0, 1, LandscapeModel.noiseQuantile(prowincje.at(x, z, PROWINCJE_FALA * zs)));
		double wz = 0;
		for (int k = 1; k <= ZACHOD_PUNKTY; k++) {
			double sea = 1 - Noise.smoothstep(-0.02, 0.02, model.seaField(x - k * ZACHOD_KROK * zs, z));
			wz += sea / k;
		}
		wz /= ZACHOD_SUMA_WAG;
		double s = 1 - Noise.smoothstep(0, MORZE_ZASIEG * zs, model.coastDistance(x, z));
		return Math.clamp(0.15 + 0.45 * n + 0.25 * wz + 0.15 * s, 0.0, 1.0);
	}

	/** Podgórskość w punkcie przed uśrednieniem w oknie węzłów (bez interpolacji). */
	double podgorskosc(double x, double z) {
		return Noise.smoothstep(P0, P1, model.mountainLinear(x, z));
	}

	/** Kafel (tx, tz): O i P w 9 × 9 węzłach; P uśrednione w oknie 5 × 5 węzłów. */
	private float[] build(long tx, long tz) {
		long ix0 = tx * TILE;
		long iz0 = tz * TILE;
		int r = P_PROMIEN;
		int w = N + 2 * r;
		double[] p = new double[w * w];
		for (int j = 0; j < w; j++) {
			for (int i = 0; i < w; i++) {
				p[j * w + i] = podgorskosc((ix0 + i - r) * spacing, (iz0 + j - r) * spacing);
			}
		}
		double norm = 1.0 / ((2 * r + 1) * (2 * r + 1));
		float[] out = new float[2 * N * N];
		for (int b = 0; b < N; b++) {
			for (int a = 0; a < N; a++) {
				double sum = 0;
				for (int j = b; j <= b + 2 * r; j++) {
					for (int i = a; i <= a + 2 * r; i++) {
						sum += p[j * w + i];
					}
				}
				int k = (b * N + a) * 2;
				out[k] = (float) oceanicznosc((ix0 + a) * spacing, (iz0 + b) * spacing);
				out[k + 1] = (float) Math.clamp(sum * norm, 0.0, 1.0);
			}
		}
		return out;
	}
}
