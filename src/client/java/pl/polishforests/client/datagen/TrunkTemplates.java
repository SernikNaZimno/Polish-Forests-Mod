package pl.polishforests.client.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import pl.polishforests.PolishForests;
import pl.polishforests.block.TrunkBlock;

/**
 * The model templates of the thin trunk ({@link TrunkBlock}, variant D, step V2; rule ZW-9 of
 * docs/04-pionowy-wycinek.md): {@code polishforests:block/template_trunk_r1…r7} and the item model
 * {@code template_trunk_inventory}, with the texture slots {@code #bark} and {@code #end}. {@code ModelTemplate} cannot
 * make elements, so this provider writes them.
 *
 * <p>The base template points the arm down ({@code parent=down}, no rotation: a standing trunk, the most common and the
 * most visible case, shows its bark upright); the blockstates rotate it to the other five parents. With radius r, a = 8 -
 * r, b = 8 + r (px), the margin ε = {@link #EPSILON} and the inset c = {@link #CAP_INSET}; on every side face the texture
 * row is 8 − y (mod 16) and the columns line up between faces on the same side, so the tile runs on from element to
 * element and from block to block:
 * <ul>
 * <li><b>arm</b>, the own half from the center to the parent: [a, 0, a]–[b, 8, b], four bark faces (rows 0-8), open on
 * top;
 * <li><b>inner walls</b>, four flat faces c inside the arm, as wide as the reach (from a + ε to b − ε), from 0 to 8: what
 * shows through the seam at the center (the ε slit under the reach of a child, or the ledge under the cap of a tip) is
 * bark lit like the side around it;
 * <li><b>cap</b>, the core above the center: four flat faces c inside the radius, as wide as the reach, from 8 up to the
 * top of a side child's reach (8 + r − ε, rows 16 − r + ε to 16), and a flat top of bark c below the radius (8 + r − c),
 * also as wide as the reach: the tip of a branch (a living tip has bark, not rings); it fills the elbow when the trunk
 * turns and hides inside the reach of a child of the same radius;
 * <li><b>reach</b> into the parent, from its center to δ into the own block: [a + ε, −8, a + ε]–[b − ε, δ, b − ε], four
 * bark faces (rows 8 − δ to 16) and the end (rings) at the bottom, which closes the 8 px stub when the parent is broken.
 * </ul>
 * 18 quads per block. Each block thus draws one whole texture tile over its own lower half and the upper half of its
 * parent, so a standing trunk shows the bark tile by tile like a vanilla log, and the variants of a bark (same top and
 * bottom rows, V1) join without a seam. The margin ε keeps the reach of a child just outside the arm of its own block;
 * the overlap δ = {@link #DELTA} puts the reach behind the seam where it meets the outer arm (without it the ε-wide slit
 * let some pixel rows see through the hollow inside of the trunk: light hairlines on the sample board, step V2). The cap
 * lies c (not ε) inside the reach of a child: faces only 0.01 px apart fight in the depth buffer beyond about 25
 * blocks, 0.1 px apart only beyond about 70 blocks, where the texture is mipmapped (V2 review). The arm has no top face:
 * lit from above, it showed through the ε slit at the center seam of a straight trunk and on the 0.1 px ledge under the
 * cap of a tip as a light hairline (V2 review, round 2); the inner walls take its place. The cap's faces reach out to the
 * ε margin, so the end of a side child's reach (rings) does not show past the cap; at an elbow of equal radii a 0.09 px
 * sliver of it still shows through the step at a slant (TrunkModelRayTest measures it).
 * <p>The models have no ambient occlusion ({@code "ambientocclusion": false}, like vanilla's cross plants): the arm is
 * lit in the context of its own block and the reach in that of the child, so smooth lighting darkened the lower half of
 * a trunk standing on the ground (the occluding ground is the neighbor of the arm, not of the reach) and broke off in a
 * hard step half a block above the ground (V2 review). Without it a trunk takes the flat light of its block and the
 * directional face shading; decision at CP-A (docs/00 G.6 V2-j), the fallback is variant C.
 * <p>The four sides show different horizontal crops of the bark ({@link #faceOffset}): the same crop on every side would
 * repeat each motif four times around the trunk (V1 review, choice V1-j). tools/art measures rings at the block period
 * on exactly these crops (texlib.TRUNK_OFFSETS, kept equal to {@link #OFFSETS} by checkArt).
 */
