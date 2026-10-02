package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sieć rzeczna świata "Polska" (etap "Rzeki, doliny i morze").
 *
 * <p>Trzy rzędy cieków, każdy na własnej siatce węzłów z przesunięciami: 1 – potoki (głównie w górach
 * i na pogórzu), 2 – rzeki, 3 – wielkie rzeki. Z każdego węzła woda spływa do najniższego z ośmiu
 * sąsiadów (według wygładzonej wysokości terenu). Gdy żaden sąsiad nie jest niżej, szukamy niższego
 * węzła w promieniu kilku oczek (przełom); dopiero gdy go nie ma, powstaje jezioro bezodpływowe.
 * Cieki kończą się w morzu, w jeziorze albo w cieku wyższego rzędu.
 *
 * <p>Wszystko jest liczone lokalnie i deterministycznie, z buforowaniem wyników na węzeł:
 * <ul>
 * <li>poziom lustra wody liczony od ujścia w górę, zawsze malejący z biegiem cieku, z limitem
 * spadku zależnym od rzędu (kaskady w górach, spokojne lustro na nizinach);</li>
 * <li>szerokość koryta rośnie z pierwiastkiem liczby węzłów zlewni;</li>
 * <li>przebieg to krzywa Hermite'a przez węzły z łagodnym zakolem i nieregularnymi meandrami z szumu,
 * których amplituda zależy od spadku;</li>
 * <li>dolina to płynne przejście od dna do oryginalnej rzeźby, ze zboczami o ograniczonym nachyleniu;
 * przy źródle dolina narasta stopniowo, więc nie powstaje klif.</li>
 * </ul>
 */
final class RiverNetwork {
	private static final int SINK = 0;
	private static final int NODE = 1;
	private static final int CAPTURE = 2;
	private static final int SEA = 3;

	private final LandscapeModel model;
	private final Noise noise;
	/** Rozstaw węzłów dla rzędów 1–3 (indeks = rząd). */
	private final double[] spacing = new double[4];
	private static final double[] BASE_SPACING = {1, 1_250, 5_000, 20_000};
	private final double chan;
	private final double wallScale;
	private final double valleyScale;
	private final double meso;
	private final double tileSize;

	private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Link> links = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> areas = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Double> levels = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Segment> segments = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, SinkLake> sinkLakes = new ConcurrentHashMap<>();
	private final ThreadLocal<TileCache> tileCache = ThreadLocal.withInitial(TileCache::new);

	/** Węzeł siatki: położenie, wysokość wygładzona do spływu, wysokość gruntu, udziały pasów. */
	record Node(int order, long i, long j, double x, double z, double route, double land, boolean sea,
			double mountains, double foothills) {
	}

	/** Dokąd spływa węzeł. Dla {@code CAPTURE} cel to punkt na cieku wyższego rzędu. */
	record Link(int kind, long di, long dj, double tx, double tz, Segment target, double targetT) {
	}

	/** Jezioro w obniżeniu bezodpływowym. */
	record SinkLake(double x, double z, double radius, int level, double depth, long seed) {
	}

	/** Odcinek cieku. */
	static final class Segment {
		final int order;
		final double x0;
		final double z0;
		final double x1;
		final double z1;
		final double mx0;
		final double mz0;
		final double mx1;
		final double mz1;
		final double len;
		final double level0;
		final double level1;
		final double width0;
		final double width1;
		final double wander1;
		final double wander2;
		/** Największe odchylenie koryta od osi doliny (m). */
		final double amp;
		/** Kąt meandrowania θ0 (rad) krzywej Kinoshity. */
		final double theta;
		final double lambda;
		final double envelope;
		final double phase;
		final double headFade;
		final boolean source;
		final Noise noise;
		double minX;
		double maxX;
		double minZ;
		double maxZ;
		/** Największe odchylenie krzywej od cięciwy i zasięg wpływu (do szybkiego odrzucania). */
		double chordDeviation;
		double reach;

		Segment(int order, double x0, double z0, double x1, double z1, double[] t0, double[] t1, double level0,
				double level1, double width0, double width1, double wander1, double wander2, double theta, double lambda,
				double phase, double headFade, boolean source, Noise noise) {
			this.order = order;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.len = Math.max(1.0, Math.hypot(x1 - x0, z1 - z0));
			this.mx0 = t0[0] * len * 0.8;
			this.mz0 = t0[1] * len * 0.8;
			this.mx1 = t1[0] * len * 0.8;
			this.mz1 = t1[1] * len * 0.8;
			this.level0 = level0;
			this.level1 = level1;
			this.width0 = width0;
			this.width1 = width1;
			this.wander1 = wander1;
			this.wander2 = wander2;
			this.theta = theta;
			this.amp = theta > 0 ? MeanderField.amplitude(Math.min(MeanderField.THETA_MAX, theta * 1.25)) * lambda : 0;
			this.lambda = lambda;
			this.envelope = Math.min(0.5, 1.5 * lambda / len);
			this.phase = phase;
			this.headFade = headFade;
			this.source = source;
			this.noise = noise;
		}

		double px(double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return (2 * t3 - 3 * t2 + 1) * x0 + (t3 - 2 * t2 + t) * mx0 + (-2 * t3 + 3 * t2) * x1 + (t3 - t2) * mx1;
		}

		double pz(double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return (2 * t3 - 3 * t2 + 1) * z0 + (t3 - 2 * t2 + t) * mz0 + (-2 * t3 + 3 * t2) * z1 + (t3 - t2) * mz1;
		}

		double dx(double t) {
			double t2 = t * t;
			return (6 * t2 - 6 * t) * x0 + (3 * t2 - 4 * t + 1) * mx0 + (-6 * t2 + 6 * t) * x1 + (3 * t2 - 2 * t) * mx1;
		}

		double dz(double t) {
			double t2 = t * t;
			return (6 * t2 - 6 * t) * z0 + (3 * t2 - 4 * t + 1) * mz0 + (-6 * t2 + 6 * t) * z1 + (3 * t2 - 2 * t) * mz1;
		}

