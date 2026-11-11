package pl.polishforests.worldgen.tree;

import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Shape parameters of a species for the tree planner ({@link TreeShapePlan}, step V6 of docs/04-pionowy-wycinek.md): the
 * archetype, the allometry by age class (height, breast-height diameter, crown length and crown radius of a canopy tree)
 * and the parameters of the skeleton, the pipe model and the foliage. A Java record for now; in M3, once the values
 * settle, a JSON file with a codec (plan V6). The values are estimates [S] after docs/research/wycinek/research-technika-drzew.md
 * §3.3 and the M2 report on the Polish forests ({@code 06 §9.2}), to be checked against the norms card (§6, N2) in step V9.
 *
 * <p>Allometry: one block is one meter (rule A7), the trunk radius in pixels is the diameter in centimeters × 0.08 (a
 * block is 16 px). The values are those of a canopy tree (layer a1) of the age class; {@link TreeShapePlan} scales them
 * for the lower layers.
 *
 * @param archetype   the skeleton builder
 * @param height      total height (blocks) by age class
 * @param dbh         diameter at breast height (cm) by age class
 * @param crownRatio  crown length as a share of the height by age class
 * @param crownRadius largest horizontal crown radius (blocks) by age class, in a closed stand
 * @param profile     crown envelope along its length ({@link Profile})
 * @param lean        largest horizontal offset of the top of the trunk (blocks)
 * @param spacing     vertical distance between whorls or lateral branches (blocks)
 * @param perWhorl    branches per whorl (conifers) or per node (broad-leaved trees: 1)
 * @param angleLow    branch angle from the vertical at the crown base (degrees)
 * @param angleHigh   branch angle from the vertical at the top of the crown (degrees)
 * @param bend        turn of a branch from its base to its tip: positive up, negative down (the planner turns the
 *                    branch by about 110–220 degrees times this value, by archetype)
 * @param limbs       number of the main limbs where the trunk forks, each forking again in two (0 = a leader to the top)
 * @param clusterH    horizontal radius of a leaf cluster at a shoot tip (blocks)
 * @param clusterV    vertical radius of a leaf cluster (blocks)
 * @param fill        share of the leaf cells of a cluster that hold a leaf (the rest are holes)
 * @param hollow      empty core of a large crown as a share of the envelope radius (0 = none)
 * @param pipe        exponent n of the pipe model, r_parent^n = Σ r_child^n
 * @param crownBaseRadius trunk radius at the crown base as a share of the radius at breast height (taper of the bole)
 * @param stubs       chance of a dry branch stub per step below the crown base (self-pruning)
 */
