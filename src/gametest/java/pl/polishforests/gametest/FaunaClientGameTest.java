package pl.polishforests.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.client.entity.BoneProbe;
import pl.polishforests.client.entity.RoeDeerRenderer;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.gametest.probe.FaunaProbes;

/**
 * The fauna of step V8 (docs/04-pionowy-wycinek.md §3), {@code -Pgametest=fauna}, only by name: the roe deer model in the
 * game against the preview from code, and the calibration of GeckoLib's axes and rotations.
 *
 * <ol>
 * <li><b>Axis gauge</b> ({@link FaunaProbes}): two gauges, one at rest facing south, one in its animation pose
 * {@code calib.pose} turned to yaw {@value #GAUGE_YAW}. GeckoLib's position listeners ({@link BoneProbe}) report every bone
 * pivot and locator in model space and in the world; they must equal the positions tools/fauna computed with its own
 * kinematics ({@code src/test/resources/fauna/fauna_expected.json}, written by {@code tools/fauna/generate.py}) within
 * {@value #MODEL_TOLERANCE} px, and in the world the left marker must be on the left of the entity and the front marker in
 * front of it. This fixes the sign of every rotation axis, the order of the rotations, the child frames, the animation
 * rotation and position, and the turn of the model by the entity's yaw (risk R17).
 * <li><b>Roe deer from 4 sides</b>: each of the 4 textures (doe and buck, summer and winter coat) in the rest pose of the
 * model ({@link RoeDeer.Display#NONE}) in front of a lime concrete backdrop, turned to 4 sides in front of a fixed camera
 * (the camera of fauna_expected.json, field of view {@code fov}). The bone positions must match as for the gauge, and the
 * silhouette of the screenshot (every pixel that is not the green backdrop) must cover the silhouette tools/fauna rendered
 * from the same camera ({@code roe_deer_<sex>_<view>.png}) with an intersection over union of at least
 * {@value #MIN_IOU}.
 * <li>Screenshots for the review (field of view 70): the 4 variants side by side on grass from the front and from behind,
 * each animation (from the right: idle, walk, run, eat, alert on a buck, rest), the spawn egg in the hotbar.
 * </ol>
 * Numbers go to the log ({@code [fauna]}) and to {@code build/fauna/fauna.txt}; screenshots {@code fauna_*}.
 */
public final class FaunaClientGameTest implements FabricClientGameTest {
	private static final int NOON = 6000;
	private static final double MODEL_TOLERANCE = 0.02;
	private static final double WORLD_TOLERANCE = 0.003;
	private static final double MIN_IOU = 0.97;
	/** Yaw of the posed gauge: a whole number of the 1.40625 degree steps in which the server sends the body yaw. */
	private static final float GAUGE_YAW = 45.0F;
	/** GeckoLib's entity renderer lifts the model by 0.01 block (GeoEntityRenderer.adjustRenderPose; tools/fauna ENTITY_LIFT). */
	private static final double ENTITY_LIFT = 0.01;
	/** The 4 variants: buck, winter coat. */
	private static final boolean[][] VARIANTS = {{false, false}, {false, true}, {true, false}, {true, true}};

