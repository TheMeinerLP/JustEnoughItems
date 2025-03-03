package mezz.jei.library.gui.recipes;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.gui.TooltipRenderer;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.library.gui.ingredients.RecipeSlot;
import mezz.jei.library.gui.ingredients.RecipeSlots;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RecipeLayout<R> implements IRecipeLayoutDrawable<R> {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int RECIPE_BORDER_PADDING = 4;
	public static final int RECIPE_BUTTON_SIZE = 13;
	public static final int RECIPE_BUTTON_SPACING = 2;

	private final int ingredientCycleOffset = (int) ((Math.random() * 10000) % Integer.MAX_VALUE);
	private final IRecipeCategory<R> recipeCategory;
	private final Collection<IRecipeCategoryDecorator<R>> recipeCategoryDecorators;
	private final IIngredientManager ingredientManager;
	private final IModIdHelper modIdHelper;
	private final Textures textures;
	private final RecipeSlots recipeSlots;
	private final R recipe;
	private final DrawableNineSliceTexture recipeBorder;
	private ImmutableRect2i recipeTransferButtonArea;
	@Nullable
	private ShapelessIcon shapelessIcon;

	private ImmutableRect2i area;

	public static <T> Optional<IRecipeLayoutDrawable<T>> create(IRecipeCategory<T> recipeCategory, Collection<IRecipeCategoryDecorator<T>> decorators, T recipe, IFocusGroup focuses, IIngredientManager ingredientManager, IIngredientVisibility ingredientVisibility, IModIdHelper modIdHelper, Textures textures) {
		RecipeLayout<T> recipeLayout = new RecipeLayout<>(recipeCategory, decorators, recipe, ingredientManager, modIdHelper, textures);
		if (recipeLayout.setRecipeLayout(recipeCategory, recipe, focuses, ingredientVisibility)) {
			ResourceLocation recipeName = recipeCategory.getRegistryName(recipe);
			if (recipeName != null) {
				addOutputSlotTooltip(recipeLayout, recipeName, modIdHelper);
			}
			return Optional.of(recipeLayout);
		}
		return Optional.empty();
	}

	private boolean setRecipeLayout(
		IRecipeCategory<R> recipeCategory,
		R recipe,
		IFocusGroup focuses,
		IIngredientVisibility ingredientVisibility
	) {
		RecipeLayoutBuilder builder = new RecipeLayoutBuilder(ingredientManager, this.ingredientCycleOffset);
		try {
			recipeCategory.setRecipe(builder, recipe, focuses);
			if (builder.isUsed()) {
				builder.setRecipeLayout(this, focuses, ingredientVisibility);
				return true;
			}
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("Error caught from Recipe Category: {}", recipeCategory.getRecipeType().getUid(), e);
		}
		return false;
	}

	private static void addOutputSlotTooltip(RecipeLayout<?> recipeLayout, ResourceLocation recipeName, IModIdHelper modIdHelper) {
		RecipeSlots recipeSlots = recipeLayout.recipeSlots;
		List<RecipeSlot> outputSlots = recipeSlots.getSlots().stream()
			.filter(r -> r.getRole() == RecipeIngredientRole.OUTPUT)
			.toList();

		if (!outputSlots.isEmpty()) {
			OutputSlotTooltipCallback callback = new OutputSlotTooltipCallback(recipeName, modIdHelper, recipeLayout.ingredientManager);
			for (RecipeSlot outputSlot : outputSlots) {
				outputSlot.addTooltipCallback(callback);
			}
		}
	}

	public RecipeLayout(
		IRecipeCategory<R> recipeCategory,
		Collection<IRecipeCategoryDecorator<R>> recipeCategoryDecorators,
		R recipe,
		IIngredientManager ingredientManager,
		IModIdHelper modIdHelper,
		Textures textures
	) {
		this.recipeCategory = recipeCategory;
		this.recipeCategoryDecorators = recipeCategoryDecorators;
		this.ingredientManager = ingredientManager;
		this.modIdHelper = modIdHelper;
		this.textures = textures;
		this.recipeSlots = new RecipeSlots();
		this.area = new ImmutableRect2i(
			0,
			0,
			recipeCategory.getWidth(),
			recipeCategory.getHeight()
		);

		this.recipeTransferButtonArea = new ImmutableRect2i(
			area.getWidth() + RECIPE_BORDER_PADDING + RECIPE_BUTTON_SPACING,
			area.getHeight() + RECIPE_BORDER_PADDING - RECIPE_BUTTON_SIZE,
			RECIPE_BUTTON_SIZE,
			RECIPE_BUTTON_SIZE
		);

		this.recipe = recipe;
		this.recipeBorder = textures.getRecipeBackground();
	}

	@Override
	public void setPosition(int posX, int posY) {
		this.area = new ImmutableRect2i(
			posX,
			posY,
			recipeCategory.getWidth(),
			recipeCategory.getHeight()
		);
	}

	@Override
	public void drawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		IDrawable background = recipeCategory.getBackground();

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		recipeBorder.draw(guiGraphics, getRectWithBorder());

		final int recipeMouseX = mouseX - area.getX();
		final int recipeMouseY = mouseY - area.getY();

		var poseStack = guiGraphics.pose();
		poseStack.pushPose();
		{
			poseStack.translate(area.getX(), area.getY(), 0);
			background.draw(guiGraphics);

			// defensive push/pop to protect against recipe categories changing the last pose
			poseStack.pushPose();
			{
				recipeCategory.draw(recipe, recipeSlots.getView(), guiGraphics, recipeMouseX, recipeMouseY);

				// drawExtras and drawInfo often render text which messes with the color, this clears it
				RenderSystem.setShaderColor(1, 1, 1, 1);
			}
			poseStack.popPose();

			for (IRecipeCategoryDecorator<R> decorator : recipeCategoryDecorators) {
				// defensive push/pop to protect against recipe category decorators changing the last pose
				poseStack.pushPose();
				{
					decorator.draw(recipe, recipeCategory, recipeSlots.getView(), guiGraphics, recipeMouseX, recipeMouseY);

					// drawExtras and drawInfo often render text which messes with the color, this clears it
					RenderSystem.setShaderColor(1, 1, 1, 1);
				}
				poseStack.popPose();
			}

			if (shapelessIcon != null) {
				shapelessIcon.draw(guiGraphics);
			}

			recipeSlots.draw(guiGraphics);
		}
		poseStack.popPose();

		RenderSystem.disableBlend();
	}

	@Override
	public void drawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		final int recipeMouseX = mouseX - area.getX();
		final int recipeMouseY = mouseY - area.getY();

		IRecipeSlotDrawable hoveredSlot = this.recipeSlots.getHoveredSlot(recipeMouseX, recipeMouseY)
			.orElse(null);

		RenderSystem.disableBlend();

		var poseStack = guiGraphics.pose();
		if (hoveredSlot != null) {
			poseStack.pushPose();
			{
				poseStack.translate(area.getX(), area.getY(), 0);
				hoveredSlot.drawHoverOverlays(guiGraphics);
			}
			poseStack.popPose();

			hoveredSlot.getDisplayedIngredient()
				.ifPresent(i -> {
					List<Component> tooltip = hoveredSlot.getTooltip();
					tooltip = modIdHelper.addModNameToIngredientTooltip(tooltip, i);
					TooltipRenderer.drawHoveringText(guiGraphics, tooltip, mouseX, mouseY, i, ingredientManager);
				});
		} else if (isMouseOver(mouseX, mouseY)) {
			List<Component> tooltipStrings = recipeCategory.getTooltipStrings(recipe, recipeSlots.getView(), recipeMouseX, recipeMouseY);
			for (IRecipeCategoryDecorator<R> decorator : recipeCategoryDecorators) {
				tooltipStrings = decorator.decorateExistingTooltips(tooltipStrings, recipe, recipeCategory, recipeSlots.getView(), recipeMouseX, recipeMouseY);
			}

			if (tooltipStrings.isEmpty() && shapelessIcon != null) {
				tooltipStrings = shapelessIcon.getTooltipStrings(recipeMouseX, recipeMouseY);
			}
			if (!tooltipStrings.isEmpty()) {
				TooltipRenderer.drawHoveringText(guiGraphics, tooltipStrings, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return MathUtil.contains(area, mouseX, mouseY);
	}

	@Override
	public Rect2i getRect() {
		return area.toMutable();
	}

	@Override
	public Rect2i getRectWithBorder() {
		return area.expandBy(RECIPE_BORDER_PADDING).toMutable();
	}

	@Override
	public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
		return getRecipeSlotUnderMouse(mouseX, mouseY)
			.flatMap(slot -> slot.getDisplayedIngredient(ingredientType));
	}

	@Override
	public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
		final double recipeMouseX = mouseX - area.getX();
		final double recipeMouseY = mouseY - area.getY();
		return this.recipeSlots.getHoveredSlot(recipeMouseX, recipeMouseY)
			.map(r -> r);
	}

	public void moveRecipeTransferButton(int posX, int posY) {
		recipeTransferButtonArea = new ImmutableRect2i(
			posX,
			posY,
			RECIPE_BUTTON_SIZE,
			RECIPE_BUTTON_SIZE
		);
	}

	public void setShapeless() {
		this.shapelessIcon = new ShapelessIcon(textures);

		// align to top-right
		int x = area.getWidth() - shapelessIcon.getIcon().getWidth();
		this.shapelessIcon.setPosition(x, 0);
	}

	public void setShapeless(int shapelessX, int shapelessY) {
		this.shapelessIcon = new ShapelessIcon(textures);
		this.shapelessIcon.setPosition(shapelessX, shapelessY);
	}

	@Override
	public IRecipeCategory<R> getRecipeCategory() {
		return recipeCategory;
	}

	@Override
	public Rect2i getRecipeTransferButtonArea() {
		return recipeTransferButtonArea.toMutable();
	}

	@Override
	public Rect2i getRecipeBookmarkButtonArea() {
		Rect2i area = getRecipeTransferButtonArea();
		area.setPosition(area.getX(), area.getY() - area.getHeight() - RECIPE_BUTTON_SPACING);
		return area;
	}

	@Override
	public IRecipeSlotsView getRecipeSlotsView() {
		return recipeSlots.getView();
	}

	@Override
	public R getRecipe() {
		return recipe;
	}

	public RecipeSlots getRecipeSlots() {
		return this.recipeSlots;
	}

}
