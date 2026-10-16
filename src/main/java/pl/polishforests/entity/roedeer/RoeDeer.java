package pl.polishforests.entity.roedeer;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * The European roe deer, step V8 of the vertical slice (docs/04-pionowy-wycinek.md §3): an entity for the model only. It
 * has no AI goals (it stands where it was put, with vanilla physics), no drops (decision C8) and no breeding; step V14
 * gives it the shared animal base, the brain, the schedule and the spawn rules.
 *
 * <p>Variants: a doe or a buck (the renderer hides the antlers of a doe) in the summer (V-IX, red-brown) or winter coat
 * (X-IV, grey-brown; the buck's antlers in velvet), each with its own texture. Both are chosen when the animal spawns
 * (sex 50 : 50, coat from the sub-season of the season source) and saved; V14 changes the coat with the season.
 *
 * <p>The model, the animations and the textures come from {@code tools/fauna} (GeckoLib files in
 * {@code assets/polishforests/geckolib/}). {@link Display} chooses the animation: {@link Display#AUTO} walks, runs or
 * stands by the movement; the other values hold one animation (or none: the rest pose of the model), so the fauna game test
 * can show and photograph each of them.
 */
public class RoeDeer extends Animal implements GeoEntity {
	/** The animations of the model ({@code tools/fauna/animations.py}). */
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	public static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	/** Speed above which a moving deer runs instead of walking (blocks per tick, horizontal). */
	private static final double RUN_SPEED = 0.12;

	private static final EntityDataAccessor<Boolean> DATA_BUCK = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_WINTER_COAT = SynchedEntityData.defineId(RoeDeer.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.INT);

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		/** Walk, run or stand (idle) by the movement. */
		AUTO,
		/** No animation: the rest pose of the model, as in the .geo.json (the 4-side check of the fauna game test). */
		NONE,
		IDLE,
		WALK,
		RUN,
		EAT,
		ALERT,
		REST
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public RoeDeer(EntityType<? extends RoeDeer> type, Level level) {
		super(type, level);
	}

	/** Roe deer: about 25 kg, a light and fast runner (V14 calibrates the speeds with the AI). */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BUCK, false);
		builder.define(DATA_WINTER_COAT, false);
		builder.define(DATA_DISPLAY, Display.AUTO.ordinal());
	}

	public boolean isBuck() {
		return entityData.get(DATA_BUCK);
	}

	public void setBuck(boolean buck) {
		entityData.set(DATA_BUCK, buck);
	}

	public boolean hasWinterCoat() {
		return entityData.get(DATA_WINTER_COAT);
	}

	public void setWinterCoat(boolean winter) {
		entityData.set(DATA_WINTER_COAT, winter);
	}

	public Display display() {
		int i = entityData.get(DATA_DISPLAY);
		Display[] all = Display.values();
		return i >= 0 && i < all.length ? all[i] : Display.AUTO;
	}

	public void setDisplay(Display display) {
		entityData.set(DATA_DISPLAY, display.ordinal());
	}

	/** Winter coat from October to April (report 07 §3.2: summer coat V-IX). */
	public static boolean winterCoat(SubSeason subSeason) {
		return switch (subSeason) {
			case LATE_SPRING, EARLY_SUMMER, MID_SUMMER, LATE_SUMMER, EARLY_AUTUMN -> false;
			default -> true;
		};
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		setBuck(getRandom().nextBoolean());
		setWinterCoat(winterCoat(Seasons.provider().subSeason(level.getLevel())));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Buck", isBuck());
		output.putBoolean("WinterCoat", hasWinterCoat());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setBuck(input.getBooleanOr("Buck", false));
		setWinterCoat(input.getBooleanOr("WinterCoat", false));
	}

	/** No food: the slice has no breeding (C10, growing up only in spawn groups from V14). */
	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", 5, this::animate));
	}

	private PlayState animate(AnimationTest<RoeDeer> test) {
		RawAnimation animation = switch (display()) {
			case NONE -> null;
			case IDLE -> IDLE;
			case WALK -> WALK;
			case RUN -> RUN;
			case EAT -> EAT;
			case ALERT -> ALERT;
			case REST -> REST;
			case AUTO -> !test.isMoving() ? IDLE : getDeltaMovement().horizontalDistance() > RUN_SPEED ? RUN : WALK;
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
