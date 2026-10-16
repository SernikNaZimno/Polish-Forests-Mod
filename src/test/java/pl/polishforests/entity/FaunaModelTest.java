package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The roe deer files of tools/fauna (step V8) without the game: the model has the 13 bones of the quadruped archetype
 * with a valid hierarchy, the animations the renderer and V14 use, and its bone pivots and locators, computed here with
 * GeckoLib's conventions (as calibrated in the game by the fauna game test: rotations X, Y, Z about the pivot with the X
 * and Y angles mirrored, child pivots in model space), equal the positions tools/fauna wrote to
 * {@code src/test/resources/fauna/fauna_expected.json}. A model edited by hand (Blockbench) without regenerating the
 * expected file, or a generator change that moves a bone, fails here and in the game test.
 */
class FaunaModelTest {
	private static final String MODEL = "/assets/polishforests/geckolib/models/entity/roe_deer.geo.json";
	private static final String ANIMATIONS = "/assets/polishforests/geckolib/animations/entity/roe_deer.animation.json";
	private static final String EXPECTED = "/fauna/fauna_expected.json";
	private static final Set<String> BONES = Set.of("root", "body", "neck", "head", "ear_left", "ear_right", "antler_left",
			"antler_right", "leg_front_left", "leg_front_right", "leg_back_left", "leg_back_right", "tail");
	private static final List<String> ANIMATION_NAMES = List.of("misc.idle", "move.walk", "move.run", "misc.eat", "misc.alert",
			"misc.rest");

	private record Bone(String name, String parent, double[] pivot, double[] rotation, Map<String, double[]> locators) {
	}

	@Test
	void modelHasTheBonesOfTheQuadruped() throws IOException {
		Map<String, Bone> bones = bones(read(MODEL));
		assertEquals(BONES, bones.keySet());
		for (Bone b : bones.values()) {
			int depth = 0;
			for (String p = b.parent(); p != null; p = bones.get(p).parent()) {
				assertTrue(bones.containsKey(p), b.name() + ": unknown parent " + p);
				assertTrue(++depth < bones.size(), b.name() + ": parent cycle");
			}
		}
		JsonObject description = read(MODEL).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
				.getAsJsonObject("description");
		assertEquals("geometry.roe_deer", description.get("identifier").getAsString());
		assertEquals("1.12.0", read(MODEL).get("format_version").getAsString());
	}

	@Test
	void restPositionsMatchToolsFauna() throws IOException {
		Map<String, Bone> bones = bones(read(MODEL));
		JsonObject rest = read(EXPECTED).getAsJsonObject("models").getAsJsonObject("roe_deer").getAsJsonObject("rest");
		Map<String, double[][]> matrices = new HashMap<>();
		int checked = 0;
		for (Bone b : bones.values()) {
			double[][] parent = b.parent() == null ? identity() : matrix(bones, b.parent(), matrices);
			assertClose("bone:" + b.name(), apply(parent, b.pivot()), rest);
			double[][] m = matrix(bones, b.name(), matrices);
			for (Map.Entry<String, double[]> l : b.locators().entrySet()) {
				assertClose("locator:" + l.getKey(), apply(m, l.getValue()), rest);
				checked++;
			}
			checked++;
		}
		assertEquals(rest.size(), checked, "fauna_expected.json lists other bones or locators than the model");
	}

	@Test
	void animationsUseTheBonesOfTheModel() throws IOException {
		Map<String, Bone> bones = bones(read(MODEL));
		JsonObject all = read(ANIMATIONS).getAsJsonObject("animations");
		for (String name : ANIMATION_NAMES) {
			assertTrue(all.has(name), "missing animation " + name);
		}
		for (Map.Entry<String, JsonElement> a : all.entrySet()) {
			JsonObject anim = a.getValue().getAsJsonObject();
			double length = anim.get("animation_length").getAsDouble();
			assertTrue(length > 0, a.getKey());
			for (Map.Entry<String, JsonElement> b : anim.getAsJsonObject("bones").entrySet()) {
				assertTrue(bones.containsKey(b.getKey()), a.getKey() + ": unknown bone " + b.getKey());
				for (Map.Entry<String, JsonElement> channel : b.getValue().getAsJsonObject().entrySet()) {
					for (String time : channel.getValue().getAsJsonObject().keySet()) {
						double t = Double.parseDouble(time);
						assertTrue(t >= 0 && t <= length + 1e-9, a.getKey() + "/" + b.getKey() + ": keyframe " + time);
					}
				}
			}
		}
	}