		double ddx(double t) {
			return (12 * t - 6) * x0 + (6 * t - 4) * mx0 + (-12 * t + 6) * x1 + (6 * t - 2) * mx1;
		}

		double ddz(double t) {
			return (12 * t - 6) * z0 + (6 * t - 4) * mz0 + (-12 * t + 6) * z1 + (6 * t - 2) * mz1;
		}

		/** Kąt meandrowania w miejscu t: zmienny co kilka zakoli, wygaszany na końcach odcinka. */
		double thetaAt(double t) {
			if (theta <= 0) {
				return 0;
			}
			double env = Noise.smoothstep(0, envelope, t) * Noise.smoothstep(0, envelope, 1 - t);
			double u = t * len / lambda;
			double f = 0.75 + 0.5 * noise.sample(u / 3.7 + phase, phase * 0.37 + 11.3);
			return Math.clamp(theta * env * f, 0.0, MeanderField.THETA_MAX);
		}

		/** Położenie wzdłuż doliny w długościach fali meandrów (z powoli zmienną fazą). */
		double meanderU(double t) {
			double u = t * len / lambda;
			return u + phase + 0.6 * noise.sample(u / 5.0 + phase * 0.61, phase * 0.53 - 7.1);
		}

		/** Odległość (m) od koryta punktu w odległości {@code lat} (ze znakiem) od krzywej w miejscu t. */
		double meanderDistance(double t, double lat) {
			double v = (lat - wanderAt(t)) / lambda;
			return MeanderField.distance(meanderU(t), v, thetaAt(t)) * lambda;
		}

		/** Przesunięcie boczne (m) punktu koryta najbliższego osi doliny w miejscu t. */
		double channelOffset(double t) {
			double a = MeanderField.amplitude(thetaAt(t)) * lambda + 1;
			double best = Double.MAX_VALUE;
			double off = 0;
			for (int q = 0; q <= 40; q++) {
				double l = -a + 2 * a * q / 40.0;
				double d = meanderDistance(t, wanderAt(t) + l);
				if (d < best) {
					best = d;
					off = l;
				}
			}
			return wanderAt(t) + off;
		}

		double levelAt(double t) {
			return level0 + (level1 - level0) * t;
		}

		/** Zakola doliny bez meandrów koryta: łagodny łuk i kilka zakoli w skali 1/5 długości odcinka. */
		double wanderAt(double t) {
			double s = wander1 * Math.sin(Math.PI * t) + wander2 * Math.sin(2 * Math.PI * t);
			double env = Noise.smoothstep(0, 0.2, t) * Noise.smoothstep(0, 0.2, 1 - t);
			return s + 0.07 * len * env * noise.sample(t * 5 + phase * 0.13, phase * 0.71 - 3.3);
		}

		/** Odległość punktu od cięciwy odcinka (dolne ograniczenie odległości od krzywej po odjęciu odchylenia). */
		double chordDistance(double x, double z) {
			double vx = x1 - x0;
			double vz = z1 - z0;
			double l2 = vx * vx + vz * vz;
			double t = l2 < 1e-9 ? 0 : Math.clamp(((x - x0) * vx + (z - z0) * vz) / l2, 0.0, 1.0);
			return Math.hypot(x - (x0 + vx * t), z - (z0 + vz * t));
		}

		double widthAt(double t) {
			return width0 + (width1 - width0) * t;
		}

		double maxLateral() {
			return Math.abs(wander1) + Math.abs(wander2) + 0.1 * len + 1.1 * amp;
		}
	}

	/**
	 * Wynik zapytania w kolumnie.
	 *
	 * @param order         rząd cieku, którego dolina dominuje w kolumnie (0 = brak)
	 * @param terrain       teren po wcięciu dolin (nie wyżej niż teren wejściowy)
	 * @param valleyWeight  1 w dnie doliny, maleje na zboczach (do tłumienia jezior i podłoża)
	 * @param inFloor       kolumna w dnie doliny
	 * @param waterLevel    poziom lustra, gdy kolumna leży w korycie; inaczej {@link ColumnSample#NO_WATER}
	 * @param channelBottom dno koryta (gdy w korycie)
	 * @param bankLevel     minimalna wysokość brzegu wymagana przez koryta w pobliżu (lustro + 1 m)
	 * @param source        dominujący ciek ma tu swoją strefę źródłową
	 * @param oxbowLevel    lustro starorzecza (gdy kolumna w starorzeczu)
	 * @param oxbowDepth    głębokość starorzecza
	 * @param lakeLevel     lustro jeziora bezodpływowego w pobliżu
	 * @param lakeShore     odległość od brzegu tego jeziora (ujemna w jeziorze)
	 * @param lakeDepth     głębokość tego jeziora
	 */
	record RiverHit(int order, double terrain, double valleyWeight, boolean inFloor, int waterLevel,
			double channelBottom, double bankLevel, boolean source, int oxbowLevel, double oxbowDepth, int lakeLevel,
			double lakeShore, double lakeDepth) {
		boolean inChannel() {
			return waterLevel != ColumnSample.NO_WATER;
		}
	}

	RiverNetwork(LandscapeModel model, Noise noise, LandscapeScale scale) {
		this.model = model;
		this.noise = noise;
		this.meso = scale.meso();
		this.spacing[3] = 20_000 * scale.meso();
		this.spacing[2] = 5_000 * Math.max(scale.meso(), 0.2);
		this.spacing[1] = 1_250 * scale.mountainSpacing();
		this.chan = scale.channel();
		this.wallScale = scale == LandscapeScale.REALISTIC ? 1.0 : 1.0 / scale.local();
		this.valleyScale = scale.local();
		this.tileSize = 64;
	}

	// ------------------------------------------------------------------ węzły i spływ

	private static long key(int order, long i, long j) {
		return Noise.key(i, j, 100 + order);
	}

