package pl.polishforests.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the compatibility mixins of step G1 ({@code polishforests.compat.mixins.json}) only when their mod is loaded:
 * {@code FallingTree*} with FallingTree, {@code TreeHarvester*} with Tree Harvester. The injections do not require their
 * targets: a renamed method in a new version of the mod turns the compatibility off instead of stopping the game (the
 * game test {@code -Pgametest=slice -Psites=felling} shows it).
 */
public final class FellingMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (name.startsWith("FallingTree")) {
			return FabricLoader.getInstance().isModLoaded(FellingCompat.FALLING_TREE);
		}
		if (name.startsWith("TreeHarvester")) {
			return FabricLoader.getInstance().isModLoaded(FellingCompat.TREE_HARVESTER);
		}
		return false;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
