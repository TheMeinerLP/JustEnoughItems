package mezz.jei.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.ingredients.GuiIngredientProperties;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.DeleteItemInputHandler;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.AlignmentUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An ingredient grid displays a rectangular area of clickable recipe ingredients.
 * It does not draw a background or have external padding, those are left up to a higher-level element.
 */
public class IngredientGrid implements IRecipeFocusSource, IIngredientGrid {
	private static final int INGREDIENT_PADDING = 1;
	public static final int INGREDIENT_WIDTH = GuiIngredientProperties.getWidth(INGREDIENT_PADDING);
	public static final int INGREDIENT_HEIGHT = GuiIngredientProperties.getHeight(INGREDIENT_PADDING);

	private final IIngredientGridConfig gridConfig;
	private final IngredientListRenderer ingredientListRenderer;
	private final DeleteItemInputHandler deleteItemHandler;
	private final IngredientGridTooltipHelper tooltipHelper;
	private Set<ImmutableRect2i> guiExclusionAreas = Set.of();
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;

	public IngredientGrid(
		IIngredientManager ingredientManager,
		IIngredientGridConfig gridConfig,
		IEditModeConfig editModeConfig,
		IIngredientFilterConfig ingredientFilterConfig,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IModIdHelper modIdHelper,
		IConnectionToServer serverConnection,
		IInternalKeyMappings keyBindings,
		IColorHelper colorHelper
	) {
		this.gridConfig = gridConfig;
		this.ingredientListRenderer = new IngredientListRenderer(editModeConfig, toggleState, ingredientManager);
		this.tooltipHelper = new IngredientGridTooltipHelper(ingredientManager, ingredientFilterConfig, toggleState, modIdHelper, keyBindings, colorHelper);
		this.deleteItemHandler = new DeleteItemInputHandler(this, toggleState, clientConfig, serverConnection, ingredientManager);
	}

	public IUserInputHandler getInputHandler() {
		return deleteItemHandler;
	}

	public int size() {
		return this.ingredientListRenderer.size();
	}

	public void updateBounds(ImmutableRect2i availableArea, Set<ImmutableRect2i> guiExclusionAreas) {
		this.ingredientListRenderer.clear();

		this.area = calculateBounds(this.gridConfig, availableArea);
		this.guiExclusionAreas = guiExclusionAreas;

		for (int y = this.area.getY(); y < this.area.getY() + this.area.getHeight(); y += INGREDIENT_HEIGHT) {
			for (int x = this.area.getX(); x < this.area.getX() + this.area.getWidth(); x += INGREDIENT_WIDTH) {
				IngredientListSlot ingredientListSlot = new IngredientListSlot(x, y, INGREDIENT_WIDTH, INGREDIENT_HEIGHT, INGREDIENT_PADDING);
				ImmutableRect2i stackArea = ingredientListSlot.getArea();
				final boolean blocked = MathUtil.intersects(guiExclusionAreas, stackArea.expandBy(2));
				ingredientListSlot.setBlocked(blocked);
				this.ingredientListRenderer.add(ingredientListSlot);
			}
		}
	}

	public static ImmutableSize2i calculateSize(IIngredientGridConfig config, ImmutableRect2i availableArea) {
		final int columns = Math.min(availableArea.getWidth() / INGREDIENT_WIDTH, config.getMaxColumns());
		final int rows = Math.min(availableArea.getHeight() / INGREDIENT_HEIGHT, config.getMaxRows());
		if (rows < config.getMinRows() || columns < config.getMinColumns()) {
			return ImmutableSize2i.EMPTY;
		}
		return new ImmutableSize2i(
			columns * INGREDIENT_WIDTH,
			rows * INGREDIENT_HEIGHT
		);
	}

	public static ImmutableRect2i calculateBounds(IIngredientGridConfig config, ImmutableRect2i availableArea) {
		ImmutableSize2i size = calculateSize(config, availableArea);
		return AlignmentUtil.align(size, availableArea, config.getHorizontalAlignment(), config.getVerticalAlignment());
	}