	private static <V> void put(ConcurrentHashMap<Long, V> map, long k, V v) {
		if (map.size() > 250_000) {
			map.clear();
		}
		map.put(k, v);
	}

	Node node(int order, long i, long j) {
		long k = key(order, i, j);
		Node n = nodes.get(k);
		if (n != null) {
			return n;
		}
		double a = spacing[order];
		double x = (i + 0.2 + 0.6 * noise.unit(i, j, 10 + order)) * a;
		double z = (j + 0.2 + 0.6 * noise.unit(i, j, 20 + order)) * a;
		double land = model.landElevation(x, z);
		double r = 0.3 * a;
		double route = land;
		for (int q = 0; q < 4; q++) {
			double ang = q * Math.PI / 2 + 0.4;
			route += model.landElevation(x + r * Math.cos(ang), z + r * Math.sin(ang));
		}
		route /= 5;
		boolean sea = model.coastDistance(x, z) < 0;
		double[] w = model.typeWeights(x, z);
		n = new Node(order, i, j, x, z, route, land, sea, w[LandscapeType.BESKIDY.ordinal()],
				w[LandscapeType.POGORZE.ordinal()]);
		put(nodes, k, n);
		return n;
	}

	/** Dokąd spływa węzeł. */
	Link link(Node n) {
		long k = key(n.order, n.i, n.j);
		Link cached = links.get(k);
		if (cached != null) {
			return cached;
		}
		Link result;
		if (n.sea) {
			result = new Link(SEA, n.i, n.j, n.x, n.z, null, 0);
		} else {
			Node best = lowestNeighbor(n, 1);
			if (best == null) {
				// Przełom: niższy węzeł w dalszym pierścieniu.
				for (int ring = 2; ring <= 4 && best == null; ring++) {
					best = lowestNeighbor(n, ring);
				}
			}
			result = null;
			if (n.order < 3) {
				double tx = best != null ? best.x : n.x;
				double tz = best != null ? best.z : n.z;
				result = capture(n, tx, tz);
			}
			if (result == null) {
				if (best == null) {
					result = new Link(SINK, n.i, n.j, n.x, n.z, null, 0);
				} else {
					result = new Link(best.sea ? SEA : NODE, best.i, best.j, best.x, best.z, null, 0);
				}
			}
		}
		put(links, k, result);
		return result;
	}

	/** Najniższy węzeł niższy od {@code n} na pierścieniu o promieniu {@code ring} (w oczkach siatki). */
	private Node lowestNeighbor(Node n, int ring) {
		Node best = null;
		for (int di = -ring; di <= ring; di++) {
			for (int dj = -ring; dj <= ring; dj++) {
				if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				if (m.route < n.route - 1e-6 && (best == null || m.route < best.route)) {
					best = m;
				}
			}
		}
		return best;
	}

	/** Szuka cieku wyższego rzędu na drodze z węzła do celu; zwraca połączenie z jego osią albo null. */
	private Link capture(Node n, double tx, double tz) {
		Segment bestSeg = null;
		double bestT = 0;
		double bestScore = Double.MAX_VALUE;
		double bestPx = 0;
		double bestPz = 0;
		double reachLow = 0.35 * spacing[n.order];
		for (int order = n.order + 1; order <= 3; order++) {
			double a = spacing[order];
			long gi = (long) Math.floor(n.x / a);
			long gj = (long) Math.floor(n.z / a);
			for (long i = gi - 2; i <= gi + 2; i++) {
				for (long j = gj - 2; j <= gj + 2; j++) {
					Segment s = segment(order, i, j);
					if (s == null) {
						continue;
					}
					double lx = Math.min(n.x, tx) - reachLow;
					double hx = Math.max(n.x, tx) + reachLow;
					double lz = Math.min(n.z, tz) - reachLow;
					double hz = Math.max(n.z, tz) + reachLow;
					if (hx < s.minX || lx > s.maxX || hz < s.minZ || lz > s.maxZ) {
						continue;
					}
					for (int q = 0; q <= 6; q++) {
						double f = q / 6.0;
						double px = n.x + (tx - n.x) * f;
						double pz = n.z + (tz - n.z) * f;
						double[] pr = project(s, px, pz);
						double d = Math.abs(pr[1] - s.wanderAt(pr[0]));
						if (d < reachLow + 0.5 * s.widthAt(pr[0])) {
							double score = d + f * spacing[n.order];
							if (score < bestScore) {
								bestScore = score;
								bestSeg = s;
								// Ujście tam, gdzie koryto przecina oś doliny.
								bestT = axisCrossing(s, pr[0]);
								bestPx = channelX(s, bestT);
								bestPz = channelZ(s, bestT);
							}
						}
					}
				}
			}
		}
		return bestSeg == null ? null : new Link(CAPTURE, 0, 0, bestPx, bestPz, bestSeg, bestT);
	}

	/** Czy w węźle bije źródło: potoki głównie w górach i na pogórzu, rzeki i wielkie rzeki wszędzie. */
	private boolean isSpring(Node n) {
		if (n.sea) {
			return false;
		}
		if (n.order > 1) {
			return true;
		}
		double chance = 0.06 + 0.94 * Math.min(1.0, n.mountains + n.foothills);
		return noise.unit(n.i, n.j, 31) < chance;
	}

