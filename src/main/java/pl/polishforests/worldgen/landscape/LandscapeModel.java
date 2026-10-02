package pl.polishforests.worldgen.landscape;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;

/**
 * Proceduralny model krajobrazu Polski w skali 1:1 (1 jednostka = 1 metr).
 *
 * <p>Warstwy (docs/01-architektura.md, sekcja 3.2):
 * <ul>
 * <li>L0 – pola stref: pasma górskie, zasięg zlodowacenia, poziom bazowy nizin;</li>
 * <li>L1 – makroregiony: komórki Voronoi o zawirowanych granicach, z typem krajobrazu,
 * regułami sąsiedztwa i własnym układem współrzędnych wzdłuż pasma górskiego;</li>
 * <li>L2 – rzeźba typu krajobrazu, mieszana gładkimi wagami softmax na granicach komórek;</li>
 * <li>L3 – wody: rynny z łańcuchami jezior, oczka, doliny wielkich rzek, każde z własnym lustrem.</li>
 * </ul>
 *
 * <p>Każdy punkt jest liczony lokalnie i deterministycznie z ziarna, więc świat jest nieskończony,
 * a generacja może działać na wielu wątkach. Klasa nie zależy od Minecrafta.
 */
public final class LandscapeModel {
	/** Średni rozmiar makroregionu przy suwaku 1,0; w Polsce 59 makroregionów na 312 700 km². */
	public static final double BASE_REGION_SIZE = 64_000.0;
	/** Szerokość pasa przejścia między makroregionami (ok. 80% zmiany wagi). */
	private static final double BLEND_WIDTH = 5_000.0;
	private static final double KETTLE_BANK = 45.0;
	/** Średnia profilu żlebów {@code p[2]} z {@link #flyschParts} (zmierzona na szumie); od niej liczymy wypukłość fliszu. */
	private static final double GULLY_MEAN = 0.52;
	/** Liczba przedziałów tablicy dystrybuanty szumu (kwantyle piaszczystości). */
	private static final int CDF_BINS = 512;
	/** Dystrybuanta wartości {@link Noise#sample} na [-1, 1], liczona raz ze stałego ziarna. */
	private static final double[] NOISE_CDF = noiseCdf();

	private final LandscapeScale scale;
	private final double regionScale;
	private final double regionSize;
	private final double tau;
	/** Mnożniki skali: strefy, formy średnie, lokalne, rozstaw gór, szerokość koryt. */
	private final double zs;
	private final double meso;
	private final double local;
	private final double mspace;
	private final double chan;
	private final double tunnelBank;
	private final double tunnelSill;
	private final double tunnelCell;

	private final Noise zoneMountain;
	private final Noise zoneMountainMask;
	private final Noise zoneGlacial;
	private final Noise warp;
	private final Noise regionWarp;
	private final Noise regionJitter;
	private final Noise lowlandBase;
	private final Noise relief;
	private final Noise dunes;
	private final Noise moraine;
	private final Noise tunnel;
	private final Noise kettle;
	private final Noise mountain;
	private final Noise zoneSea;
	private final Noise coast;
	/** Piaszczystość utworu (M2, siedliska). */
	private final Noise habitatSandiness;
	private final RiverNetwork rivers;
	/** Siatki z pamięcią (M2, S3): wygładzony teren z nachyleniem i ekspozycją oraz pola regionalne O, P. */
	private final CoarseTerrainField coarse;
	/** Najwyższy teren w promieniu 3 km·mspace (duży masyw w piętrach, E12). */
	private final PeakField peaks;
	private final RegionalField regional;

	private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> lakeLevels = new ConcurrentHashMap<>();
	/** Stan oczka: {@link #KETTLE_NONE}, {@link #KETTLE_MINEROTROPHIC} albo {@link #KETTLE_OMBROTROPHIC}. */
	private final ConcurrentHashMap<Long, Byte> kettleExistence = new ConcurrentHashMap<>();
	private static final byte KETTLE_NONE = 0;
	private static final byte KETTLE_MINEROTROPHIC = 1;
	private static final byte KETTLE_OMBROTROPHIC = 2;

	/**
	 * Komórka makroregionu. {@code cos}/{@code sin} opisują kierunek w poprzek pasma górskiego;
	 * rzeźba gór jest wydłużona prostopadle do niego, czyli wzdłuż pasma.
	 */
	public record Cell(long cx, long cz, LandscapeType type, double centerX, double centerZ, double cos, double sin) {
	}

	/** Model w skali rzeczywistej. */
	public LandscapeModel(long seed, double regionScale) {
		this(seed, LandscapeScale.REALISTIC, regionScale);
	}

	/**
	 * @param scale       skala pozioma krajobrazu
	 * @param regionScale mnożnik rozmiaru regionów z suwaka w opcjach świata
	 */
	public LandscapeModel(long seed, LandscapeScale scale, double regionScale) {
		if (!(regionScale > 0.01 && regionScale <= 4.0)) {
			throw new IllegalArgumentException("regionScale poza zakresem (0.01, 4]: " + regionScale);
		}
		this.scale = scale;
		this.regionScale = regionScale;
		this.regionSize = scale.regionSize() * regionScale;
		this.tau = Math.min(BLEND_WIDTH, 0.2 * regionSize) / 2.2;
		this.zs = regionSize / BASE_REGION_SIZE;
		this.meso = scale.meso();
		this.local = scale.local();
		this.mspace = scale.mountainSpacing();
		this.chan = scale.channel();
		this.tunnelBank = Math.max(70.0, 140.0 * local);
		this.tunnelSill = 250.0 * local;
		this.tunnelCell = 4_500.0 * local;
		Noise root = new Noise(seed);
		this.zoneMountain = root.derive("zone.mountain");
		this.zoneMountainMask = root.derive("zone.mountain.mask");
		this.zoneGlacial = root.derive("zone.glacial");
		this.warp = root.derive("warp");
		this.regionWarp = root.derive("region.warp");
		this.regionJitter = root.derive("region.jitter");
		this.lowlandBase = root.derive("lowland.base");
		this.relief = root.derive("relief");
		this.dunes = root.derive("dunes");
		this.moraine = root.derive("moraine");
		this.tunnel = root.derive("tunnel");
		this.kettle = root.derive("kettle");
		this.mountain = root.derive("mountain");
		this.zoneSea = root.derive("zone.sea");
		this.coast = root.derive("coast");
		// Nowe szumy M2 tylko przez „habitat.*”: pochodne ziarna są niezależne, więc teren się nie zmienia.
		this.habitatSandiness = root.derive("habitat.piask");
		this.rivers = new RiverNetwork(this, root.derive("rivers"), scale);
		this.coarse = new CoarseTerrainField(this, 32.0 * local);
		this.peaks = new PeakField(this, 3_000 * mspace, 62.5 * mspace, 4);
		this.regional = new RegionalField(this, root.derive("habitat.prowincje"), zs);
	}

	public double regionScale() {
		return regionScale;
	}

	public LandscapeScale scale() {
		return scale;
	}

	/** Średni rozmiar makroregionu w metrach. */
	public double regionSize() {
		return regionSize;
	}

	/** Siatka wygładzonego terenu (testy). */
	CoarseTerrainField coarseTerrain() {
		return coarse;
	}

	/** Siatka pól regionalnych (testy). */
	RegionalField regional() {
		return regional;
	}

	// ------------------------------------------------------------------ L0: strefy

	/** Szum pasm górskich: linia zerowa to oś pasma (bez masek; testy zasięgu P w {@link RegionalField}). */
	double mountainRaw(double x, double z) {
		double s = zs;
		double wx = x + 60_000 * s * warp.at(x, z, 250_000 * s);
		double wz = z + 60_000 * s * warp.at(x + 7_777, z - 3_333, 250_000 * s);
		return zoneMountain.at(wx, wz, 1_100_000 * s);
	}

