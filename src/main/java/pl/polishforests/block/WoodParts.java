package pl.polishforests.block;

import java.util.List;
import net.minecraft.world.level.block.Block;

/**
 * The doors, trapdoors, signs and shelves of one wood set ({@link WoodSet}, step FA7 of docs/04-pionowy-wycinek.md, option
 * O3 of CP-D.2): door, trapdoor, standing sign and its wall form, hanging sign and its wall form, shelf. Block ids
 * {@code <prefix>_door}, {@code _trapdoor}, {@code _sign}, {@code _wall_sign}, {@code _hanging_sign},
 * {@code _wall_hanging_sign}, {@code _shelf}; the wall forms have no item (the sign items place them on a wall), take the
 * loot and the name of their standing form, as vanilla's.
 */
public record WoodParts(Block door, Block trapdoor, Block sign, Block wallSign, Block hangingSign, Block wallHangingSign, Block shelf) {
	/** The id suffixes of the parts, in the order of {@link #all()} (also the order of registration). */
	public static final List<String> SUFFIXES = List.of("_door", "_trapdoor", "_sign", "_wall_sign", "_hanging_sign",
			"_wall_hanging_sign", "_shelf");

	/** Every block of the parts, in the order of registration. */
	public List<Block> all() {
		return List.of(door, trapdoor, sign, wallSign, hangingSign, wallHangingSign, shelf);
	}

	/** The parts with an item of their own (the wall forms are placed by the sign items). */
	public List<Block> withItems() {
		return List.of(door, trapdoor, sign, hangingSign, shelf);
	}

	/** The parts that hold a sign text (standing, wall, hanging and wall hanging sign). */
	public List<Block> signs() {
		return List.of(sign, wallSign, hangingSign, wallHangingSign);
	}
}
