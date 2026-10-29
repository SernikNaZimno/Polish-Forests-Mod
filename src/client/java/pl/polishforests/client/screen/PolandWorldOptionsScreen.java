package pl.polishforests.client.screen;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.chunk.PolandScale;

/** World generation options screen for "Poland". */
public final class PolandWorldOptionsScreen extends Screen {
	private static final int WIDGET_WIDTH = 310;
	/** Row spacing of the options column (4 since step J1: seven rows of 20 px fit in 164 GUI px). */
	private static final int SPACING = 4;

	private final Screen parent;
	private final Consumer<PolandSettings> apply;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private LinearLayout column;

	private PolandScale scale;
	private double regionScale;
	private ValueSlider regionSlider;
	private boolean agriculture;
	private double managedShare;
	private boolean alienSpecies;
	private final int version;
	private final boolean coverInBlocks;
	private LooseCover looseCover;
	private boolean caves;

	public PolandWorldOptionsScreen(Screen parent, PolandSettings current, Consumer<PolandSettings> apply) {
		super(Component.translatable("polishforests.options.title"));
		this.parent = parent;
		this.apply = apply;
		this.scale = current.scale();
		this.regionScale = current.regionScale();
		this.agriculture = current.agriculture();
		this.managedShare = current.managedForestShare();
		this.alienSpecies = current.alienSpecies();
		this.version = current.version();
		this.coverInBlocks = current.coverInBlocks();
		this.looseCover = current.looseCover();
		this.caves = current.caves();
	}

	@Override
	protected void init() {
		layout.addTitleHeader(getTitle(), font);
		// The column does not scroll, so every explanation lives in a tooltip: seven 20-px rows 4 px apart (164 GUI px) fit
		// between the header and the footer down to a GUI height of 230, and the automatic GUI scale never goes below 240
		// (step K1, review round 1: the former note under the buttons pushed the Scale button off the top at 854x480;
		// step J1 added the caves switch).
		column = layout.addToContents(LinearLayout.vertical().spacing(SPACING));
		column.defaultCellSetting().alignHorizontallyCenter();

		column.addChild(CycleButton.<PolandScale>builder(
				v -> Component.translatable("polishforests.options.scale." + v.getSerializedName()), scale)
				.withValues(PolandScale.values())
				.withTooltip(v -> Tooltip.create(Component.translatable(
						"polishforests.options.scale." + v.getSerializedName() + ".tooltip")))
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.scale"),
						(button, value) -> {
							scale = value;
							regionSlider.updateMessage();
						}));

		regionSlider = column.addChild(new ValueSlider(0.1, 2.0, regionScale, v -> Component.translatable(
				"polishforests.options.region_scale", percent(v),
				String.format(Locale.ROOT, "%.1f", scale.landscape().regionSize() * v / 1000.0)),
				v -> regionScale = Math.round(v * 20.0) / 20.0));

		column.addChild(CycleButton.booleanBuilder(Component.translatable("polishforests.options.landscape.today"),
				Component.translatable("polishforests.options.landscape.natural"), agriculture)
				.withTooltip(v -> Tooltip.create(Component.translatable(v ? "polishforests.options.landscape.today.tooltip"
						: "polishforests.options.landscape.natural.tooltip")))
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.landscape"),
						(button, value) -> agriculture = value));

		ValueSlider managedSlider = column.addChild(new ValueSlider(0.0, 1.0, managedShare,
				v -> Component.translatable("polishforests.options.managed_share", percent(v)),
				v -> managedShare = Math.round(v * 20.0) / 20.0));
		managedSlider.setTooltip(Tooltip.create(Component.translatable("polishforests.options.managed_share.tooltip")));

		column.addChild(CycleButton.<LooseCover>builder(
				v -> Component.translatable("polishforests.options.loose_cover." + v.getSerializedName()), looseCover)
				.withValues(LooseCover.values())
				.withTooltip(v -> Tooltip.create(Component.translatable(
						"polishforests.options.loose_cover." + v.getSerializedName() + ".tooltip")))
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.loose_cover"),
						(button, value) -> looseCover = value));

		column.addChild(CycleButton.onOffBuilder(caves)
				.withTooltip(v -> Tooltip.create(Component.translatable(v ? "polishforests.options.caves.on.tooltip"
						: "polishforests.options.caves.off.tooltip")))
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.caves"),
						(button, value) -> caves = value));

		column.addChild(CycleButton.onOffBuilder(alienSpecies)
				.withTooltip(v -> Tooltip.create(Component.translatable("polishforests.options.alien_species.tooltip")))
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.alien_species"),
						(button, value) -> alienSpecies = value));

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
			apply.accept(new PolandSettings(scale, regionScale, agriculture, managedShare, alienSpecies, version,
					coverInBlocks, looseCover, caves));
			onClose();
		}).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	/** Whether the options column lies wholly between the header and the footer (checked by the UI game test). */
	public boolean contentsFit() {
		return column.getY() >= layout.getHeaderHeight()
				&& column.getY() + column.getHeight() <= height - layout.getFooterHeight();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private static String percent(double v) {
		return String.format(Locale.ROOT, "%.0f", v * 100);
	}

	/** Slider with a value in a given range. */
	private static final class ValueSlider extends AbstractSliderButton {
		private final double min;
		private final double max;
		private final DoubleFunction<Component> label;
		private final DoubleConsumer onChange;

		ValueSlider(double min, double max, double initial, DoubleFunction<Component> label, DoubleConsumer onChange) {
			super(0, 0, WIDGET_WIDTH, 20, Component.empty(), (initial - min) / (max - min));
			this.min = min;
			this.max = max;
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		private double actual() {
			return min + value * (max - min);
		}

		@Override
		protected void updateMessage() {
			if (label != null) {
				setMessage(label.apply(actual()));
			}
		}

		@Override
		protected void applyValue() {
			onChange.accept(actual());
		}
	}
}
