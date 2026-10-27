package pl.polishforests.season;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import pl.polishforests.block.BerryShrubBlock;

/**
 * The block properties that the season changes in the world after the generation (rule ZW-5 of
 * docs/04-pionowy-wycinek.md, in one place): the generation writes their neutral value, the season sets them in the
 * ticks of the world (step V13: the fruit of the bilberry, {@link BerrySeason}). The determinism hash of the game test and
 * every comparison of generated chunks read the {@link #neutral} state, so two runs at different dates compare equal.
 */
public final class SeasonalProperties {
	/** The seasonal properties with their neutral values. */
	private static final List<Neutral<?>> PROPERTIES = List.of(new Neutral<>(BerryShrubBlock.BERRIES, false));

	private record Neutral<T extends Comparable<T>>(Property<T> property, T value) {
		BlockState apply(BlockState state) {
			return state.hasProperty(property) ? state.setValue(property, value) : state;
		}
	}

	private SeasonalProperties() {
	}

	/** The state with every seasonal property at its neutral value (the state itself when it has none). */
	public static BlockState neutral(BlockState state) {
		BlockState out = state;
		for (Neutral<?> n : PROPERTIES) {
			out = n.apply(out);
		}
		return out;
	}

	/** Whether a property is seasonal. */
	public static boolean seasonal(Property<?> property) {
		for (Neutral<?> n : PROPERTIES) {
			if (n.property() == property) {
				return true;
			}
		}
		return false;
	}

	/** The fruit property of the bilberry (for the tests). */
	public static BooleanProperty berries() {
		return BerryShrubBlock.BERRIES;
	}
}
