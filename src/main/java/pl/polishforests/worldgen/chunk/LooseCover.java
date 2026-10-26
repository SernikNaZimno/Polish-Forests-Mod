package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Thickness of the loose cover above the rock in the lowland (world setting {@code loose_cover}, step K1 of the slice,
 * docs/04-pionowy-wycinek.md). The landscape model always gives the cover in meters ({@code coverDepth}: 40–100 m in the
 * lowland, as in Poland); the surface plan turns it into blocks. The serialized names are stored in saved worlds and must
 * not change.
 */
public enum LooseCover implements StringRepresentable {
	/**
	 * The model cover converted to blocks: tens of blocks of sand or clay in the lowland, so stone lies deep there. The
	 * cover of every world created before step K1 (a missing {@code loose_cover} reads as this value).
	 */
	REALISTIC("realistic"),
	/**
	 * Thin cover for gameplay (the default of new worlds in both scales): in the lowland the soil and about 3–7 blocks of
	 * deposits (4–10 blocks in all, varying smoothly), stone below; the foothills and mountains keep their thin model cover.
	 */
	THIN("thin");

	public static final Codec<LooseCover> CODEC = StringRepresentable.fromEnum(LooseCover::values);

	private final String id;

	LooseCover(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}
}
