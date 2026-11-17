package pl.polishforests.entity.spawn;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * The game rule and the block tag of the fauna of the mod (step FA1, G.62-b).
 *
 * <p>{@code polishforests:forest_fauna} (boolean, default true, category spawning): whether the animals of the mod appear,
 * at chunk generation and from the fauna spawner. Animals already in the world stay. In 26.3 a game rule is a registry
 * entry with an identifier, whose path must be lower case, hence {@code forest_fauna} instead of the plan's
 * {@code forestFauna} (G.63). The rule is saved in the world by that identifier.
 *
 * <p>{@code polishforests:boar_rooting} (boolean, default true, category mobs; step FA3, docs/04 "Projekt FA3"): whether
 * the wild boar roots up the ground (grass to coarse or rooted dirt) and leaves mud at a wallow. Vanilla game rule
 * {@code mob_griefing} off turns it off as well ({@link #rooting}). Lower case for the same reason as above.
 *
 * <p>{@code #polishforests:forest_animals_spawnable_on}: the ground the forest animals of the mod appear on (data/…/tags).
 */
public final class FaunaRules {
	public static final TagKey<Block> SPAWNABLE_ON = TagKey.create(Registries.BLOCK, PolishForests.id("forest_animals_spawnable_on"));
	private static @Nullable GameRule<Boolean> forestFauna;
	private static @Nullable GameRule<Boolean> boarRooting;

	private FaunaRules() {
	}

	public static void register() {
		if (forestFauna == null) {
			forestFauna = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.SPAWNING)
					.buildAndRegister(PolishForests.id("forest_fauna"));
		}
		if (boarRooting == null) {
			boarRooting = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS)
					.buildAndRegister(PolishForests.id("boar_rooting"));
		}
	}

	public static GameRule<Boolean> boarRooting() {
		if (boarRooting == null) {
			throw new IllegalStateException("The fauna game rules are not registered yet");
		}
		return boarRooting;
	}

	/** Whether the animals of the mod may change blocks (rooting, mud): the own rule and vanilla mob_griefing. */
	public static boolean rooting(ServerLevel level) {
		GameRules rules = level.getGameRules();
		return rules.get(boarRooting()) && rules.get(GameRules.MOB_GRIEFING);
	}

	public static GameRule<Boolean> forestFauna() {
		if (forestFauna == null) {
			throw new IllegalStateException("The fauna game rule is not registered yet");
		}
		return forestFauna;
	}

	/** Whether new animals of the mod may appear in the level: the own rule and vanilla's {@code spawn_mobs}. */
	public static boolean spawning(ServerLevel level) {
		GameRules rules = level.getGameRules();
		return rules.get(forestFauna()) && rules.get(GameRules.SPAWN_MOBS);
	}
}
