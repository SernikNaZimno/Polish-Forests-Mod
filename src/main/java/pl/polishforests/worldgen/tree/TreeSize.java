package pl.polishforests.worldgen.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Factors of the tree planner's size of a tree (step S1 of docs/04-pionowy-wycinek.md, the sizes of a tree grown from a
 * sapling): the height, the breast-height diameter and the crown radius the planner takes from the species and the age
 * class ({@link SpeciesShape}) are multiplied by these, before the crown layer and the open-grown form change them. The
 * planner's random draws do not depend on the factors, so a tree with factors below 1 is the smaller version of the tree of
 * the same request without them. Every tree of the world generation has {@link #ONE}.
 *
 * @param height factor of the height
 * @param dbh    factor of the diameter at breast height (the trunk radius)
 * @param crown  factor of the crown radius
 */
public record TreeSize(float height, float dbh, float crown) {
	/** The planner's own size. */
	public static final TreeSize ONE = new TreeSize(1, 1, 1);

	public static final Codec<TreeSize> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0.3F, 1.5F).fieldOf("height").forGetter(TreeSize::height),
			Codec.floatRange(0.3F, 1.5F).fieldOf("dbh").forGetter(TreeSize::dbh),
			Codec.floatRange(0.3F, 1.5F).fieldOf("crown").forGetter(TreeSize::crown))
			.apply(i, TreeSize::new));

	public TreeSize {
		if (!(height > 0 && dbh > 0 && crown > 0)) {
			throw new IllegalArgumentException("tree size factors must be positive: " + height + ", " + dbh + ", " + crown);
		}
	}

	/** Whether this is the planner's own size. */
	public boolean one() {
		return equals(ONE);
	}
}
