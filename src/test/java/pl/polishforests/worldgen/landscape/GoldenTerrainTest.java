package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Złoty test terenu (M2, krok S0). Liczy skrót pól {@link ColumnSample}, które istniały w M1
 * (surface z dokładnością 1e-6 m, waterLevel, waterKind, type, substrate, coverDepth z dokładnością 1e-6 m),
 * oraz Y bloków gruntu i wody w obu odwzorowaniach pionowych, i porównuje go z plikiem wzorcowym
 * {@code src/test/resources/zloty_teren_m1.txt}. Nowe pola dodane do próbki nie wchodzą do skrótu.
 *
 * <p>Zestawy: 2 skale × 2 ziarna przy suwaku regionów 1,0 i jeden zestaw z suwakiem 0,5. W każdym
 * siatka 32 × 32 na obszarze ok. 19 makroregionów i 17 łat 16 × 16 w miejscach trudnych (wybrzeże,
 * zalew, plaża, klif, ujście, rzeki, starorzecze, jeziora, oczko, torfowisko, potok, Beskidy, szczyt
 * z reglem górnym, Pogórze), razem 5376 kolumn. Każda łata ma cel (np. plaża: kolumny BEACH_SAND i forma
 * PLAZA), sprawdzany przy zapisie i przy każdym porównaniu. Środki łat szukamy tylko przy zapisie
 * pliku; przy porównaniu bierzemy je z pliku.
 *
 * <p>Przy różnicy test liczy te same kolumny zamrożoną kopią kodu M1 ({@code landscape.m1}) i wypisuje,
 * o ile różnią się wartości (max |Δsurface|, liczba kolumn z innym enumem). Różnica rzędu 1e-12 m po
 * zmianie kolejności działań różni się wtedy wyraźnie od prawdziwej zmiany terenu.
 *
 * <p>Nowy plik wzorcowy: {@code ./gradlew test --tests '*GoldenTerrainTest*' -PzlotyZapisz}.
 * Wolno go nadpisać tylko przy zamierzonej zmianie terenu, opisanej w dokumentacji.
 */
class GoldenTerrainTest {
	static final String RESOURCE = "/golden_terrain_m1.txt";
	static final long SEED_A = 20260927L;
	static final long SEED_B = -7_316_550_294_015_845_337L;
	/** Zestawy kolumn: 2 skale × 2 ziarna przy suwaku 1,0 i jeden przy suwaku regionów 0,5. */
	static final List<SetKey> SETS = List.of(
			new SetKey(LandscapeScale.REALISTIC, SEED_A, 1.0),
			new SetKey(LandscapeScale.REALISTIC, SEED_B, 1.0),
			new SetKey(LandscapeScale.GAMEPLAY, SEED_A, 1.0),
			new SetKey(LandscapeScale.GAMEPLAY, SEED_B, 1.0),
			new SetKey(LandscapeScale.REALISTIC, SEED_A, 0.5));
	static final String[] FIELDS = {"surface", "waterLevel", "waterKind", "type", "substrate", "coverDepth", "blocks"};
	/** Najmniejsza liczba kolumn w zestawie (wymóg planu M2). */
	static final int MIN_COLUMNS = 4096;

	/** Skala, ziarno i suwak regionów jednego zestawu. */
	record SetKey(LandscapeScale scale, long seed, double regionScale) {
		String key() {
			return scale.id() + " " + seed + " " + regionScale;
		}

		LandscapeModel model() {
			return new LandscapeModel(seed, scale, regionScale);
		}

		pl.polishforests.worldgen.landscape.m1.LandscapeModel modelM1() {
			return new pl.polishforests.worldgen.landscape.m1.LandscapeModel(seed, scale == LandscapeScale.REALISTIC
					? pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC
					: pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, regionScale);
		}
	}

	/** Łata kolumn: n × n punktów co {@code step} m wokół środka (x, z). */
	record Patch(String name, int n, long x, long z, int step) {
		long px(int i) {
			return x + (long) (i - n / 2) * step;
		}

		long pz(int j) {
			return z + (long) (j - n / 2) * step;
		}

		int count() {
			return n * n;
		}
	}

	/** Zestaw z pliku wzorcowego: łaty i ich skróty. */
	record GoldenSet(String key, List<Patch> patches, Map<String, String> hashes) {
	}

