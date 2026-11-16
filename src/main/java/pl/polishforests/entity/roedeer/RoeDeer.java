package pl.polishforests.entity.roedeer;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.base.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.Panic;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.AnimalProfile.Range;
import pl.polishforests.entity.base.FleeFromPlayers;
import pl.polishforests.entity.base.LeaveWater;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The European roe deer (step V8: model; step FA1: the shared animal base, the basic brain and the spawn;
 * docs/04-pionowy-wycinek.md "Projekt FA1"). FA2 adds the daily rhythm, the groups by season, the coat change and the alarm.
 *
 * <p>Variants: a doe or a buck (the renderer hides the antlers of a doe) in the summer (V-IX, red-brown) or winter coat
 * (X-IV, grey-brown; the buck's antlers in velvet), each with its own texture. Both are chosen when the animal spawns
 * (sex 50 : 50, coat from the sub-season of the season source) and saved in the fields of V8 ({@value #TAG_BUCK},
 * {@value #TAG_WINTER_COAT}, unchanged: the registry snapshot lists them).
 *
 * <p><b>Brain</b> (SmartBrainLib): it flees from a player within {@link PolishAnimal#fleeDistance} (20 blocks sneaking, 30
 * walking, 40 sprinting) to 16 blocks beyond that ({@link FleeFromPlayers}, by distance, without a line of sight), panics when hurt, swims up in water, and otherwise watches a
 * player who comes close ({@code alert}), or walks, grazes or rests in turns. Its walk targets lie in its habitats (a
 * forest of weight above 0 or the forest edge), so it does not wander far into the fields; out in the open it may walk
 * anywhere, back to the forest.
 *
 * <p>The model, the animations and the textures come from {@code tools/fauna} (GeckoLib files in
 * {@code assets/polishforests/geckolib/}). {@link Display} lets the fauna game test hold one animation; in the game it is
 * {@link Display#AUTO}: running, walking or the animation of the brain's {@link PolishAnimal.Action}.
 */
public class RoeDeer extends PolishAnimal<RoeDeer> {
	/** The animations of the model ({@code tools/fauna/animations.py}). */
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	public static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	/** The fields the roe deer adds to its saved data; they go into saved worlds (the registry snapshot lists them). */
	public static final String TAG_BUCK = "Buck";
	public static final String TAG_WINTER_COAT = "WinterCoat";
	public static final List<String> SAVED_FIELDS = List.of(TAG_BUCK, TAG_WINTER_COAT);
	/** Speed above which a moving deer runs instead of walking (blocks per tick, horizontal). */
	private static final double RUN_SPEED = 0.12;
	/** How far a fleeing deer runs beyond its flight distance (blocks). */
	private static final float FLEE_BEYOND = 16;
	/** How far beyond the flight distance a player makes a deer stop and watch (blocks). */
	private static final float ALERT_BEYOND = 10;

	/**
	 * The profile (step FA1; calibration in FA2). Real density about 0.1 per hectare of forest [Ź: GUS "Leśnictwo", about
	 * 0.9-1.0 million roe deer on about 9.3 million ha of forest; docs/04 "Projekt FA1"], game density ×30 (PW-15 (b)).
	 */
	public static final AnimalProfile PROFILE = new AnimalProfile("roe_deer", 0.7F, 1.1F, new Range(1, 3), new Range(2, 6),
			List.of(new Period(0, Activity.FEED), new Period(3_000, Activity.REST), new Period(9_000, Activity.FEED),
					new Period(14_000, Activity.REST), new Period(22_000, Activity.FEED)),
			EnumSet.of(SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.EARLY_WINTER, SubSeason.MID_WINTER, SubSeason.LATE_WINTER,
					SubSeason.EARLY_SPRING, SubSeason.MID_SPRING),
			habitats(), 0.35F, 0.1F, 30, 8, 20, 40);

	private static final EntityDataAccessor<Boolean> DATA_BUCK = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_WINTER_COAT = SynchedEntityData.defineId(RoeDeer.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.INT);

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		/** In the game: run, walk, or the animation of the brain's action (idle when it does nothing). */
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

	public RoeDeer(EntityType<? extends RoeDeer> type, Level level) {
		super(type, level);
		// No path through water (KFA-4: no deer in water); one that ends up in water walks out (LeaveWater).
		setPathfindingMalus(PathType.WATER, -1.0F);
	}

	/** Weights of the habitats: all forests of the mod, less in the wet, coniferous and mountain ones (FA2 calibrates). */
	private static Map<HabitatBiome, Float> habitats() {
		Map<HabitatBiome, Float> w = new java.util.EnumMap<>(HabitatBiome.class);
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				w.put(b, 1.0F);
			}
		}
		for (HabitatBiome b : HabitatBiome.PINE_FORESTS) {
			w.put(b, 0.8F);
		}
		w.put(HabitatBiome.BOG_WOODLAND, 0.4F);
		w.put(HabitatBiome.ALDER_CARR, 0.5F);
		w.put(HabitatBiome.UPLAND_FIR_FOREST, 0.8F);
		w.put(HabitatBiome.MONTANE_BEECH_FOREST, 0.8F);
		w.put(HabitatBiome.MONTANE_SPRUCE_FOREST, 0.5F);
		w.put(HabitatBiome.GRAY_ALDER_FOREST, 0.8F);
		w.put(HabitatBiome.DWARF_PINE_SCRUB, 0.2F);
		return w;
	}

	/** Roe deer: about 25 kg, a light and fast runner; it sees a player from 40 blocks (the largest flight distance). */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, PROFILE.fleeMax());
	}

	@Override
	public AnimalProfile profile() {
		return PROFILE;
	}

	@Override
	public String variant() {
		return variant(isBuck(), hasWinterCoat());
	}

	/** The texture variant of a sex and coat: {@code <doe|buck>_<summer|winter>}. */
	public static String variant(boolean buck, boolean winter) {
		return (buck ? "buck" : "doe") + "_" + (winter ? "winter" : "summer");
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
		return PROFILE.winterCoat(subSeason);
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
		output.putBoolean(TAG_BUCK, isBuck());
		output.putBoolean(TAG_WINTER_COAT, hasWinterCoat());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setBuck(input.getBooleanOr(TAG_BUCK, false));
		setWinterCoat(input.getBooleanOr(TAG_WINTER_COAT, false));
	}

	// ------------------------------------------------------------------ brain (SmartBrainLib)

	@Override
	public List<? extends ExtendedSensor<? extends RoeDeer>> getSensors(RoeDeer deer) {
		return List.of(new NearbyLivingEntitySensor<RoeDeer>().setRadius(PROFILE.fleeMax() + ALERT_BEYOND, 12)
				.setPredicate((self, e) -> e instanceof Player p && !p.isSpectator()).scanRate(d -> 10), new HurtBySensor<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RoeDeer>> getAlwaysRunningBehaviours(RoeDeer deer) {
		return List.of(new FloatToSurfaceOfFluid<>(), new LeaveWater<RoeDeer>(1.2F),
				new Panic<RoeDeer>().speedModifier(2.4F).whenStarting(d -> d.markFleeing(100)),
				new FleeFromPlayers<RoeDeer>(2.4F, FLEE_BEYOND),
				new LookAtTarget<>(), new MoveToWalkTarget<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RoeDeer>> getIdleBehaviours(RoeDeer deer) {
		return List.of(new FirstApplicableBehaviour<RoeDeer>(
				new Idle<RoeDeer>().startCondition(RoeDeer::playerNear).runFor(d -> 30 + d.getRandom().nextInt(40))
						.whenStarting(d -> d.setAction(Action.ALERT)).whenStopping(d -> d.setAction(Action.NONE)),
				new OneRandomBehaviour<RoeDeer>(
						new SetRandomWalkTarget<RoeDeer>().setRadius(12, 4).speedModifier(1.0F).isValidPositionIf(RoeDeer::walkTargetOk)
								.whenStarting(d -> d.setAction(Action.NONE)),
						new Idle<RoeDeer>().runFor(d -> 80 + d.getRandom().nextInt(160)).whenStarting(d -> d.setAction(Action.EAT))
								.whenStopping(d -> d.setAction(Action.NONE)),
						new Idle<RoeDeer>().runFor(d -> 200 + d.getRandom().nextInt(400)).whenStarting(d -> d.setAction(Action.REST))
								.whenStopping(d -> d.setAction(Action.NONE)))));
	}

	/** A player is within the flight distance plus {@value #ALERT_BEYOND} blocks (and the deer is not running). */
	private static boolean playerNear(RoeDeer d) {
		if (d.isFleeing()) {
			return false;
		}
		for (Player p : d.level().players()) {
			if (!p.isSpectator() && p.isAlive() && d.distanceTo(p) < d.fleeDistance(p) + ALERT_BEYOND) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Walk targets stay in the deer's habitats: a column of weight above 0 (forest or forest edge), unless the deer itself
	 * stands where it has none (out in the fields it may walk anywhere, the next targets take it back); never on leaves
	 * (the navigation does not walk on them either, {@code ForestGroundNavigation}) and never in water.
	 */
	private static boolean walkTargetOk(RoeDeer d, Vec3 target) {
		Level level = d.level();
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(target);
		if (level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.LEAVES) || !level.getFluidState(pos).isEmpty()
				|| !level.getFluidState(pos.below()).isEmpty()) {
			return false;
		}
		if (FaunaHabitat.weight(level, PROFILE, (int) Math.floor(target.x), (int) Math.floor(target.z)) > 0) {
			return true;
		}
		return FaunaHabitat.weight(level, PROFILE, d.getBlockX(), d.getBlockZ()) <= 0;
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
			case AUTO -> test.isMoving() ? getDeltaMovement().horizontalDistance() > RUN_SPEED ? RUN : WALK : switch (action()) {
				case EAT -> EAT;
				case REST -> REST;
				case ALERT -> ALERT;
				case NONE -> IDLE;
			};
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}
}
