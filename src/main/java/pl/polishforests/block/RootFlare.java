package pl.polishforests.block;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The root flare blocks (step I1 of docs/04-pionowy-wycinek.md, roots): one {@link RootFlareBlock} per species of the
 * slice with the bark of its lower trunk ({@link #trunk()}). The block id ({@link #id()}) goes into the saved worlds; new
 * flares only at the end (registry snapshot, PW-11).
 */
public enum RootFlare {
	SCOTS_PINE(TreeTrunk.SCOTS_PINE),
	SPRUCE(TreeTrunk.SPRUCE),
	BIRCH(TreeTrunk.BIRCH),
	OAK(TreeTrunk.OAK),
	HORNBEAM(TreeTrunk.HORNBEAM),
	LINDEN(TreeTrunk.LINDEN),
	BEECH(TreeTrunk.BEECH),
	ASH(TreeTrunk.ASH),
	NORWAY_MAPLE(TreeTrunk.NORWAY_MAPLE);

	private final TreeTrunk trunk;

	RootFlare(TreeTrunk trunk) {
		this.trunk = trunk;
	}

	/** The trunk whose bark, end, map color and loot the flare takes. */
	public TreeTrunk trunk() {
		return trunk;
	}

	public Species species() {
		return trunk.species();
	}

	/** Block and item path ({@code <species>_root_flare}). */
	public String id() {
		return trunk.id().replace("_trunk", "_root_flare");
	}

	public String englishName() {
		return trunk.englishName().replace(" Trunk", " Root Flare");
	}

	public String polishName() {
		return trunk.polishName().replace("Pień ", "Nabieg korzeniowy ");
	}

	/** The flare of a species, or null. */
	public static @Nullable RootFlare of(Species species) {
		for (RootFlare f : values()) {
			if (f.species() == species) {
				return f;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
