package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/**
 * Deterministyczny szum gradientowy 2D (ulepszony szum Perlina) i funkcje skrótu.
 * Klasa nie zależy od Minecrafta. Instancje są niezmienne i bezpieczne wątkowo.
 */
public final class Noise {
	private static final double[] GRAD_X = new double[16];
	private static final double[] GRAD_Z = new double[16];

	static {
		for (int i = 0; i < 16; i++) {
			double a = (i + 0.5) * (Math.PI * 2.0 / 16.0);
			GRAD_X[i] = Math.cos(a);
			GRAD_Z[i] = Math.sin(a);
		}
	}

	private final long seed;

	public Noise(long seed) {
		this.seed = mix(seed);
	}

	/** Pochodna instancja o niezależnym ziarnie, np. dla kolejnej warstwy. */
	public Noise derive(String salt) {
		return new Noise(seed ^ mix(salt.hashCode() * 0x9E3779B97F4A7C15L));
	}

	public long seed() {
		return seed;
	}

	/** SplitMix64: dobre rozproszenie bitów dla skrótów współrzędnych. */
	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * Klucz pamięci podręcznej dla pary liczb całkowitych. Mnożenie przez liczbę nieparzystą
	 * i dodawanie jest różnowartościowe dla rozsądnych zakresów, a {@link #mix} jest bijekcją,
	 * więc różne pary nie kolidują (w przeciwieństwie do łączenia przez XOR).
	 */
	public static long key(long a, long b, long salt) {
		return mix(mix(a * 0x9E3779B97F4A7C15L + b) + salt);
	}

	public long hash(long x, long z) {
		return mix(seed ^ mix(x * 0x632BE59BD9B4E019L + z * 0x85157AF5L));
	}

	public long hash(long x, long z, long salt) {
		return mix(hash(x, z) ^ mix(salt));
	}

	/** Liczba z przedziału [0, 1) wyznaczona skrótem komórki. */
	public double unit(long x, long z, long salt) {
		return (hash(x, z, salt) >>> 11) * 0x1.0p-53;
	}

	/** Szum w przybliżeniu z przedziału [-1, 1]; okres to 1 jednostka wejścia. */
	public double sample(double x, double z) {
		long x0 = (long) Math.floor(x);
		long z0 = (long) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double n00 = grad(x0, z0, fx, fz);
		double n10 = grad(x0 + 1, z0, fx - 1, fz);
		double n01 = grad(x0, z0 + 1, fx, fz - 1);
		double n11 = grad(x0 + 1, z0 + 1, fx - 1, fz - 1);
		double u = fade(fx);
		double v = fade(fz);
		double nx0 = n00 + u * (n10 - n00);
		double nx1 = n01 + u * (n11 - n01);
		return (nx0 + v * (nx1 - nx0)) * 1.4142135623730951;
	}

	/**
	 * Szum z pochodnymi analitycznymi. Zwraca wartość, a do {@code d[0]} i {@code d[1]} zapisuje
	 * pochodne cząstkowe względem x i z (w jednostkach wejścia).
	 */
	public double sampleD(double x, double z, double[] d) {
		long x0 = (long) Math.floor(x);
		long z0 = (long) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		int h00 = (int) (hash(x0, z0) >>> 60);
		int h10 = (int) (hash(x0 + 1, z0) >>> 60);
		int h01 = (int) (hash(x0, z0 + 1) >>> 60);
		int h11 = (int) (hash(x0 + 1, z0 + 1) >>> 60);
		double a = GRAD_X[h00] * fx + GRAD_Z[h00] * fz;
		double b = GRAD_X[h10] * (fx - 1) + GRAD_Z[h10] * fz;
		double c = GRAD_X[h01] * fx + GRAD_Z[h01] * (fz - 1);
		double e = GRAD_X[h11] * (fx - 1) + GRAD_Z[h11] * (fz - 1);
		double u = fade(fx);
		double v = fade(fz);
		double du = 30 * fx * fx * (fx - 1) * (fx - 1);
		double dv = 30 * fz * fz * (fz - 1) * (fz - 1);
		double k1 = b - a;
		double k2 = c - a;
		double k3 = a - b - c + e;
		double k1x = GRAD_X[h10] - GRAD_X[h00];
		double k2x = GRAD_X[h01] - GRAD_X[h00];
		double k3x = GRAD_X[h00] - GRAD_X[h10] - GRAD_X[h01] + GRAD_X[h11];
		double k1z = GRAD_Z[h10] - GRAD_Z[h00];
		double k2z = GRAD_Z[h01] - GRAD_Z[h00];
		double k3z = GRAD_Z[h00] - GRAD_Z[h10] - GRAD_Z[h01] + GRAD_Z[h11];
		double s = 1.4142135623730951;
		d[0] = s * (GRAD_X[h00] + du * k1 + u * k1x + v * k2x + du * v * k3 + u * v * k3x);
		d[1] = s * (GRAD_Z[h00] + u * k1z + dv * k2 + v * k2z + u * dv * k3 + u * v * k3z);
		return s * (a + u * k1 + v * k2 + u * v * k3);
	}

	/**
	 * fBm z tłumieniem przez nachylenie (tzw. szum erozyjny): wyższe oktawy słabną na stokach,
	 * co daje gładkie grzbiety i rozgałęzione doliny. Wynik w przybliżeniu [-1, 1].
	 */
	public double eroded(double x, double z, double wavelength, int octaves, double persistence) {
		return eroded(x, z, wavelength, octaves, persistence, 1.0);
	}

	/**
	 * Jak {@link #eroded(double, double, double, int, double)}, z parametrem {@code sharpness}
	 * wzmacniającym tłumienie przez nachylenie (większa wartość = wyraźniejsze grzbiety i doliny).
	 */
	public double eroded(double x, double z, double wavelength, int octaves, double persistence, double sharpness) {
		double[] d = new double[2];
		double sum = 0;
		double amp = 1;
		double norm = 0;
		double sx = 0;
		double sz = 0;
		double px = x / wavelength;
		double pz = z / wavelength;
		for (int i = 0; i < octaves; i++) {
			double n = sampleD(px, pz, d);
			sx += d[0];
			sz += d[1];
			sum += amp * n / (1 + sharpness * (sx * sx + sz * sz));
			norm += amp;
			amp *= persistence;
			// Obrót o ok. 37° między oktawami usuwa artefakty siatki.
			double nx = 1.6 * px - 1.2 * pz + 13.1;
			double nz = 1.2 * px + 1.6 * pz - 7.7;
			px = nx;
			pz = nz;
		}
		return sum / norm;
	}

	/** Szum o zadanej długości fali w metrach. */
	public double at(double x, double z, double wavelength) {
		return sample(x / wavelength, z / wavelength);
	}

	/** Suma oktaw (fBm), wynik w przybliżeniu [-1, 1]. */
	public double fbm(double x, double z, double wavelength, int octaves, double persistence) {
		double sum = 0;
		double amp = 1;
		double norm = 0;
		double f = 1.0 / wavelength;
		for (int i = 0; i < octaves; i++) {
			sum += amp * sample(x * f + i * 17.31, z * f - i * 9.73);
			norm += amp;
			amp *= persistence;
			f *= 2.03;
		}
		return sum / norm;
	}

	/** Szum grzbietowy w przedziale [0, 1]: 1 na grzbietach, 0 w dolinach. */
	public double ridged(double x, double z, double wavelength, int octaves, double persistence) {
		double sum = 0;
		double amp = 1;
		double norm = 0;
		double f = 1.0 / wavelength;
		double weight = 1;
		for (int i = 0; i < octaves; i++) {
			double n = 1.0 - Math.abs(sample(x * f + i * 31.7, z * f + i * 11.3));
			n *= n;
			n *= weight;
			weight = Math.clamp(n * 1.6, 0.0, 1.0);
			sum += amp * n;
			norm += amp;
			amp *= persistence;
			f *= 2.07;
		}
		return sum / norm;
	}

	private double grad(long ix, long iz, double dx, double dz) {
		int h = (int) (hash(ix, iz) >>> 60);
		return GRAD_X[h] * dx + GRAD_Z[h] * dz;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	public static double smoothstep(double edge0, double edge1, double x) {
		double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}

	public static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}
}
