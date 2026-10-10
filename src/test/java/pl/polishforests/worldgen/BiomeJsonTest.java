package pl.polishforests.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The biome files from datagen (step S5, docs/03-m2-biomy.md §2, §6, §10, §12.1): exactly 36 biomes, one per
 * {@link HabitatBiome}, with the base temperature at most 0.8 (the Serene Seasons gate) and the explicit colors and
 * climate of the enum, no carvers, names in {@code en_us} and {@code pl_pl}, and presence in {@code #is_overworld},
 * {@code #has_structure/trial_chambers} and {@code #polishforests:polish_climate}. Reads {@code src/main/generated}.
 */
public class BiomeJsonTest {
	public static final Path GENERATED = Path.of("src/main/generated");

	public static JsonObject json(Path path) {
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		} catch (IOException e) {
			throw new AssertionError("cannot read " + path, e);
		}
	}

	static Path biomeFile(HabitatBiome b) {
		return GENERATED.resolve("data/polishforests/worldgen/biome/" + b.id() + ".json");
	}

	/** Values of a biome tag from datagen. */
	static Set<String> tag(String namespace, String path) {
		Set<String> values = new TreeSet<>();
		for (JsonElement e : json(GENERATED.resolve("data/" + namespace + "/tags/worldgen/biome/" + path + ".json"))
				.getAsJsonArray("values")) {
			values.add(e.getAsString());
		}
		return values;
	}

	static Set<String> allIds() {
		Set<String> ids = new TreeSet<>();
		for (HabitatBiome b : HabitatBiome.values()) {
			ids.add("polishforests:" + b.id());
		}
		return ids;
	}

	@Test
	void exactlyOneFilePerBiome() throws IOException {
		Set<String> files = new TreeSet<>();
		try (Stream<Path> list = Files.list(GENERATED.resolve("data/polishforests/worldgen/biome"))) {
			list.forEach(p -> files.add(p.getFileName().toString()));
		}
		Set<String> expected = new TreeSet<>();
		for (HabitatBiome b : HabitatBiome.values()) {
			expected.add(b.id() + ".json");
		}
		assertEquals(36, HabitatBiome.values().length);
		assertEquals(expected, files);
	}

	@Test
	void climateAndColorsComeFromTheEnum() {
		for (HabitatBiome b : HabitatBiome.values()) {
			JsonObject j = json(biomeFile(b));
			float t = j.get("temperature").getAsFloat();
			assertTrue(t <= PolandClimate.MAX_BASE_TEMPERATURE, b.id() + ": temperature above the Serene Seasons gate");
			assertEquals(b.temperature(), t, 1e-6, b.id());
			assertEquals(b.downfall(), j.get("downfall").getAsFloat(), 1e-6, b.id());
			assertTrue(j.get("has_precipitation").getAsBoolean(), b.id());
			assertEquals(0, j.getAsJsonArray("carvers").size(), b.id() + ": the generator runs no carvers");
			JsonObject effects = j.getAsJsonObject("effects");
			assertEquals(color(b.waterColor()), effects.get("water_color").getAsString(), b.id());
			assertEquals(color(b.grassColor()), effects.get("grass_color").getAsString(), b.id());
			assertEquals(color(b.foliageColor()), effects.get("foliage_color").getAsString(), b.id());
			assertEquals(color(b.dryFoliageColor()), effects.get("dry_foliage_color").getAsString(), b.id());
			JsonObject attributes = j.getAsJsonObject("attributes");
			assertTrue(attributes.has("minecraft:gameplay/natural_mob_spawns"), b.id() + ": spawns");
			assertTrue(attributes.has("minecraft:visual/sky_color"), b.id() + ": sky color");
			assertEquals(b.increasedFireBurnout(), attributes.has("minecraft:gameplay/increased_fire_burnout"), b.id());
			assertEquals(b.music() != HabitatBiome.Music.DEFAULT, attributes.has("minecraft:audio/background_music"), b.id());
			assertEquals(b == HabitatBiome.DYSTROPHIC_LAKE, attributes.has("minecraft:visual/water_fog_color"), b.id());
		}
	}

	/** §10: no farm animals in forests, frogs in wetlands, no squid in the sea, no witches in the alder carr. */
	@Test
	void spawnsFollowTheProfiles() {
		for (HabitatBiome b : HabitatBiome.values()) {
			Set<String> mobs = new HashSet<>();
			JsonObject byCategory = json(biomeFile(b)).getAsJsonObject("attributes")
					.getAsJsonObject("minecraft:gameplay/natural_mob_spawns").getAsJsonObject("argument")
					.getAsJsonObject("spawns_by_category");
			for (var category : byCategory.entrySet()) {
				for (JsonElement e : category.getValue().getAsJsonArray()) {
					mobs.add(e.getAsJsonObject().get("type").getAsString().replace("minecraft:", ""));
				}
			}
			if (b.isForest()) {
				assertTrue(mobs.contains("wolf") && mobs.contains("fox"), b.id());
				assertTrue(!mobs.contains("cow") && !mobs.contains("sheep"), b.id() + ": farm animals in a forest");
			}
			assertTrue(!mobs.contains("squid"), b.id());
			assertEquals(b == HabitatBiome.ALDER_CARR || b == HabitatBiome.RAISED_BOG || b == HabitatBiome.FEN
					|| b == HabitatBiome.REEDBED, mobs.contains("frog"), b.id() + ": frogs");
			assertEquals(b != HabitatBiome.ALDER_CARR, mobs.contains("witch"), b.id() + ": witches");
			assertTrue(mobs.contains("zombie") && mobs.contains("creeper"), b.id() + ": monsters");
		}
		assertTrue(json(biomeFile(HabitatBiome.SEA)).toString().contains("minecraft:cod"));
		assertTrue(json(biomeFile(HabitatBiome.RIVER)).toString().contains("minecraft:salmon"));
	}

	@Test
	void namesInBothLanguages() {
		for (String language : List.of("en_us", "pl_pl")) {
			JsonObject lang = json(GENERATED.resolve("assets/polishforests/lang/" + language + ".json"));
			for (HabitatBiome b : HabitatBiome.values()) {
				String key = "biome.polishforests." + b.id();
				assertTrue(lang.has(key), language + ": " + key);
				assertEquals(language.equals("pl_pl") ? b.polishName() : b.englishName(), lang.get(key).getAsString());
			}
			for (PolishForestsCommands.Target t : PolishForestsCommands.Target.values()) {
				assertTrue(lang.has("polishforests.target." + t.id()), language + ": target " + t.id());
			}
			assertTrue(lang.get("polishforests.target.upper_montane").getAsString().contains("1150 m"), language);
			assertTrue(lang.get("polishforests.target.lower_montane").getAsString().contains("1150 m"), language);
		}
	}

	@Test
	void tagsCoverAllBiomes() {
		Set<String> all = allIds();
		assertEquals(all, tag("minecraft", "is_overworld"));
		assertEquals(all, tag("minecraft", "has_structure/trial_chambers"));
		assertEquals(all, tag("polishforests", "polish_climate"));
		Set<String> forests = new TreeSet<>();
		Set<String> land = new TreeSet<>();
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				forests.add("polishforests:" + b.id());
			}
			if (!b.isWater()) {
				land.add("polishforests:" + b.id());
			}
		}
		assertEquals(18, forests.size());
		assertEquals(forests, tag("minecraft", "is_forest"));
		assertEquals(forests, tag("polishforests", "forests"));
		assertEquals(land, tag("minecraft", "stronghold_biased_to"));
		assertEquals(Set.of("polishforests:river", "polishforests:stream"), tag("minecraft", "is_river"));
		assertEquals(Set.of("polishforests:beach"), tag("minecraft", "is_beach"));
		assertEquals(Set.of("polishforests:sea"), tag("minecraft", "is_ocean"));
		assertEquals(Set.of("polishforests:montane_beech_forest", "polishforests:montane_spruce_forest",
				"polishforests:dwarf_pine_scrub", "polishforests:alpine_grassland"), tag("minecraft", "is_mountain"));
		// §10: neither is_hill nor is_taiga.
		assertTrue(!Files.exists(GENERATED.resolve("data/minecraft/tags/worldgen/biome/is_hill.json")));
		assertTrue(!Files.exists(GENERATED.resolve("data/minecraft/tags/worldgen/biome/is_taiga.json")));
	}

	/**
	 * Every land biome and every inland water can have a mineshaft: forests through {@code #is_forest}, rivers through
	 * {@code #is_river}, the beach through {@code #is_beach}, the sea through {@code #is_ocean}, the mountains through
	 * {@code #is_mountain} (vanilla list), the rest explicitly.
	 */
	@Test
	void mineshaftsEverywhere() {
		Set<String> reached = new TreeSet<>(tag("minecraft", "has_structure/mineshaft"));
		for (String viaTag : List.of("is_forest", "is_river", "is_beach", "is_ocean", "is_mountain")) {
			reached.addAll(tag("minecraft", viaTag));
		}
		assertEquals(allIds(), reached);
	}

	private static String color(int rgb) {
		return String.format(Locale.ROOT, "#%06x", rgb);
	}

	/**
	 * Color steps between neighboring biomes (rule Z10, step S8b, docs/03-m2-biomy.md §8.8 and §10): the client blends the
	 * colors over 5 blocks, so the listed neighbor pairs stay within the documented ΔE (CIELAB, CIE76) of their water and
	 * grass colors; the dark brown water of the dystrophic lake against the bog water is the documented exception (ΔE 20).
	 */
	@Test
	void neighborColorsStayClose() {
		Object[][] pairs = {
				// Water: mountains, streams and gray alder forests, lowland waters and floodplains.
				{"water_color", HabitatBiome.MONTANE_SPRUCE_FOREST, HabitatBiome.GRAY_ALDER_FOREST, 12.5},
				{"water_color", HabitatBiome.STREAM, HabitatBiome.RIVER, 12.5},
				{"water_color", HabitatBiome.GRAY_ALDER_FOREST, HabitatBiome.ASH_ALDER_FOREST, 12.5},
				{"water_color", HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.UPLAND_FIR_FOREST, 12.5},
				{"water_color", HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.LAKE, 12.5},
				// Water of the bog woodland and the raised bog against their mineral neighbors.
				{"water_color", HabitatBiome.BOG_WOODLAND, HabitatBiome.MOIST_PINE_FOREST, 12.5},
				{"water_color", HabitatBiome.RAISED_BOG, HabitatBiome.FEN, 12.5},
				{"water_color", HabitatBiome.RAISED_BOG, HabitatBiome.HEATH, 12.5},
				{"water_color", HabitatBiome.RAISED_BOG, HabitatBiome.DYSTROPHIC_LAKE, 20.5},
				// Grass of the PRESENT_DAY mosaic and the dunes.
				{"grass_color", HabitatBiome.ARABLE_LAND, HabitatBiome.HAY_MEADOW, 10.5},
				{"grass_color", HabitatBiome.ARABLE_LAND, HabitatBiome.WET_MEADOW, 15.0},
				{"grass_color", HabitatBiome.ARABLE_LAND, HabitatBiome.OAK_HORNBEAM_FOREST, 15.0},
				{"grass_color", HabitatBiome.GRAY_DUNE, HabitatBiome.WET_MEADOW, 15.0}};
		StringBuilder report = new StringBuilder();
		List<String> failures = new java.util.ArrayList<>();
		for (Object[] p : pairs) {
			String key = (String) p[0];
			HabitatBiome a = (HabitatBiome) p[1];
			HabitatBiome b = (HabitatBiome) p[2];
			double de = deltaE(colorOf(a, key), colorOf(b, key));
			report.append(String.format(Locale.ROOT, "%s %s | %s: ΔE %.1f%n", key, a.id(), b.id(), de));
			if (de > (Double) p[3]) {
				failures.add(String.format(Locale.ROOT, "%s %s | %s: ΔE %.1f > %.1f", key, a.id(), b.id(), de, (Double) p[3]));
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures.toString());
	}

	private static int colorOf(HabitatBiome b, String key) {
		return Integer.parseInt(json(biomeFile(b)).getAsJsonObject("effects").get(key).getAsString().substring(1), 16);
	}

	/** CIE76 color difference of two sRGB colors (D65). */
	static double deltaE(int a, int b) {
		double[] x = lab(a);
		double[] y = lab(b);
		return Math.sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]));
	}

	private static double[] lab(int rgb) {
		double r = linear((rgb >> 16 & 255) / 255.0);
		double g = linear((rgb >> 8 & 255) / 255.0);
		double b = linear((rgb & 255) / 255.0);
		double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
		double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
		double fx = labF(x);
		double fy = labF(y);
		double fz = labF(z);
		return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
	}

	private static double linear(double v) {
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static double labF(double t) {
		return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
	}
}
