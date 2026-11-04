package pl.polishforests.block;

import java.util.Locale;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The stripped thin trunks (step P1 of docs/04-pionowy-wycinek.md, decision U-2 of docs/00 G.36): one {@link TrunkBlock}
 * of bare wood per species with trunks of the mod, made by stripping a trunk with an axe as vanilla strips a log (the
 * radius, the parent and the water stay, {@code CopyPropertiesProvider}). The four barks of the Scots pine (lower,
 * transition, upper transition, "mirror") cover the same wood, so they all strip into the one stripped pine trunk (default
 * choice P1-b). The block id ({@link #id()}) goes into the saved worlds (registry snapshot, PW-11): new stripped trunks only
 * at the end.
 *
 * <p>Loot as a trunk, with the stripped log of the species ({@link #log()}) where the trunk gives its log (default choice
 * P1-d): the own stripped Scots pine log, vanilla's stripped spruce and birch logs (D2), and for the others the stripped
 * counterpart of the closest vanilla log of {@link TreeTrunk#log()} until their own wood comes (U-8).
 */
public enum StrippedTrunk {
	SCOTS_PINE(TreeTrunk.SCOTS_PINE, MapColor.WOOD),
	SPRUCE(TreeTrunk.SPRUCE, MapColor.PODZOL),
	BIRCH(TreeTrunk.BIRCH, MapColor.SAND),
	OAK(TreeTrunk.OAK, MapColor.WOOD),
	HORNBEAM(TreeTrunk.HORNBEAM, MapColor.QUARTZ),
	LINDEN(TreeTrunk.LINDEN, MapColor.SAND),
	BEECH(TreeTrunk.BEECH, MapColor.TERRACOTTA_WHITE),
	ASH(TreeTrunk.ASH, MapColor.SAND),
	NORWAY_MAPLE(TreeTrunk.NORWAY_MAPLE, MapColor.SAND);

	private final TreeTrunk trunk;
	private final MapColor mapColor;

	StrippedTrunk(TreeTrunk trunk, MapColor mapColor) {
		this.trunk = trunk;
		this.mapColor = mapColor;
	}

	/** The first trunk of the species (its lower bark), whose names, end and log this stripped trunk follows. */
	public TreeTrunk trunk() {
		return trunk;
	}

	public Species species() {
		return trunk.species();
	}

	public MapColor mapColor() {
		return mapColor;
	}

	/** Block and item path ({@code stripped_<species>_trunk}). */
	public String id() {
		return "stripped_" + trunk.id();
	}

	public String englishName() {
		return "Stripped " + trunk.englishName();
	}

	public String polishName() {
		return trunk.polishName().replace("Pień ", "Okorowany pień ");
	}

	/** The stripped log a stripped trunk of radius ≥ 3 drops (and 1-2 of a stem base, S1). */
	public Block log() {
		return switch (this) {
			case SCOTS_PINE -> ModBlocks.strippedPineLog();
			case SPRUCE -> Blocks.STRIPPED_SPRUCE_LOG;
			case BIRCH -> Blocks.STRIPPED_BIRCH_LOG;
			case OAK, LINDEN, ASH, NORWAY_MAPLE -> Blocks.STRIPPED_OAK_LOG;
			case HORNBEAM, BEECH -> Blocks.STRIPPED_PALE_OAK_LOG;
		};
	}

	/** The stripped trunk a trunk becomes under the axe (every bark of a species gives the same wood). */
	public static StrippedTrunk of(TreeTrunk trunk) {
		for (StrippedTrunk s : values()) {
			if (s.species() == trunk.species()) {
				return s;
			}
		}
		throw new IllegalArgumentException("No stripped trunk for " + trunk);
	}

	@Override
	public String toString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