	/**
	 * Pola kolumny z M1 niezależnie od wersji {@link ColumnSample} (enumy po nazwie), żeby ten sam skrót
	 * liczył się z obecnego modelu i z zamrożonej kopii M1, także gdy próbka dostanie nowe pola.
	 */
	record Column(double surface, int waterLevel, String waterKind, String type, String substrate, double coverDepth) {
		/**
		 * Name of the M1 {@link LandscapeType} constant hashed in the golden file. The enum got English names,
		 * the terrain did not change, so the hash keeps the M1 names (rename to English, docs/rename).
		 */
		static String m1TypeName(LandscapeType t) {
			return switch (t) {
				case OUTWASH_PLAIN -> "SANDR";
				case MORAINE_PLATEAU -> "WYSOCZYZNA_MORENOWA";
				case OLD_GLACIAL_PLAIN -> "ROWNINA_STAROGLACJALNA";
				case FOOTHILLS -> "POGORZE";
				case BESKIDS -> "BESKIDY";
				case COASTLAND -> "POBRZEZE";
				case SEA -> "MORZE";
			};
		}

		static Column of(ColumnSample s) {
			return new Column(s.surface(), s.waterLevel(), s.waterKind().name(), m1TypeName(s.type()), s.substrate().name(),
					s.coverDepth());
		}

		static Column of(pl.polishforests.worldgen.landscape.m1.ColumnSample s) {
			return new Column(s.surface(), s.waterLevel(), s.waterKind().name(), s.type().name(), s.substrate().name(),
					s.coverDepth());
		}

		/** Jak {@link ColumnSample#hasWater()} w M1. */
		boolean hasWater() {
			return waterLevel != ColumnSample.NO_WATER && waterLevel > (int) Math.floor(surface);
		}

		/** Y bloku gruntu i wody tak jak w {@code PolskaChunkGenerator} (bez przycięcia do ramy wymiaru). */
		int[] blocks() {
			return new int[] {VerticalScale.REAL.topBlockY(surface),
					hasWater() ? VerticalScale.REAL.topBlockY(waterLevel) : Integer.MIN_VALUE,
					VerticalScale.GAMEPLAY.topBlockY(surface),
					hasWater() ? VerticalScale.GAMEPLAY.topBlockY(waterLevel) : Integer.MIN_VALUE};
		}
	}

