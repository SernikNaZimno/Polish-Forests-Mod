package pl.polishforests.block;

import java.util.List;
import net.minecraft.world.level.block.Block;

/**
 * The blocks of one wood set ({@link WoodSet}, step P2): the four pillars, the planks and the products of the planks, in
 * the order of vanilla's building blocks tab (log, wood, stripped log, stripped wood, planks, stairs, slab, fence, fence
 * gate, pressure plate, button).
 */
public record WoodBlocks(Block log, Block wood, Block strippedLog, Block strippedWood, Block planks, Block stairs, Block slab,
		Block fence, Block fenceGate, Block pressurePlate, Block button) {
	/** Every block of the set, in the order of the building blocks tab. */
	public List<Block> all() {
		return List.of(log, wood, strippedLog, strippedWood, planks, stairs, slab, fence, fenceGate, pressurePlate, button);
	}

	/** The full blocks that make planks (log, wood, stripped log, stripped wood). */
	public List<Block> fullLogs() {
		return List.of(log, wood, strippedLog, strippedWood);
	}

	/** The products of the planks (stairs, slab, fence, fence gate, pressure plate, button). */
	public List<Block> products() {
		return List.of(stairs, slab, fence, fenceGate, pressurePlate, button);
	}
}
