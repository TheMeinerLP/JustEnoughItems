package mezz.jei.gui.ghost;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Rectangle2d;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.config.IWorldConfig;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientManager;
import mezz.jei.input.IClickedIngredient;

public class GhostIngredientDragManager {
	private final IGhostIngredientDragSource source;
	private final GuiScreenHelper guiScreenHelper;
	private final IngredientManager ingredientManager;
	private final IWorldConfig worldConfig;
	private final List<GhostIngredientReturning> ghostIngredientsReturning = new ArrayList<>();
	@Nullable
	private GhostIngredientDrag<?> ghostIngredientDrag;
	@Nullable
	private Object hoveredIngredient;
	@Nullable
	private List<IGhostIngredientHandler.Target<Object>> hoveredIngredientTargets;

	public GhostIngredientDragManager(IGhostIngredientDragSource source, GuiScreenHelper guiScreenHelper, IngredientManager ingredientManager, IWorldConfig worldConfig) {
		this.source = source;
		this.guiScreenHelper = guiScreenHelper;
		this.ingredientManager = ingredientManager;
		this.worldConfig = worldConfig;
	}

	public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
		if (!(minecraft.currentScreen instanceof GuiContainer)) { // guiContainer uses drawOnForeground
			drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
		}
		if (ghostIngredientDrag != null) {
			ghostIngredientDrag.drawItem(minecraft, mouseX, mouseY);
		}
		ghostIngredientsReturning.forEach(returning -> returning.drawItem(minecraft));
		ghostIngredientsReturning.removeIf(GhostIngredientReturning::isComplete);
	}

	public void drawOnForeground(Minecraft minecraft, int mouseX, int mouseY) {
		drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
	}

	private void drawGhostIngredientHighlights(Minecraft minecraft, int mouseX, int mouseY) {
		if (this.ghostIngredientDrag != null) {
			this.ghostIngredientDrag.drawTargets(mouseX, mouseY);
		} else {
			IIngredientListElement elementUnderMouse = this.source.getElementUnderMouse();
			Object hovered = elementUnderMouse == null ? null : elementUnderMouse.getIngredient();
			if (!Objects.equals(hovered, this.hoveredIngredient)) {
				this.hoveredIngredient = hovered;
				this.hoveredIngredientTargets = null;
				GuiScreen currentScreen = minecraft.currentScreen;
				if (currentScreen != null && hovered != null) {
					IGhostIngredientHandler<GuiScreen> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
					if (handler != null && handler.shouldHighlightTargets()) {
						this.hoveredIngredientTargets = handler.getTargets(currentScreen, hovered, false);
					}
				}
			}
			if (this.hoveredIngredientTargets != null && !worldConfig.isCheatItemsEnabled()) {
				GhostIngredientDrag.drawTargets(mouseX, mouseY, this.hoveredIngredientTargets);
			}
		}
	}

	public boolean handleMouseClicked(double mouseX, double mouseY) {
		if (this.ghostIngredientDrag != null) {
			boolean success = this.ghostIngredientDrag.onClick(mouseX, mouseY);
			if (!success) {
				GhostIngredientReturning<?> returning = GhostIngredientReturning.create(this.ghostIngredientDrag, mouseX, mouseY);
				this.ghostIngredientsReturning.add(returning);
			}
			this.ghostIngredientDrag = null;
			return success;
		}
		return false;
	}

	public void stopDrag() {
		if (this.ghostIngredientDrag != null) {
			this.ghostIngredientDrag.stop();
			this.ghostIngredientDrag = null;
		}
	}

	public <T extends GuiScreen, V> boolean handleClickGhostIngredient(T currentScreen, IClickedIngredient<V> clicked) {
		IGhostIngredientHandler<T> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
		if (handler != null) {
			V ingredient = clicked.getValue();
			List<IGhostIngredientHandler.Target<V>> targets = handler.getTargets(currentScreen, ingredient, true);
			if (!targets.isEmpty()) {
				IIngredientRenderer<V> ingredientRenderer = ingredientManager.getIngredientRenderer(ingredient);
				Rectangle2d clickedArea = clicked.getArea();
				this.ghostIngredientDrag = new GhostIngredientDrag<>(handler, targets, ingredientRenderer, ingredient, clickedArea);
				clicked.onClickHandled();
				return true;
			}
		}
		return false;
	}
}