	@Test
	void texturesOfTheFourVariantsExist() {
		for (String sex : new String[] {"doe", "buck"}) {
			for (String coat : new String[] {"summer", "winter"}) {
				assertNotNull(FaunaModelTest.class.getResource("/assets/polishforests/textures/entity/roe_deer/" + sex + "_" + coat + ".png"),
						sex + " " + coat);
			}
		}
		assertNotNull(FaunaModelTest.class.getResource("/assets/polishforests/textures/item/roe_deer_spawn_egg.png"));
	}

	// ------------------------------------------------------------------ GeckoLib kinematics in Bedrock space

	/** Matrix of a bone: parent . T(pivot) . Rz(-z) . Ry(y) . Rx(-x) . T(-pivot) (tools/fauna/model.py bone_matrices). */
	private static double[][] matrix(Map<String, Bone> bones, String name, Map<String, double[][]> cache) {
		double[][] m = cache.get(name);
		if (m != null) {
			return m;
		}
		Bone b = bones.get(name);
		double[][] parent = b.parent() == null ? identity() : matrix(bones, b.parent(), cache);
		double[] p = b.pivot();
		double[] r = b.rotation();
		double[][] local = mul(translate(p[0], p[1], p[2]), mul(rotZ(-r[2]), mul(rotY(r[1]), mul(rotX(-r[0]), translate(-p[0], -p[1], -p[2])))));
		m = mul(parent, local);
		cache.put(name, m);
		return m;
	}

	private static double[][] identity() {
		return new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
	}

	private static double[][] translate(double x, double y, double z) {
		return new double[][] {{1, 0, 0, x}, {0, 1, 0, y}, {0, 0, 1, z}, {0, 0, 0, 1}};
	}

	private static double[][] rotX(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[][] {{1, 0, 0, 0}, {0, c, -s, 0}, {0, s, c, 0}, {0, 0, 0, 1}};
	}

	private static double[][] rotY(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[][] {{c, 0, s, 0}, {0, 1, 0, 0}, {-s, 0, c, 0}, {0, 0, 0, 1}};
	}

	private static double[][] rotZ(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[][] {{c, -s, 0, 0}, {s, c, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
	}

	private static double[][] mul(double[][] a, double[][] b) {
		double[][] out = new double[4][4];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				for (int k = 0; k < 4; k++) {
					out[i][j] += a[i][k] * b[k][j];
				}
			}
		}
		return out;
	}

	private static double[] apply(double[][] m, double[] p) {
		double[] out = new double[3];
		for (int i = 0; i < 3; i++) {
			out[i] = m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3];
		}
		return out;
	}

	private static void assertClose(String key, double[] got, JsonObject expected) {
		assertTrue(expected.has(key), "fauna_expected.json lacks " + key);
		JsonArray want = expected.getAsJsonArray(key);
		for (int i = 0; i < 3; i++) {
			assertEquals(want.get(i).getAsDouble(), got[i], 1e-3, key + " axis " + i);
		}
	}

	// ------------------------------------------------------------------ reading

	private static JsonObject read(String resource) throws IOException {
		try (InputStream in = FaunaModelTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "missing resource " + resource + " (python tools/fauna/generate.py)");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static Map<String, Bone> bones(JsonObject geo) {
		Map<String, Bone> out = new LinkedHashMap<>();
		for (JsonElement e : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			Map<String, double[]> locators = new LinkedHashMap<>();
			if (b.has("locators")) {
				for (Map.Entry<String, JsonElement> l : b.getAsJsonObject("locators").entrySet()) {
					locators.put(l.getKey(), vec(l.getValue().getAsJsonArray()));
				}
			}
			String name = b.get("name").getAsString();
			out.put(name, new Bone(name, b.has("parent") ? b.get("parent").getAsString() : null, vec(b.getAsJsonArray("pivot")),
					b.has("rotation") ? vec(b.getAsJsonArray("rotation")) : new double[3], locators));
		}
		return out;
	}

	private static double[] vec(JsonArray a) {
		List<Double> v = new ArrayList<>();
		a.forEach(x -> v.add(x.getAsDouble()));
		return new double[] {v.get(0), v.get(1), v.get(2)};
	}
}