	/**
	 * Pole pasm górskich w [0, 1]: 1 na osi pasma. Pasma to linie zerowe szumu o bardzo
	 * długiej fali, pocięte maską na skończone łańcuchy.
	 */
	public double mountainField(double x, double z) {
		double ridge = 1.0 - Math.abs(mountainRaw(x, z));
		ridge = ridge * ridge * ridge;
		double mask = Noise.smoothstep(-0.25, 0.25, zoneMountainMask.at(x, z, 900_000 * zs));
		// Góry trzymają się z dala od morza (w Polsce ok. 500 km od wybrzeża).
		double inland = Noise.smoothstep(0.10, 0.32, seaField(x, z));
		return ridge * mask * inland;
	}

	/**
	 * Liniowe pole pasm w [0, 1]: jak {@link #mountainField}, ale bez sześcianu, więc opada wolniej i sięga
	 * dalej od osi pasma. Podstawa podgórskości P w {@link RegionalField}.
	 */
	double mountainLinear(double x, double z) {
		double ridge = 1.0 - Math.abs(mountainRaw(x, z));
		double mask = Noise.smoothstep(-0.25, 0.25, zoneMountainMask.at(x, z, 900_000 * zs));
		double inland = Noise.smoothstep(0.10, 0.32, seaField(x, z));
		return ridge * mask * inland;
	}

	/** Próg pola ląd–morze; poniżej jest morze (ok. 25% powierzchni świata). */
	private static final double SEA_THRESHOLD = -0.22;

	/** Pole ląd–morze: wartości ujemne to morze. Bardzo długa fala daje gładkie, rozległe wybrzeża. */
	public double seaField(double x, double z) {
		// Wielkie zatoki i półwyspy z fBm, a na nich łagodne łuki brzegu co kilkadziesiąt km i drobne
		// zafalowania co kilka km (przesunięcie linii brzegowej rzędu 3 km i 0,7 km).
		return zoneSea.fbm(x, z, 900_000 * zs, 3, 0.45) - SEA_THRESHOLD
				+ 0.006 * zoneSea.at(x + 7_777, z - 3_333, 45_000 * zs)
				+ 0.0015 * zoneSea.at(x - 1_234, z + 5_678, 9_000 * zs);
	}

	/** Przybliżona odległość od linii brzegu w metrach: dodatnia na lądzie, ujemna na morzu. */
	public double coastDistance(double x, double z) {
		double c = seaField(x, z);
		double e = Math.max(20.0, 400.0 * zs);
		double gx = (seaField(x + e, z) - seaField(x - e, z)) / (2 * e);
		double gz = (seaField(x, z + e) - seaField(x, z - e)) / (2 * e);
		double g = Math.sqrt(gx * gx + gz * gz);
		return c / Math.max(g, 1e-12);
	}

	/** Szerokość plaży w metrach. */
	private double beachWidth() {
		return 60.0 * local;
	}

	/**
	 * Kształt wybrzeża: dno morza, plaża, wydma przednia i wydmy nadmorskie na niskim brzegu, klif tam,
	 * gdzie wysoczyzna dochodzi do morza, i miejscami zalew (jezioro przybrzeżne) za mierzeją.
	 *
	 * @param h wysokość terenu z typów krajobrazu
	 * @param d odległość od linii brzegu (dodatnia na lądzie)
	 */
	private double shapeCoast(double h, double d, double x, double z) {
		double band = 25_000 * meso;
		if (d >= band) {
			return h;
		}
		if (d < 0) {
			return -seaDepth(-d);
		}
		// Teren obniża się ku morzu, ale część rzeźby zostaje, żeby mogły powstać klify.
		double hl = h * (0.12 + 0.88 * Noise.smoothstep(0, band, d));
		double beach = beachWidth();
		double shore = 2.0 * Noise.smoothstep(0, beach, d);
		double cliffLimit = shore + 2.5 * Math.max(0, d - beach);
		double result = Math.max(Math.min(hl, cliffLimit), shore);
		// Niski brzeg: wydma przednia i wydmy za nią. Wysoki brzeg (klif) jest bez wydm.
		double low = 1 - Noise.smoothstep(6, 20, hl);
		if (low > 0) {
			double duneWidth = 220 * local;
			double u = (d - beach) / duneWidth;
			if (u > 0 && u < 1) {
				double bump = Math.sin(Math.PI * u);
				double height = 6 + 14 * (0.5 + 0.5 * coast.at(x, z, 3_000 * meso));
				result = Math.max(result, shore + height * bump * low);
			}
			// Zalew za mierzeją: płytkie jezioro na poziomie morza, oddzielone wydmą.
			double lagoon = Noise.smoothstep(0.25, 0.5, coast.at(x + 999, z, 60_000 * meso)) * low;
			if (lagoon > 0) {
				double start = beach + duneWidth + 80 * local;
				// Szerokość i głębokość maleją razem z polem zalewu, więc zalew zwęża się ku końcom
				// zamiast urywać się prostą linią; brzeg od lądu jest nieregularny (zatoki, półwyspy).
				double width = (1_500 + 1_500 * (0.5 + 0.5 * coast.at(x, z, 20_000 * meso))) * meso * lagoon;
				double ragged = 0.18 * width * coast.fbm(x - 555, z + 777, 2_500 * meso, 2, 0.5);
				double v = (d - start + ragged) / Math.max(1e-6, width);
				if (v > 0 && v < 1) {
					double bowl = Noise.smoothstep(0, 0.15, v) * (1 - Noise.smoothstep(0.7, 1, v));
					double depth = (1 + 5 * lagoon) * bowl;
					result = Math.min(result, Noise.lerp(bowl, result, -depth));
				}
			}
		}
		return result;
	}

	/** Głębokość morza w metrach w odległości {@code off} od brzegu (Bałtyk: płytki szelf). */
	private double seaDepth(double off) {
		double s = meso;
		double depth = 22 * (1 - Math.exp(-off / (2_500 * s))) + 45 * Noise.smoothstep(12_000 * s, 70_000 * s, off);
		// Rewy: podwodne wały piaszczyste przy brzegu.
		double bars = off < 600 * s ? 0.8 * Math.sin(off / (90 * s) * Math.PI) * (1 - off / (600 * s)) : 0;
		return Math.max(0.2, depth - bars);
	}

	/** Pole zlodowacenia: wartości dodatnie to strefa młodoglacjalna. Góry wypychają ją na zewnątrz. */
	public double glacialField(double x, double z) {
		// Przy morzu przeważa rzeźba młodoglacjalna, jak na Pomorzu i Mazurach.
		double nearSea = 1 - Noise.smoothstep(0.0, 0.35, seaField(x, z));
		return zoneGlacial.fbm(x, z, 500_000 * zs, 2, 0.5) - 1.4 * mountainField(x, z) + 0.05 + 0.8 * nearSea;
	}

	/** Regionalny poziom bazowy nizin w metrach n.p.m. (ok. 70–190 m), zmienny bardzo łagodnie. */
	public double lowlandBaseline(double x, double z) {
		return 130.0 + 55.0 * lowlandBase.fbm(x, z, 140_000 * meso, 2, 0.5);
	}

	// ------------------------------------------------------------------ L1: makroregiony

	/** Typ krajobrazu komórki makroregionu (po zastosowaniu reguł sąsiedztwa). */
	public LandscapeType regionType(long cx, long cz) {
		return cell(cx, cz).type();
	}

