package pl.polishforests.worldgen.chunk;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.worldgen.surface.Material;

/**
 * Block states of the surface materials ({@link Material}), looked up in the block registry on first use
 * (docs/03-m2-biomy.md §7.1: lazily, so the mod's own blocks of M4 can be registered before the first chunk).
 */
public final class MaterialStates {
	private MaterialStates() {
	}

	private static final Supplier<BlockState[]> STATES = Suppliers.memoize(() -> {
		Material[] materials = Material.values();
		BlockState[] states = new BlockState[materials.length];
		for (Material m : materials) {
			Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(m.id()));
			if (block == null || !BuiltInRegistries.BLOCK.getKey(block).toString().equals(m.id())) {
				throw new IllegalStateException("Unknown block of the surface material " + m + ": " + m.id());
			}
			states[m.ordinal()] = block.defaultBlockState();
		}
		return states;
	});

	/** States indexed by {@link Material#ordinal()} (the array must not be modified). */
	public static BlockState[] all() {
		return STATES.get();
	}

	public static BlockState of(Material m) {
		return STATES.get()[m.ordinal()];
	}
}
