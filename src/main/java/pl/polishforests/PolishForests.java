package pl.polishforests;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.climate.ClimateBinding;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.season.Seasons;
import pl.polishforests.worldgen.PolishForestsWorldgen;

/**
 * Entry point of the Polish Forests mod.
 */
public final class PolishForests implements ModInitializer {
	public static final String MOD_ID = "polishforests";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		PolishForestsWorldgen.register();
		PolishForestsCommands.register();
		Seasons.init();
		// Step V13: the fruit of the bilberry through the year (random tick and the catch-up of loaded chunks).
		pl.polishforests.season.BerrySeason.register();
		ClimateBinding.register();
		// Step G1: the tree-felling mods (FallingTree, Tree Harvester) fell the trees of the planner by their parent graph.
		pl.polishforests.compat.FellingCompat.announce();
		LOG.info("Polish Forests: initialization complete (calendar: {})", Seasons.provider().name());
	}
}