final class TrunkTemplates implements DataProvider {
	/** Margin of the reach, px. */
	static final double EPSILON = 0.01;
	/** Overlap of the reach behind the seam with the arm, px. */
	static final double DELTA = 0.25;
	/** Inset of the cap's sides and top, px: far enough behind the reach of a child to stay out of depth fighting. */
	static final double CAP_INSET = 0.1;
	/** Radius of the item model, px. */
	static final int ITEM_RADIUS = TrunkBlock.DEFAULT_RADIUS;
	/** Sides in the order around the trunk in which the bark continues over the corner (the right edge of a face seen from
	 * outside meets the left edge of the next): north, west, south, east. */
	private static final String[] SIDES = {"north", "west", "south", "east"};
	/**
	 * Left edge of the bark crop (u, px) of each side in {@link #SIDES} order, by radius 1-7. Up to r = 2 the bark wraps
	 * around the trunk (r = 2: the whole 16 px tile). Wider trunks cannot show four different crops, so the offsets spread
	 * over 0…16 − 2r with the most different crops on neighboring sides and the bark continuous over some corners
	 * (r = 3: north → west and east → north).
	 */
	static final int[][] OFFSETS = {
			{4, 6, 8, 10},   // r 1: 8 px of bark around the trunk, continuous over three corners
			{0, 4, 8, 12},   // r 2: the whole tile around the trunk
			{0, 6, 3, 10},   // r 3
			{0, 8, 2, 6},    // r 4
			{0, 6, 2, 4},    // r 5
			{0, 4, 1, 3},    // r 6
			{0, 2, 0, 1},    // r 7: only 0…2 px of freedom
	};

	private final PackOutput.PathProvider models;

	TrunkTemplates(FabricPackOutput output) {
		this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
	}

	/** Model id of the template of radius r. */
	static String template(int r) {
		return "block/template_trunk_r" + r;
	}

	static final String ITEM_TEMPLATE = "block/template_trunk_inventory";

