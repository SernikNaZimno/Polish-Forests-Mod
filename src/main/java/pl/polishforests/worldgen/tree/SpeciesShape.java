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
 * @param bend        bend of the branch tips: positive up, negative down (share of the branch length)
 * @param limbs       number of the main limbs where the trunk forks (0 = a leader to the top)
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
		BROAD
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
				1.4F, 3, 70, 50, 0.12F, 0, 1.6F, 1.25F, 0.76F, 0, 2.2F, 0.78F, 0.35F));
		// Norway spruce (admixture: a cone down to about half the height, drooping tips).
		SHAPES.put(Species.SPRUCE, new SpeciesShape(Archetype.SPRUCE, new ByAge(15, 27, 31), new ByAge(14, 34, 48),
				new ByAge(0.62F, 0.52F, 0.46F), new ByAge(2.0F, 3.0F, 3.4F), new Profile(1, 0, 0.05F, 1), 0.2F, 1.0F, 4, 88,
				72, -0.25F, 0, 1.4F, 1.1F, 0.9F, 0, 2.2F, 0.8F, 0.45F));
		// Birch (slender, ascending branches with hanging tips).
		SHAPES.put(Species.BIRCH, new SpeciesShape(Archetype.SLENDER, new ByAge(13, 21, 24), new ByAge(10, 26, 36),
				new ByAge(0.5F, 0.46F, 0.45F), new ByAge(1.8F, 2.8F, 3.4F), new Profile(0.6F, 0.4F, 0.2F, 1.5F), 0.9F, 0.8F,
				1, 48, 30, -0.35F, 0, 1.9F, 1.4F, 0.82F, 0, 2.3F, 0.8F, 0.15F));
		// Pedunculate oak (a1 of the oak-hornbeam forest: thick limbs close to the axis, a broad irregular crown).
		SHAPES.put(Species.OAK, new SpeciesShape(Archetype.BROAD, new ByAge(15, 27, 31), new ByAge(14, 45, 78),
				new ByAge(0.46F, 0.52F, 0.56F), new ByAge(2.4F, 4.3F, 5.6F), new Profile(0.55F, 0.45F, 0.35F, 2.2F), 0.7F,
				1.1F, 1, 72, 40, 0.1F, 3, 2.3F, 1.7F, 0.8F, 0.45F, 2.4F, 0.82F, 0.08F));
		// Small-leaved linden (a1: a dense ovate crown with a leader).
		SHAPES.put(Species.LINDEN, new SpeciesShape(Archetype.BROAD, new ByAge(15, 25, 28), new ByAge(13, 40, 64),
				new ByAge(0.55F, 0.6F, 0.62F), new ByAge(2.2F, 3.7F, 4.7F), new Profile(0.65F, 0.35F, 0.2F, 1.6F), 0.5F,
				0.8F, 1, 62, 30, 0.1F, 0, 2.0F, 2.0F, 0.9F, 0.4F, 2.4F, 0.84F, 0.05F));
		// Hornbeam (a2 under the canopy: dense, lower, rounded); the archetype is shared with beech, maple and ash.
		SHAPES.put(Species.HORNBEAM, new SpeciesShape(Archetype.BROAD, new ByAge(13, 21, 23), new ByAge(10, 25, 36),
				new ByAge(0.6F, 0.65F, 0.66F), new ByAge(2.2F, 3.6F, 4.4F), new Profile(0.65F, 0.45F, 0.25F, 1.8F), 0.6F,
				0.7F, 1, 64, 36, 0.08F, 0, 2.2F, 1.7F, 0.92F, 0.4F, 2.4F, 0.84F, 0.05F));
		SHAPES.put(Species.BEECH, new SpeciesShape(Archetype.BROAD, new ByAge(15, 28, 32), new ByAge(13, 42, 64),
				new ByAge(0.5F, 0.6F, 0.6F), new ByAge(2.2F, 4.1F, 5.1F), new Profile(0.6F, 0.45F, 0.25F, 1.9F), 0.4F, 0.8F,
				1, 70, 38, 0.06F, 0, 2.3F, 1.6F, 0.9F, 0.45F, 2.4F, 0.84F, 0.04F));
		SHAPES.put(Species.NORWAY_MAPLE, new SpeciesShape(Archetype.BROAD, new ByAge(14, 24, 27), new ByAge(12, 38, 55),
				new ByAge(0.55F, 0.6F, 0.6F), new ByAge(2.0F, 3.8F, 4.6F), new Profile(0.6F, 0.45F, 0.25F, 2.0F), 0.5F,
				0.9F, 1, 62, 34, 0.1F, 0, 2.3F, 1.7F, 0.88F, 0.4F, 2.4F, 0.84F, 0.05F));
		// Ash: a tall bole, a high and open crown of few, steep branches.
		SHAPES.put(Species.ASH, new SpeciesShape(Archetype.BROAD, new ByAge(16, 28, 32), new ByAge(13, 42, 60),
				new ByAge(0.45F, 0.48F, 0.5F), new ByAge(2.0F, 3.8F, 4.8F), new Profile(0.55F, 0.5F, 0.3F, 2.0F), 0.5F,
				1.2F, 1, 58, 30, 0.12F, 0, 2.1F, 1.5F, 0.8F, 0.4F, 2.4F, 0.82F, 0.06F));
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
