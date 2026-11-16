package pl.polishforests.gametest.probe;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * The axis gauge of step V8 (calibration of the GeckoLib axes and rotations in the game): an entity that exists only in
 * the client game test run {@code -Pgametest=fauna}, in the probe namespace {@value RenderProbes#NAMESPACE} (never in the
 * mod jar or the registry snapshot). Its model comes from {@code tools/fauna} ({@code species.axis_gauge}: arms turned by
 * known angles about each axis, a chain of turned bones, a bone moved and turned only by the animation
 * {@code calib.pose}, markers on the left and the front). {@link AxisGauge#setPosed(boolean)} plays that animation.
 */
public final class FaunaProbes implements ModInitializer {
	private static @Nullable EntityType<AxisGauge> axisGauge;

	/** True only in the client game test run of the fauna ({@code -Pgametest=fauna}). */
	public static boolean enabled() {
		return "fauna".equals(System.getProperty("polishforests.gametest"));
	}

	@Override
	public void onInitialize() {
		if (!enabled()) {
			return;
		}
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, RenderProbes.id("axis_gauge"));
		axisGauge = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(AxisGauge::new, MobCategory.MISC).sized(0.6F, 1.2F).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(axisGauge, Mob.createMobAttributes());
		PolishForests.LOG.info("[fauna] axis gauge registered: {}", key.identifier());
	}

	public static EntityType<AxisGauge> axisGauge() {
		if (axisGauge == null) {
			throw new IllegalStateException("The axis gauge is registered only with -Pgametest=fauna");
		}
		return axisGauge;
	}

	/** The calibration model: stands still, without AI; posed or at rest. */
	public static final class AxisGauge extends PathfinderMob implements GeoEntity {
		static final RawAnimation POSE = RawAnimation.begin().thenLoop("calib.pose");
		private static final EntityDataAccessor<Boolean> DATA_POSED = SynchedEntityData.defineId(AxisGauge.class,
				EntityDataSerializers.BOOLEAN);
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		AxisGauge(EntityType<? extends AxisGauge> type, Level level) {
			super(type, level);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			super.defineSynchedData(builder);
			builder.define(DATA_POSED, false);
		}

		public void setPosed(boolean posed) {
			entityData.set(DATA_POSED, posed);
		}

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new AnimationController<>("main", 0,
					test -> entityData.get(DATA_POSED) ? test.setAndContinue(POSE) : PlayState.STOP));
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}
	}
}