	/** Left edge of the bark crop of a side ({@code north}, {@code west}, {@code south}, {@code east}) at radius r, px. */
	static int faceOffset(int r, String side) {
		for (int i = 0; i < SIDES.length; i++) {
			if (SIDES[i].equals(side)) {
				return OFFSETS[r - 1][i];
			}
		}
		throw new IllegalArgumentException(side);
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		List<CompletableFuture<?>> out = new ArrayList<>();
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			out.add(DataProvider.saveStable(cache, trunk(r), models.json(PolishForests.id(template(r)))));
		}
		out.add(DataProvider.saveStable(cache, item(), models.json(PolishForests.id(ITEM_TEMPLATE))));
		return CompletableFuture.allOf(out.toArray(CompletableFuture[]::new));
	}

	@Override
	public String getName() {
		return "Polish Forests trunk model templates";
	}

	private static JsonObject trunk(int r) {
		double a = 8 - r;
		double b = 8 + r;
		double c = CAP_INSET;
		double e = EPSILON;
		JsonArray elements = new JsonArray();
		elements.add(element(new double[] {a, 0, a}, new double[] {b, 8, b}, r, 0, 0, 8));
		// Inner walls behind the seam at the center: the ε slit under the reach of a child and the ledge under the cap.
		for (String side : SIDES) {
			elements.add(inner(side, r, 0, 8, 0, 8));
		}
		// The cap: four sides up to the top of a side child's reach and a top face lower by the inset.
		for (String side : SIDES) {
			elements.add(inner(side, r, 8, 8 + r - e, 16 - r + e, 16));
		}
		JsonObject top = flat(new double[] {a + e, 8 + r - c, a + e}, new double[] {b - e, 8 + r - c, b - e});
		JsonObject f = new JsonObject();
		f.add("uv", vector(new double[] {8 - r + e, 8 - r + e, 8 + r - e, 8 + r - e}));
		f.addProperty("texture", "#bark");
		top.getAsJsonObject("faces").add("up", f);
		elements.add(top);
		JsonObject reach = element(new double[] {a + e, -8, a + e}, new double[] {b - e, DELTA, b - e}, r, e, 8 - DELTA, 16);
		reach.getAsJsonObject("faces").add("down", square(r, "#end"));
		elements.add(reach);
		JsonObject m = model(elements);
		m.addProperty("ambientocclusion", false);
		return m;
	}

	/**
	 * A flat side face {@link #CAP_INSET} inside the radius, as wide as the reach (ε margins), from y0 to y1 with texture rows
	 * v0 to v1; its columns line up with those of the arm's face on the same side.
	 */
	private static JsonObject inner(String side, int r, double y0, double y1, double v0, double v1) {
		double a = 8 - r;
		double b = 8 + r;
		double c = CAP_INSET;
		double e = EPSILON;
		double plane = switch (side) {
			case "north", "west" -> a + c;
			default -> b - c;
		};
		boolean alongX = side.equals("north") || side.equals("south");
		double[] from = alongX ? new double[] {a + e, y0, plane} : new double[] {plane, y0, a + e};
		double[] to = alongX ? new double[] {b - e, y1, plane} : new double[] {plane, y1, b - e};
		JsonObject el = flat(from, to);
		int u = faceOffset(r, side);
		JsonObject f = new JsonObject();
		f.add("uv", vector(new double[] {u + e, v0, u + 2 * r - e, v1}));
		f.addProperty("texture", "#bark");
		el.getAsJsonObject("faces").add(side, f);
		return el;
	}

	/** An element without faces yet (a flat one has from = to on one axis). */
	private static JsonObject flat(double[] from, double[] to) {
		JsonObject el = new JsonObject();
		el.add("from", vector(from));
		el.add("to", vector(to));
		el.add("faces", new JsonObject());
		return el;
	}

	/** The item: a standing piece of trunk of {@link #ITEM_RADIUS}, a whole block high, the end on both sides. */
	private static JsonObject item() {
		int r = ITEM_RADIUS;
		JsonArray elements = new JsonArray();
		JsonObject e = element(new double[] {8 - r, 0, 8 - r}, new double[] {8 + r, 16, 8 + r}, r, 0, 0, 16);
		e.getAsJsonObject("faces").add("up", square(r, "#end"));
		e.getAsJsonObject("faces").add("down", square(r, "#end"));
		elements.add(e);
		return model(elements);
	}

	private static JsonObject model(JsonArray elements) {
		JsonObject m = new JsonObject();
		m.addProperty("parent", "minecraft:block/block");
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "#bark");
		m.add("textures", textures);
		m.add("elements", elements);
		return m;
	}

	/** A box with bark on its four sides, inset by m from the radius (texture rows v0 to v1, columns as on the arm). */
	private static JsonObject element(double[] from, double[] to, int r, double m, double v0, double v1) {
		JsonObject e = new JsonObject();
		e.add("from", vector(from));
		e.add("to", vector(to));
		JsonObject faces = new JsonObject();
		for (String side : SIDES) {
			int u = faceOffset(r, side);
			JsonObject f = new JsonObject();
			f.add("uv", vector(new double[] {u + m, v0, u + 2 * r - m, v1}));
			f.addProperty("texture", "#bark");
			faces.add(side, f);
		}
		e.add("faces", faces);
		return e;
	}

	/** A face across a trunk of radius r: the middle 2r × 2r of the texture (the end: rings; the tip: bark). */
	private static JsonObject square(int r, String texture) {
		JsonObject f = new JsonObject();
		f.add("uv", vector(new double[] {8 - r, 8 - r, 8 + r, 8 + r}));
		f.addProperty("texture", texture);
		return f;
	}

	private static JsonArray vector(double[] v) {
		JsonArray a = new JsonArray();
		for (double x : v) {
			// Whole numbers as integers, the margins with two decimals (stable output for checkDatagen).
			if (x == Math.rint(x)) {
				a.add((int) x);
			} else {
				a.add(Double.parseDouble(String.format(Locale.ROOT, "%.2f", x)));
			}
		}
		return a;
	}
}