	@Test
	void terrainMatchesM1() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			write();
			return;
		}
		Map<String, GoldenSet> golden = read();
		assertEquals(SETS.size(), golden.size(), "plik wzorcowy ma złą liczbę zestawów");
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "brak zestawu " + k.key() + " w pliku wzorcowym");
			int points = expected.patches().stream().mapToInt(Patch::count).sum();
			assertTrue(points >= MIN_COLUMNS, "set " + k.key() + " ma tylko " + points + " kolumn");
			LandscapeModel m = k.model();
			List<ColumnSample[]> samples = samples(m, expected.patches());
			List<Column[]> cols = columns(samples);
			Map<String, String> actual = hashes(expected.patches(), cols);
			Set<String> changed = new LinkedHashSet<>();
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				String got = actual.get(e.getKey());
				if (!e.getValue().equals(got)) {
					errors.add(k.key() + " / " + e.getKey() + ": oczekiwano " + e.getValue() + ", jest " + got);
					if (e.getKey().startsWith("patch ")) {
						changed.add(e.getKey().substring("patch ".length()));
					}
				}
			}
			if (!changed.isEmpty()) {
				errors.addAll(diagnose(k, expected, cols, changed));
			}
			// Cele łat i pokrycie po skrótach, żeby duża zmiana terenu najpierw pokazała zmienione łaty.
			errors.addAll(checkTargets(m, k.key(), expected.patches(), samples));
		}
		assertTrue(errors.isEmpty(), "Teren różni się od M1 (" + errors.size() + " uwag):\n" + String.join("\n", errors));
	}

	/**
	 * Zamrożona kopia kodu M1 daje te same skróty co plik wzorcowy. Pilnuje, że kopia jest wiernym
	 * wzorcem dla diagnozy różnic i dla porównań kosztu w {@code SampleKosztTest}.
	 */
	@Test
	void frozenM1CopyMatchesGolden() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			return;
		}
		Map<String, GoldenSet> golden = read();
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "brak zestawu " + k.key() + " w pliku wzorcowym");
			Map<String, String> actual = hashes(expected.patches(), samplesM1(k.modelM1(), expected.patches()));
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				if (!e.getValue().equals(actual.get(e.getKey()))) {
					errors.add(k.key() + " / " + e.getKey());
				}
			}
		}
		assertTrue(errors.isEmpty(), "Kopia M1 (landscape.m1) różni się od pliku wzorcowego: inna JVM lub procesor, "
				+ "zmieniona kopia albo plik wzorcowy wygenerowany z innego terenu:\n" + String.join("\n", errors));
	}

	/** Przy różnicy skrótu: o ile zmienione łaty różnią się od zamrożonej kopii M1. */
	private static List<String> diagnose(SetKey k, GoldenSet expected, List<Column[]> actual, Set<String> changed) {
		List<String> out = new ArrayList<>();
		List<Column[]> ref = samplesM1(k.modelM1(), expected.patches());
		if (!hashes(expected.patches(), ref).equals(expected.hashes())) {
			out.add(k.key() + ": UWAGA, zamrożona kopia M1 też nie zgadza się z plikiem wzorcowym, więc różnica "
					+ "pochodzi z JVM lub procesora (albo z pliku wzorcowego), a nie ze zmiany kodu modelu");
		}
		for (int q = 0; q < expected.patches().size(); q++) {
			Patch p = expected.patches().get(q);
			if (!changed.contains(p.name())) {
				continue;
			}
			double dSurface = 0;
			double dCover = 0;
			int[] diff = new int[5];
			for (int i = 0; i < p.count(); i++) {
				Column a = actual.get(q)[i];
				Column b = ref.get(q)[i];
				dSurface = Math.max(dSurface, Math.abs(a.surface() - b.surface()));
				dCover = Math.max(dCover, Math.abs(a.coverDepth() - b.coverDepth()));
				diff[0] += a.waterLevel() != b.waterLevel() ? 1 : 0;
				diff[1] += a.waterKind().equals(b.waterKind()) ? 0 : 1;
				diff[2] += a.type().equals(b.type()) ? 0 : 1;
				diff[3] += a.substrate().equals(b.substrate()) ? 0 : 1;
				diff[4] += java.util.Arrays.equals(a.blocks(), b.blocks()) ? 0 : 1;
			}
			boolean numeric = dSurface < 1e-6 && dCover < 1e-6 && java.util.Arrays.stream(diff).sum() == 0;
			out.add(String.format(Locale.ROOT, "%s / %s względem kopii M1: max|Δsurface| = %.3g m, max|ΔcoverDepth| = %.3g m,"
					+ " różne kolumny (z %d): waterLevel %d, waterKind %d, type %d, substrate %d, bloki %d%s", k.key(), p.name(),
					dSurface, dCover, p.count(), diff[0], diff[1], diff[2], diff[3], diff[4],
					numeric ? " -> różnica tylko numeryczna (< 1e-6 m), zaokrąglenie skrótu trafiło na granicę" : ""));
		}
		return out;
	}

	/** Cele łat i pokrycie całego zestawu; zwraca listę uwag. */
	static List<String> checkTargets(LandscapeModel m, String key, List<Patch> patches, List<ColumnSample[]> samples) {
		List<String> out = new ArrayList<>();
		Set<String> kinds = new LinkedHashSet<>();
		Set<String> types = new LinkedHashSet<>();
		Set<String> substrates = new LinkedHashSet<>();
		for (int q = 0; q < patches.size(); q++) {
			Patch p = patches.get(q);
			ColumnSample[] a = samples.get(q);
			for (ColumnSample s : a) {
				kinds.add(s.waterKind().name());
				types.add(s.type().name());
				substrates.add(s.substrate().name());
			}
			if (p.name().equals("grid")) {
				continue;
			}
			Site site = site(p.name());
			if (site == null) {
				out.add(key + ": nieznana łata " + p.name());
			} else if (!site.target().test(m, p, a)) {
				out.add(key + " / " + p.name() + ": łata nie zawiera celu: " + site.description());
			}
		}
		for (WaterKind k : WaterKind.values()) {
			if (!kinds.contains(k.name())) {
				out.add(key + ": brak wody " + k + " w zestawie kolumn " + kinds);
			}
		}
		for (LandscapeType t : LandscapeType.values()) {
			if (!types.contains(t.name())) {
				out.add(key + ": brak typu " + t + " w zestawie kolumn " + types);
			}
		}
		for (Substrate s : Substrate.values()) {
			if (!substrates.contains(s.name())) {
				out.add(key + ": brak utworu " + s + " w zestawie kolumn " + substrates);
			}
		}
		return out;
	}

	/**
	 * Próbki łat, wiersz po wierszu. Liczone równolegle jak w generatorze (C2ME), więc test pilnuje
	 * też, że wynik nie zależy od kolejności wywołań ani od stanu pamięci podręcznych modelu.
	 */
	static List<ColumnSample[]> samples(LandscapeModel m, List<Patch> patches) {
		List<ColumnSample[]> out = new ArrayList<>();
		for (Patch p : patches) {
			ColumnSample[] a = new ColumnSample[p.count()];
			IntStream.range(0, a.length).parallel().forEach(k -> a[k] = m.sample(p.px(k % p.n()), p.pz(k / p.n())));
			out.add(a);
		}
		return out;
	}

	/** Te same kolumny policzone zamrożoną kopią kodu M1. */
	static List<Column[]> samplesM1(pl.polishforests.worldgen.landscape.m1.LandscapeModel m, List<Patch> patches) {
		List<Column[]> out = new ArrayList<>();
		for (Patch p : patches) {
			Column[] a = new Column[p.count()];
			IntStream.range(0, a.length).parallel()
					.forEach(k -> a[k] = Column.of(m.sample(p.px(k % p.n()), p.pz(k / p.n()))));
			out.add(a);
		}
		return out;
	}

	static List<Column[]> columns(List<ColumnSample[]> samples) {
		List<Column[]> out = new ArrayList<>();
		for (ColumnSample[] a : samples) {
			Column[] k = new Column[a.length];
			for (int i = 0; i < a.length; i++) {
				k[i] = Column.of(a[i]);
			}
			out.add(k);
		}
		return out;
	}

	/** Skróty łat, pól i całości dla zestawu kolumn; klucze w stałej kolejności. */
	static Map<String, String> hashes(List<Patch> patches, List<Column[]> samples) {
		MessageDigest all = sha();
		MessageDigest[] fields = new MessageDigest[FIELDS.length];
		for (int f = 0; f < fields.length; f++) {
			fields[f] = sha();
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (int q = 0; q < patches.size(); q++) {
			MessageDigest local = sha();
			for (Column s : samples.get(q)) {
				byte[][] parts = fieldBytes(s);
				for (int f = 0; f < parts.length; f++) {
					local.update(parts[f]);
					all.update(parts[f]);
					fields[f].update(parts[f]);
				}
			}
			out.put("patch " + patches.get(q).name(), hex(local));
		}
		for (int f = 0; f < fields.length; f++) {
			out.put("field " + FIELDS[f], hex(fields[f]));
		}
		out.put("total", hex(all));
		return out;
	}

	/** Pola próbki z M1 jako bajty; nazwy enumów zamiast liczb porządkowych, żeby dopisanie wartości nic nie psuło. */
	static byte[][] fieldBytes(Column s) {
		ByteBuffer blocks = ByteBuffer.allocate(16);
		for (int y : s.blocks()) {
			blocks.putInt(y);
		}
		return new byte[][] {
				longBytes(Math.round(s.surface() * 1e6)),
				longBytes(s.waterLevel()),
				(s.waterKind() + ";").getBytes(StandardCharsets.UTF_8),
				(s.type() + ";").getBytes(StandardCharsets.UTF_8),
				(s.substrate() + ";").getBytes(StandardCharsets.UTF_8),
				longBytes(Math.round(s.coverDepth() * 1e6)),
				blocks.array()};
	}

	private static byte[] longBytes(long v) {
		return ByteBuffer.allocate(8).putLong(v).array();
	}

	private static MessageDigest sha() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static String hex(MessageDigest d) {
		return HexFormat.of().formatHex(d.digest());
	}

	// ---------------------------------------------------------------- odczyt i zapis pliku wzorcowego

	private static Map<String, GoldenSet> read() throws IOException {
		Map<String, GoldenSet> sets = new LinkedHashMap<>();
		try (InputStream in = GoldenTerrainTest.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				fail("missing golden file " + RESOURCE + "; create it: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden");
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			GoldenSet current = null;
			String line;
			while ((line = r.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] t = line.split("\\s+");
				switch (t[0]) {
					case "set" -> {
						// zestaw <skala> <ziarno> [<suwak regionów>]
						double regionScale = t.length > 3 ? Double.parseDouble(t[3]) : 1.0;
						current = new GoldenSet(t[1] + " " + Long.parseLong(t[2]) + " " + regionScale, new ArrayList<>(),
								new LinkedHashMap<>());
						sets.put(current.key(), current);
					}
					case "patch" -> {
						// miejsce <nazwa> <n> <x> <z> <krok> <skrót>
						current.patches().add(new Patch(t[1], Integer.parseInt(t[2]), Long.parseLong(t[3]),
								Long.parseLong(t[4]), Integer.parseInt(t[5])));
						current.hashes().put("patch " + t[1], t[6]);
					}
					case "field" -> current.hashes().put("field " + t[1], t[2]);
					case "total" -> current.hashes().put("total", t[1]);
					default -> fail("nieznany wiersz w pliku wzorcowym: " + line);
				}
			}
		}
		return sets;
	}

	private static void write() throws IOException {
		String target = System.getProperty("polishforests.golden.file");
		assertNotNull(target, "missing property polishforests.golden.file (set by build.gradle with -PwriteGolden)");
		StringBuilder sb = new StringBuilder();
		sb.append("# Golden terrain M1 (M2, step S0). SHA-256 hashes of M1 ColumnSample fields: surface (1e-6 m),\n");
		sb.append("# waterLevel, waterKind, type, substrate, coverDepth (1e-6 m) and blocks (Y of ground and water in both\n");
		sb.append("# vertical mappings). Generated by GoldenTerrainTest. Overwrite only for an intended terrain\n");
		sb.append("# change: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden\n");
		sb.append("# set <scale> <seed> <region slider>\n");
		sb.append("# patch <name> <n> <center x> <center z> <step in m> <hash of the n × n patch>\n");
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			List<String> missing = new ArrayList<>();
			List<Patch> patches = choosePatches(k.model(), missing);
			for (String name : missing) {
				errors.add(k.key() + ": nie znaleziono miejsca " + name);
			}
			if (!missing.isEmpty()) {
				continue;
			}
			int points = patches.stream().mapToInt(Patch::count).sum();
			assertTrue(points >= MIN_COLUMNS);
			// Skróty i cele liczy świeży model, tak jak przy porównaniu (bez pamięci podręcznej z wyszukiwania).
			LandscapeModel fresh = k.model();
			List<ColumnSample[]> samples = samples(fresh, patches);
			errors.addAll(checkTargets(fresh, k.key(), patches, samples));
			Map<String, String> h = hashes(patches, columns(samples));
			sb.append('\n').append("set ").append(k.key()).append('\n');
			for (Patch p : patches) {
				sb.append(String.format(Locale.ROOT, "patch %s %d %d %d %d %s%n", p.name(), p.n(), p.x(), p.z(),
						p.step(), h.get("patch " + p.name())));
			}
			for (String f : FIELDS) {
				sb.append("field ").append(f).append(' ').append(h.get("field " + f)).append('\n');
			}
			sb.append("total ").append(h.get("total")).append('\n');
		}
		assertTrue(errors.isEmpty(), "Plik wzorcowy niezapisany:\n" + String.join("\n", errors));
		Path path = Path.of(target);
		Files.createDirectories(path.getParent());
		Files.writeString(path, sb.toString().replace("\r\n", "\n"), StandardCharsets.UTF_8);
		System.out.println("Zapisano plik wzorcowy złotego testu: " + path);
	}

	// ---------------------------------------------------------------- wybór łat

	/** Skąd zaczyna się spiralne szukanie środka łaty (WYBRZEZE: środek łaty „wybrzeze”). */
	enum Start {
		ZERO, COAST, BESKIDS, FOOTHILLS
	}

	/** Wstępny, tani warunek dla punktu spirali. */
	interface Pre {
		boolean test(LandscapeModel m, long x, long z, ColumnSample s);
	}

	/** Cel łaty: co musi w niej być. */
	interface Target {
		boolean test(LandscapeModel m, Patch p, ColumnSample[] a);
	}

	/**
	 * Miejsce łaty. Środka szukamy po spirali od punktu {@code start} z krokiem {@code searchReal} lub
	 * {@code searchGameplay}: kandydatem jest punkt spełniający {@code pre} albo, gdy podano
	 * {@code border}, punkt na granicy tego warunku (bisekcja między sąsiednimi punktami spirali).
	 * Kandydat wygrywa, gdy łata wokół niego spełnia {@code target}. Poza górami pomijamy kandydatów
	 * blisko łat już wybranych, żeby np. plaża nie powtarzała łaty wybrzeża.
	 */
	record Site(String name, int stepReal, int stepGameplay, Start start, double searchReal, double searchGameplay,
			Pre pre, Predicate<ColumnSample> border, String description, Target target) {
	}

	private static final Predicate<ColumnSample> SEA_OPEN = s -> s.waterKind() == WaterKind.SEA
			&& s.type() == LandscapeType.SEA;
	private static final Predicate<ColumnSample> SEA = s -> s.waterKind() == WaterKind.SEA;
	private static final Predicate<ColumnSample> RIVER = s -> s.waterKind() == WaterKind.RIVER;

	static final List<Site> SITES = List.of(
			new Site("coast", 4, 4, Start.ZERO, 2_000, 100, null, SEA_OPEN,
					"otwarte morze (SEA/MORZE) i suchy ląd, po co najmniej 16 kolumn",
					(m, p, a) -> count(a, SEA_OPEN) >= 16 && count(a, s -> !s.hasWater()) >= 16),
			new Site("lagoon", 4, 4, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.COASTLAND, null,
					"zalew (SEA/POBRZEZE)",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.COASTLAND) > 0),
			new Site("beach", 6, 3, Start.ZERO, 1_700, 85, null, SEA,
					"plaża: co najmniej 8 kolumn BEACH_SAND i forma PLAZA",
					(m, p, a) -> count(a, s -> s.substrate() == Substrate.BEACH_SAND) >= 8
							&& hasForm(m, p, a, s -> s.substrate() == Substrate.BEACH_SAND, Landform.BEACH)),
			new Site("cliff", 10, 6, Start.ZERO, 2_300, 115, null, SEA, "klif (forma KLIF)",
					(m, p, a) -> hasForm(m, p, a, s -> s.type() == LandscapeType.COASTLAND && !s.hasWater()
							&& s.surface() > 8, Landform.CLIFF)),
			new Site("river_mouth", 4, 3, Start.COAST, 250, 15,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && m.coastDistance(x, z) < 3_000 * m.scale().meso(),
					null, "ujście rzeki (RIVER z formą UJSCIE)", (m, p, a) -> hasForm(m, p, a, RIVER, Landform.RIVER_MOUTH)),
			new Site("lowland_river", 3, 3, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(), null, "rzeka na nizinie",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland()) > 0),
			new Site("large_river", 8, 3, Start.ZERO, 1_500, 60,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(), null,
					"rzeka rzędu ≥ 2 (forma RZEKA) zajmująca co najmniej 1/8 łaty",
					(m, p, a) -> count(a, RIVER) >= 32 && hasForm(m, p, a, RIVER, Landform.RIVER)),
			new Site("foothills_river", 3, 3, Start.FOOTHILLS, 250, 15,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.FOOTHILLS, null,
					"rzeka rzędu ≥ 2 (forma RZEKA) na Pogórzu",
					(m, p, a) -> hasForm(m, p, a, s -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.FOOTHILLS,
							Landform.RIVER)),
			new Site("oxbow_lake", 3, 3, Start.ZERO, 500, 25, (m, x, z, s) -> s.waterKind() == WaterKind.OXBOW, null,
					"starorzecze (OXBOW)", (m, p, a) -> count(a, s -> s.waterKind() == WaterKind.OXBOW) > 0),
			new Site("tunnel_valley_lake", 6, 6, Start.ZERO, 500, 25, (m, x, z, s) -> s.waterKind() == WaterKind.LAKE, null,
					"jezioro rynnowe (LAKE)", (m, p, a) -> count(a, s -> s.waterKind() == WaterKind.LAKE) > 0),
			new Site("outwash_plain_lake", 6, 6, Start.ZERO, 2_500, 150,
					(m, x, z, s) -> s.type() == LandscapeType.OUTWASH_PLAIN
							&& (s.waterKind() == WaterKind.LAKE || s.waterKind() == WaterKind.KETTLE), null,
					"jezioro lub oczko (LAKE, KETTLE) na sandrze",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.OUTWASH_PLAIN
							&& (s.waterKind() == WaterKind.LAKE || s.waterKind() == WaterKind.KETTLE)) > 0),
			new Site("kettle_pond", 4, 2, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.KETTLE && s.type() == LandscapeType.MORAINE_PLATEAU, null,
					"oczko wytopiskowe (KETTLE) na wysoczyźnie",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.KETTLE) > 0),
			new Site("peatland", 4, 2, Start.ZERO, 250, 15, (m, x, z, s) -> s.substrate() == Substrate.PEAT, null,
					"torf (PEAT, forma OCZKO_TORFOWE)", (m, p, a) -> hasForm(m, p, a, s -> s.substrate() == Substrate.PEAT,
							Landform.KETTLE_BOG)),
			new Site("mountain_stream", 2, 2, Start.BESKIDS, 20, 4,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.BESKIDS, null,
					"potok (forma POTOK) w Beskidach",
					(m, p, a) -> hasForm(m, p, a, s -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.BESKIDS,
							Landform.STREAM)),
			new Site("beskids", 60, 20, Start.BESKIDS, 5_000, 200, (m, x, z, s) -> true, null,
					"wnętrze Beskidów (co najmniej 200 kolumn BESKIDY)",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.BESKIDS) >= 200),
			// W skali rozgrywki Beskidy rzadko sięgają 1150 m (ziarno 20260927: najwyżej ok. 1015 m w promieniu
			// 8 km od wnętrza), więc tam łata wymaga tylko szczytu powyżej 900 m.
			new Site("summit", 20, 8, Start.BESKIDS, 200, 10,
					(m, x, z, s) -> s.type() == LandscapeType.BESKIDS && s.surface() >= highPeak(m), null,
					"szczyt (forma SZCZYT) powyżej 900 m, w skali realistycznej powyżej 1150 m z formą REGIEL_GORNY",
					(m, p, a) -> hasForm(m, p, a, s -> s.type() == LandscapeType.BESKIDS && s.surface() >= highPeak(m),
							Landform.SUMMIT)
							&& (m.scale() != LandscapeScale.REALISTIC || hasForm(m, p, a, s -> s.type() == LandscapeType.BESKIDS
									&& s.surface() >= 1_150, Landform.UPPER_MONTANE))),
			new Site("foothills", 40, 12, Start.FOOTHILLS, 5_000, 200, (m, x, z, s) -> true, null,
					"wnętrze Pogórza (co najmniej 200 kolumn POGORZE)",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.FOOTHILLS) >= 200));

	static Site site(String name) {
		for (Site s : SITES) {
			if (s.name().equals(name)) {
				return s;
			}
		}
		return null;
	}

	/** Próg wysokości łaty „szczyt”: regiel górny w skali realistycznej, 900 m w skali rozgrywki. */
	static double highPeak(LandscapeModel m) {
		return m.scale() == LandscapeScale.REALISTIC ? 1_150 : 900;
	}

	static int count(ColumnSample[] a, Predicate<ColumnSample> test) {
		int n = 0;
		for (ColumnSample s : a) {
			n += test.test(s) ? 1 : 0;
		}
		return n;
	}

	/** Czy w którejś kolumnie spełniającej {@code test} opis terenu zawiera formę {@code form}. */
	static boolean hasForm(LandscapeModel m, Patch p, ColumnSample[] a, Predicate<ColumnSample> test, Landform form) {
		for (int k = 0; k < a.length; k++) {
			if (test.test(a[k]) && m.describe(p.px(k % p.n()), p.pz(k / p.n())).forms().contains(form)) {
				return true;
			}
		}
		return false;
	}

	/** Siatka 32 × 32 i łaty 16 × 16 z {@link #SITES}; nazwy nieznalezionych miejsc trafiają do {@code missing}. */
	static List<Patch> choosePatches(LandscapeModel m, List<String> missing) {
		List<Patch> out = new ArrayList<>();
		int gridStep = (int) Math.round(m.regionSize() * 0.6);
		out.add(new Patch("grid", 32, 0, 0, gridStep));
		Map<Start, double[]> starts = new LinkedHashMap<>();
		starts.put(Start.ZERO, new double[] {0, 0});
		starts.put(Start.BESKIDS, LandscapePreview.findInterior(m, LandscapeType.BESKIDS));
		starts.put(Start.FOOTHILLS, LandscapePreview.findInterior(m, LandscapeType.FOOTHILLS));
		for (Site site : SITES) {
			double[] start = starts.get(site.start());
			long t0 = System.nanoTime();
			Patch p = start == null ? null : search(m, site, start, out);
			String time = String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9);
			if (p == null) {
				System.out.println("Złoty test: nie znaleziono miejsca " + site.name() + " (" + time + ")");
				missing.add(site.name());
			} else {
				System.out.println("Złoty test: " + site.name() + " x=" + p.x() + " z=" + p.z() + " (" + time + ")");
				out.add(p);
				if (site.start() == Start.ZERO && site.name().equals("coast")) {
					starts.put(Start.COAST, new double[] {p.x(), p.z()});
				}
			}
		}
		return out;
	}

	/** Spiralne szukanie środka łaty (zob. {@link Site}). */
	static Patch search(LandscapeModel m, Site site, double[] start, List<Patch> chosen) {
		boolean real = m.scale() == LandscapeScale.REALISTIC;
		double avoid = real ? 1_000 : 100;
		double step = real ? site.searchReal() : site.searchGameplay();
		int patchStep = real ? site.stepReal() : site.stepGameplay();
		for (int r = 0; r < 800; r++) {
			int n = Math.max(12, r * 2);
			long px = 0;
			long pz = 0;
			boolean prev = false;
			for (int k = 0; k < n; k++) {
				double a = k * (2 * Math.PI / n) + r * 0.37;
				long x = Math.round(start[0] + Math.cos(a) * r * step);
				long z = Math.round(start[1] + Math.sin(a) * r * step);
				ColumnSample s = m.sample(x, z);
				long[] candidate = null;
				if (site.border() != null) {
					boolean in = site.border().test(s);
					if (k > 0 && in != prev) {
						candidate = in ? bisect(m, site.border(), x, z, px, pz) : bisect(m, site.border(), px, pz, x, z);
					}
					prev = in;
				} else if (site.pre().test(m, x, z, s)) {
					candidate = new long[] {x, z};
				}
				px = x;
				pz = z;
				if (candidate != null && (site.start() == Start.ZERO || site.start() == Start.COAST)
						&& near(chosen, candidate, avoid)) {
					candidate = null;
				}
				if (candidate != null) {
					Patch p = new Patch(site.name(), 16, candidate[0], candidate[1], patchStep);
					if (site.target().test(m, p, samples(m, List.of(p)).get(0))) {
						return p;
					}
				}
			}
		}
		return null;
	}

	private static boolean near(List<Patch> chosen, long[] c, double distance) {
		for (Patch p : chosen) {
			if (!p.name().equals("grid") && Math.hypot(p.x() - c[0], p.z() - c[1]) < distance) {
				return true;
			}
		}
		return false;
	}

	/** Punkt na granicy warunku między (ix, iz), gdzie warunek zachodzi, a (ox, oz), gdzie nie zachodzi. */
	private static long[] bisect(LandscapeModel m, Predicate<ColumnSample> test, long ix, long iz, long ox, long oz) {
		double ax = ix;
		double az = iz;
		double bx = ox;
		double bz = oz;
		while (Math.hypot(bx - ax, bz - az) > 1) {
			double mx = (ax + bx) / 2;
			double mz = (az + bz) / 2;
			if (test.test(m.sample(Math.round(mx), Math.round(mz)))) {
				ax = mx;
				az = mz;
			} else {
				bx = mx;
				bz = mz;
			}
		}
		return new long[] {Math.round(ax), Math.round(az)};
	}
}
