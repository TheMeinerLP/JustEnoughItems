package mezz.jei.library.plugins.vanilla.brewing;

import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.common.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BrewingRecipeCategory implements IRecipeCategory<IJeiBrewingRecipe> {
	private final IDrawable background;
	private final IDrawable icon;
	private final IDrawable slotDrawable;
	private final Component localizedName;
	private final IDrawableAnimated arrow;
	private final IDrawableAnimated bubbles;
	private final IDrawableStatic blazeHeat;

	public BrewingRecipeCategory(IGuiHelper guiHelper) {
		ResourceLocation location = Constants.RECIPE_GUI_VANILLA;
		background = guiHelper.drawableBuilder(location, 0, 0, 64, 60)
			.addPadding(1, 0, 0, 50)
			.build();
		icon = guiHelper.createDrawableItemStack(new ItemStack(Blocks.BREWING_STAND));
		localizedName = Component.translatable("gui.jei.category.brewing");

		arrow = guiHelper.drawableBuilder(location, 64, 0, 9, 28)
			.buildAnimated(400, IDrawableAnimated.StartDirection.TOP, false);

		ITickTimer bubblesTickTimer = new BrewingBubblesTickTimer(guiHelper);
		bubbles = guiHelper.drawableBuilder(location, 73, 0, 12, 29)
			.buildAnimated(bubblesTickTimer, IDrawableAnimated.StartDirection.BOTTOM);

		blazeHeat = guiHelper.createDrawable(location, 64, 29, 18, 4);

		slotDrawable = guiHelper.getSlotDrawable();
	}

	@Override
	public RecipeType<IJeiBrewingRecipe> getRecipeType() {
		return RecipeTypes.BREWING;
	}

	@Override
	public Component getTitle() {
		return localizedName;
	}

	@Override
	public IDrawable getBackground() {
		return background;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void draw(IJeiBrewingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
		blazeHeat.draw(guiGraphics, 5, 30);
		bubbles.draw(guiGraphics, 8, 0);
		arrow.draw(guiGraphics, 42, 2);

		int brewingSteps = recipe.getBrewingSteps();
		String brewingStepsString = brewingSteps < Integer.MAX_VALUE ? Integer.toString(brewingSteps) : "?";
		Component steps = Component.translatable("gui.jei.category.brewing.steps", brewingStepsString);
		Minecraft minecraft = Minecraft.getInstance();
		guiGraphics.drawString(minecraft.font, steps, 70, 28, 0xFF808080, false);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, IJeiBrewingRecipe recipe, IFocusGroup focuses) {
		List<ItemStack> potionInputs = recipe.getPotionInputs();

		builder.addSlot(RecipeIngredientRole.INPUT, 1, 37)
			.addItemStacks(potionInputs);

		builder.addSlot(RecipeIngredientRole.INPUT, 24, 44)
			.addItemStacks(potionInputs);

		builder.addSlot(RecipeIngredientRole.INPUT, 47, 37)
			.addItemStacks(potionInputs);

		builder.addSlot(RecipeIngredientRole.INPUT, 24, 3)
			.addItemStacks(recipe.getIngredients());

		builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 3)
			.addItemStack(recipe.getPotionOutput())
			.setBackground(slotDrawable, -1, -1);
	}

	@Override
	public @Nullable ResourceLocation getRegistryName(IJeiBrewingRecipe recipe) {
		return recipe.getUid();
	}

	private static class BrewingBubblesTickTimer implements ITickTimer {
		/**
		 * Similar to {@link BrewingStandScreen#BUBBLELENGTHS}
		 */
		@SuppressWarnings("JavadocReference")
		private static final int[] BUBBLE_LENGTHS = new int[]{29, 23, 18, 13, 9, 5, 0};
		private final ITickTimer internalTimer;

		public BrewingBubblesTickTimer(IGuiHelper guiHelper) {
			this.internalTimer = guiHelper.createTickTimer(14, BUBBLE_LENGTHS.length - 1, false);
		}

		@Override
		public int getValue() {
			int timerValue = this.internalTimer.getValue();
			return BUBBLE_LENGTHS[timerValue];
		}

		@Override
		public int getMaxValue() {
			return BUBBLE_LENGTHS[0];
		}
	}
}