	/** Liczba źródeł w zlewni tego samego rzędu spływających przez węzeł (bez przełomów). */
	int area(Node n) {
		long k = key(n.order, n.i, n.j);
		Integer cached = areas.get(k);
		if (cached != null) {
			return cached;
		}
		int a = isSpring(n) ? 1 : 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				Link l = link(m);
				if (l.kind == NODE && l.di == n.i && l.dj == n.j) {
					a += area(m);
				}
			}
		}
		put(areas, k, a);
		return a;
	}

	/** Jezioro bezodpływowe węzła (albo null, jeśli węzeł ma odpływ). */
	SinkLake sinkLake(Node n) {
		if (link(n).kind != SINK) {
			return null;
		}
		long k = key(n.order, n.i, n.j);
		SinkLake cached = sinkLakes.get(k);
		if (cached != null) {
			return cached;
		}
		double a = spacing[n.order];
		double radius = Math.clamp(0.12 * a * Math.sqrt(Math.max(1, area(n))), 25 * valleyScale, 0.4 * a);
		double min = model.landElevation(n.x, n.z);
		for (int q = 0; q < 16; q++) {
			double ang = q * Math.PI / 8;
			min = Math.min(min, model.landElevation(n.x + 1.3 * radius * Math.cos(ang), n.z + 1.3 * radius * Math.sin(ang)));
		}
		int level = (int) Math.floor(min) - 1;
		double depth = 2 + 8 * noise.unit(n.i, n.j, 81 + n.order);
		SinkLake lake = new SinkLake(n.x, n.z, radius, level, depth, Noise.key(n.i, n.j, 90 + n.order));
		put(sinkLakes, k, lake);
		return lake;
	}

	/** Poziom lustra w węźle, liczony od ujścia w górę. */
	double level(Node n) {
		long k = key(n.order, n.i, n.j);
		Double cached = levels.get(k);
		if (cached != null) {
			return cached;
		}
		Link l = link(n);
		double incision = switch (n.order) {
			case 3 -> 8.0;
			case 2 -> 4.0;
			default -> 2.0;
		};
		double maxSlope = switch (n.order) {
			case 3 -> 0.004;
			case 2 -> 0.03;
			default -> 0.15;
		};
		double lvl;
		if (l.kind == SINK) {
			lvl = sinkLake(n).level();
		} else if (l.kind == SEA && n.sea) {
			lvl = 0.0;
		} else {
			double down;
			if (l.kind == SEA) {
				down = 0.0;
			} else if (l.kind == CAPTURE) {
				down = l.target.levelAt(l.targetT);
			} else {
				down = level(node(n.order, l.di, l.dj));
			}
			double len = Math.hypot(l.tx - n.x, l.tz - n.z);
			// Najniższy teren na drodze do celu: ciek nie może płynąć ponad obniżeniem (np. zalewem).
			double pathLand = n.land;
			for (int q = 1; q <= 7; q++) {
				double f = q / 8.0;
				pathLand = Math.min(pathLand, model.landElevation(n.x + (l.tx - n.x) * f, n.z + (l.tz - n.z) * f));
			}
			lvl = Math.max(down + 1e-4 * len + 0.01, Math.min(pathLand - incision, down + maxSlope * len));
		}
		put(levels, k, lvl);
		return lvl;
	}

	double width(Node n) {
		double base = switch (n.order) {
			case 3 -> 12.0;
			case 2 -> 4.0;
			default -> 1.5;
		};
		return Math.clamp(base * Math.sqrt(Math.max(1, area(n))) * chan, 1.5, 400.0);
	}

	/** Czy {@code m} jest głównym (o największej zlewni) dopływem węzła {@code d}. */
	private boolean isMainUpstream(Node m, Node d) {
		int best = -1;
		long bi = 0;
		long bj = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node u = node(d.order, d.i + di, d.j + dj);
				Link ul = link(u);
				if (ul.kind == NODE && ul.di == d.i && ul.dj == d.j) {
					int a = area(u);
					if (a > best) {
						best = a;
						bi = u.i;
						bj = u.j;
					}
				}
			}
		}
		return best >= 0 && bi == m.i && bj == m.j;
	}

	/** Kierunek wypływu z węzła, wygładzony kierunkiem napływu z głównego dopływu. */
	private double[] tangent(Node n) {
		Link l = link(n);
		double ox = l.tx - n.x;
		double oz = l.tz - n.z;
		double ol = Math.hypot(ox, oz);
		if (ol < 1e-9) {
			return new double[] {1, 0};
		}
		ox /= ol;
		oz /= ol;
		Node main = null;
		int mainArea = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				Link ml = link(m);
				if (ml.kind == NODE && ml.di == n.i && ml.dj == n.j) {
					int a = area(m);
					if (a > mainArea) {
						mainArea = a;
						main = m;
					}
				}
			}
		}
		if (main != null) {
			double ix = n.x - main.x;
			double iz = n.z - main.z;
			double il = Math.hypot(ix, iz);
			ox += ix / il;
			oz += iz / il;
			double s = Math.hypot(ox, oz);
			if (s > 1e-9) {
				ox /= s;
				oz /= s;
			}
		}
		return new double[] {ox, oz};
	}

	// ------------------------------------------------------------------ odcinki

	private static final Segment EMPTY = new Segment(0, 0, 0, 1, 0, new double[] {1, 0}, new double[] {1, 0}, 0, 0,
			0, 0, 0, 0, 0, 1, 0, 0, false, null);

	/** Odcinek wypływający z węzła albo null (węzeł morski, bezodpływowy lub bez źródeł w zlewni). */
	Segment segment(int order, long i, long j) {
		long k = key(order, i, j);
		Segment cached = segments.get(k);
		if (cached != null) {
			return cached == EMPTY ? null : cached;
		}
		Node n = node(order, i, j);
		Segment s = null;
		Link l = link(n);
		if (!n.sea && l.kind != SINK && area(n) > 0) {
			s = build(n, l);
		}
		put(segments, k, s == null ? EMPTY : s);
		return s;
	}

	private Segment build(Node n, Link l) {
		double x1 = l.tx;
		double z1 = l.tz;
		double len = Math.max(1.0, Math.hypot(x1 - n.x, z1 - n.z));
		double[] t0 = tangent(n);
		double[] t1 = l.kind == NODE ? tangent(node(n.order, l.di, l.dj))
				: new double[] {(x1 - n.x) / len, (z1 - n.z) / len};
		double level0 = level(n);
		double width0 = width(n);
		double level1;
		double width1;
		if (l.kind == NODE && !isMainUpstream(n, node(n.order, l.di, l.dj))) {
			// Dopływ boczny uchodzi do koryta poniżej węzła, w miejscu zależnym od węzła, zamiast
			// wpadać w sam węzeł (bez "gwiazd" zbiegających się rzek).
			Node d = node(n.order, l.di, l.dj);
			Segment down = segment(n.order, d.i, d.j);
			if (down != null) {
				double tj = axisCrossing(down, 0.1 + 0.3 * noise.unit(n.i, n.j, 91 + n.order));
				x1 = channelX(down, tj);
				z1 = channelZ(down, tj);
				len = Math.max(1.0, Math.hypot(x1 - n.x, z1 - n.z));
				double dl = Math.max(1e-9, Math.hypot(down.dx(tj), down.dz(tj)));
				double ex = (x1 - n.x) / len + down.dx(tj) / dl;
				double ez = (z1 - n.z) / len + down.dz(tj) / dl;
				double el = Math.max(1e-9, Math.hypot(ex, ez));
				t1 = new double[] {ex / el, ez / el};
				level1 = Math.min(level(d), down.levelAt(tj));
			} else {
				level1 = level(d);
			}
			width1 = width0;
		} else if (l.kind == NODE) {
			Node d = node(n.order, l.di, l.dj);
			level1 = level(d);
			width1 = width(d);
		} else if (l.kind == CAPTURE) {
			level1 = l.target.levelAt(l.targetT);
			width1 = width0;
		} else {
			level1 = 0.0;
			width1 = width0 * 1.3;
		}
		double r1 = noise.unit(n.i, n.j, 41 + n.order) * 2 - 1;
		double r2 = noise.unit(n.i, n.j, 51 + n.order) * 2 - 1;
		double wander1 = 0.12 * len * r1;
		double wander2 = 0.05 * len * r2;
		// Meandry: długość fali ok. 11 szerokości koryta; amplituda duża, gdy spadek mały.
		double w = 0.5 * (width0 + width1);
		double slope = Math.max(0, level0 - level1) / len;
		// Spadek przeliczony na skalę rzeczywistą: w skali rozgrywki odległości są skrócone, wysokości nie.
		double sEff = slope * spacing[n.order] / BASE_SPACING[n.order];
		// Krętość: rzeki nizinne o spadku poniżej ok. 0,5 ‰ ok. 1,8–2,2, potoki górskie ok. 1,02.
		double sinuosity = 1.02 + 1.0 * (1 - Noise.smoothstep(0.0002, 0.003, sEff))
				* (0.8 + 0.4 * noise.unit(n.i, n.j, 61 + n.order));
		double theta = MeanderField.thetaForSinuosity(sinuosity);
		// Długość fali meandrów ok. 11 szerokości koryta.
		double lambda = 11 * Math.max(w, 2.0);
		double phase = noise.unit(n.i, n.j, 71 + n.order) * 1_000;
		boolean source = area(n) == 1 && isSpring(n);
		// Długość (m), na której koryto przy źródle narasta od zera do pełnej szerokości.
		double headFade = source ? 400 * valleyScale : 0.0;
		Segment s = new Segment(n.order, n.x, n.z, x1, z1, t0, t1, level0, level1, width0, width1, wander1, wander2,
				theta, lambda, phase, headFade, source, noise);
		// Ramka wpływu z rzeczywistej krzywej, powiększona o meandry, dno doliny i zbocza.
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (int q = 0; q <= 24; q++) {
			double t = q / 24.0;
			minX = Math.min(minX, s.px(t));
			maxX = Math.max(maxX, s.px(t));
			minZ = Math.min(minZ, s.pz(t));
			maxZ = Math.max(maxZ, s.pz(t));
		}
		double dev = 0;
		for (int q = 0; q <= 24; q++) {
			dev = Math.max(dev, s.chordDistance(s.px(q / 24.0), s.pz(q / 24.0)));
		}
		double reach = s.maxLateral() + floorHalfMax(Math.max(width0, width1)) + maxWall() + Math.max(width0, width1);
		s.chordDeviation = dev;
		s.reach = reach;
		s.minX = minX - reach;
		s.maxX = maxX + reach;
		s.minZ = minZ - reach;
		s.maxZ = maxZ + reach;
		return s;
	}

	private double floorHalfMax(double w) {
		return w / 2 + 5 * w + 40 * valleyScale;
	}

	/** Największa szerokość zbocza doliny. */
	private double maxWall() {
		return 1_200 * valleyScale;
	}

	// ------------------------------------------------------------------ geometria

	/** Rzutowanie punktu na krzywą odcinka: {t, odległość boczna ze znakiem}. */
	static double[] project(Segment s, double px, double pz) {
		double bestT = 0;
		double bestD = Double.MAX_VALUE;
		for (int q = 0; q <= 16; q++) {
			double t = q / 16.0;
			double dx = s.px(t) - px;
			double dz = s.pz(t) - pz;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				bestT = t;
			}
		}
		double t = bestT;
		for (int it = 0; it < 5; it++) {
			double ex = s.px(t) - px;
			double ez = s.pz(t) - pz;
			double tx = s.dx(t);
			double tz = s.dz(t);
			double g = ex * tx + ez * tz;
			double h = tx * tx + tz * tz;
			if (h < 1e-12) {
				break;
			}
			t = Math.clamp(t - g / h, 0.0, 1.0);
		}
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.hypot(tx, tz);
		double ex = px - s.px(t);
		double ez = pz - s.pz(t);
		double along = tl < 1e-12 ? 0 : (ex * tx + ez * tz) / tl;
		double lateral = tl < 1e-12 ? Math.hypot(ex, ez) : (tx * ez - tz * ex) / tl;
		// Poza końcami odcinka odległość rośnie także wzdłuż osi.
		if ((t <= 0 && along < 0) || (t >= 1 && along > 0)) {
			lateral = Math.copySign(Math.hypot(lateral, along), lateral == 0 ? 1 : lateral);
		}
		return new double[] {t, lateral};
	}

	/**
	 * Położenie punktu względem cieku: {t, odległość od koryta (z meandrami), odległość od osi
	 * doliny (z zakolami, bez meandrów), t najbliższego ramienia, odległość boczna od krzywej na nim}.
	 * <p>
	 * Najbliższy punkt krzywej przeskakuje między ramionami zakola, gdy punkt leży po wewnętrznej
	 * stronie łuku. Dlatego sprawdzane są wszystkie lokalne minima odległości, a położenie wzdłuż
	 * cieku (od którego zależą poziom wody, szerokość i narastanie doliny) jest ich miękką średnią
	 * – ciągłą także w miejscu przeskoku. Odległości są minimum po ramionach, więc też są ciągłe.
	 */
	static double[] projectChannel(Segment s, double px, double pz) {
		final int n = 16;
		double[] ds = new double[n + 1];
		for (int q = 0; q <= n; q++) {
			double t = (double) q / n;
			double ex = s.px(t) - px;
			double ez = s.pz(t) - pz;
			ds[q] = ex * ex + ez * ez;
		}
		double bestSkel = Double.MAX_VALUE;
		double dChannel = Double.MAX_VALUE;
		double dValley = Double.MAX_VALUE;
		double tNear = 0;
		double latNear = 0;
		double[] bt = new double[4];
		double[] bd = new double[4];
		double[] bf = new double[4];
		int branches = 0;
		for (int q = 0; q <= n; q++) {
			boolean min = (q == 0 || ds[q] <= ds[q - 1]) && (q == n || ds[q] < ds[q + 1]);
			if (!min) {
				continue;
			}
			double t = refine(s, px, pz, (double) q / n, Math.max(0, q - 1.0) / n, Math.min(n, q + 1.0) / n);
			double tx = s.dx(t);
			double tz = s.dz(t);
			double tl = Math.max(1e-12, Math.hypot(tx, tz));
			double ex = px - s.px(t);
			double ez = pz - s.pz(t);
			double along = (ex * tx + ez * tz) / tl;
			double lat = (tx * ez - tz * ex) / tl;
			double skel;
			double ch;
			double va;
			// Wyrazistość minimum: 1 dla prostego odcinka, 0 tam, gdzie minimum znika (środek krzywizny).
			double fold = 1;
			if (t > 0 && t < 1) {
				double g = 1 - (ex * s.ddx(t) + ez * s.ddz(t)) / (tl * tl);
				fold = Noise.smoothstep(0, 0.5, g);
			}
			if ((t <= 0 && along < 0) || (t >= 1 && along > 0)) {
				// Za końcem odcinka (meandry tu wygaszone): odległość od końca osi.
				skel = Math.hypot(lat, along);
				va = Math.hypot(lat - s.wanderAt(t), along);
				ch = va;
			} else {
				skel = Math.abs(lat);
				va = Math.abs(lat - s.wanderAt(t));
				ch = s.meanderDistance(t, lat);
			}
			if (ch < dChannel) {
				dChannel = ch;
				tNear = t;
				latNear = lat;
			}
			dValley = Math.min(dValley, va);
			bestSkel = Math.min(bestSkel, skel);
			if (branches < bt.length) {
				bt[branches] = t;
				bd[branches] = skel;
				bf[branches] = fold;
				branches++;
			}
		}
		double tSoft = bt[0];
		if (branches > 1) {
			double sigma = 10 + 0.3 * bestSkel;
			double sw = 0;
			double st = 0;
			for (int b = 0; b < branches; b++) {
				double wgt = Math.exp(-(bd[b] - bestSkel) / sigma) * (bf[b] + 1e-3);
				sw += wgt;
				st += wgt * bt[b];
			}
			tSoft = st / sw;
		}
		return new double[] {tSoft, dChannel, dValley, tNear, latNear};
	}

	/** Newton na odległości od krzywej, w przedziale [lo, hi]. */
	private static double refine(Segment s, double px, double pz, double t, double lo, double hi) {
		for (int it = 0; it < 5; it++) {
			double ex = s.px(t) - px;
			double ez = s.pz(t) - pz;
			double tx = s.dx(t);
			double tz = s.dz(t);
			double g = ex * tx + ez * tz;
			double h = tx * tx + tz * tz;
			if (h < 1e-12) {
				break;
			}
			t = Math.clamp(t - g / h, lo, hi);
		}
		return t;
	}

	/** Punkt osi doliny w miejscu t (koryto przecina tu oś, gdy t pochodzi z {@link #axisCrossing}). */
	private static double channelX(Segment s, double t) {
		double tl = Math.max(1e-12, Math.hypot(s.dx(t), s.dz(t)));
		return s.px(t) - s.dz(t) / tl * s.wanderAt(t);
	}

	private static double channelZ(Segment s, double t) {
		double tl = Math.max(1e-12, Math.hypot(s.dx(t), s.dz(t)));
		return s.pz(t) + s.dx(t) / tl * s.wanderAt(t);
	}

	/** Miejsce blisko t, w którym koryto przecina oś doliny (tam uchodzi dopływ). */
	private static double axisCrossing(Segment s, double t) {
		if (s.theta <= 0) {
			return t;
		}
		double span = s.lambda / s.len;
		double best = Double.MAX_VALUE;
		double bestT = t;
		for (int q = -16; q <= 16; q++) {
			double tk = Math.clamp(t + span * q / 16.0, 0.02, 0.98);
			double d = s.meanderDistance(tk, s.wanderAt(tk));
			if (d < best) {
				best = d;
				bestT = tk;
			}
		}
		return bestT;
	}

	// ------------------------------------------------------------------ zapytanie w kolumnie

	/** Kandydaci (odcinki i jeziora) dla kafla 64 × 64 m, buforowani na wątek. */
	private static final class TileCache {
		long key = Long.MIN_VALUE;
		RiverNetwork owner;
		final List<Segment> segments = new ArrayList<>();
		final List<SinkLake> lakes = new ArrayList<>();
	}

	private TileCache candidates(double x, double z) {
		long tx = (long) Math.floor(x / tileSize);
		long tz = (long) Math.floor(z / tileSize);
		long k = Noise.key(tx, tz, 7);
		TileCache c = tileCache.get();
		if (c.key == k && c.owner == this) {
			return c;
		}
		c.key = k;
		c.owner = this;
		c.segments.clear();
		c.lakes.clear();
		double lx = tx * tileSize;
		double lz = tz * tileSize;
		double hx = lx + tileSize;
		double hz = lz + tileSize;
		for (int order = 3; order >= 1; order--) {
			double a = spacing[order];
			long gi = (long) Math.floor((lx + tileSize / 2) / a);
			long gj = (long) Math.floor((lz + tileSize / 2) / a);
			// Promień obejmuje najdłuższe odcinki (przełom do 4 oczek) i pełny zasięg doliny.
			int r = Math.min(7, (int) Math.ceil((4.5 * a + maxWall() + 600 * valleyScale) / a) + 1);
			for (long i = gi - r; i <= gi + r; i++) {
				for (long j = gj - r; j <= gj + r; j++) {
					Segment s = segment(order, i, j);
					if (s != null && !(hx < s.minX || lx > s.maxX || hz < s.minZ || lz > s.maxZ)) {
						c.segments.add(s);
					}
					SinkLake lake = sinkLake(node(order, i, j));
					if (lake != null && Math.abs(lake.x - (lx + tileSize / 2)) < lake.radius * 1.6 + tileSize
							&& Math.abs(lake.z - (lz + tileSize / 2)) < lake.radius * 1.6 + tileSize) {
						c.lakes.add(lake);
					}
				}
			}
		}
		return c;
	}

	/**
	 * Wpływ cieków na kolumnę.
	 *
	 * @param terrain   wysokość terenu przed wcięciem dolin
	 * @param lowland   udział nizin w punkcie
	 * @param foothills udział pogórza
	 * @param mountains udział gór
	 */
	RiverHit query(double x, double z, double terrain, double lowland, double foothills, double mountains) {
		TileCache c = candidates(x, z);
		double fpFactor = 5.0 * lowland + 1.5 * foothills + 0.3 * mountains;
		double fpBase = (40.0 * lowland + 10.0 * foothills + 2.0 * mountains) * valleyScale;
		// Największe nachylenie zboczy dolin (tangens), zanim teren wróci do oryginalnej rzeźby.
		double maxSlope = (0.12 * lowland + 0.35 * foothills + 0.7 * mountains) * wallScale;
		maxSlope = Math.max(maxSlope, 0.05);

		double result = terrain;
		double bank = Double.NEGATIVE_INFINITY;
		Segment best = null;
		double bestScore = Double.MAX_VALUE;
		double bestFloorHalf = 0;
		double bestFloorDist = 0;
		double bestT = 0;
		double bestLat = 0;
		double bestFade = 1;
		int water = ColumnSample.NO_WATER;
		double channelBottom = 0;
		// Koryta w pobliżu: {połowa szerokości, odległość, poziom, głębokość}; oceniane po wcięciu dolin.
		int channelCount = 0;
		double[] chHalf = new double[8];
		double[] chDist = new double[8];
		double[] chLevel = new double[8];
		double[] chDepth = new double[8];
		double[] chOwn = new double[8];

		for (Segment s : c.segments) {
			if (x < s.minX || x > s.maxX || z < s.minZ || z > s.maxZ
					|| s.chordDistance(x, z) - s.chordDeviation > s.reach) {
				continue;
			}
			double[] pr = projectChannel(s, x, z);
			double t = pr[0];
			double d = pr[1];
			double w = s.widthAt(t);
			double level = s.levelAt(t);
			double floorHalf = w / 2 + fpFactor * w + fpBase;
			// Dno doliny liczone od osi doliny (bez meandrów), więc zawsze obejmuje koryto; na nizinach
			// obejmuje cały pas meandrów po obu stronach.
			double floorDist = Math.max(0, pr[2] - w / 2 - s.amp * (1 + (lowland > 0.3 ? 1.4 * lowland : 0)));
			double fromSource = t * s.len;
			double fade = s.headFade > 0 ? Noise.smoothstep(0, s.headFade, fromSource) : 1.0;
			// Dno doliny ciągłe (bez stopni lustra), zawsze co najmniej 1,2 m nad wodą.
			double floor = level + 1.2 + 1.0 * (0.5 + 0.5 * noise.at(x, z, 90 * valleyScale));
			// Dolina: dno, a za nim zbocze o ograniczonym nachyleniu, łączące się płynnie z rzeźbą.
			double wall = Math.clamp((terrain - floor) / maxSlope, 20 * valleyScale, maxWall());
			if (s.source) {
				// Głowica doliny: od źródła dno wznosi się ku górze najwyżej z połową nachylenia zboczy,
				// więc dolina zamyka się zaokrąglonym lejem, a nie urwiskiem – niezależnie od długości odcinka.
				floor = Math.max(floor, terrain - 0.5 * maxSlope * fromSource);
			}
			double mask = 1 - Noise.smoothstep(floorHalf, floorHalf + wall, floorDist);
			double own = mask > 0 ? Noise.lerp(mask, terrain, floor) : terrain;
			result = Math.min(result, own);
			double score = floorDist / Math.max(1.0, floorHalf);
			if (score < bestScore) {
				bestScore = score;
				best = s;
				bestFloorHalf = floorHalf;
				bestFloorDist = floorDist;
				bestT = pr[3];
				bestLat = pr[4];
				bestFade = fade;
			}
			// Koryto (oceniane po pętli, gdy znany jest teren po wcięciu wszystkich dolin).
			double half = 0.5 * w * fade;
			if (half > 0.3 && d < half + 12 && channelCount < chHalf.length) {
				chHalf[channelCount] = half;
				chDist[channelCount] = d;
				chLevel[channelCount] = level;
				chDepth[channelCount] = (1.0 + Math.min(7.0, 0.02 * w)) * fade;
				chOwn[channelCount] = own;
				channelCount++;
			}
		}
		// Gdy ciek wchodzi w głębszą dolinę innego cieku, jego lustro schodzi razem z jej zboczem
		// (bystrze), zamiast wisieć nad jej dnem między sztucznie podniesionymi brzegami.
		for (int q = 0; q < channelCount; q++) {
			double level = chLevel[q];
			// Teren (po wcięciu wszystkich dolin) nie wystaje tu bezpiecznie nad lustro tego cieku.
			boolean cascade = result < level + 1.0 && result < chOwn[q] - 0.5;
			if (cascade) {
				// Kaskada wcięta głębiej niż zwykłe koryto, bo zbocze obcej doliny bywa strome.
				level = Math.min(level, Math.max(result - 2.5, 0));
			}
			int lvl = (int) Math.floor(level);
			// Koryto tylko tam, gdzie dolina zeszła już blisko lustra. Wyżej (głowica doliny przy
			// źródle) zostaje sucha dolina, a woda wypływa tam, gdzie dno doliny osiąga poziom lustra.
			if (!cascade && result - lvl > 3.0) {
				continue;
			}
			if (chDist[q] < chHalf[q]) {
				// Dno liczone od zaokrąglonego lustra, więc każda kolumna koryta ma wodę.
				double bottom = lvl - 0.3 - chDepth[q] * Math.sqrt(1 - chDist[q] / chHalf[q]);
				if (water == ColumnSample.NO_WATER || lvl < water) {
					water = lvl;
					channelBottom = bottom;
				}
			} else {
				bank = Math.max(bank, lvl + 1.0);
			}
		}

		// Jeziora bezodpływowe.
		int lakeLevel = ColumnSample.NO_WATER;
		double lakeShore = Double.POSITIVE_INFINITY;
		double lakeDepth = 0;
		for (SinkLake lake : c.lakes) {
			double dist = Math.hypot(x - lake.x, z - lake.z);
			double shore = dist - lake.radius * (1 + 0.2 * noise.at(x, z, Math.max(40, lake.radius * 0.6)));
			if (shore < lakeShore) {
				lakeShore = shore;
				lakeLevel = lake.level;
				lakeDepth = lake.depth;
			}
		}

		if (best == null) {
			return new RiverHit(0, result, 0, false, ColumnSample.NO_WATER, 0, bank, false, ColumnSample.NO_WATER, 0,
					lakeLevel, lakeShore, lakeDepth);
		}
		boolean inFloor = bestFloorDist < bestFloorHalf && bestFade > 0.5;
		double valleyWeight = (1 - Noise.smoothstep(bestFloorHalf, bestFloorHalf + 200 * valleyScale, bestFloorDist))
				* bestFade;
		int oxbowLevel = ColumnSample.NO_WATER;
		double oxbowDepth = 0;
		// Starorzecza tylko na płaskich nizinach i z dala od każdego koryta.
		// Spadek w skali rzeczywistej; starorzecza mają rzeki nizinne o spadku do ok. 1,5 ‰.
		double bestSlope = Math.max(0, best.level0 - best.level1) / best.len * spacing[best.order]
				/ BASE_SPACING[best.order];
		if (inFloor && water == ColumnSample.NO_WATER && bank == Double.NEGATIVE_INFINITY && lowland > 0.6
				&& bestSlope < 0.0015) {
			double[] ox = oxbow(best, bestT, bestLat);
			if (ox != null) {
				oxbowDepth = ox[0];
				oxbowLevel = (int) ox[1];
			}
		}
		return new RiverHit(best.order, result, valleyWeight, inFloor, water, channelBottom, bank,
				best.source && bestT < 0.5, oxbowLevel, oxbowDepth, lakeLevel, lakeShore, lakeDepth);
	}

	/**
	 * Starorzecze: odcięta pętla meandra – półksiężyc za łukiem obecnego koryta, po jego zewnętrznej
	 * stronie. Zwraca {głębokość, lustro} albo null. Lustro jest stałe dla całego starorzecza.
	 */
	private double[] oxbow(Segment s, double t, double lat) {
		double theta = s.thetaAt(t);
		if (theta < 0.8) {
			return null;
		}
		double w = s.widthAt(t);
		double lambda = s.lambda;
		double u = s.meanderU(t);
		double v = (lat - s.wanderAt(t)) / lambda;
		double amp = MeanderField.amplitude(theta);
		// Pół okresu = jeden łuk; łuki leżą na przemian po obu stronach osi doliny.
		long m = (long) Math.floor(u * 2 + 0.5);
		double side = (m & 1) == 1 ? 1 : -1;
		long seed = Noise.key((long) (s.x0 * 7), (long) (s.z0 * 7), 70);
		if (noise.unit(seed, m, 71) > 0.3 || Math.abs(u - m * 0.5) > 0.3) {
			return null;
		}
		double shift = 0.9 * amp + 1.5 * w / lambda + 0.05;
		double vOld = v - side * shift;
		if (vOld * side < 0.35 * amp) {
			return null;
		}
		double d = MeanderField.distance(u, vOld, theta) * lambda;
		double ow = 0.45 * w + 3;
		if (d > ow) {
			return null;
		}
		double depth = (1.0 + 2.0 * noise.unit(seed, m, 73)) * (1 - d / ow);
		// Lustro metr poniżej rzeki przy łuku, więc dno doliny wokół jest zawsze wyżej. Liczone tylko
		// z numeru łuku, więc stałe w całym starorzeczu.
		double tc = Math.clamp((m * 0.5 - s.phase) * lambda / s.len, 0.0, 1.0);
		return new double[] {depth, Math.floor(s.levelAt(tc)) - 1};
	}
}
