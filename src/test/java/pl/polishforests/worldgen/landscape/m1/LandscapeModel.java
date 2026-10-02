package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
	private final RiverNetwork rivers;

	private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> lakeLevels = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Boolean> kettleExistence = new ConcurrentHashMap<>();

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
		this.rivers = new RiverNetwork(this, root.derive("rivers"), scale);
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

	// ------------------------------------------------------------------ L0: strefy

	private double mountainRaw(double x, double z) {
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
		if (type == LandscapeType.BESKIDY) {
			// Reguła: Beskidy nigdy nie graniczą bezpośrednio z niziną; pośredniczy pogórze.
			outer:
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && rawRegionType(cx + dx, cz + dz).isLowland()) {
						type = LandscapeType.POGORZE;
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
			return LandscapeType.BESKIDY;
		}
		if (m >= 0.55) {
			return LandscapeType.POGORZE;
		}
		if (glacialField(px, pz) > 0.0) {
			return regionJitter.unit(cx, cz, 3) < 0.35 ? LandscapeType.SANDR : LandscapeType.WYSOCZYZNA_MORENOWA;
		}
		return regionJitter.unit(cx, cz, 4) < 0.12 ? LandscapeType.SANDR : LandscapeType.ROWNINA_STAROGLACJALNA;
	}

	/** Środek komórki w przestrzeni wyszukiwania (zawirowanej). */
	double regionCenterX(long cx, long cz) {
		return (cx + 0.15 + 0.7 * regionJitter.unit(cx, cz, 1)) * regionSize;
	}

	double regionCenterZ(long cx, long cz) {
		return (cz + 0.15 + 0.7 * regionJitter.unit(cx, cz, 2)) * regionSize;
	}

	/** Komórki wpływające na punkt i ich wagi (suma = 1). */
	public record Blend(Cell[] cells, double[] weights, int count, double[] typeWeights) {
		public LandscapeType dominant() {
			int best = 0;
			for (int i = 1; i < typeWeights.length; i++) {
				if (typeWeights[i] > typeWeights[best]) {
					best = i;
				}
			}
			return LandscapeType.values()[best];
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
		Cell[] near = new Cell[9];
		double[] dist = new double[9];
		double min = Double.MAX_VALUE;
		int i = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++, i++) {
				Cell c = cell(gx + dx, gz + dz);
				near[i] = c;
				double ddx = lx - c.centerX();
				double ddz = lz - c.centerZ();
				dist[i] = Math.sqrt(ddx * ddx + ddz * ddz);
				min = Math.min(min, dist[i]);
			}
		}
		Cell[] used = new Cell[9];
		double[] w = new double[9];
		double[] tw = new double[LandscapeType.values().length];
		int n = 0;
		double total = 0;
		for (i = 0; i < 9; i++) {
			double d = (dist[i] - min) / tau;
			if (d < 9.0) {
				double wi = Math.exp(-d);
				used[n] = near[i];
				w[n] = wi;
				total += wi;
				n++;
			}
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
		return switch (c.type()) {
			case SANDR -> lowlandBaseline(x, z) + sandrRelief(x, z);
			case WYSOCZYZNA_MORENOWA -> lowlandBaseline(x, z) + 22.0 + moraineRelief(x, z);
			case ROWNINA_STAROGLACJALNA -> lowlandBaseline(x, z) - 8.0 + 6.0 * relief.fbm(x, z, 3_000 * local, 3, 0.5)
					+ 2.0 * relief.fbm(x, z, 500 * local, 2, 0.5);
			case POGORZE -> foothillElevation(c, x, z);
			case BESKIDY -> mountainElevation(c, x, z);
			case POBRZEZE, MORZE -> lowlandBaseline(x, z);
		};
	}

	/** Sandr: łagodne nachylenie z wielkoskalowego szumu, drobne falowanie i pola wydm. */
	private double sandrRelief(double x, double z) {
		double tilt = 12.0 * relief.at(x, z, 25_000 * meso);
		double ripple = 1.5 * relief.fbm(x + 311, z - 97, 400 * local, 2, 0.5);
		return tilt + ripple + duneHeight(x, z);
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
	private double moraineRelief(double x, double z) {
		double hills = 11.0 * relief.fbm(x, z, 1_100 * local, 4, 0.55);
		return hills + moraineRidge(x, z);
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
	 * @param spacing rozstaw dolin podłużnych w metrach
	 */
	private double flyschRelief(Cell c, double x, double z, double spacing) {
		double[] p = flyschParts(c, x, z, spacing);
		return p[0] * (0.45 + 0.55 * p[1]) * (0.8 + 0.2 * p[2]) * p[3];
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
		return (type == LandscapeType.BESKIDY ? 5_200 : 3_200) * mspace;
	}

	/** Pogórze: garby wydłużone wzdłuż pasma, 300–600 m n.p.m., deniwelacje 100–250 m. */
	private double foothillElevation(Cell c, double x, double z) {
		double m = mountainField(x, z);
		double floor = 270.0 + 90.0 * Noise.smoothstep(0.45, 0.85, m);
		double relief = 170.0 + 90.0 * Noise.smoothstep(0.5, 0.85, m);
		double rough = 12.0 * relief(x, z, 700 * local);
		return floor + relief * flyschRelief(c, x, z, 3_200 * mspace) + rough;
	}

	/** Beskidy: 500–1725 m n.p.m., deniwelacje 400–900 m, stoki 15–30°. */
	private double mountainElevation(Cell c, double x, double z) {
		double m = mountainField(x, z);
		double axis = Noise.smoothstep(0.82, 1.0, m);
		double floor = 480.0 + 180.0 * axis;
		double relief = 520.0 + 380.0 * axis;
		// Pojedyncze wyższe masywy typu Babiej Góry lub Pilska.
		double massif = Noise.smoothstep(0.35, 0.8, mountain.at(x - 55_555, z + 22_222, 30_000 * meso));
		relief += 350.0 * massif;
		double rough = 25.0 * relief(x, z, 900 * local);
		double h = floor + relief * flyschRelief(c, x, z, 5_200 * mspace) + rough;
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

	/** Wysokość gruntu po zmieszaniu komórek i ukształtowaniu wybrzeża, jeszcze bez rzek i jezior. */
	public double landElevation(double x, double z) {
		return shapeCoast(elevation(blend(x, z), x, z), coastDistance(x, z), x, z);
	}

	/** Pełna próbka kolumny: rzeźba, wody, podłoże. */
	public ColumnSample sample(double x, double z) {
		Blend b = blend(x, z);
		double raw = elevation(b, x, z);
		double coastD = coastDistance(x, z);
		double surface = shapeCoast(raw, coastD, x, z);
		LandscapeType dominant = b.dominant();
		double lowland = b.weight(LandscapeType.SANDR) + b.weight(LandscapeType.WYSOCZYZNA_MORENOWA)
				+ b.weight(LandscapeType.ROWNINA_STAROGLACJALNA);
		double young = b.weight(LandscapeType.SANDR) + b.weight(LandscapeType.WYSOCZYZNA_MORENOWA);
		double foothills = b.weight(LandscapeType.POGORZE);
		double mountains = b.weight(LandscapeType.BESKIDY);

		Substrate substrate = dominant.defaultSubstrate();
		int water = ColumnSample.NO_WATER;
		WaterKind kind = WaterKind.NONE;

		// Morze i zalewy (na poziomie morza).
		if (coastD < 0 || surface < 0) {
			LandscapeType t = coastD < 0 ? LandscapeType.MORZE : LandscapeType.POBRZEZE;
			Substrate sub = coastD < 0 && -coastD > 3_000 * meso ? Substrate.LAKE_MUD : Substrate.SAND;
			return new ColumnSample(surface, 0, WaterKind.SEA, t, coastD < 0 ? sub : Substrate.LAKE_MUD, 30.0);
		}
		if (coastD < beachWidth() + 400 * local) {
			dominant = LandscapeType.POBRZEZE;
			// Plaża i biała wydma bez darni; dalej od morza wydma szara, porośnięta.
			double bare = beachWidth() + 220 * local * (0.6 + 0.4 * coast.at(x, z, 400 * local));
			substrate = surface > 8 ? Substrate.GLACIAL_TILL : coastD < bare ? Substrate.BEACH_SAND : Substrate.SAND;
		}

		// Doliny i koryta sieci rzecznej oraz jeziora bezodpływowe.
		double valley = 0;
		RiverNetwork.RiverHit r = rivers.query(x, z, surface, lowland + b.weight(LandscapeType.POBRZEZE), foothills,
				mountains);
		surface = r.terrain();
		boolean inSinkLake = r.lakeLevel() != ColumnSample.NO_WATER && r.lakeShore() < 0;
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
			LakeHit sinkLake = new LakeHit(r.lakeLevel(), r.lakeDepth(), r.lakeShore(), false, 0.25, KETTLE_BANK);
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
				surface = applyLake(surface, lake);
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
		return new ColumnSample(surface, water, kind, dominant, substrate, cover);
	}

	// ------------------------------------------------------------------ opis form terenu

	/** Próbka kolumny i formy terenu rozpoznane w punkcie. */
	public record Description(ColumnSample sample, Set<Landform> forms) {
	}

	/**
	 * Opis terenu w punkcie: próbka kolumny i rozpoznane formy. Kosztuje kilka razy więcej niż
	 * {@link #sample}, więc służy komendom i narzędziom, a nie generacji.
	 */
	public Description describe(double x, double z) {
		ColumnSample s = sample(x, z);
		Set<Landform> f = EnumSet.noneOf(Landform.class);
		Blend b = blend(x, z);
		LandscapeType type = s.type();

		double coastD = coastDistance(x, z);
		RiverNetwork.RiverHit r = rivers.query(x, z, landElevation(x, z), b.weight(LandscapeType.SANDR)
				+ b.weight(LandscapeType.WYSOCZYZNA_MORENOWA) + b.weight(LandscapeType.ROWNINA_STAROGLACJALNA),
				b.weight(LandscapeType.POGORZE), b.weight(LandscapeType.BESKIDY));
		switch (s.waterKind()) {
			case RIVER -> {
				f.add(r.order() == 1 ? Landform.POTOK : Landform.RZEKA);
				if (coastD < 3_000 * meso) {
					f.add(Landform.UJSCIE);
				}
			}
			case LAKE -> f.add(Landform.JEZIORO_RYNNOWE);
			case KETTLE -> f.add(Landform.OCZKO_WODNE);
			case OXBOW -> f.add(Landform.STARORZECZE);
			case SEA -> {
				if (coastD >= 0) {
					f.add(Landform.ZALEW);
				}
			}
			default -> {
			}
		}
		if (r.order() > 0 && r.source() && (r.inChannel() || r.inFloor())) {
			f.add(Landform.ZRODLO);
		}
		if (type == LandscapeType.POBRZEZE && coastD >= 0 && !s.hasWater()) {
			// Formy brzegu morskiego tylko w pasie, w którym powstają (plaża, wydma przednia, ściana
			// klifu), a nie np. na brzegu zalewu kilka kilometrów od morza.
			double beach = beachWidth();
			double raw = landElevation(x, z);
			if (s.surface() > 8 && raw > 8 && coastD < beach + raw / 2.5 + 20 * local) {
				f.add(Landform.KLIF);
			} else if (s.surface() < 3 && coastD < 1.3 * beach) {
				f.add(Landform.PLAZA);
			} else if (s.surface() >= 3 && coastD >= 0.8 * beach && coastD < beach + 220 * local) {
				f.add(Landform.WYDMY_NADMORSKIE);
			}
		}
		if (s.substrate() == Substrate.PEAT) {
			f.add(Landform.OCZKO_TORFOWE);
		}
		if (s.substrate() == Substrate.ALLUVIUM) {
			f.add(Landform.DNO_DOLINY);
		}

		if (r.order() > 0 && !s.hasWater() && !r.inFloor() && s.surface() < landElevation(x, z) - 2) {
			f.add(Landform.ZBOCZE_DOLINY);
		}
		double young = b.weight(LandscapeType.SANDR) + b.weight(LandscapeType.WYSOCZYZNA_MORENOWA);
		if (young > 0.05) {
			double[] t = tunnelChannel(x, z, young);
			if (t != null && t[1] < t[2]) {
				f.add(Landform.RYNNA);
			}
		}
		if (type == LandscapeType.SANDR && duneHeight(x, z) > 4) {
			f.add(Landform.WYDMY);
		}
		if (type == LandscapeType.WYSOCZYZNA_MORENOWA && moraineRidge(x, z) > 25) {
			f.add(Landform.WAL_MORENOWY);
		}

		if (type == LandscapeType.POGORZE || type == LandscapeType.BESKIDY) {
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
			if (p[0] > 0.9) {
				f.add(Landform.GRZBIET);
			}
			if (p[0] < 0.15) {
				f.add(Landform.DOLINA_GORSKA);
			}
			if (p[0] > 0.8 && p[1] < 0.3) {
				f.add(Landform.PRZELECZ);
			}
			if (p[0] > 0.75 && isLocalMaximum(x, z, s.surface(), 0.06 * spacing)) {
				f.add(Landform.SZCZYT);
			}
			if (type == LandscapeType.BESKIDY) {
				f.add(s.surface() >= 1_150 ? Landform.REGIEL_GORNY : Landform.REGIEL_DOLNY);
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
	 */
	private record LakeHit(int level, double depthBelowLevel, double shoreDistance, boolean peat, double slope,
			double bank) {
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
		if (half < 25 * local || dist > half + tunnelBank) {
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
		if (shore > tunnelBank) {
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
		return new LakeHit(level, depth, shore, false, 0.35, tunnelBank);
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
		if (!kettleExists(key, roll, kx, kz)) {
			return null;
		}
		int level = lakeLevel(key, kx, kz, r * 1.3 + 25);
		double depth = 2 + 8 * kettle.unit(cx, cz, 5);
		boolean peat = kettle.unit(cx, cz, 6) < 0.4;
		return new LakeHit(level, depth, shore, peat, 0.25, KETTLE_BANK);
	}

	/**
	 * Czy oczko istnieje: szansa z wag regionu i brak doliny rzeki w jego środku. Wynik jest
	 * buforowany na oczko, bo te same warunki sprawdza każda kolumna w jego strefie.
	 */
	private boolean kettleExists(long key, double roll, double kx, double kz) {
		Boolean cached = kettleExistence.get(key);
		if (cached != null) {
			return cached;
		}
		Blend cb = blend(kx, kz);
		double chance = KETTLE_MAX_CHANCE * cb.weight(LandscapeType.WYSOCZYZNA_MORENOWA)
				+ 0.15 * cb.weight(LandscapeType.SANDR);
		boolean exists = roll <= chance;
		if (exists) {
			double lowland = cb.weight(LandscapeType.SANDR) + cb.weight(LandscapeType.WYSOCZYZNA_MORENOWA)
					+ cb.weight(LandscapeType.ROWNINA_STAROGLACJALNA);
			RiverNetwork.RiverHit river = rivers.query(kx, kz, landElevation(kx, kz), lowland,
					cb.weight(LandscapeType.POGORZE), cb.weight(LandscapeType.BESKIDY));
			exists = river.valleyWeight() < 0.3 && coastDistance(kx, kz) > 500 * local;
		}
		if (kettleExistence.size() > 500_000) {
			kettleExistence.clear();
		}
		kettleExistence.put(key, exists);
		return exists;
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