	private final List<String> results = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"fauna".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		if (!FaunaProbes.enabled()) {
			throw new AssertionError("The axis gauge is not registered");
		}
		JsonObject expected = loadExpected();
		int[] screen = ints(expected.getAsJsonArray("screen"));
		FabricLoader loader = FabricLoader.getInstance();
		result("mods: sodium %s, nvidium %s, entityculling %s, immediatelyfast %s", loader.isModLoaded("sodium"),
				loader.isModLoaded("nvidium"), loader.isModLoaded("entityculling"), loader.isModLoaded("immediatelyfast"));
		context.runOnClient(mc -> {
			mc.options.fov().set(expected.get("fov").getAsInt());
			mc.options.fovEffectScale().set(0.0);
			mc.options.bobView().set(false);
			mc.options.entityShadows().set(false);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.renderDistance().set(8);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
					"time set " + NOON, "weather clear", "difficulty peaceful", "gamerule send_command_feedback false"}) {
				sp.getServer().runCommand(c);
			}
			BlockPos o = sp.getServer().computeOnServer(FaunaClientGameTest::buildBackdrop);
			result("backdrop at %s", o.toShortString());
			resources(context);
			sp.getServer().runCommand("gamemode spectator @a");
			hud(context, false);
			BoneProbe.start();
			gauges(context, sp, o, expected);
			fourSides(context, sp, o, expected, screen);
			BoneProbe.stop();
			context.runOnClient(mc -> mc.options.fov().set(70));
			showcase(context, sp, o);
			animations(context, sp, o);
			spawnEgg(context, sp, o);
		} finally {
			BoneProbe.stop();
			context.runOnClient(mc -> mc.options.fov().set(70));
		}
		write();
		if (!failures.isEmpty()) {
			throw new AssertionError("Fauna test failed:\n" + String.join("\n", failures));
		}
	}

	// ------------------------------------------------------------------ scene

	/** A lime concrete floor and a wall behind the place of the animal (the backdrop of the silhouettes). */
	private static BlockPos buildBackdrop(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		BlockPos o = new BlockPos(0, ground + 1, 0);
		BlockState lime = Blocks.CONCRETE.lime().defaultBlockState();
		for (int x = -14; x <= 14; x++) {
			for (int z = -12; z <= 6; z++) {
				level.setBlockAndUpdate(o.offset(x, -1, z), lime);
			}
			for (int y = 0; y <= 8; y++) {
				level.setBlockAndUpdate(o.offset(x, y, 6), lime);
			}
		}
		return o;
	}

	private static void resources(ClientGameTestContext context) {
		List<Identifier> ids = new ArrayList<>();
		for (boolean[] v : VARIANTS) {
			ids.add(RoeDeerRenderer.texture(v[0], v[1]));
		}
		ids.add(PolishForests.id("textures/item/roe_deer_spawn_egg.png"));
		ids.add(PolishForests.id("geckolib/models/entity/roe_deer.geo.json"));
		ids.add(PolishForests.id("geckolib/animations/entity/roe_deer.animation.json"));
		List<Identifier> missing = context.computeOnClient(mc -> ids.stream()
				.filter(id -> mc.getResourceManager().getResource(id).isEmpty()).toList());
		if (!missing.isEmpty()) {
			throw new AssertionError("Missing resources: " + missing);
		}
	}

	private static void hud(ClientGameTestContext context, boolean visible) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == visible) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** Spawns a roe deer (no AI: it stays as placed) and returns its position. */
	private static Vec3 deer(TestSingleplayerContext sp, double x, double y, double z, float yaw, boolean buck, boolean winter,
			RoeDeer.Display display) {
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			RoeDeer d = ModEntities.roeDeer().create(level, EntitySpawnReason.COMMAND);
			place(d, x, y, z, yaw);
			d.setBuck(buck);
			d.setWinterCoat(winter);
			d.setDisplay(display);
			level.addFreshEntity(d);
			return d.position();
		});
	}

	private static void place(net.minecraft.world.entity.Mob m, double x, double y, double z, float yaw) {
		m.snapTo(x, y, z, yaw, 0);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		m.setNoAi(true);
		m.setPersistenceRequired();
	}

	private static void clear(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> {
			List<Entity> all = new ArrayList<>();
			s.overworld().getAllEntities().forEach(e -> {
				if (e instanceof RoeDeer || e instanceof FaunaProbes.AxisGauge) {
					all.add(e);
				}
			});
			all.forEach(Entity::discard);
		});
	}

	private static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double eyeY, double z,
			double yaw, double pitch, int w, int h) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", x, eyeY - 1.62, z, yaw, pitch));
		context.waitTicks(20);
		Path p = context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(w, h));
		PolishForests.LOG.info(String.format(Locale.ROOT, "[fauna] %s: camera eye (%.3f, %.3f, %.3f) yaw %.1f pitch %.1f", name, x, eyeY, z,
				yaw, pitch));
		return p;
	}

	// ------------------------------------------------------------------ axis gauge

	private void gauges(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o, JsonObject expected) {
		JsonObject gauge = expected.getAsJsonObject("models").getAsJsonObject("axis_gauge");
		Vec3[] pos = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			FaunaProbes.AxisGauge rest = FaunaProbes.axisGauge().create(level, EntitySpawnReason.COMMAND);
			place(rest, o.getX() - 1.5, o.getY(), o.getZ() + 0.5, 0);
			level.addFreshEntity(rest);
			FaunaProbes.AxisGauge posed = FaunaProbes.axisGauge().create(level, EntitySpawnReason.COMMAND);
			place(posed, o.getX() + 2.5, o.getY(), o.getZ() + 0.5, GAUGE_YAW);
			posed.setPosed(true);
			level.addFreshEntity(posed);
			return new Vec3[] {rest.position(), posed.position()};
		});
		// The overview of both gauges with the normal field of view (the positions do not depend on the camera).
		int fov = context.computeOnClient(mc -> mc.options.fov().get());
		context.runOnClient(mc -> mc.options.fov().set(70));
		Path shot = shot(context, sp, "fauna_axis_gauge", o.getX() + 0.5, o.getY() + 2.0, o.getZ() - 2.6, 0, 32, 1280, 720);
		context.runOnClient(mc -> mc.options.fov().set(fov));
		results.add("axis gauge screenshot " + shot.getFileName());
		checkBones(context, "axis gauge at rest, yaw 0", pos[0], 0, gauge.getAsJsonObject("rest"), List.of());
		checkBones(context, "axis gauge in calib.pose, yaw " + GAUGE_YAW, pos[1], GAUGE_YAW, gauge.getAsJsonObject("posed"), List.of());
		// The named directions in the world: the left marker on the entity's left, the front marker in front of it.
		for (int k = 0; k < 2; k++) {
			float yaw = k == 0 ? 0 : GAUGE_YAW;
			Map<String, BoneProbe.Point> pts = BoneProbe.points(pos[k]);
			BoneProbe.Point left = pts.get("locator:left_marker_tip");
			BoneProbe.Point front = pts.get("locator:front_marker_tip");
			if (left == null || front == null || left.world() == null || front.world() == null) {
				fail("axis gauge %d: no world position of the markers", k);
				continue;
			}
			double r = Math.toRadians(yaw);
			Vec3 facing = new Vec3(-Math.sin(r), 0, Math.cos(r));
			Vec3 leftDir = new Vec3(Math.cos(r), 0, Math.sin(r));
			double l = left.world().subtract(pos[k]).dot(leftDir);
			double f = front.world().subtract(pos[k]).dot(facing);
			result("axis gauge yaw %.0f: left marker %.3f blocks to the entity's left, front marker %.3f blocks ahead", yaw, l, f);
			if (l < 0.4 || f < 0.4) {
				fail("axis gauge yaw %.0f: left marker %.3f to the left, front marker %.3f ahead (expected 0.5)", yaw, l, f);
			}
		}
		clear(sp);
	}

	/**
	 * Every expected bone pivot and locator of the entity at pos (yaw in degrees) against the probe: model space within
	 * {@link #MODEL_TOLERANCE} px, world within {@link #WORLD_TOLERANCE} blocks (expected world: the model point mirrored
	 * in X by GeckoLib, turned by 180 - yaw and lifted by {@link #ENTITY_LIFT}, as tools/fauna/model.py to_world). Names in
	 * hidden may be absent.
	 */
	private void checkBones(ClientGameTestContext context, String what, Vec3 pos, float yaw, JsonObject expected, List<String> hidden) {
		context.waitFor(mc -> BoneProbe.points(pos).size() >= expected.size() - hidden.size() * 2, 200);
		Map<String, BoneProbe.Point> pts = BoneProbe.points(pos);
		double worstModel = 0;
		double worstWorld = 0;
		int checked = 0;
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : expected.entrySet()) {
			String name = e.getKey();
			double[] want = doubles(e.getValue().getAsJsonArray());
			BoneProbe.Point p = pts.get(name);
			if (p == null) {
				if (hidden.stream().noneMatch(name::contains)) {
					bad.add(name + " not reported");
				}
				continue;
			}
			checked++;
			double dm = dist(p.model(), want);
			worstModel = Math.max(worstModel, dm);
			if (dm > MODEL_TOLERANCE) {
				bad.add(String.format(Locale.ROOT, "%s model (%.3f, %.3f, %.3f) expected (%.3f, %.3f, %.3f)", name, p.model().x, p.model().y,
						p.model().z, want[0], want[1], want[2]));
			}
			if (p.world() != null) {
				Vec3 w = toWorld(want, pos, yaw);
				double dw = p.world().distanceTo(w);
				worstWorld = Math.max(worstWorld, dw);
				if (dw > WORLD_TOLERANCE) {
					bad.add(String.format(Locale.ROOT, "%s world (%.4f, %.4f, %.4f) expected (%.4f, %.4f, %.4f)", name, p.world().x,
							p.world().y, p.world().z, w.x, w.y, w.z));
				}
			} else {
				bad.add(name + " without a world position");
			}
		}
		result("%s: %d of %d bone and locator positions checked, largest difference %.4f px (model), %.5f blocks (world)", what,
				checked, expected.size(), worstModel, worstWorld);
		if (!bad.isEmpty()) {
			fail("%s: %d positions differ: %s", what, bad.size(), String.join("; ", bad.subList(0, Math.min(8, bad.size()))));
		}
	}

	/** tools/fauna/model.py to_world: model pixels (Bedrock axes) of an entity at pos with body yaw -> world blocks. */
	static Vec3 toWorld(double[] p, Vec3 pos, double yaw) {
		double xj = -p[0] / 16.0;
		double y = p[1] / 16.0;
		double z = p[2] / 16.0;
		double a = Math.toRadians(180.0 - yaw);
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new Vec3(pos.x + xj * c + z * s, pos.y + y + ENTITY_LIFT, pos.z - xj * s + z * c);
	}

	// ------------------------------------------------------------------ roe deer from 4 sides

	private void fourSides(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o, JsonObject expected, int[] screen) {
		JsonObject deer = expected.getAsJsonObject("models").getAsJsonObject("roe_deer");
		JsonObject cam = expected.getAsJsonObject("camera");
		double[] eye = doubles(cam.getAsJsonArray("eye"));
		Path dir = Path.of(System.getProperty("polishforests.fauna.expected", "src/test/resources/fauna"));
		double feetX = o.getX() + 0.5;
		double feetZ = o.getZ() + 0.5;
		double worstIou = 1;
		for (boolean[] v : VARIANTS) {
			String sex = v[0] ? "buck" : "doe";
			String variant = sex + "_" + (v[1] ? "winter" : "summer");
			List<String> hidden = v[0] ? List.of() : List.of("antler_left", "antler_right");
			for (JsonElement view : expected.getAsJsonArray("views")) {
				String name = view.getAsJsonObject().get("name").getAsString();
				float yaw = view.getAsJsonObject().get("yaw").getAsFloat();
				Vec3 pos = deer(sp, feetX, o.getY(), feetZ, yaw, v[0], v[1], RoeDeer.Display.NONE);
				String shotName = "fauna_roe_deer_" + variant + "_" + name;
				Path shot = shot(context, sp, shotName, feetX + eye[0], o.getY() + eye[1], feetZ + eye[2], cam.get("yaw").getAsDouble(),
						cam.get("pitch").getAsDouble(), screen[0], screen[1]);
				checkBones(context, "roe deer " + variant + " " + name, pos, yaw, deer.getAsJsonObject("rest"), hidden);
				String mask = deer.getAsJsonObject("masks").get(sex + "_" + name).getAsString();
				double iou = silhouette(shot, dir.resolve(mask), shotName);
				worstIou = Math.min(worstIou, iou);
				clear(sp);
				context.waitTicks(2);
			}
		}
		result("roe deer 4 sides: smallest silhouette IoU game / preview %.4f (at least %.2f)", worstIou, MIN_IOU);
	}

	/** Intersection over union of the screenshot silhouette (not the green backdrop) and the preview mask. */
	private double silhouette(Path shot, Path maskFile, String name) {
		try {
			BufferedImage game = ImageIO.read(shot.toFile());
			BufferedImage mask = ImageIO.read(maskFile.toFile());
			if (game.getWidth() != mask.getWidth() || game.getHeight() != mask.getHeight()) {
				fail("%s: screenshot %d x %d, mask %d x %d", name, game.getWidth(), game.getHeight(), mask.getWidth(), mask.getHeight());
				return 0;
			}
			long both = 0;
			long onlyGame = 0;
			long onlyPreview = 0;
			for (int y = 0; y < game.getHeight(); y++) {
				for (int x = 0; x < game.getWidth(); x++) {
					int c = game.getRGB(x, y);
					int r = (c >> 16) & 255;
					int g = (c >> 8) & 255;
					int b = c & 255;
					boolean animal = !(g > r * 1.15 && g > b * 1.15);
					boolean preview = (mask.getRGB(x, y) & 0xFFFFFF) != 0;
					if (animal && preview) {
						both++;
					} else if (animal) {
						onlyGame++;
					} else if (preview) {
						onlyPreview++;
					}
				}
			}
			double iou = both / (double) Math.max(1, both + onlyGame + onlyPreview);
			result("%s: silhouette %d px in both, %d only in the game, %d only in the preview, IoU %.4f", name, both, onlyGame, onlyPreview,
					iou);
			if (iou < MIN_IOU) {
				fail("%s: silhouette IoU %.4f < %.2f", name, iou, MIN_IOU);
			}
			return iou;
		} catch (IOException e) {
			fail("%s: cannot read the images: %s", name, e);
			return 0;
		}
	}

	// ------------------------------------------------------------------ screenshots for the review

	/** The 4 variants side by side on the grass, from the front left and from the side. */
	private static void showcase(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o) {
		double x0 = o.getX() + 30.5;
		double z0 = o.getZ() + 0.5;
		int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x0, (int) z0));
		for (int k = 0; k < VARIANTS.length; k++) {
			deer(sp, x0 + 1.6 * k, y, z0, -30, VARIANTS[k][0], VARIANTS[k][1], RoeDeer.Display.AUTO);
		}
		shot(context, sp, "fauna_roe_deer_variants", x0 + 2.4, y + 1.5, z0 + 3.2, 180, 15, 1280, 720);
		shot(context, sp, "fauna_roe_deer_variants_back", x0 + 2.4, y + 1.5, z0 - 2.6, 0, 15, 1280, 720);
		clear(sp);
	}

	/** Each animation on a summer deer (a buck for the alert), side by side, from the side. */
	private static void animations(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o) {
		double x0 = o.getX() + 30.5;
		double z0 = o.getZ() + 12.5;
		int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x0, (int) z0));
		RoeDeer.Display[] all = {RoeDeer.Display.IDLE, RoeDeer.Display.WALK, RoeDeer.Display.RUN, RoeDeer.Display.EAT,
				RoeDeer.Display.ALERT, RoeDeer.Display.REST};
		for (int k = 0; k < all.length; k++) {
			deer(sp, x0 + 1.8 * k, y, z0, 90, all[k] == RoeDeer.Display.ALERT, false, all[k]);
		}
		for (int t = 0; t < 3; t++) {
			shot(context, sp, "fauna_roe_deer_animations_" + t, x0 + 4.5, y + 0.9, z0 - 3.4, 0, 6, 1600, 600);
		}
		clear(sp);
	}

	/** The spawn egg in the hotbar (survival, HUD on). */
	private static void spawnEgg(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o) {
		sp.getServer().runCommand("gamemode survival @a");
		sp.getServer().runCommand("clear @a");
		sp.getServer().runCommand("give @a polishforests:roe_deer_spawn_egg 3");
		hud(context, true);
		shot(context, sp, "fauna_spawn_egg", o.getX() + 0.5, o.getY() + 3.62, o.getZ() - 4.5, 0, 30, 854, 480);
		hud(context, false);
	}

	// ------------------------------------------------------------------ helpers

	private static JsonObject loadExpected() {
		Path dir = Path.of(System.getProperty("polishforests.fauna.expected", "src/test/resources/fauna"));
		try {
			return JsonParser.parseString(Files.readString(dir.resolve("fauna_expected.json"))).getAsJsonObject();
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + dir.resolve("fauna_expected.json") + " (python tools/fauna/generate.py)", e);
		}
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	private static double[] doubles(JsonArray a) {
		double[] out = new double[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsDouble();
		}
		return out;
	}

	private static double dist(Vec3 v, double[] p) {
		return Math.sqrt((v.x - p[0]) * (v.x - p[0]) + (v.y - p[1]) * (v.y - p[1]) + (v.z - p[2]) * (v.z - p[2]));
	}

	private void result(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		results.add(line);
		PolishForests.LOG.info("[fauna] {}", line);
	}

	private void fail(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		failures.add(line);
		results.add("FAIL " + line);
		PolishForests.LOG.error("[fauna] {}", line);
	}

	private void write() {
		Path out = Path.of(System.getProperty("polishforests.fauna.out", "build/fauna"));
		try {
			Files.createDirectories(out);
			Map<String, String> header = new LinkedHashMap<>();
			header.put("result", failures.isEmpty() ? "PASS" : "FAIL (" + failures.size() + ")");
			List<String> lines = new ArrayList<>();
			header.forEach((k, v) -> lines.add(k + ": " + v));
			lines.addAll(results);
			Files.write(out.resolve("fauna.txt"), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			PolishForests.LOG.error("[fauna] cannot write the results", e);
		}
	}
}
