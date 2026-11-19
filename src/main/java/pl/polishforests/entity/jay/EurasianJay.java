package pl.polishforests.entity.jay;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.AnimalProfile.Range;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.LeaveWater;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.ModSounds;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The Eurasian jay, sójka (step FA6, docs/04-pionowy-wycinek.md "Projekt FA6", option O2): the first flying animal of the
 * mod.
 *
 * <p><b>Look.</b> The model of {@code tools/fauna/bird.py}: at real size (34 cm, the F3 enlargement does not apply),
 * modelled at four times the resolution of the quadruped and drawn at a quarter of the size ({@code EurasianJayRenderer},
 * GeckoLib's {@code withScale(0.25)}), hitbox 0.4 x 0.3. One plumage ({@code textures/entity/eurasian_jay/adult.png}):
 * pinkish brown with the blue patch of the wing; the acorn in the bill shows only while it carries one to its cache.
 *
 * <p><b>Life</b> ({@link JayMind}): diurnal ({@link #SCHEDULE}), feeding on the ground and in the crowns, moving from perch
 * to perch under the canopy ({@link JayFlight}, {@link JaySpace}: branches and the edges of the crowns), hiding acorns in
 * the autumn in the oak forests, roosting at night on a perch. It screeches at a running player it sees and so warns the
 * roe deer and the wild boars ({@link ForestAlarm.Kind#JAY_BIRD}); the screech of the forest ambience of step FA4 then
 * stays only where no jay is near ({@code sound/ForestAmbience}). Alone or in pairs (no groups, no young: C10).
 *
 * <p><b>Spawn.</b> Category {@code AMBIENT} (G.62-j); like the other animals of the mod outside vanilla's mob counts, with
 * its own limit of the fauna spawner ({@link AnimalProfile#maxNearPlayer()}). It appears on the ground (the places of the
 * spawners) and flies up. No saved fields of its own: the flight is not saved (a jay saved in the air drops to what is
 * under it when loaded).
 */
public class EurasianJay extends PolishAnimal<EurasianJay> {
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation HOP = RawAnimation.begin().thenLoop("move.hop");
	public static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
	public static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("move.glide");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	/** No fields of its own in the saved data (the registry snapshot lists them). */
	public static final List<String> SAVED_FIELDS = List.of();
	/** The bone of the acorn in the bill (hidden unless it carries one). */
	public static final String ACORN = "food_acorn";
	/** The only texture variant. */
	public static final String VARIANT = "adult";
	/** Speed above which a jay on the ground hops (blocks per tick, horizontal): below it, it stands. */
	private static final double HOP_SPEED = 0.02;
	/** Ticks between two of its own calls (the screech without an alarm) on average. */
	static final int CALL_INTERVAL = 1600;

	/**
	 * The day of the jay: active from dawn to dusk with more feeding in the morning and the afternoon, a calmer midday, the
	 * night on a roost (report 07; field guides).
	 */
	public static final List<Period> SCHEDULE = List.of(new Period(0, 0.55F, 0.10F, 0.35F),   // 6:00
			new Period(5_000, 0.40F, 0.30F, 0.30F),                                         // 11:00 a calmer midday
			new Period(8_000, 0.50F, 0.12F, 0.38F),                                         // 14:00
			new Period(12_500, 0.02F, 0.96F, 0.02F),                                        // 18:30 the roost
			new Period(22_800, 0.25F, 0.50F, 0.25F));                                       // 4:48 dawn

	/**
	 * The profile (docs/04 "Projekt FA6"). Real density about 0.3 birds per hectare of deciduous forest [Ź: Tomiałojć,
	 * Wesołowski and Walankiewicz 1984, Acta Ornithologica 20 (the breeding birds of the primeval stands of the Białowieża
	 * National Park: the jay at about 1-3 pairs per 10 ha of oak-hornbeam forest); the Atlas of the breeding birds of Poland
	 * (common in deciduous and mixed forests); approximate], generation ×20 and spawner target ×24 (KFA-5), alone or a pair
	 * (40% alone), flight from 6 blocks (sneaking) to 16 (sprinting), its own limit of 10 near a player, home range 48.
	 */
	public static final AnimalProfile PROFILE = new AnimalProfile("eurasian_jay", 0.4F, 0.3F, new Range(2, 2), new Range(2, 2),
			EnumSet.noneOf(SubSeason.class), SCHEDULE, EnumSet.noneOf(SubSeason.class), EnumSet.noneOf(SubSeason.class), 0.0F, habitats(),
			0.0F, 0.3F, 20, 24, 10, 6, 16, 48, 0.4F, 1);

	private static final EntityDataAccessor<Integer> DATA_FLIGHT = SynchedEntityData.defineId(EurasianJay.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CARRY = SynchedEntityData.defineId(EurasianJay.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(EurasianJay.class, EntityDataSerializers.INT);

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		AUTO, NONE, IDLE, HOP, FLY, GLIDE, EAT, ALERT, REST
	}

	private final JayFlight flight = new JayFlight(this);
	private final JayMind mind = new JayMind(this);
	private @Nullable SubSeason subSeason;

	public EurasianJay(EntityType<? extends EurasianJay> type, Level level) {
		super(type, level);
		setPathfindingMalus(PathType.WATER, -1.0F);
	}

	/**
	 * Weights of the habitats (docs/04 "Projekt FA6"): the deciduous and mixed forests with oaks and beeches (the
	 * oak-hornbeam forest 1, the mixed forests 0.9, the beech forests and the elm-ash floodplain forest 0.7-0.8, the other
	 * floodplain forests and the fir-beech forest 0.5-0.6), less the alder and the pine forests, rare in the spruce forests;
	 * not the dwarf pine and no open land.
	 */
	private static Map<HabitatBiome, Float> habitats() {
		Map<HabitatBiome, Float> w = new java.util.EnumMap<>(HabitatBiome.class);
		w.put(HabitatBiome.OAK_HORNBEAM_FOREST, 1.0F);
		w.put(HabitatBiome.MIXED_FOREST, 0.9F);
		w.put(HabitatBiome.MIXED_PINE_FOREST, 0.9F);
		w.put(HabitatBiome.LOWLAND_BEECH_FOREST, 0.8F);
		w.put(HabitatBiome.ELM_ASH_FOREST, 0.8F);
		w.put(HabitatBiome.MONTANE_BEECH_FOREST, 0.7F);
		w.put(HabitatBiome.ASH_ALDER_FOREST, 0.6F);
		w.put(HabitatBiome.UPLAND_FIR_FOREST, 0.5F);
		w.put(HabitatBiome.WILLOW_POPLAR_FOREST, 0.5F);
		w.put(HabitatBiome.ALDER_CARR, 0.3F);
		w.put(HabitatBiome.GRAY_ALDER_FOREST, 0.3F);
		w.put(HabitatBiome.FRESH_PINE_FOREST, 0.3F);
		w.put(HabitatBiome.MOIST_PINE_FOREST, 0.3F);
		w.put(HabitatBiome.MONTANE_SPRUCE_FOREST, 0.2F);
		return w;
	}

	/** Eurasian jay: 140-190 g; hops on the ground, flies under the crowns; no fall damage (it flies). */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 5.0).add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.FLYING_SPEED, JayFlight.CRUISE_SPEED);
	}

	@Override
	public AnimalProfile profile() {
		return PROFILE;
	}

	public JayFlight flight() {
		return flight;
	}

	public JayMind mind() {
		return mind;
	}

	@Nullable SubSeason subSeason() {
		return subSeason;
	}

	// ------------------------------------------------------------------ look

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLIGHT, JayFlight.Kind.GROUND.ordinal());
		builder.define(DATA_CARRY, false);
		builder.define(DATA_DISPLAY, Display.AUTO.ordinal());
	}

	/** The kind of its motion (synchronized; {@link JayFlight.Kind#GROUND} when it stands or hops). */
	public JayFlight.Kind flightKind() {
		int i = entityData.get(DATA_FLIGHT);
		JayFlight.Kind[] all = JayFlight.Kind.values();
		return i >= 0 && i < all.length ? all[i] : JayFlight.Kind.GROUND;
	}

	void syncFlight(JayFlight.Kind kind) {
		if (entityData.get(DATA_FLIGHT) != kind.ordinal()) {
			entityData.set(DATA_FLIGHT, kind.ordinal());
		}
	}

	/** Whether it is in the air (both sides: from the synchronized kind). */
	public boolean flying() {
		return flightKind() != JayFlight.Kind.GROUND;
	}

	/** Whether it carries an acorn in its bill (to its cache). */
	public boolean isCarrying() {
		return entityData.get(DATA_CARRY);
	}

	public void setCarrying(boolean carrying) {
		if (carrying != isCarrying()) {
			entityData.set(DATA_CARRY, carrying);
		}
	}

	public Display display() {
		int i = entityData.get(DATA_DISPLAY);
		Display[] all = Display.values();
		return i >= 0 && i < all.length ? all[i] : Display.AUTO;
	}

	public void setDisplay(Display display) {
		entityData.set(DATA_DISPLAY, display.ordinal());
	}

	@Override
	public String variant() {
		return VARIANT;
	}

	/** Every texture variant of the jay. */
	public static List<String> variants() {
		return List.of(VARIANT);
	}

	@Override
	public String hiddenBones() {
		return isCarrying() ? "" : ACORN;
	}

	@Override
	protected void onCalendar(SubSeason subSeason) {
		this.subSeason = subSeason;
	}

	// ------------------------------------------------------------------ life

	/** Alone or in a pair: no group takes it. */
	@Override
	public boolean herds() {
		return false;
	}

	/** It perches on the crowns: it may stand on leaves. */
	@Override
	protected boolean staysOffLeaves() {
		return false;
	}

	/** The jay watches with its own eyes: the alarm of a running player itself does not make it flee (it screeches at one). */
	@Override
	public void hearAlarm(ForestAlarm alarm, double distance) {
		if (alarm.kind() != ForestAlarm.Kind.RUNNING_PLAYER) {
			super.hearAlarm(alarm, distance);
		}
	}

	@Override
	public State state() {
		if (flying()) {
			return isFleeing() ? State.FLEEING : State.FLYING;
		}
		if (isCarrying() && action() == Action.EAT) {
			return State.CACHING;
		}
		if (mind.perched() && action() == Action.NONE) {
			return State.PERCHED;
		}
		return super.state();
	}

	@Override
	protected void onServerTick(ServerLevel level) {
		if (subSeason == null) {
			onCalendar(pl.polishforests.season.Seasons.provider().subSeason(level));
		}
		flight.tick();
		mind.tick(level);
	}

	/** In the air no vanilla movement (no gravity, no walk control): {@link JayFlight} sets the velocity. */
	@Override
	public void travel(Vec3 input) {
		if (flight.active()) {
			flight.move();
		} else {
			super.travel(input);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && flying()) {
			// the client turns the body after the movement; in the air the jay faces where the server says
			yBodyRot = getYRot();
			yBodyRotO = yRotO;
			yHeadRot = getYRot();
			yHeadRotO = yRotO;
		}
	}

	@Override
	public boolean isPushable() {
		return !flying() && super.isPushable();
	}

	/** A bird does not hurt itself falling. */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			mind.hurt(source.getSourcePosition());
		}
		return hurt;
	}

	/** Its own call now and then (the screech of {@code tools/sound} without an alarm). */
	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return ModSounds.JAY;
	}

	@Override
	public int getAmbientSoundInterval() {
		return CALL_INTERVAL;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}

	// ------------------------------------------------------------------ brain (SmartBrainLib): only the hops on the ground

	@Override
	public List<? extends ExtendedSensor<? extends EurasianJay>> getSensors(EurasianJay jay) {
		return List.of(new HurtBySensor<>());
	}

	@Override
	public List<? extends BehaviorControl<? super EurasianJay>> getAlwaysRunningBehaviours(EurasianJay jay) {
		return List.of(new FloatToSurfaceOfFluid<>(), new LeaveWater<EurasianJay>(1.4F), new MoveToWalkTarget<>());
	}

	@Override
	public List<? extends BehaviorControl<? super EurasianJay>> getIdleBehaviours(EurasianJay jay) {
		return List.of();
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", 2, this::animate));
	}

	private PlayState animate(AnimationTest<EurasianJay> test) {
		RawAnimation animation = switch (display()) {
			case NONE -> null;
			case IDLE -> IDLE;
			case HOP -> HOP;
			case FLY -> FLY;
			case GLIDE -> GLIDE;
			case EAT -> EAT;
			case ALERT -> ALERT;
			case REST -> REST;
			case AUTO -> switch (flightKind()) {
				case FLY -> FLY;
				case GLIDE -> GLIDE;
				case GROUND -> test.isMoving() && getDeltaMovement().horizontalDistance() > HOP_SPEED ? HOP : switch (action()) {
					case EAT -> EAT;
					case REST -> REST;
					case ALERT -> ALERT;
					default -> IDLE;
				};
			};
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}
}