	public Cell cell(long cx, long cz) {
		long key = Noise.key(cx, cz, 1);
		Cell cached = cells.get(key);
		if (cached != null) {
			return cached;
		}
		LandscapeType type = rawRegionType(cx, cz);
		if (type == LandscapeType.BESKIDS) {
			// Reguła: Beskidy nigdy nie graniczą bezpośrednio z niziną; pośredniczy pogórze.
			outer:
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && rawRegionType(cx + dx, cz + dz).isLowland()) {
						type = LandscapeType.FOOTHILLS;
						break outer;
					}
				}
			}
		}
		double px = regionCenterX(cx, cz);
		double pz = regionCenterZ(cx, cz);
		// Kierunek w poprzek pasma: gradient surowego pola gór (nie zmienia znaku na osi pasma).
		double e = Math.max(50.0, 2_000 * zs);
		double gx = mountainRaw(px + e, pz) - mountainRaw(px - e, pz);
		double gz = mountainRaw(px, pz + e) - mountainRaw(px, pz - e);
		double len = Math.sqrt(gx * gx + gz * gz);
		double cos = len > 1e-12 ? gx / len : 1;
		double sin = len > 1e-12 ? gz / len : 0;
		Cell c = new Cell(cx, cz, type, px, pz, cos, sin);
		if (cells.size() > 200_000) {
			cells.clear();
		}
		cells.put(key, c);
		return c;
	}

	private LandscapeType rawRegionType(long cx, long cz) {
		double px = regionCenterX(cx, cz);
		double pz = regionCenterZ(cx, cz);
		double m = mountainField(px, pz);
		if (m >= 0.80) {
			return LandscapeType.BESKIDS;
		}
		if (m >= 0.55) {
			return LandscapeType.FOOTHILLS;
		}
		if (glacialField(px, pz) > 0.0) {
			return regionJitter.unit(cx, cz, 3) < 0.35 ? LandscapeType.OUTWASH_PLAIN : LandscapeType.MORAINE_PLATEAU;
		}
		return regionJitter.unit(cx, cz, 4) < 0.12 ? LandscapeType.OUTWASH_PLAIN : LandscapeType.OLD_GLACIAL_PLAIN;
	}

	/** Środek komórki w przestrzeni wyszukiwania (zawirowanej). */
	double regionCenterX(long cx, long cz) {
		return (cx + 0.15 + 0.7 * regionJitter.unit(cx, cz, 1)) * regionSize;
	}

	double regionCenterZ(long cx, long cz) {
		return (cz + 0.15 + 0.7 * regionJitter.unit(cx, cz, 2)) * regionSize;
	}

	/** {@link LandscapeType#values()} raz (values() kopiuje tablicę przy każdym wywołaniu). */
	private static final LandscapeType[] TYPES = LandscapeType.values();

	/** Komórki wpływające na punkt i ich wagi (suma = 1). */
	public record Blend(Cell[] cells, double[] weights, int count, double[] typeWeights) {
		public LandscapeType dominant() {
			int best = 0;
			for (int i = 1; i < typeWeights.length; i++) {
				if (typeWeights[i] > typeWeights[best]) {
					best = i;
				}
			}
			return TYPES[best];
		}

		public double weight(LandscapeType t) {
			return typeWeights[t.ordinal()];
		}
	}

	/**
	 * Wagi komórek w punkcie. Granice są zawirowane szumem, a wagi to softmax odległości,
	 * więc powierzchnia nie ma załamań wzdłuż dwusiecznych Voronoi.
	 */
	public Blend blend(double x, double z) {
		double a = 0.22 * regionSize;
		double lx = x + a * regionWarp.fbm(x, z, 0.7 * regionSize, 2, 0.5);
		double lz = z + a * regionWarp.fbm(x + 12_345, z - 6_789, 0.7 * regionSize, 2, 0.5);
		long gx = (long) Math.floor(lx / regionSize);
		long gz = (long) Math.floor(lz / regionSize);
		// Najpierw wszystkie 9 komórek i ich odległości (w tablicach wyniku), potem w tych samych tablicach
		// zostają tylko komórki o istotnej wadze, w tej samej kolejności (n ≤ i, więc nadpisujemy tylko
		// przeczytane miejsca).
		Cell[] used = new Cell[9];
		double[] w = new double[9];
		double min = Double.MAX_VALUE;
		int i = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++, i++) {
				Cell c = cell(gx + dx, gz + dz);
				used[i] = c;
				double ddx = lx - c.centerX();
				double ddz = lz - c.centerZ();
				w[i] = Math.sqrt(ddx * ddx + ddz * ddz);
				min = Math.min(min, w[i]);
			}
		}
		double[] tw = new double[TYPES.length];
		int n = 0;
		double total = 0;
		for (i = 0; i < 9; i++) {
			double d = (w[i] - min) / tau;
			if (d < 9.0) {
				double wi = Math.exp(-d);
				used[n] = used[i];
				w[n] = wi;
				total += wi;
				n++;
			}
		}
		for (i = n; i < 9; i++) {
			used[i] = null;
			w[i] = 0;
		}
		for (i = 0; i < n; i++) {
			w[i] /= total;
			tw[used[i].type().ordinal()] += w[i];
		}
		return new Blend(used, w, n, tw);
	}

	/** Wagi typów krajobrazu w punkcie (suma = 1). */
	public double[] typeWeights(double x, double z) {
		return blend(x, z).typeWeights();
	}

	// ------------------------------------------------------------------ L2: rzeźba

	/** Wysokość gruntu komórki bez wód, w metrach n.p.m. */
	double cellElevation(Cell c, double x, double z) {
		return cellElevation(c, x, z, null, 0);
	}

	/**
	 * Składowe rzeźby zapisywane przy liczeniu wysokości w {@link #sample} (kontekst wyjściowy
	 * {@link #cellElevation}). Nie wpływają na wysokość; obiekt jest lokalny dla jednej próbki.
	 */
	private static final class ReliefParts {
		/** Suma składowych krótkofalowych ważonych wagami komórek. */
		double convexity;
		/** Wysokość wydmy (z komórki sandru) i wału moreny (z komórki wysoczyzny). */
		double duneHeight;
		double moraineRidgeHeight;
		/** Siła masywu (z komórki Beskidów). */
		double massif;
		/** Profil dolin podłużnych p[0] komórki pogórza i Beskidów o największej wadze (jak w {@link #describe}). */
		double foothillsWeight = -1;
		double foothillsProfile = Double.NaN;
		double beskidsWeight = -1;
		double beskidsProfile = Double.NaN;

		void flysch(LandscapeType type, double w, double profile) {
			if (type == LandscapeType.FOOTHILLS) {
				if (w > foothillsWeight) {
					foothillsWeight = w;
					foothillsProfile = profile;
				}
			} else if (w > beskidsWeight) {
				beskidsWeight = w;
				beskidsProfile = profile;
			}
		}
	}

	/**
	 * Jak {@link #cellElevation(Cell, double, double)}; gdy {@code o} nie jest null, dopisuje do niego
	 * składowe rzeźby komórki o wadze {@code w}. Wysokość jest identyczna w obu wariantach.
	 */
	private double cellElevation(Cell c, double x, double z, ReliefParts o, double w) {
		return switch (c.type()) {
			case OUTWASH_PLAIN -> lowlandBaseline(x, z) + outwashPlainRelief(x, z, o, w);
			case MORAINE_PLATEAU -> lowlandBaseline(x, z) + 22.0 + moraineRelief(x, z, o, w);
			case OLD_GLACIAL_PLAIN -> plainElevation(x, z, o, w);
			case FOOTHILLS -> foothillElevation(c, x, z, o, w);
			case BESKIDS -> mountainElevation(c, x, z, o, w);
			case COASTLAND, SEA -> lowlandBaseline(x, z);
		};
	}

	/** Równina staroglacjalna: płaska, z łagodnym falowaniem. */
	private double plainElevation(double x, double z, ReliefParts o, double w) {
		double fine = 2.0 * relief.fbm(x, z, 500 * local, 2, 0.5);
		if (o != null) {
			o.convexity += w * fine;
		}
		return lowlandBaseline(x, z) - 8.0 + 6.0 * relief.fbm(x, z, 3_000 * local, 3, 0.5) + fine;
	}

	/** Sandr: łagodne nachylenie z wielkoskalowego szumu, drobne falowanie i pola wydm. */
	private double outwashPlainRelief(double x, double z, ReliefParts o, double w) {
		double tilt = 12.0 * relief.at(x, z, 25_000 * meso);
		double ripple = 1.5 * relief.fbm(x + 311, z - 97, 400 * local, 2, 0.5);
		double dune = duneHeight(x, z);
		if (o != null) {
			o.convexity += w * (ripple + dune);
			o.duneHeight = dune;
		}
		return tilt + ripple + dune;
	}

	/** Wysokość wydmy nad powierzchnią sandru (0 poza polami wydmowymi). */
	private double duneHeight(double x, double z) {
		double field = Noise.smoothstep(0.15, 0.45, dunes.at(x, z, 18_000 * meso));
		if (field <= 0) {
			return 0.0;
		}
		// Wydmy wydłużone z zachodu na wschód (przeważające wiatry zachodnie).
		double d = dunes.ridged(x / 3.0, z, 700 * local, 2, 0.45);
		return field * 22.0 * Noise.smoothstep(0.45, 0.95, d);
	}

	/** Wysoczyzna morenowa: pagórki o fali 300–1500 m oraz pasy wałów moren czołowych. */
	private double moraineRelief(double x, double z, ReliefParts o, double w) {
		double hills = 11.0 * relief.fbm(x, z, 1_100 * local, 4, 0.55);
		double ridge = moraineRidge(x, z);
		if (o != null) {
			o.convexity += w * (hills + ridge);
			o.moraineRidgeHeight = ridge;
		}
		return hills + ridge;
	}

	/** Wysokość wałów moren czołowych (0 poza pasami moren). */
	private double moraineRidge(double x, double z) {
		double belt = Noise.smoothstep(0.10, 0.40, moraine.at(x, z, 35_000 * meso));
		if (belt <= 0) {
			return 0.0;
		}
		double wx = x + 1_500 * local * moraine.at(x, z, 6_000 * local);
		double r = moraine.ridged(wx, z / 2.5, 4_200 * local, 3, 0.5);
		return belt * 95.0 * Math.pow(r, 1.6);
	}

	/**
	 * Współrzędna w poprzek pasma (u) i wzdłuż pasma (v), liczona od globalnego początku układu.
	 * Nie liczymy jej od środka komórki: przy małych komórkach (skala rozgrywki) każda komórka
	 * próbkowałaby wtedy ten sam wycinek szumu i góry byłyby systematycznie zaniżone.
	 */
	private static double across(Cell c, double x, double z) {
		return x * c.cos() + z * c.sin();
	}

	private static double along(Cell c, double x, double z) {
		return -x * c.sin() + z * c.cos();
	}

	/**
	 * Profil doliny: 0 na osi doliny (przekrój V), 1 na grzbiecie (zaokrąglony wierzchołek).
	 * {@code n} to wartość szumu, którego izolinia zerowa wyznacza oś doliny.
	 */
	private static double valleyProfile(double n, double width) {
		return Math.tanh(1.3 * Math.abs(n) / width) / 0.96;
	}

	/**
	 * Rzeźba fliszowa: doliny podłużne wzdłuż pasma, doliny poprzeczne dzielące grzbiety na
	 * szczyty i przełęcze, modulacja kopuł i drobna szorstkość.
	 *
	 * @param p składowe z {@link #flyschParts}
	 */
	private static double flyschRelief(double[] p) {
		return p[0] * (0.45 + 0.55 * p[1]) * (0.8 + 0.2 * p[2]) * p[3];
	}

	/**
	 * Wypukłość fliszu (m): wkład żlebów do rzeźby względem ich średniej i drobna szorstkość. Grzbiety
	 * i doliny podłużne (fala kilku km) nie wchodzą, bo opisuje je profil p[0].
	 */
	private static double flyschConvexity(double relief, double[] p, double rough) {
		return relief * p[0] * (0.45 + 0.55 * p[1]) * 0.2 * (p[2] - GULLY_MEAN) * p[3] + rough;
	}

	/**
	 * Składowe rzeźby fliszowej: {profil dolin podłużnych, profil dolin poprzecznych, żleby, kopuły}.
	 * Profile mają wartość 0 na osi doliny i ok. 1 na grzbiecie.
	 */
	private double[] flyschParts(Cell c, double x, double z, double spacing) {
		double u = across(c, x, z);
		double v = along(c, x, z);
		double wu = u + 0.35 * spacing * mountain.fbm(x - 999, z, 1.6 * spacing, 2, 0.5);
		double wv = v + 0.35 * spacing * mountain.fbm(x, z + 999, 1.6 * spacing, 2, 0.5);
		double main = valleyProfile(mountain.sample(wu / spacing + 0.5, wv / (3.0 * spacing)), 0.55);
		double cross = valleyProfile(mountain.sample(wu / (1.2 * spacing) - 7.3, wv / (0.45 * spacing)), 0.6);
		// Żleby i dolinki na stokach: gęsta, zawirowana sieć o rozstawie ok. 0,2 rozstawu dolin.
		double g = 0.2 * spacing;
		double gx = x + 0.5 * g * mountain.at(x + 111, z - 222, 0.8 * g);
		double gz = z + 0.5 * g * mountain.at(x - 333, z + 444, 0.8 * g);
		double gully = valleyProfile(mountain.sample(gx / g + 3.1, gz / (1.7 * g)), 0.5);
		double domes = 0.75 + 0.25 * mountain.at(x + 4_321, z, 1.8 * spacing);
		return new double[] {main, cross, gully, domes};
	}

	private double flyschSpacing(LandscapeType type) {
		return (type == LandscapeType.BESKIDS ? 5_200 : 3_200) * mspace;
	}

	/** Pogórze: garby wydłużone wzdłuż pasma, 300–600 m n.p.m., deniwelacje 100–250 m. */
	private double foothillElevation(Cell c, double x, double z, ReliefParts o, double w) {
		double m = mountainField(x, z);
		double floor = 270.0 + 90.0 * Noise.smoothstep(0.45, 0.85, m);
		double relief = 170.0 + 90.0 * Noise.smoothstep(0.5, 0.85, m);
		double rough = 12.0 * relief(x, z, 700 * local);
		double[] p = flyschParts(c, x, z, 3_200 * mspace);
		if (o != null) {
			o.convexity += w * flyschConvexity(relief, p, rough);
			o.flysch(LandscapeType.FOOTHILLS, w, p[0]);
		}
		return floor + relief * flyschRelief(p) + rough;
	}

	/** Beskidy: 500–1725 m n.p.m., deniwelacje 400–900 m, stoki 15–30°. */
	private double mountainElevation(Cell c, double x, double z, ReliefParts o, double w) {
		double m = mountainField(x, z);
		double axis = Noise.smoothstep(0.82, 1.0, m);
		double floor = 480.0 + 180.0 * axis;
		double relief = 520.0 + 380.0 * axis;
		// Pojedyncze wyższe masywy typu Babiej Góry lub Pilska.
		double massif = Noise.smoothstep(0.35, 0.8, mountain.at(x - 55_555, z + 22_222, 30_000 * meso));
		relief += 350.0 * massif;
		double rough = 25.0 * relief(x, z, 900 * local);
		double[] p = flyschParts(c, x, z, 5_200 * mspace);
		if (o != null) {
			o.convexity += w * flyschConvexity(relief, p, rough);
			o.flysch(LandscapeType.BESKIDS, w, p[0]);
			o.massif = massif;
		}
		double h = floor + relief * flyschRelief(p) + rough;
		// Łagodne nasycenie powyżej 1500 m: najwyższe szczyty Beskidów to ok. 1725 m (Babia Góra).
		if (h > 1_500) {
			h = 1_500 + 250 * Math.tanh((h - 1_500) / 250);
		}
		return h;
	}

	private double relief(double x, double z, double wavelength) {
		return relief.fbm(x, z, wavelength, 3, 0.5);
	}

	// ------------------------------------------------------------------ próbkowanie

	private double elevation(Blend b, double x, double z) {
		double h = 0;
		for (int i = 0; i < b.count(); i++) {
			h += b.weights()[i] * cellElevation(b.cells()[i], x, z);
		}
		return h;
	}

	/** Jak {@link #elevation(Blend, double, double)}, z zapisem składowych rzeźby do {@code o}. */
	private double elevation(Blend b, double x, double z, ReliefParts o) {
		double h = 0;
		for (int i = 0; i < b.count(); i++) {
			h += b.weights()[i] * cellElevation(b.cells()[i], x, z, o, b.weights()[i]);
		}
		return h;
	}

	/** Pola regionalne O i P w punkcie, bez pełnej próbki (podgląd map regionalnych i testy). */
	ColumnSample.Region region(double x, double z) {
		return regional.sample(x, z);
	}

	/** Wysokość gruntu po zmieszaniu komórek i ukształtowaniu wybrzeża, jeszcze bez rzek i jezior. */
	public double landElevation(double x, double z) {
		return shapeCoast(elevation(blend(x, z), x, z), coastDistance(x, z), x, z);
	}

	/** Pełna próbka kolumny: rzeźba, wody, podłoże. */
	public ColumnSample sample(double x, double z) {
		Blend b = blend(x, z);
		ReliefParts parts = new ReliefParts();
		double raw = elevation(b, x, z, parts);
		double coastD = coastDistance(x, z);
		double surface = shapeCoast(raw, coastD, x, z);
		double rawSurface = surface;
		LandscapeType dominant = b.dominant();
		double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
				+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN);
		double young = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU);
		double foothills = b.weight(LandscapeType.FOOTHILLS);
		double mountains = b.weight(LandscapeType.BESKIDS);

		Substrate substrate = dominant.defaultSubstrate();
		int water = ColumnSample.NO_WATER;
		WaterKind kind = WaterKind.NONE;

		// Morze i zalewy (na poziomie morza).
		if (coastD < 0 || surface < 0) {
			LandscapeType t = coastD < 0 ? LandscapeType.SEA : LandscapeType.COASTLAND;
			Substrate sub = coastD < 0 && -coastD > 3_000 * meso ? Substrate.LAKE_MUD : Substrate.SAND;
			return new ColumnSample(surface, 0, WaterKind.SEA, t, coastD < 0 ? sub : Substrate.LAKE_MUD, 30.0,
					terrain(b, parts, t, raw, rawSurface, coastD, 0, Double.NaN, Double.NaN, 0, x, z), ColumnSample.Waters.NONE,
					regional.sample(x, z));
		}
		double bare = Double.NaN;
		if (coastD < beachWidth() + 400 * local) {
			dominant = LandscapeType.COASTLAND;
			// Plaża i biała wydma bez darni; dalej od morza wydma szara, porośnięta.
			bare = beachWidth() + 220 * local * (0.6 + 0.4 * coast.at(x, z, 400 * local));
			substrate = surface > 8 ? Substrate.GLACIAL_TILL : coastD < bare ? Substrate.BEACH_SAND : Substrate.SAND;
		}

		// Doliny i koryta sieci rzecznej oraz jeziora bezodpływowe.
		double valley = 0;
		RiverNetwork.RiverHit r = rivers.query(x, z, surface, lowland + b.weight(LandscapeType.COASTLAND), foothills,
				mountains);
		surface = r.terrain();
		boolean inSinkLake = r.lakeLevel() != ColumnSample.NO_WATER && r.lakeShore() < 0;
		// Najbliższa woda stojąca (pola siedlisk): jezioro bezodpływowe, starorzecze, jezioro rynnowe, oczko.
		// Zmienne lokalne zamiast obiektu, bo sample woła się dla każdej kolumny (koszt, §3.1 planu M2);
		// kolejne źródła zastępują wcześniejsze tylko przy bliższym brzegu.
		double standingShore = Double.POSITIVE_INFINITY;
		int standingLevel = ColumnSample.NO_WATER;
		ColumnSample.StandingWaterKind standingKind = ColumnSample.StandingWaterKind.NONE;
		boolean standingOmbrotrophic = false;
		long standingId = 0;
		double standingRadius = Double.NaN;
		// Jezioro bezodpływowe w pierścieniu do 150 m·k (ols, §4.4 planu M2); teren zmienia tylko do KETTLE_BANK.
		if (r.ringLevel() != ColumnSample.NO_WATER && r.ringShore() < standingShore) {
			standingShore = r.ringShore();
			standingLevel = r.ringLevel();
			standingKind = ColumnSample.StandingWaterKind.SINK_LAKE;
			standingId = r.ringId();
			standingRadius = r.ringRadius();
		}
		if (r.order() > 0 && !inSinkLake && !r.inChannel() && r.oxbowShore() < standingShore) {
			standingShore = r.oxbowShore();
			standingLevel = r.oxbowMirror();
			standingKind = ColumnSample.StandingWaterKind.OXBOW_LAKE;
			standingOmbrotrophic = false;
			standingId = r.oxbowId();
			standingRadius = r.oxbowWidth();
		}
		if (r.order() > 0 && !inSinkLake) {
			valley = r.valleyWeight();
			if (r.inFloor()) {
				substrate = lowland > 0.5 ? Substrate.ALLUVIUM : Substrate.RIVERBED;
			}
			if (!r.inChannel() && surface < r.bankLevel()) {
				surface = r.bankLevel();
			}
			if (r.inChannel()) {
				surface = Math.min(surface, r.channelBottom());
				if (surface < r.waterLevel()) {
					water = r.waterLevel();
					kind = WaterKind.RIVER;
					substrate = Substrate.RIVERBED;
				}
			} else if (r.oxbowLevel() != ColumnSample.NO_WATER) {
				surface = Math.min(surface, r.oxbowLevel() - 0.3 - r.oxbowDepth());
				water = r.oxbowLevel();
				kind = WaterKind.OXBOW;
				substrate = Substrate.LAKE_MUD;
			}
		}
		if (kind == WaterKind.NONE && r.lakeLevel() != ColumnSample.NO_WATER && r.lakeShore() < KETTLE_BANK) {
			LakeHit sinkLake = new LakeHit(r.lakeLevel(), r.lakeDepth(), r.lakeShore(), false, 0.25, KETTLE_BANK,
					ColumnSample.StandingWaterKind.SINK_LAKE, r.lakeId(), r.lakeRadius(), false);
			surface = applyLake(surface, sinkLake);
			if (r.lakeShore() < 0 && surface < r.lakeLevel()) {
				water = r.lakeLevel();
				kind = WaterKind.LAKE;
				substrate = Substrate.LAKE_MUD;
			}
			valley = Math.max(valley, 1 - Noise.smoothstep(0, 200 * local, r.lakeShore()));
		}

		// Rynny polodowcowe z łańcuchami jezior (tylko strefa młodoglacjalna, poza dolinami rzek).
		// Obecność rynny wygasa płynnie w dolinach rzek, więc jeziora nie są ucinane na krawędzi doliny.
		double tunnelPresence = young * (1 - Noise.smoothstep(0.1, 0.45, valley)) * Noise.smoothstep(0, 1_500 * meso, coastD);
		if (tunnelPresence > 0.05 && kind == WaterKind.NONE) {
			LakeHit lake = tunnelLakeAt(x, z, tunnelPresence);
			if (lake != null) {
				if (lake.shoreDistance < standingShore) {
					standingShore = lake.shoreDistance;
					standingLevel = lake.level;
					standingKind = lake.kind;
					standingOmbrotrophic = lake.ombrotrophic;
					standingId = lake.id;
					standingRadius = lake.radius;
				}
				// Za zasięgiem niecki (pierścień siedlisk do 150 m·k) jezioro nie zmienia terenu.
				if (lake.shoreDistance <= lake.bank) {
					surface = applyLake(surface, lake);
				}
				// Woda tylko wewnątrz linii brzegu; poza nią pas 15 m ma zawsze wał na poziomie lustra + 1 m.
				if (lake.shoreDistance < 0 && surface < lake.level) {
					water = lake.level;
					kind = WaterKind.LAKE;
					substrate = Substrate.LAKE_MUD;
				}
			}
		}

		// Oczka wytopiskowe na wysoczyźnie morenowej i sandrze. O istnieniu oczka decydują warunki
		// w jego środku, dlatego sprawdzamy je w każdej kolumnie bez wody.
		if (kind == WaterKind.NONE) {
			LakeHit k = kettleAt(x, z);
			if (k != null) {
				if (k.shoreDistance < standingShore) {
					standingShore = k.shoreDistance;
					standingLevel = k.level;
					standingKind = k.kind;
					standingOmbrotrophic = k.ombrotrophic;
					standingId = k.id;
					standingRadius = k.radius;
				}
				surface = applyLake(surface, k);
				if (k.shoreDistance < 0 && surface < k.level) {
					if (k.peat) {
						surface = k.level - 0.5;
						substrate = Substrate.PEAT;
					} else {
						water = k.level;
						kind = WaterKind.KETTLE;
						substrate = Substrate.LAKE_MUD;
					}
				}
			}
		}

		double cover = switch (dominant.belt()) {
			case SEA, LOWLAND -> 40.0 + 60.0 * (0.5 + 0.5 * relief.at(x, z, 20_000 * meso));
			case FOOTHILLS -> 3.0 + 4.0 * (0.5 + 0.5 * relief.at(x, z, 800));
			case MOUNTAINS -> 1.0 + 2.5 * (0.5 + 0.5 * relief.at(x, z, 600));
		};

		ColumnSample.Waters waters = r.order() == 0 && standingKind == ColumnSample.StandingWaterKind.NONE ? ColumnSample.Waters.NONE
				: new ColumnSample.Waters(r.order(), r.source(), r.channelDist(), r.channelWidth(), r.channelLevel(),
						r.inFloor(), r.floorU(), r.floorHalf(), r.slope(), r.convexBank(), standingShore, standingLevel, standingKind,
						standingOmbrotrophic, standingId, standingRadius);
		int landformBits = forms(r, parts, dominant, surface, rawSurface, coastD, water);
		// Duży masyw (E12): najwyższy teren w promieniu 3 km·mspace, tylko tam, gdzie piętra go potrzebują.
		double summit = mountains > 0 && surface >= AltitudinalBelts.SUMMIT_FROM ? peaks.sample(x, z) : 0;
		return new ColumnSample(surface, water, kind, dominant, substrate, cover,
				terrain(b, parts, dominant, raw, rawSurface, coastD, landformBits, sandiness(x, z), bare, summit, x, z), waters,
				regional.sample(x, z));
	}

	/**
	 * Formy terenu z warunków {@link #describe} przeniesione do próbki, bez dodatkowych próbek: bity
	 * {@link Landform#bit()} form z {@link Landform#FROM_SAMPLE}. Osobna metoda, żeby {@link #sample} nie rósł.
	 */
	private int forms(RiverNetwork.RiverHit r, ReliefParts parts, LandscapeType dominant, double surface, double rawSurface,
			double coastD, int water) {
		int landformBits = 0;
		if (r.order() > 0 && r.source() && (r.inChannel() || r.inFloor())) {
			landformBits |= Landform.HEADWATERS.bit();
		}
		boolean wet = water != ColumnSample.NO_WATER && water > (int) Math.floor(surface);
		if (dominant == LandscapeType.COASTLAND && coastD >= 0 && !wet) {
			// Formy brzegu morskiego tylko w pasie, w którym powstają (plaża, wydma przednia, ściana
			// klifu), a nie np. na brzegu zalewu kilka kilometrów od morza.
			double beach = beachWidth();
			if (surface > 8 && rawSurface > 8 && coastD < beach + rawSurface / 2.5 + 20 * local) {
				landformBits |= Landform.CLIFF.bit();
			} else if (surface < 3 && coastD < 1.3 * beach) {
				landformBits |= Landform.BEACH.bit();
			} else if (surface >= 3 && coastD >= 0.8 * beach && coastD < beach + 220 * local) {
				landformBits |= Landform.COASTAL_DUNES.bit();
			}
		}
		if (dominant == LandscapeType.OUTWASH_PLAIN && parts.duneHeight > 4) {
			landformBits |= Landform.INLAND_DUNES.bit();
		}
		if (dominant == LandscapeType.MORAINE_PLATEAU && parts.moraineRidgeHeight > 25) {
			landformBits |= Landform.END_MORAINE.bit();
		}
		if (dominant == LandscapeType.FOOTHILLS || dominant == LandscapeType.BESKIDS) {
			double p0 = dominant == LandscapeType.FOOTHILLS ? parts.foothillsProfile : parts.beskidsProfile;
			if (p0 > 0.9) {
				landformBits |= Landform.RIDGE.bit();
			}
			if (p0 < 0.15) {
				landformBits |= Landform.MOUNTAIN_VALLEY.bit();
			}
		}
		return landformBits;
	}

	/**
	 * Rekord rzeźby z wag makroregionów, składowych zapisanych w {@code rz} i siatki wygładzonego terenu
	 * w punkcie (x, z).
	 */
	private ColumnSample.Terrain terrain(Blend b, ReliefParts parts, LandscapeType dominant, double raw, double rawSurface,
			double coastD, int landformBits, double sandiness, double bare, double summit, double x, double z) {
		// Krawędź klifu: teren przed wcięciem dolin w pasie formy KLIF.
		double cliffHeight = (landformBits & Landform.CLIFF.bit()) != 0 ? rawSurface : 0;
		// Pas wybrzeża: 1 tam, gdzie próbka dostaje typ POBRZEZE, 0 na skraju pasa B + D + 2000k (§3.4 planu M2).
		double coastBand = 1 - Noise.smoothstep(beachWidth() + 400 * local, beachWidth() + 2_220 * local, coastD);
		// Profil fliszu tej komórki, której używa opis form; poza górami komórka fliszu o największej wadze.
		// Przy morzu shapeCoast ściska całą rzeźbę (h · (0,12 + 0,88 smoothstep(0, 25 km·meso, cD))), więc
		// wypukłość i wysokość wydmy skalujemy tak samo; bit WYDMY w formach liczony jest bez skalowania (jak w M1).
		double band = 25_000 * meso;
		double coastScale = coastD >= band ? 1.0 : 0.12 + 0.88 * Noise.smoothstep(0, band, coastD);
		// Niski brzeg z wydmami albo wysoki z klifem: to samo „low” co w shapeCoast, z wysokości przed wybrzeżem.
		double low = coastD >= 0 && coastD < band ? 1 - Noise.smoothstep(6, 20, raw * coastScale) : 0;
		CoarseTerrainField.CoarseSample g = coarse.sample(x, z);
		double ridgeProfile = switch (dominant) {
			case FOOTHILLS -> parts.foothillsProfile;
			case BESKIDS -> parts.beskidsProfile;
			default -> parts.beskidsWeight > parts.foothillsWeight ? parts.beskidsProfile : parts.foothillsProfile;
		};
		return new ColumnSample.Terrain(rawSurface, coastD, b.weight(LandscapeType.OUTWASH_PLAIN),
				b.weight(LandscapeType.MORAINE_PLATEAU), b.weight(LandscapeType.OLD_GLACIAL_PLAIN),
				b.weight(LandscapeType.FOOTHILLS), b.weight(LandscapeType.BESKIDS), coastBand, landformBits, parts.convexity * coastScale,
				parts.duneHeight * coastScale,
				ridgeProfile, parts.massif, summit, cliffHeight, low, bare, sandiness, g.sBar(), g.slope(), g.aspect());
	}

	/** Piaszczystość utworu 0–1: kwantyl szumu o fali 2 km·k, więc udział piasków to prosty próg. */
	private double sandiness(double x, double z) {
		return noiseQuantile(habitatSandiness.at(x, z, 2_000 * local));
	}

	/** Kwantyl wartości szumu {@link Noise#sample} (dystrybuanta z {@link #noiseCdf}): rozkład jednostajny na [0, 1]. */
	public static double noiseQuantile(double v) {
		double f = Math.clamp((v + 1) * 0.5 * CDF_BINS, 0.0, CDF_BINS);
		int k = Math.min(CDF_BINS - 1, (int) f);
		return NOISE_CDF[k] + (NOISE_CDF[k + 1] - NOISE_CDF[k]) * (f - k);
	}

	/**
	 * Dystrybuanta wartości szumu z siatki 512 × 512 punktów (ok. 90 × 75 oczek szumu) dla stałego ziarna.
	 * Rozkład szumu nie zależy od ziarna świata, a tablica jest zawsze taka sama.
	 */
	private static double[] noiseCdf() {
		Noise n = new Noise(0x5A4D_1E5AL);
		long[] hist = new long[CDF_BINS];
		int total = 0;
		for (int i = 0; i < 512; i++) {
			for (int j = 0; j < 512; j++) {
				double v = n.sample(i * 0.1731 + 0.37, j * 0.1469 - 0.11);
				int k = Math.clamp((long) Math.floor((v + 1) * 0.5 * CDF_BINS), 0, CDF_BINS - 1);
				hist[k]++;
				total++;
			}
		}
		double[] cdf = new double[CDF_BINS + 1];
		long acc = 0;
		for (int k = 0; k < CDF_BINS; k++) {
			acc += hist[k];
			cdf[k + 1] = (double) acc / total;
		}
		return cdf;
	}

	// ------------------------------------------------------------------ opis form terenu

	/** Próbka kolumny i formy terenu rozpoznane w punkcie. */
	public record Description(ColumnSample sample, Set<Landform> forms) {
	}

	/**
	 * Opis terenu w punkcie: próbka kolumny i rozpoznane formy. Formy z {@link ColumnSample.Terrain#landformBits()}
	 * bierze z próbki; szczyt, przełęcz i rynna wymagają dodatkowych próbek, więc opis kosztuje więcej
	 * niż {@link #sample} i służy komendom i narzędziom, a nie generacji.
	 */
	public Description describe(double x, double z) {
		ColumnSample s = sample(x, z);
		ColumnSample.Terrain t = s.terrain();
		ColumnSample.Waters w = s.waters();
		Set<Landform> f = EnumSet.noneOf(Landform.class);
		LandscapeType type = s.type();

		double coastD = t.coastD();
		switch (s.waterKind()) {
			case RIVER -> {
				f.add(w.streamOrder() == 1 ? Landform.STREAM : Landform.RIVER);
				if (coastD < 3_000 * meso) {
					f.add(Landform.RIVER_MOUTH);
				}
			}
			case LAKE -> f.add(Landform.TUNNEL_VALLEY_LAKE);
			case KETTLE -> f.add(Landform.KETTLE_POND);
			case OXBOW -> f.add(Landform.OXBOW_LAKE);
			case SEA -> {
				if (coastD >= 0) {
					f.add(Landform.LAGOON);
				}
			}
			default -> {
			}
		}
		// Źródło, formy brzegu morskiego, wydmy, wały morenowe, grzbiety i doliny górskie rozpoznaje już sample.
		t.addLandforms(f);
		if (s.substrate() == Substrate.PEAT) {
			f.add(Landform.KETTLE_BOG);
		}
		if (s.substrate() == Substrate.ALLUVIUM) {
			f.add(Landform.VALLEY_FLOOR);
		}

		if (w.streamOrder() > 0 && !s.hasWater() && !w.inFloor() && s.surface() < t.rawSurface() - 2) {
			f.add(Landform.VALLEY_SLOPE);
		}
		double young = t.wOutwashPlain() + t.wMorainePlateau();
		if (young > 0.05) {
			double[] tc = tunnelChannel(x, z, young);
			if (tc != null && tc[1] < tc[2]) {
				f.add(Landform.TUNNEL_VALLEY);
			}
		}

		if (type == LandscapeType.FOOTHILLS || type == LandscapeType.BESKIDS) {
			Blend b = blend(x, z);
			Cell cell = null;
			double best = -1;
			for (int i = 0; i < b.count(); i++) {
				if (b.cells()[i].type() == type && b.weights()[i] > best) {
					best = b.weights()[i];
					cell = b.cells()[i];
				}
			}
			double spacing = flyschSpacing(type);
			double[] p = flyschParts(cell, x, z, spacing);
			if (p[0] > 0.8 && p[1] < 0.3) {
				f.add(Landform.MOUNTAIN_PASS);
			}
			if (p[0] > 0.75 && isLocalMaximum(x, z, s.surface(), 0.06 * spacing)) {
				f.add(Landform.SUMMIT);
			}
			if (type == LandscapeType.BESKIDS) {
				// Nominalna granica pięter (bez korekty ekspozycji i szumu), jedna z habitat/Pietra.
				f.add(AltitudinalBelts.isUpperMontane(s.surface()) ? Landform.UPPER_MONTANE : Landform.LOWER_MONTANE);
			}
		}
		return new Description(s, f);
	}

	/** Czy punkt jest najwyższy wśród 16 punktów na okręgu o promieniu r i 8 na okręgu r/2. */
	private boolean isLocalMaximum(double x, double z, double h, double r) {
		for (int ring = 1; ring <= 2; ring++) {
			double rr = r * ring / 2.0;
			int n = ring == 1 ? 8 : 16;
			for (int i = 0; i < n; i++) {
				double a = i * (2 * Math.PI / n);
				if (landElevation(x + rr * Math.cos(a), z + rr * Math.sin(a)) >= h) {
					return false;
				}
			}
		}
		return true;
	}

	/** Geometria rynny w punkcie: {bramka, odległość od osi, połowa szerokości} albo null. */
	private double[] tunnelChannel(double x, double z, double presence) {
		double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso))
				* Noise.smoothstep(0.2, 0.7, presence);
		if (gate <= 0) {
			return null;
		}
		double e = 40;
		double gx = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
		double gz = (tunnelField(x, z + e) - tunnelField(x, z - e)) / (2 * e);
		double grad = Math.sqrt(gx * gx + gz * gz);
		if (grad < 1e-12) {
			return null;
		}
		double dist = Math.abs(tunnelField(x, z)) / grad;
		double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
		return new double[] {gate, dist, half};
	}

	// ------------------------------------------------------------------ L3: wody

	/**
	 * @param shoreDistance odległość od linii brzegu w metrach, ujemna w jeziorze
	 * @param slope         nachylenie stoku niecki nad lustrem (tangens)
	 * @param bank          zasięg wpływu niecki poza brzegiem
	 * @param kind          rodzaj zbiornika (pola siedlisk)
	 * @param id            skrót zbiornika, stały w całym zbiorniku
	 * @param radius        promień oczka lub półszerokość jeziora w tym miejscu
	 * @param ombrotrophic         oczko torfowe ombrotroficzne
	 */
	private record LakeHit(int level, double depthBelowLevel, double shoreDistance, boolean peat, double slope,
			double bank, ColumnSample.StandingWaterKind kind, long id, double radius, boolean ombrotrophic) {
	}

	/** Pole rynien: rzadkie, wydłużone z północy na południe izolinie zawirowanego szumu. */
	private double tunnelField(double x, double z) {
		double wx = x + 1_500 * meso * tunnel.at(x, z, 6_000 * meso);
		return tunnel.sample(wx / (22_000 * meso), z / (60_000 * meso));
	}

	/** Granica między jeziorami numer k w łańcuchu, nieregularna i zależna od położenia w poprzek. */
	private double tunnelBoundary(long k, double x) {
		return k * tunnelCell + 0.3 * tunnelCell * tunnel.sample(x / (15_000 * meso), k * 0.618_034);
	}

	/**
	 * Rynna polodowcowa z łańcuchem jezior soczewkowatych. Jezioro ma stały poziom lustra; między
	 * jeziorami zostają przesmyki. Część odcinków rynny jest sucha.
	 */
	private LakeHit tunnelLakeAt(double x, double z, double presence) {
		double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso))
				* Noise.smoothstep(0.2, 0.7, presence);
		if (gate <= 0) {
			return null;
		}
		double n = tunnelField(x, z);
		double e = 40;
		double gx = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
		double gz = (tunnelField(x, z + e) - tunnelField(x, z - e)) / (2 * e);
		double grad = Math.sqrt(gx * gx + gz * gz);
		if (grad < 1e-12) {
			return null;
		}
		double dist = Math.abs(n) / grad;
		double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
		// Zasięg zapytania: niecka (tunnelBank) albo pierścień siedlisk 150 m·k, jeśli szerszy.
		double reach = Math.max(tunnelBank, 150 * local);
		if (half < 25 * local || dist > half + reach) {
			return null;
		}
		long k = (long) Math.floor(z / tunnelCell);
		if (z < tunnelBoundary(k, x)) {
			k--;
		} else if (z >= tunnelBoundary(k + 1, x)) {
			k++;
		}
		double b0 = tunnelBoundary(k, x);
		double b1 = tunnelBoundary(k + 1, x);
		double edge = Math.min(z - b0, b1 - z);
		double lens = Math.sqrt(Noise.smoothstep(tunnelSill, tunnelSill + 0.35 * (b1 - b0), edge));
		double shore = dist - half * lens;
		if (shore > reach) {
			return null;
		}
		// Jezioro identyfikujemy punktem, w którym oś rynny przecina środek jego odcinka.
		// Punkt stały iteracji jest ten sam dla wszystkich kolumn jeziora.
		double ax = x;
		double az = 0.5 * (b0 + b1);
		for (int outer = 0; outer < 3; outer++) {
			for (int it = 0; it < 12; it++) {
				double f = tunnelField(ax, az);
				double d = (tunnelField(ax + e, az) - tunnelField(ax - e, az)) / (2 * e);
				if (Math.abs(d) < 1e-15) {
					break;
				}
				double step = Math.clamp(f / d, -2_000.0, 2_000.0);
				ax -= step;
				if (Math.abs(step) < 1e-4) {
					break;
				}
			}
			az = 0.5 * (tunnelBoundary(k, ax) + tunnelBoundary(k + 1, ax));
		}
		long anchor = Math.round(ax / (200.0 * local));
		if (tunnel.unit(anchor, k, 8) < 0.3) {
			return null;
		}
		long key = Noise.key(anchor, k, 2);
		double depth = lens * gate * (18 + 45 * tunnel.unit(anchor, k, 7));
		int level = lakeLevel(key, ax, az, 450 * local);
		return new LakeHit(level, depth, shore, false, 0.35, tunnelBank, ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE,
				key, half * lens, false);
	}

	/** Największa szansa na oczko w komórce (czysta wysoczyzna morenowa). */
	private static final double KETTLE_MAX_CHANCE = 0.45;

	/**
	 * Oczko wytopiskowe: w komórce 700 m (w skali rozgrywki 350 m) najwyżej jedno zagłębienie
	 * o promieniu 20–150 m. Istnienie oczka zależy od warunków w jego środku, więc oczko jest
	 * zawsze w całości albo wcale, a cała strefa jego brzegu mieści się w komórce.
	 */
	private LakeHit kettleAt(double x, double z) {
		double cell = 700 * local;
		long cx = (long) Math.floor(x / cell);
		long cz = (long) Math.floor(z / cell);
		double roll = kettle.unit(cx, cz, 1);
		if (roll > KETTLE_MAX_CHANCE) {
			return null;
		}
		double r = local * (20 + 130 * Math.pow(kettle.unit(cx, cz, 2), 2));
		double margin = 1.3 * r + KETTLE_BANK + 5;
		double kx = cx * cell + margin + (cell - 2 * margin) * kettle.unit(cx, cz, 3);
		double kz = cz * cell + margin + (cell - 2 * margin) * kettle.unit(cx, cz, 4);
		double dx = x - kx;
		double dz = z - kz;
		double d = Math.sqrt(dx * dx + dz * dz);
		// Lekko nieregularny brzeg.
		double shore = d - r * (1 + 0.25 * kettle.at(x, z, Math.max(30, r)));
		if (shore > KETTLE_BANK) {
			return null;
		}
		long key = Noise.key(cx, cz, 3);
		byte state = kettleState(key, roll, kx, kz, r);
		if (state == KETTLE_NONE) {
			return null;
		}
		int level = lakeLevel(key, kx, kz, r * 1.3 + 25);
		double depth = 2 + 8 * kettle.unit(cx, cz, 5);
		boolean peat = kettle.unit(cx, cz, 6) < 0.4;
		return new LakeHit(level, depth, shore, peat, 0.25, KETTLE_BANK,
				peat ? ColumnSample.StandingWaterKind.KETTLE_BOG : ColumnSample.StandingWaterKind.KETTLE_POND, key, r,
				peat && state == KETTLE_OMBROTROPHIC);
	}

	/**
	 * Czy oczko istnieje: szansa z wag regionu i brak doliny rzeki w jego środku. Wynik jest
	 * buforowany na oczko, bo te same warunki sprawdza każda kolumna w jego strefie. Istniejące oczko
	 * jest ombrotroficzne ({@link #KETTLE_OMBROTROPHIC}), gdy jego brzeg leży dalej niż 300 m·k od koryta.
	 */
	private byte kettleState(long key, double roll, double kx, double kz, double r) {
		Byte cached = kettleExistence.get(key);
		if (cached != null) {
			return cached;
		}
		Blend cb = blend(kx, kz);
		double chance = KETTLE_MAX_CHANCE * cb.weight(LandscapeType.MORAINE_PLATEAU)
				+ 0.15 * cb.weight(LandscapeType.OUTWASH_PLAIN);
		boolean exists = roll <= chance;
		boolean ombrotrophic = false;
		if (exists) {
			double lowland = cb.weight(LandscapeType.OUTWASH_PLAIN) + cb.weight(LandscapeType.MORAINE_PLATEAU)
					+ cb.weight(LandscapeType.OLD_GLACIAL_PLAIN);
			RiverNetwork.RiverHit river = rivers.query(kx, kz, landElevation(kx, kz), lowland,
					cb.weight(LandscapeType.FOOTHILLS), cb.weight(LandscapeType.BESKIDS));
			exists = river.valleyWeight() < 0.3 && coastDistance(kx, kz) > 500 * local;
			// Torf ombrotroficzny: misa zasilana tylko opadem, z dala od cieków (raport ekologii, §0.1).
			ombrotrophic = river.channelDist() - 1.3 * r > 300 * local;
		}
		byte state = !exists ? KETTLE_NONE : ombrotrophic ? KETTLE_OMBROTROPHIC : KETTLE_MINEROTROPHIC;
		if (kettleExistence.size() > 500_000) {
			kettleExistence.clear();
		}
		kettleExistence.put(key, state);
		return state;
	}

	/**
	 * Poziom lustra jeziora: najniższy punkt gruntu na okręgu wokół niecki minus 1 m, liczony raz
	 * na jezioro i buforowany.
	 */
	private int lakeLevel(long key, double cx, double cz, double radius) {
		Integer cached = lakeLevels.get(key);
		if (cached != null) {
			return cached;
		}
		double min = landElevation(cx, cz);
		for (int i = 0; i < 12; i++) {
			double a = i * (Math.PI * 2 / 12);
			min = Math.min(min, landElevation(cx + radius * Math.cos(a), cz + radius * Math.sin(a)));
		}
		int level = (int) Math.floor(min) - 1;
		if (lakeLevels.size() > 500_000) {
			lakeLevels.clear();
		}
		lakeLevels.put(key, level);
		return level;
	}

	/**
	 * Rzeźbi nieckę jeziora: dno pod lustrem, stok nad lustrem wygaszany do granicy wpływu
	 * i wał brzegowy na poziomie lustro + 1 m tam, gdzie grunt leżałby niżej. Dzięki temu woda
	 * jest zawsze otoczona lądem, a funkcja pozostaje ciągła.
	 */
	private static double applyLake(double surface, LakeHit lake) {
		double s = lake.shoreDistance;
		if (s < 0) {
			double inner = Noise.smoothstep(0, 60, -s);
			double bottom = lake.level - 0.5 - lake.depthBelowLevel * inner;
			return Math.min(surface, bottom);
		}
		double flank = lake.level + 1.0 + lake.slope * Math.max(0, s - 5)
				+ 5_000.0 * Noise.smoothstep(0.7 * lake.bank, lake.bank, s);
		double result = Math.min(surface, flank);
		double raise = 1.0 - Noise.smoothstep(15, 40, s);
		if (result < lake.level + 1 && raise > 0) {
			result = Math.max(result, Noise.lerp(raise, result, lake.level + 1.0));
		}
		return result;
	}
}
