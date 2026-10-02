package pl.polishforests.client.screen;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.chunk.PolandScale;

/** Ekran opcji generowania świata "Polska". */
public final class PolandWorldOptionsScreen extends Screen {
	private static final int WIDGET_WIDTH = 310;

	private final Screen parent;
	private final Consumer<PolandSettings> apply;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	private PolandScale scale;
	private double regionScale;
	private ValueSlider regionSlider;
	private boolean agriculture;
	private double managedShare;
	private boolean alienSpecies;

	public PolandWorldOptionsScreen(Screen parent, PolandSettings current, Consumer<PolandSettings> apply) {
		super(Component.translatable("polishforests.options.title"));
		this.parent = parent;
		this.apply = apply;
		this.scale = current.scale();
		this.regionScale = current.regionScale();
		this.agriculture = current.agriculture();
		this.managedShare = current.managedForestShare();
		this.alienSpecies = current.alienSpecies();
	}

	@Override
	protected void init() {
		layout.addTitleHeader(getTitle(), font);
		LinearLayout column = layout.addToContents(LinearLayout.vertical().spacing(6));
		column.defaultCellSetting().alignHorizontallyCenter();

		column.addChild(CycleButton.<PolandScale>builder(
				v -> Component.translatable("polishforests.options.scale." + v.getSerializedName()), scale)
				.withValues(PolandScale.values())
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
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.landscape"),
						(button, value) -> agriculture = value));

		column.addChild(new ValueSlider(0.0, 1.0, managedShare,
				v -> Component.translatable("polishforests.options.managed_share", percent(v)),
				v -> managedShare = Math.round(v * 20.0) / 20.0));

		column.addChild(CycleButton.onOffBuilder(alienSpecies)
				.create(0, 0, WIDGET_WIDTH, 20, Component.translatable("polishforests.options.alien_species"),
						(button, value) -> alienSpecies = value));

		MultiLineTextWidget note = new MultiLineTextWidget(Component.translatable("polishforests.options.note"), font);
		note.setMaxWidth(WIDGET_WIDTH);
		column.addChild(note);

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
			apply.accept(new PolandSettings(scale, regionScale, agriculture, managedShare, alienSpecies));
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

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private static String percent(double v) {
		return String.format(Locale.ROOT, "%.0f", v * 100);
	}

	/** Suwak z wartością w zadanym zakresie. */
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
