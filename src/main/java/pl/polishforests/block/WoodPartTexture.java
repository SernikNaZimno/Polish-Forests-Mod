package pl.polishforests.block;

/**
 * The textures of the doors, trapdoors, signs, hanging signs and shelves of the wood sets (step FA7 of
 * docs/04-pionowy-wycinek.md), derived in the client from vanilla's texture of the same part of the set's base wood
 * ({@link WoodSet#partsBase()}, read by id: no pixel of vanilla in the mod) the way the planks of step P2 are: the wood
 * pixels of the part take the planks palette of the species ({@link WoodSet#planksPalette()}) by their brightness, measured
 * against the brightness range of the base's planks (so a door is as much lighter or darker than the planks of its species
 * as vanilla's door is than vanilla's planks); the other pixels (the iron of hinges, handles and chains, the gray and blue
 * of the chains) and the transparency stay as they are. Pure Java on ARGB pixels, deterministic, no game classes.
 *
 * <p>A pixel is wood when it is not transparent and its color is a brown: saturation at least {@link #MIN_SATURATION} and a
 * hue of {@link #MIN_HUE}–{@link #MAX_HUE} degrees (measured on vanilla 26.3: the oak and spruce parts are 20–50 degrees with
 * a saturation of 0.2–0.7, their iron and chains a saturation under 0.12 or a hue of 220 degrees). A wood pixel inside the
 * planks range lies on the ramp of the palette as the planks of P2 do ({@link PlanksTexture#ramp}); a darker one (the dark
 * outlines and the post of a sign) is the darkest tone of the palette darkened in proportion, a lighter one the lightest
 * tone lightened in proportion towards white.
 *
 * <p>Step FA8: the boats and chest boats of the sets, the entity textures ({@code textures/entity/boat/<wood>.png},
 * {@code chest_boat/<wood>.png}) and the items, derived the same way from vanilla's boat textures of the base wood. The
 * chest boat keeps what is not the boat as it is ({@link Part#shared()}, {@link #deriveShared}): only the pixels equal to
 * the boat texture of the base at the same position are recolored (measured on vanilla 26.3: the chest of the entity
 * texture and of the item is the same in every wood, its brown wood stays vanilla's chest).
 */
public final class WoodPartTexture {
	/** Least HSV saturation of a wood pixel. */
	public static final double MIN_SATURATION = 0.18;
	/** Hue range of a wood pixel, degrees. */
	public static final double MIN_HUE = 10;
	public static final double MAX_HUE = 60;

	/**
	 * The derived parts of a wood set: the atlas or the screen they go to, the name of vanilla's texture without its wood
	 * ({@code door_top} in {@code minecraft:block/oak_door_top}).
	 */
	public enum Part {
		DOOR_TOP(Kind.BLOCK, "door_top"),
		DOOR_BOTTOM(Kind.BLOCK, "door_bottom"),
		TRAPDOOR(Kind.BLOCK, "trapdoor"),
		SIGN(Kind.BLOCK, "sign"),
		HANGING_SIGN(Kind.BLOCK, "hanging_sign"),
		SHELF(Kind.BLOCK, "shelf"),
		DOOR_ITEM(Kind.ITEM, "door"),
		SIGN_ITEM(Kind.ITEM, "sign"),
		HANGING_SIGN_ITEM(Kind.ITEM, "hanging_sign"),
		/** The background of the sign edit screen ({@code textures/gui/signs/<wood>.png}). */
		SIGN_SCREEN(Kind.SCREEN, "signs"),
		/** The background of the hanging sign edit screen ({@code textures/gui/hanging_signs/<wood>.png}). */
		HANGING_SIGN_SCREEN(Kind.SCREEN, "hanging_signs"),
		/** Step FA8: the entity texture of the boat ({@code textures/entity/boat/<wood>.png}). */
		BOAT(Kind.ENTITY, "boat"),
		/**
		 * Step FA8: the entity texture of the chest boat ({@code textures/entity/chest_boat/<wood>.png}); only the pixels it
		 * shares with the boat of the same base are wood of the set, the chest stays ({@link #shared()}).
		 */
		CHEST_BOAT(Kind.ENTITY, "chest_boat"),
		/** Step FA8: the flat item of the boat ({@code item/<wood>_boat}). */
		BOAT_ITEM(Kind.ITEM, "boat"),
		/** Step FA8: the flat item of the chest boat ({@code item/<wood>_chest_boat}); the chest stays ({@link #shared()}). */
		CHEST_BOAT_ITEM(Kind.ITEM, "chest_boat");

		private final Kind kind;
		private final String name;

		Part(Kind kind, String name) {
			this.kind = kind;
			this.name = name;
		}

		public Kind kind() {
			return kind;
		}

		/**
		 * Step FA8: the part whose base pixels this part shares (the boat for the chest boat, entity and item): only the
		 * pixels of this part's base equal to the shared base's pixel at the same place are recolored, the others (the chest,
		 * the same in every vanilla chest boat: measured on 26.3, 0 of 1228 chest pixels differ between oak and spruce) stay as
		 * they are ({@link #deriveShared}). Null for a part that is recolored whole.
		 */
		public Part shared() {
			return switch (this) {
				case CHEST_BOAT -> BOAT;
				case CHEST_BOAT_ITEM -> BOAT_ITEM;
				default -> null;
			};
		}