public record SpeciesShape(Archetype archetype, ByAge height, ByAge dbh, ByAge crownRatio, ByAge crownRadius, Profile profile,
		float lean, float spacing, int perWhorl, float angleLow, float angleHigh, float bend, int limbs, float clusterH,
		float clusterV, float fill, float hollow, float pipe, float crownBaseRadius, float stubs) {

	/** The skeleton builders (plan V6, research-technika-drzew.md §3.3). */
	public enum Archetype {
		/** Scots pine: a clear bole with dry stubs, the crown in the upper 25–35% of the height, tufts of needles. */
		PINE,
		/** Norway spruce: a cone of whorls down to about half the height, horizontal branches with drooping tips. */
		SPRUCE,
		/** Birch: slender, a leader with ascending branches whose tips hang down, a narrow crown. */
		SLENDER,
		/**
		 * Broad-leaved trees: a bole up to the crown base, then a leader with lateral branches (linden, hornbeam, beech,
		 * maple, ash) or a fork into thick limbs close to the axis (oak).
		 */
		BROAD,
		/**
		 * Silver fir (step F3): a straight column, regular horizontal tiers of whorl branches with flat sprays of needles
		 * along them and slightly upturned tips, a narrow column of needles at the top (the spruce's spire) while the tree
		 * grows; an old canopy fir stops growing in height and its top flattens into the "stork's nest" of nearly equally
		 * long top branches.
		 */
		FIR,
		/**
		 * Swiss stone pine (step TT4): a dense column of whorls from low on the stem to a rounded top, short ascending
		 * branches with upturned tips and dense brush-like tufts of long needles along their outer two thirds; no spire.
		 */
		STONE_PINE,
		/**
		 * European larch (step TT4): a loose, open cone of widely spaced whorls, horizontal branches sagging gently with the
		 * short-shoot clusters of soft needles in a sparse, light foliage and hanging branchlets along them.
		 */
		LARCH;

		/** Needle-leaved (the whorl skeleton of {@code TreeShapePlan}): the pine, the spruce, the fir, the stone pine and the larch. */
		public boolean conifer() {
			return this == PINE || this == SPRUCE || this == FIR || this == STONE_PINE || this == LARCH;
		}
	}

	/** A value for each age class. */
	public record ByAge(float pole, float mature, float old) {
		public float get(AgeClass age) {
			return switch (age) {
				case POLE_STAGE -> pole;
				case MATURE -> mature;
				case OLD_GROWTH -> old;
			};
		}
	}

	/**
	 * Crown envelope: the relative crown radius f(t) at the relative height t in the crown (0 at the base, 1 at the
	 * top): it rises from {@code base} at t = 0 to 1 at t = {@code peak} and falls to {@code top} at t = 1 with the
	 * exponent {@code shape} (1 a straight cone, above 1 a rounded and above 2 a flat top).
	 */
	public record Profile(float base, float peak, float top, float shape) {
		public double at(double t) {
			t = Math.clamp(t, 0, 1);
			if (t < peak) {
				double u = t / peak;
				return base + (1 - base) * u * (2 - u);
			}
			double u = peak >= 1 ? 1 : (t - peak) / (1 - peak);
			return 1 - (1 - top) * StrictMath.pow(u, shape);
		}
	}

	private static final Map<Species, SpeciesShape> SHAPES = new EnumMap<>(Species.class);

	static {
		// Scots pine (fresh pine forest a1: 20-28 blocks; crown in the upper quarter to third, ovoid, flat in old age).
		SHAPES.put(Species.SCOTS_PINE, new SpeciesShape(Archetype.PINE, new ByAge(14, 23, 27), new ByAge(13, 32, 48),
				new ByAge(0.36F, 0.31F, 0.28F), new ByAge(1.4F, 2.2F, 2.9F), new Profile(0.55F, 0.45F, 0.2F, 1.8F), 0.8F,
				1.4F, 3, 76, 55, 0.12F, 0, 1.6F, 1.25F, 0.76F, 0, 2.2F, 0.78F, 0.35F));
		// Norway spruce (admixture: a cone down to about half the height, drooping tips).
		SHAPES.put(Species.SPRUCE, new SpeciesShape(Archetype.SPRUCE, new ByAge(15, 27, 31), new ByAge(14, 34, 48),
				new ByAge(0.62F, 0.52F, 0.46F), new ByAge(2.0F, 3.0F, 3.4F), new Profile(1, 0, 0.05F, 1), 0.2F, 1.0F, 4, 88,
				72, -0.25F, 0, 1.4F, 1.1F, 0.9F, 0, 2.2F, 0.8F, 0.45F));
		// Birch (slender, ascending branches with hanging tips).
		SHAPES.put(Species.BIRCH, new SpeciesShape(Archetype.SLENDER, new ByAge(13, 21, 24), new ByAge(10, 26, 36),
				new ByAge(0.5F, 0.46F, 0.45F), new ByAge(1.6F, 2.4F, 3.0F), new Profile(0.6F, 0.4F, 0.2F, 1.5F), 0.9F, 0.8F,
				1, 48, 30, -0.35F, 0, 1.9F, 1.4F, 0.82F, 0, 2.3F, 0.8F, 0.15F));
		// Pedunculate oak (a1 of the oak-hornbeam forest: a fork into thick, crooked limbs, a broad irregular crown).
		SHAPES.put(Species.OAK, new SpeciesShape(Archetype.BROAD, new ByAge(15, 27, 31), new ByAge(14, 45, 78),
				new ByAge(0.46F, 0.44F, 0.47F), new ByAge(2.6F, 5.4F, 6.8F), new Profile(0.6F, 0.5F, 0.45F, 2.6F), 0.7F,
				1.1F, 1, 72, 45, 0.1F, 3, 2.3F, 1.8F, 0.8F, 0.45F, 2.4F, 0.82F, 0.08F));
		// Small-leaved linden (a1: a dense, low ovate crown with a leader).
		SHAPES.put(Species.LINDEN, new SpeciesShape(Archetype.BROAD, new ByAge(15, 25, 28), new ByAge(13, 40, 64),
				new ByAge(0.6F, 0.66F, 0.68F), new ByAge(2.3F, 4.0F, 5.0F), new Profile(0.75F, 0.3F, 0.15F, 1.4F), 0.5F,
				0.7F, 1, 66, 34, 0.1F, 0, 2.0F, 2.0F, 0.94F, 0.3F, 2.4F, 0.84F, 0.05F));
		// Hornbeam (a2 under the canopy: a dense, rounded ovate crown on a short bole); the archetype is shared with
		// beech, maple and ash.
		SHAPES.put(Species.HORNBEAM, new SpeciesShape(Archetype.BROAD, new ByAge(13, 21, 23), new ByAge(10, 25, 36),
				new ByAge(0.62F, 0.66F, 0.68F), new ByAge(2.2F, 3.4F, 4.2F), new Profile(0.72F, 0.4F, 0.3F, 1.6F), 0.6F,
				0.6F, 1, 64, 36, 0.08F, 0, 2.1F, 1.9F, 0.96F, 0.2F, 2.4F, 0.84F, 0.05F));
		// Beech: a dense dome of nearly horizontal, layered sprays.
		SHAPES.put(Species.BEECH, new SpeciesShape(Archetype.BROAD, new ByAge(15, 28, 32), new ByAge(13, 42, 64),
				new ByAge(0.5F, 0.52F, 0.52F), new ByAge(2.4F, 4.6F, 5.6F), new Profile(0.6F, 0.35F, 0.55F, 3.0F), 0.4F, 0.8F,
				1, 74, 42, 0.0F, 0, 2.4F, 1.9F, 0.93F, 0.4F, 2.4F, 0.84F, 0.04F));
		// Norway maple: broad and round, shorter than the oak.
		SHAPES.put(Species.NORWAY_MAPLE, new SpeciesShape(Archetype.BROAD, new ByAge(14, 22, 25), new ByAge(12, 38, 55),
				new ByAge(0.58F, 0.62F, 0.62F), new ByAge(2.1F, 4.0F, 4.8F), new Profile(0.65F, 0.5F, 0.35F, 2.0F), 0.5F,
				0.9F, 1, 62, 34, 0.1F, 0, 2.2F, 2.0F, 0.88F, 0.4F, 2.4F, 0.84F, 0.05F));
		// Ash: a tall bole, a high, open dome of few, ascending branches with foliage at their ends.
		SHAPES.put(Species.ASH, new SpeciesShape(Archetype.BROAD, new ByAge(16, 28, 32), new ByAge(13, 42, 60),
				new ByAge(0.42F, 0.45F, 0.48F), new ByAge(2.0F, 3.8F, 4.8F), new Profile(0.5F, 0.6F, 0.45F, 2.2F), 0.5F,
				1.6F, 1, 50, 26, 0.12F, 0, 2.5F, 2.1F, 0.8F, 0.4F, 2.4F, 0.82F, 0.06F));
		// Step F2. Black alder (a1 of the alder carr and the ash-alder forest: a straight bole with a leader to the top, a
		// narrow ovate crown of short, nearly horizontal branches; 20-28 m, docs/research 08); multi-stem on a stool in the
		// stands ({@link #stoolChance}).
		SHAPES.put(Species.BLACK_ALDER, new SpeciesShape(Archetype.BROAD, new ByAge(14, 24, 27), new ByAge(11, 32, 46),
				new ByAge(0.5F, 0.5F, 0.52F), new ByAge(1.8F, 2.9F, 3.6F), new Profile(0.7F, 0.35F, 0.15F, 1.4F), 0.4F,
				0.75F, 1, 72, 44, 0.04F, 0, 1.9F, 1.7F, 0.9F, 0.2F, 2.4F, 0.84F, 0.1F));
		// Elm (field and fluttering elm of the ash-alder forest, also in its subcanopy: a fork into a few ascending limbs
		// and a broad, rounded crown, a buttressed base).
		SHAPES.put(Species.ELM, new SpeciesShape(Archetype.BROAD, new ByAge(14, 26, 30), new ByAge(12, 44, 72),
				new ByAge(0.5F, 0.52F, 0.55F), new ByAge(2.4F, 4.8F, 6.0F), new Profile(0.55F, 0.6F, 0.5F, 2.4F), 0.6F,
				1.0F, 1, 66, 38, 0.12F, 3, 2.2F, 1.8F, 0.88F, 0.4F, 2.4F, 0.82F, 0.06F));
		// Step F3. Silver fir (a1 of the upland fir forest, an admixture of the Carpathian beech forest: 30-40 m, a straight
		// column, a cone of horizontal tiers in youth, the flat top of the old trees; research/06 §3.3, §9): the cone of the
		// young and mature fir (the "stork's nest" of the old canopy fir in TreeShapePlan), horizontal branches with flat
		// sprays and a slight upturn of the tips, regular whorls of four to five.
		SHAPES.put(Species.FIR, new SpeciesShape(Archetype.FIR, new ByAge(15, 28, 33), new ByAge(14, 40, 62),
				new ByAge(0.62F, 0.52F, 0.44F), new ByAge(2.0F, 3.0F, 3.6F), new Profile(1, 0, 0.08F, 1), 0.2F, 1.05F, 4, 90,
				78, 0.06F, 0, 1.35F, 0.85F, 0.92F, 0, 2.2F, 0.8F, 0.4F));
		// Sycamore maple (a1 of the sycamore ravine forest, an admixture of the Carpathian beech forest: a tall bole and a
		// broad, high-domed crown like the Norway maple's but larger and denser, 25-35 m).
		SHAPES.put(Species.SYCAMORE_MAPLE, new SpeciesShape(Archetype.BROAD, new ByAge(15, 27, 31), new ByAge(13, 42, 66),
				new ByAge(0.58F, 0.6F, 0.6F), new ByAge(2.2F, 4.4F, 5.4F), new Profile(0.65F, 0.5F, 0.45F, 2.2F), 0.5F,
				0.9F, 1, 64, 36, 0.1F, 0, 2.3F, 2.0F, 0.9F, 0.4F, 2.4F, 0.84F, 0.05F));
		// Step F5 (docs/research 06 §3.9, §9). White willow (a1 of the willow floodplain forest, Salicetum albae: 15-25 m, a
		// stout, short bole, often leaning towards the water or forked low into a few thick ascending limbs, a broad, loose,
		// rounded crown with hanging tips): the largest lean of the broad-leaved trees, a fork into three limbs, a thick
		// trunk for its height; 25% of the willows of a stand grow as two or three stems from the base (stoolChance).
		SHAPES.put(Species.WHITE_WILLOW, new SpeciesShape(Archetype.BROAD, new ByAge(12, 20, 23), new ByAge(14, 58, 95),
				new ByAge(0.6F, 0.58F, 0.56F), new ByAge(2.6F, 5.2F, 6.6F), new Profile(0.7F, 0.45F, 0.5F, 2.2F), 3.8F,
				0.9F, 1, 64, 34, -0.12F, 3, 2.2F, 1.8F, 0.86F, 0.35F, 2.4F, 0.8F, 0.08F));
		// Poplar (one species until M3 for the white and the black poplar of the floodplain forests, Populetum albae: 25-40 m,
		// the tallest broad-leaved tree of the lowlands, a long straight bole and a broad, high crown of a few thick ascending
		// limbs; the planner's leader to the top with long ascending laterals, the limbs of the old poplar in the
		// laterals).
		SHAPES.put(Species.POPLAR, new SpeciesShape(Archetype.BROAD, new ByAge(18, 31, 36), new ByAge(16, 55, 90),
				new ByAge(0.5F, 0.52F, 0.55F), new ByAge(2.6F, 5.4F, 7.0F), new Profile(0.6F, 0.55F, 0.45F, 2.4F), 0.6F,
				1.2F, 1, 60, 30, 0.08F, 0, 2.3F, 2.0F, 0.86F, 0.45F, 2.4F, 0.82F, 0.06F));
		// Step F6 (docs/research 06 §3.9, §9). Gray alder (a1 of the gray alder forest, Alnetum incanae: 12-20 m, up to 25,
		// dbh 15-30 cm, a slender straight stem with a leader to the top and a narrow ovate crown of short ascending branches,
		// 5-8 m long and 3-5 m wide): narrower and lower than the black alder, often of two or three stems from the stool
		// and the root suckers (stoolChance).
		SHAPES.put(Species.GRAY_ALDER, new SpeciesShape(Archetype.BROAD, new ByAge(11, 17, 20), new ByAge(9, 24, 34),
				new ByAge(0.52F, 0.5F, 0.52F), new ByAge(1.5F, 2.2F, 2.7F), new Profile(0.7F, 0.38F, 0.15F, 1.4F), 0.5F,
				0.7F, 1, 64, 36, 0.06F, 0, 1.8F, 1.6F, 0.88F, 0.15F, 2.4F, 0.84F, 0.12F));
		// Rowan (an admixture of the montane spruce forest, the timberline, the dwarf pine scrub and the regeneration of the
		// pine and mixed forests: 8-15 m, up to 20, dbh 15-30 cm, a short bole and a loose, rounded to ovate crown of a few
		// ascending branches, 5-8 m long and 3-5 m wide, the pinnate leaves at the shoot ends).
		SHAPES.put(Species.ROWAN, new SpeciesShape(Archetype.BROAD, new ByAge(7, 11, 14), new ByAge(8, 20, 30),
				new ByAge(0.6F, 0.6F, 0.62F), new ByAge(1.4F, 2.2F, 2.8F), new Profile(0.65F, 0.45F, 0.35F, 1.8F), 0.6F,
				0.8F, 1, 56, 30, 0.08F, 0, 1.9F, 1.6F, 0.8F, 0.2F, 2.4F, 0.84F, 0.06F));
		// Step TT4 (docs/research 06 §3.10, §9). Swiss stone pine (a1 of the spruce-stone pine forest of the upper Tatra
		// regiel, Pino cembrae-Piceetum: 10-15 m, up to 20, dbh 30-60 cm, up to 1 m and more in old trees of 300-600 years,
		// a dense crown 6-10 m long and 4-6 m wide, conical in youth, cylindrical with a rounded top later; a slow-growing,
		// windfirm tree of the timberline): a long crown from low on the stem, many short ascending branches in close whorls
		// of five with upturned tips, dense tufts; thick for its height.
		SHAPES.put(Species.STONE_PINE, new SpeciesShape(Archetype.STONE_PINE, new ByAge(9, 15, 18), new ByAge(14, 42, 75),
				new ByAge(0.75F, 0.68F, 0.6F), new ByAge(1.5F, 2.3F, 2.9F), new Profile(0.85F, 0.3F, 0.45F, 2.2F), 0.3F,
				0.85F, 5, 72, 48, 0.14F, 0, 1.45F, 1.2F, 0.92F, 0, 2.2F, 0.75F, 0.25F));
		// European larch (an admixture of the Tatra regiel on the sunny slopes and of the spruce-stone pine forest: 25-35 m,
		// dbh 40-70 cm, a loose conical crown 8-12 m long and 5-7 m wide with a closure of only 30-45%, a straight stem,
		// light-demanding, self-pruning high; research/06 §9): widely spaced whorls of three to four horizontal branches,
		// gently sagging, a sparse foliage of small clusters (fill) with hanging branchlets.
		SHAPES.put(Species.LARCH, new SpeciesShape(Archetype.LARCH, new ByAge(15, 28, 32), new ByAge(14, 42, 62),
				new ByAge(0.55F, 0.46F, 0.42F), new ByAge(1.8F, 2.9F, 3.4F), new Profile(1, 0, 0.08F, 1.1F), 0.3F, 1.3F, 3,
				95, 70, -0.2F, 0, 1.2F, 0.85F, 0.62F, 0, 2.2F, 0.78F, 0.4F));
	}

	/**
	 * Step F2: the chance that a tree of a stand (not a lone tree, not the regeneration) of the species grows as two or
	 * three stems from one stool, the coppice shoots of an alder after a cut or a dieback (20-40% of the alders of the
	 * carr, docs/04 "Projekt F2"); 0 for every other species. Drawn from a hash of its own salt, so the other draws of
	 * the tree stay as they are.
	 */
	public static double stoolChance(Species species) {
		// Step F5: the white willow, often of two or three stems from the base (old pollards and coppice shoots on the banks).
		// Step F6: the gray alder, of two or three stems from the stool and its root suckers in a third of the trees.
		return species == Species.BLACK_ALDER ? 0.3 : species == Species.WHITE_WILLOW ? 0.25 : species == Species.GRAY_ALDER ? 0.35 : 0;
	}

	/**
	 * Step F5: whether the species forks low into its thick limbs, below the floor of the canopy layer of a stand (the white
	 * willow: a short bole and a fork at a third of its height); the limbs of the fork may then stand below that floor next
	 * to the trunk. False for every species of the earlier steps, so their trees stay as they were.
	 */
	public static boolean lowFork(Species species) {
		return species == Species.WHITE_WILLOW;
	}

	/**
	 * Step F6: whether the species is a tree of the lower layers that never reaches the canopy's floor in a stand (the
	 * rowan, 7-14 m): a canopy request of it in a stand is planned as a tree of the subcanopy ({@link TreeShapePlan}), so
	 * its crown keeps to the subcanopy's split surfaces instead of being cut away; a lone tree keeps its own crown. False
	 * for every species of the earlier steps.
	 */
	public static boolean understory(Species species) {
		return species == Species.ROWAN;
	}

	/**
	 * Step TT4: whether a canopy tree of the species keeps a long crown down into the subcanopy's height in a stand (the
	 * Swiss stone pine, 10-20 m with a crown from low on the stem in the loose stand of the timberline): its crown takes the
	 * lower split surface as its floor instead of the upper one, as the open-land side of an edge tree does. False for
	 * every species of the earlier steps, so their trees stay as they were.
	 */
	public static boolean longCrown(Species species) {
		return species == Species.STONE_PINE;
	}

	/** The shape of a species of the slice, or null when the planner has none (species outside the slice). */
	public static @Nullable SpeciesShape of(Species species) {
		return SHAPES.get(species);
	}

	/** Whether the planner has a shape for the species. */
	public static boolean supported(Species species) {
		return SHAPES.containsKey(species);
	}

	/** The species with a shape, in the order of {@link Species}. */
	public static java.util.Set<Species> species() {
		return java.util.Collections.unmodifiableSet(SHAPES.keySet());
	}
}