	public record SlotInfo(int total, int blocked) {
		public float percentBlocked() {
			return blocked / (float) total;
		}
	}

	public static SlotInfo calculateBlockedSlotPercentage(IIngredientGridConfig config, ImmutableRect2i availableArea, Set<ImmutableRect2i> exclusionAreas) {
		ImmutableRect2i area = calculateBounds(config, availableArea);

		int total = 0;
		int blocked = 0;
		for (int y = area.getY(); y < area.getY() + area.getHeight(); y += INGREDIENT_HEIGHT) {
			for (int x = area.getX(); x < area.getX() + area.getWidth(); x += INGREDIENT_WIDTH) {
				IngredientListSlot ingredientListSlot = new IngredientListSlot(x, y, INGREDIENT_WIDTH, INGREDIENT_HEIGHT, INGREDIENT_PADDING);
				ImmutableRect2i stackArea = ingredientListSlot.getArea();
				if (MathUtil.intersects(exclusionAreas, stackArea.expandBy(2))) {
					blocked++;
				}
				total++;
			}
		}
		return new SlotInfo(total, blocked);
	}

	public ImmutableRect2i getArea() {
		return area;
	}

	public void draw(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		RenderSystem.disableBlend();

		ingredientListRenderer.render(guiGraphics);

		if (isMouseOver(mouseX, mouseY)) {
			if (!this.deleteItemHandler.shouldDeleteItemOnClick(minecraft, mouseX, mouseY)) {
				ingredientListRenderer.getSlots()
					.filter(s -> s.getArea().contains(mouseX, mouseY))
					.filter(s -> s.getElement().isPresent())
					.findFirst()
					.ifPresent(s -> drawHighlight(guiGraphics, s.getArea()));
			}
		}
	}

	/**
	 * Matches the highlight code in {@link AbstractContainerScreen#renderSlotHighlight(GuiGraphics, int, int, int)}
	 * but with a custom area width and height
	 */
	public static void drawHighlight(GuiGraphics guiGraphics, ImmutableRect2i area) {
		guiGraphics.fillGradient(
			RenderType.guiOverlay(),
			area.getX(),
			area.getY(),
			area.getX() + area.getWidth(),
			area.getY() + area.getHeight(),
			0x80FFFFFF,
			0x80FFFFFF,
			0
		);
	}

	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (isMouseOver(mouseX, mouseY)) {
			if (this.deleteItemHandler.shouldDeleteItemOnClick(minecraft, mouseX, mouseY)) {
				this.deleteItemHandler.drawTooltips(guiGraphics, mouseX, mouseY);
			} else {
				ingredientListRenderer.getSlots()
					.filter(s -> s.isMouseOver(mouseX, mouseY))
					.filter(s -> s.getElement().isPresent())
					.findFirst()
					.ifPresent(s -> s.drawTooltip(guiGraphics, mouseX, mouseY, tooltipHelper));
			}
		}
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return area.contains(mouseX, mouseY) &&
			guiExclusionAreas.stream()
				.noneMatch(area -> area.contains(mouseX, mouseY));
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		return ingredientListRenderer.getSlots()
			.filter(s -> s.isMouseOver(mouseX, mouseY))
			.map(IngredientListSlot::getClickableIngredient)
			.flatMap(Optional::stream);
	}

	public <T> Stream<T> getVisibleIngredients(IIngredientType<T> ingredientType) {
		return this.ingredientListRenderer.getSlots()
			.map(IngredientListSlot::getElement)
			.flatMap(Optional::stream)
			.map(IElement::getTypedIngredient)
			.map(i -> i.getIngredient(ingredientType))
			.flatMap(Optional::stream);
	}

	public void set(int firstItemIndex, List<IElement<?>> ingredientList) {
		this.ingredientListRenderer.set(firstItemIndex, ingredientList);
	}

	public boolean hasRoom() {
		return !this.area.isEmpty();
	}
}
