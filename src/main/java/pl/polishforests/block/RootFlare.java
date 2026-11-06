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
	NORWAY_MAPLE(TreeTrunk.NORWAY_MAPLE),
	/** Step F2. */
	BLACK_ALDER(TreeTrunk.BLACK_ALDER),
	/** Step F2: the elm's buttresses (Ulmus laevis builds plank buttresses on wet ground). */
	ELM(TreeTrunk.ELM),
	/** Step F3: the fir's buttresses (a deep heart and tap root system with strong flares on the mountain slopes). */
	FIR(TreeTrunk.FIR),
	/** Step F3: the sycamore maple's flares (a heart root system that holds the scree of the ravines). */
	SYCAMORE_MAPLE(TreeTrunk.SYCAMORE_MAPLE),
	/** Step F5: the white willow's flares (a broad, shallow heart root system on the wet alluvium). */
	WHITE_WILLOW(TreeTrunk.WHITE_WILLOW),
	/** Step F5: the poplar's buttresses (the large flares of the old floodplain poplars). */
	POPLAR(TreeTrunk.POPLAR),
	/** Step F6: the gray alder's low flares (a shallow, wide root system with root suckers on the gravel of mountain streams). */
	GRAY_ALDER(TreeTrunk.GRAY_ALDER),
	/** Step F6: the rowan's small flares (a shallow heart root system, often over a stone or a rotten log). */
	ROWAN(TreeTrunk.ROWAN);

	private final TreeTrunk trunk;

	RootFlare(TreeTrunk trunk) {
		this.trunk = trunk;
	}

	/** The trunk whose bark, end, map color and loot the flare takes. */
	public TreeTrunk trunk() {
		return trunk;
	}

	/** Whether the flares are flat surface roots (the spruce) rather than fins ({@link RootFlareBlock#flat}). */
	public boolean flat() {
		return this == SPRUCE;
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
