package pl.polishforests.client.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import pl.polishforests.PolishForests;

/**
 * Model templates of the herb layer (step V12): the berries of a dwarf shrub as a cross of two planes along the block's
 * axes ({@value #BERRIES}: the vanilla cross of the shrub lies on the diagonals, so the two crosses never lie in one
 * plane and do not fight in the depth buffer), and the litter segments over a moss carpet ({@link #mossyLitter}: the
 * planes of vanilla's leaf litter templates, one pixel higher, over the vanilla moss carpet; PW-7). Not tinted. Round 1
 * of the V12 review: the variants of the tinted shrub cross ({@link #shrubCross}: the planes turned by 22.5° or on the
 * diagonals, shifted by 1–2 px, some shorter, the texture mirrored), so a carpet of dwarf shrubs is no grid of identical
 * crosses.
 */
final class GroundTemplates implements DataProvider {
	/** Model id of the berries cross. */
	static final String BERRIES = "block/template_berries_cross";
	/** Height of the litter planes over the moss carpet, px (vanilla leaf litter: 0.25 over the ground). */
	static final double MOSSY_LITTER_Y = 1.25;

	private final PackOutput.PathProvider models;

	GroundTemplates(FabricPackOutput output) {
		this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
	}

	/**
	 * The shrub cross variants (k = 1 … {@value #SHRUB_VARIANTS}): angle of the planes about the vertical, shift in x and
	 * z, height (the texture cut at the top, not squashed), mirrored texture. Variant 0 is the vanilla tinted cross.
	 */
	static final double[][] SHRUB = {{22.5, 1.5, -1, 16, 1}, {-22.5, -1.5, 1.5, 14, 0}, {45, 1, 2, 15, 1}};
	/** Number of the own shrub cross variants. */
	static final int SHRUB_VARIANTS = 3;

	/** Model id of the shrub cross variant k (1 … {@value #SHRUB_VARIANTS}). */
	static String shrubCross(int k) {
		return "block/template_shrub_cross_" + k;
	}

	/** Model id of the litter template of k segments (1–4) over moss. */
	static String mossyLitter(int k) {
		return "block/template_mossy_litter_" + k;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		List<CompletableFuture<?>> out = new ArrayList<>();
		out.add(DataProvider.saveStable(cache, berries(), models.json(PolishForests.id(BERRIES))));
		for (int k = 1; k <= SHRUB_VARIANTS; k++) {
			out.add(DataProvider.saveStable(cache, shrub(SHRUB[k - 1]), models.json(PolishForests.id(shrubCross(k)))));
		}
		// The planes of vanilla's template_leaf_litter_1 … 4 (x0, z0, x1, z1 and the uv of the up face).
		double[][] planes = {{0, 0, 8, 8}, {0, 0, 8, 16}, {8, 8, 16, 16}, {0, 0, 16, 16}};
		for (int k = 1; k <= 4; k++) {
			double[] p = planes[k - 1];
			out.add(DataProvider.saveStable(cache, litter(p), models.json(PolishForests.id(mossyLitter(k)))));
		}
		return CompletableFuture.allOf(out.toArray(CompletableFuture[]::new));
	}

	@Override
	public String getName() {
		return "Polish Forests herb layer model templates";
	}

	private static JsonObject berries() {
		JsonArray elements = new JsonArray();
		elements.add(plane(new double[] {0.8, 0, 8}, new double[] {15.2, 16, 8}, "north", "south"));
		elements.add(plane(new double[] {8, 0, 0.8}, new double[] {8, 16, 15.2}, "west", "east"));
		JsonObject m = new JsonObject();
		m.addProperty("ambientocclusion", false);
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "#berries");
		m.add("textures", textures);
		m.add("elements", elements);
		return m;
	}

	/** A tinted cross of two planes turned by v[0] degrees about the shifted center, as vanilla's tinted cross. */
	private static JsonObject shrub(double[] v) {
		double dx = v[1];
		double dz = v[2];
		double top = v[3];
		boolean mirror = v[4] != 0;
		JsonArray elements = new JsonArray();
		elements.add(shrubPlane(new double[] {0.8 + dx, 0, 8 + dz}, new double[] {15.2 + dx, top, 8 + dz}, "north", "south", v[0], dx, dz,
				top, mirror));
		elements.add(shrubPlane(new double[] {8 + dx, 0, 0.8 + dz}, new double[] {8 + dx, top, 15.2 + dz}, "west", "east", v[0], dx, dz,
				top, mirror));
		JsonObject m = new JsonObject();
		m.addProperty("ambientocclusion", false);
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "#cross");
		m.add("textures", textures);
		m.add("elements", elements);
		return m;
	}

	private static JsonObject shrubPlane(double[] from, double[] to, String a, String b, double angle, double dx, double dz, double top,
			boolean mirror) {
		JsonObject e = new JsonObject();
		e.add("from", vector(from));
		e.add("to", vector(to));
		JsonObject rotation = new JsonObject();
		rotation.add("origin", vector(new double[] {8 + dx, 8, 8 + dz}));
		rotation.addProperty("axis", "y");
		rotation.addProperty("angle", angle);
		rotation.addProperty("rescale", true);
		e.add("rotation", rotation);
		e.addProperty("shade_direction_override", "up");
		JsonObject faces = new JsonObject();
		for (String side : new String[] {a, b}) {
			JsonObject f = new JsonObject();
			f.add("uv", vector(mirror ? new double[] {16, 16 - top, 0, 16} : new double[] {0, 16 - top, 16, 16}));
			f.addProperty("texture", "#cross");
			f.addProperty("tintindex", 0);
			faces.add(side, f);
		}
		e.add("faces", faces);
		return e;
	}

	private static JsonObject plane(double[] from, double[] to, String a, String b) {
		JsonObject e = new JsonObject();
		e.add("from", vector(from));
		e.add("to", vector(to));
		e.addProperty("shade_direction_override", "up");
		JsonObject faces = new JsonObject();
		for (String side : new String[] {a, b}) {
			JsonObject f = new JsonObject();
			f.add("uv", vector(new double[] {0, 0, 16, 16}));
			f.addProperty("texture", "#berries");
			faces.add(side, f);
		}
		e.add("faces", faces);
		return e;
	}

	private static JsonObject litter(double[] p) {
		JsonObject e = new JsonObject();
		e.add("from", vector(new double[] {p[0], MOSSY_LITTER_Y, p[1]}));
		e.add("to", vector(new double[] {p[2], MOSSY_LITTER_Y, p[3]}));
		JsonObject faces = new JsonObject();
		JsonObject up = new JsonObject();
		up.add("uv", vector(new double[] {p[0], p[1], p[2], p[3]}));
		up.addProperty("texture", "#texture");
		faces.add("up", up);
		JsonObject down = new JsonObject();
		down.add("uv", vector(new double[] {p[0], p[3], p[2], p[1]}));
		down.addProperty("texture", "#texture");
		faces.add("down", down);
		e.add("faces", faces);
		JsonArray elements = new JsonArray();
		elements.add(e);
		JsonObject m = new JsonObject();
		m.addProperty("ambientocclusion", false);
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "#texture");
		m.add("textures", textures);
		m.add("elements", elements);
		return m;
	}

	private static JsonArray vector(double[] v) {
		JsonArray a = new JsonArray();
		for (double x : v) {
			if (x == Math.rint(x)) {
				a.add((int) x);
			} else {
				a.add(x);
			}
		}
		return a;
	}
}
