package pl.polishforests.worldgen.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/**
 * The growth form of a tree by its site (step F4 of docs/04-pionowy-wycinek.md, "Projekt F4"): a parameter of the tree
 * request ({@link TreeRequest#form}) besides the species' shape ({@link SpeciesShape}). {@link #NORMAL}, the default,
 * is the tree of the planner as before step F4 (every tree of the slice and of the steps F1-F3); the other forms scale
 * the allometry of the species after the planner's random draws (as {@link TreeSize} does), so the draws of a tree stay
 * in their order, and change its trunk and crown:
 * <ul>
 * <li>{@link Kind#STUNTED}: the low, crooked pine (and birch) of the bog woodland and the raised bog (Vaccinio uliginosi-
 * Pinetum: 6-15 m, research/06 §3.1), a long crown on a leaning, bent trunk;</li>
 * <li>{@link Kind#SLENDER}: the thin pine of the dry pine forest (Cladonio-Pinetum: 10-18 m, a narrow crown);</li>
 * <li>{@link Kind#WINDSWEPT}: the pine of the wind belt of the coastal pine forest (Empetro nigri-Pinetum: 5-15 m), low,
 * leaning away from the sea, with a banner crown: its branches on the windward side short and bare, the crown on the lee
 * side ({@link #leeAzimuth}, from the coast model);</li>
 * <li>{@link Kind#MULTI_STEM}: the downy birch of the birch bog woodland (Vaccinio uliginosi-Betuletum pubescentis), often
 * with two or three stems from one stool (the stool of the alder, step F2) and lower than in a fresh forest;</li>
 * <li>{@link Kind#MONTANE} (step F6): the narrow "candle" spruce of the montane spruce forest;</li>
 * <li>{@link Kind#DWARF} (step F6): the dwarf spruce of the timberline and of the dwarf pine scrub.</li>
 * </ul>
 * In a stand the layer split surfaces scale with the form ({@link Kind#split}), so the low canopy of a bog keeps its
 * crown above the split; the trees of a stand with one form see the same surfaces.
 *
 * @param kind       the form
 * @param leeAzimuth the direction away from the sea (radians, the planner's azimuth: x = cos, z = sin) for
 *                   {@link Kind#WINDSWEPT}; 0 for the other forms
 */
public record TreeForm(Kind kind, float leeAzimuth) {
	/** The planner's own tree (every tree before step F4). */
	public static final TreeForm NORMAL = new TreeForm(Kind.NORMAL, 0);

	/**
	 * The forms (not saved in chunks; their lower-case names stand in the configured features, so new forms only at the
	 * end).
	 */
	public enum Kind {
		/** The planner's own tree. */
		NORMAL(1, 1, 1, 0, 1, 0, 0, 0, 0),
		/** The low, crooked tree of a bog (6-15 m). */
		STUNTED(0.52F, 0.55F, 0.8F, 0.22F, 0.52F, 1.3F, 0.4F, 0, 0),
		/** The thin pine of the dry pine forest (10-18 m). */
		SLENDER(0.72F, 0.62F, 0.72F, 0, 0.72F, 0, 0, 0, 0),
		/** The low pine of the coastal wind belt with a banner crown on the lee side (5-15 m). */
		WINDSWEPT(0.5F, 0.75F, 1.0F, 0.18F, 0.5F, 1.4F, 0.15F, 0, 0.3F),
		/** The birch of the birch bog woodland, often of several stems. */
		MULTI_STEM(0.78F, 0.6F, 0.85F, 0.08F, 0.78F, 0.5F, 0.2F, 0.7F, 0),
		/**
		 * Step F6: the spruce of the upper montane belt (Plagiothecio-Piceetum, 15-25 m), a slender "candle" with a narrow
		 * crown down to low on the trunk.
		 */
		MONTANE(0.82F, 0.86F, 0.62F, 0.12F, 0.82F, 0, 0, 0, 0),
		/**
		 * Step F6: the dwarf spruce of the timberline and the dwarf pine scrub (5-12 m at the timberline, lower among the
		 * dwarf pine), a crooked, short trunk with a crown nearly to the ground.
		 */
		DWARF(0.36F, 0.5F, 0.72F, 0.3F, 0.36F, 0.7F, 0.3F, 0, 0);

		private final float height;
		private final float dbh;
		private final float crown;
		private final float ratio;
		private final float split;
		private final float lean;
		private final float wobble;
		private final float stool;
		private final float windward;

		Kind(float height, float dbh, float crown, float ratio, float split, float lean, float wobble, float stool, float windward) {
			this.height = height;
			this.dbh = dbh;
			this.crown = crown;
			this.ratio = ratio;
			this.split = split;
			this.lean = lean;
			this.wobble = wobble;
			this.stool = stool;
			this.windward = windward;
		}

		/** Factor of the height. */
		public float height() {
			return height;
		}

		/** Factor of the breast-height diameter. */
		public float dbh() {
			return dbh;
		}

		/** Factor of the crown radius. */
		public float crown() {
			return crown;
		}

		/** Crown ratio added (a longer crown of an open, low stand). */
		public float ratio() {
			return ratio;
		}

		/** Factor of the height of the layer split surfaces of a stand of trees of this form. */
		public float split() {
			return split;
		}

		/** Least lean of the trunk top (blocks; 0: the species' own), the crooked trunk of a bog or the coast. */
		public float lean() {
			return lean;
		}

		/** Wobble of the trunk (blocks; 0: the species' own), the bends of a crooked trunk. */
		public float wobble() {
			return wobble;
		}

		/** Least chance of a stool of two or three stems ({@link SpeciesShape#stoolChance}; 0: the species' own). */
		public float stool() {
			return stool;
		}

		/** Crown envelope on the windward side as a share of the lee side (0: no banner crown). */
		public float windward() {
			return windward;
		}

		/** The lower-case name of the configured features. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Codec of the forms by their lower-case names. */
	public static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
		for (Kind k : Kind.values()) {
			if (k.id().equals(name)) {
				return DataResult.success(k);
			}
		}
		return DataResult.error(() -> "unknown tree form " + name);
	}, Kind::id);

	public TreeForm {
		if (kind == null) {
			kind = Kind.NORMAL;
		}
		if (kind != Kind.WINDSWEPT) {
			leeAzimuth = 0;
		}
	}

	/** The form of a kind (the lee of a windswept tree: east until {@link #toward}). */
	public static TreeForm of(Kind kind) {
		return kind == Kind.NORMAL ? NORMAL : new TreeForm(kind, 0);
	}

	/** Whether this is the planner's own tree. */
	public boolean normal() {
		return kind == Kind.NORMAL;
	}

	/** This form with the lee side towards an azimuth (radians); the same form for the forms without a lee side. */
	public TreeForm toward(double azimuth) {
		return kind == Kind.WINDSWEPT ? new TreeForm(kind, (float) azimuth) : this;
	}
}