		/**
		 * The path of the texture of a wood in its namespace: a sprite id ({@code block/<wood>_door_top},
		 * {@code item/<wood>_door}) or, for a screen, the texture file ({@code textures/gui/signs/<wood>.png}).
		 */
		public String path(String wood) {
			return switch (kind) {
				case BLOCK -> "block/" + wood + "_" + name;
				case ITEM -> "item/" + wood + "_" + name;
				case SCREEN -> "textures/gui/" + name + "/" + wood + ".png";
				case ENTITY -> "textures/entity/" + name + "/" + wood + ".png";
			};
		}
	}

	/**
	 * Where a derived texture goes: the block atlas, the item atlas, a screen texture of its own, or (step FA8) an entity
	 * texture of its own (outside every atlas, as vanilla's boat textures).
	 */
	public enum Kind {
		BLOCK,
		ITEM,
		SCREEN,
		ENTITY;

		/** Whether the texture is a file of its own in the texture manager, not an atlas sprite. */
		public boolean ownFile() {
			return this == SCREEN || this == ENTITY;
		}
	}

	private WoodPartTexture() {
	}

	/**
	 * The derived part: {@code part} is vanilla's texture of the part ({@code width × height}, row by row), {@code planks}
	 * vanilla's planks of the same wood ({@code planksWidth × planksHeight}; an animation strip counts by its first frame),
	 * the result has the size of the part.
	 */
	public static int[] derive(int[] part, int width, int height, int[] planks, int planksWidth, int planksHeight, int[] palette) {
		if (part.length != width * height) {
			throw new IllegalArgumentException("part of " + part.length + " pixels is not " + width + " x " + height);
		}
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		int frame = planksWidth * PlanksTexture.frameHeight(planksWidth, planksHeight);
		for (int i = 0; i < Math.min(frame, planks.length); i++) {
			if ((planks[i] >>> 24) >= 128) {
				double l = PlanksTexture.luminance(planks[i]);
				lo = Math.min(lo, l);
				hi = Math.max(hi, l);
			}
		}
		int[] out = part.clone();
		if (lo > hi) {
			return out;
		}
		for (int i = 0; i < part.length; i++) {
			if (wood(part[i])) {
				out[i] = (part[i] & 0xff000000) | tone(PlanksTexture.luminance(part[i]), lo, hi, palette);
			}
		}
		return out;
	}

	/**
	 * Step FA8: {@link #derive} of a part that shares its wood with another part of the same base (the chest boat with the
	 * boat, {@link Part#shared()}): a pixel of {@code part} is derived only where {@code shared} ({@code sharedWidth ×
	 * sharedHeight}, the same pixel scale) has the same ARGB at the same place; every other pixel (outside the shared texture
	 * or different from it: the chest) stays as it is.
	 */
	public static int[] deriveShared(int[] part, int width, int height, int[] shared, int sharedWidth, int sharedHeight, int[] planks,
			int planksWidth, int planksHeight, int[] palette) {
		if (shared.length != sharedWidth * sharedHeight) {
			throw new IllegalArgumentException("shared part of " + shared.length + " pixels is not " + sharedWidth + " x " + sharedHeight);
		}
		int[] out = derive(part, width, height, planks, planksWidth, planksHeight, palette);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = y * width + x;
				if (x >= sharedWidth || y >= sharedHeight || shared[y * sharedWidth + x] != part[i]) {
					out[i] = part[i];
				}
			}
		}
		return out;
	}

	/** Whether a pixel is wood (not transparent, a brown: see the class comment). */
	public static boolean wood(int argb) {
		if ((argb >>> 24) == 0) {
			return false;
		}
		int r = (argb >> 16) & 0xff;
		int g = (argb >> 8) & 0xff;
		int b = argb & 0xff;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if (max == 0 || (max - min) < MIN_SATURATION * max) {
			return false;
		}
		double hue = hue(r, g, b, max, min);
		return hue >= MIN_HUE && hue <= MAX_HUE;
	}

	/** The RGB of a wood pixel of brightness {@code l} against the planks range {@code lo … hi}. */
	static int tone(double l, double lo, double hi, int[] palette) {
		if (l < lo) {
			double f = lo > 0 ? Math.max(0, l / lo) : 0;
			return scale(palette[0], f);
		}
		if (l > hi) {
			double g = hi < 255 ? Math.min(1, (l - hi) / (255 - hi)) : 1;
			int c = palette[palette.length - 1];
			int r = (c >> 16) & 0xff;
			int gr = (c >> 8) & 0xff;
			int b = c & 0xff;
			return (lift(r, g) << 16) | (lift(gr, g) << 8) | lift(b, g);
		}
		return PlanksTexture.ramp(palette, hi > lo ? (l - lo) / (hi - lo) : 0.5);
	}

	private static int scale(int rgb, double f) {
		int r = (int) Math.round(((rgb >> 16) & 0xff) * f);
		int g = (int) Math.round(((rgb >> 8) & 0xff) * f);
		int b = (int) Math.round((rgb & 0xff) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static int lift(int c, double g) {
		return (int) Math.round(c + (255 - c) * g);
	}

	private static double hue(int r, int g, int b, int max, int min) {
		double d = max - min;
		double h;
		if (max == r) {
			h = ((g - b) / d) % 6;
		} else if (max == g) {
			h = (b - r) / d + 2;
		} else {
			h = (r - g) / d + 4;
		}
		h *= 60;
		return h < 0 ? h + 360 : h;
	}
}
