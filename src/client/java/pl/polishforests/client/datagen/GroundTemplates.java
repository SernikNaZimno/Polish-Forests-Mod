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
 * planes of vanilla's leaf litter templates, one pixel higher, over the vanilla moss carpet; PW-7). Not tinted.
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

	/** Model id of the litter template of k segments (1–4) over moss. */
	static String mossyLitter(int k) {
		return "block/template_mossy_litter_" + k;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		List<CompletableFuture<?>> out = new ArrayList<>();
		out.add(DataProvider.saveStable(cache, berries(), models.json(PolishForests.id(BERRIES))));
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
