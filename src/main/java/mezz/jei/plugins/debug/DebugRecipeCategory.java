package mezz.jei.plugins.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import mezz.jei.Internal;
import mezz.jei.api.constants.ModIds;
import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IGuiIngredientGroup;
import mezz.jei.api.gui.ingredient.IGuiItemStackGroup;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.IMutableIngredients;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.config.Constants;
import mezz.jei.gui.GuiHelper;
import mezz.jei.plugins.jei.ingredients.DebugIngredient;
import mezz.jei.runtime.JeiHelpers;

public class DebugRecipeCategory implements IRecipeCategory<DebugRecipe> {
	public static final ResourceLocation UID = new ResourceLocation(ModIds.JEI_ID, "debug");
	public static final int RECIPE_WIDTH = 160;
	public static final int RECIPE_HEIGHT = 60;
	private final IDrawable background;
	private final String localizedName;
	private final IDrawable tankBackground;
	private final IDrawable tankOverlay;
	private boolean hiddenRecipes;

	public DebugRecipeCategory(IGuiHelper guiHelper) {
		this.background = guiHelper.createBlankDrawable(RECIPE_WIDTH, RECIPE_HEIGHT);
		this.localizedName = "debug";

		ResourceLocation backgroundTexture = new ResourceLocation(ModIds.JEI_ID, Constants.TEXTURE_GUI_PATH + "debug.png");
		this.tankBackground = guiHelper.drawableBuilder(backgroundTexture, 220, 196, 18, 60)
			.addPadding(-1, -1, -1, -1)
			.build();
		this.tankOverlay = guiHelper.drawableBuilder(backgroundTexture, 238, 196, 18, 60)
			.addPadding(-1, -1, -1, -1)
			.build();
	}

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public Class<? extends DebugRecipe> getRecipeClass() {
		return DebugRecipe.class;
	}

	@Override
	public String getTitle() {
		return localizedName;
	}

	@Override
	public IDrawable getBackground() {
		return background;
	}

	@Override
	public IDrawable getIcon() {
		JeiHelpers helpers = Internal.getHelpers();
		GuiHelper guiHelper = helpers.getGuiHelper();
		return guiHelper.getConfigButtonIcon();
	}

	@Override
	public void setIngredients(DebugRecipe recipe, IMutableIngredients ingredients) {
		//		FluidStack water = new FluidStack(FluidRegistry.WATER, 1000 + (int) (Math.random() * 1000));
//		FluidStack lava = new FluidStack(FluidRegistry.LAVA, 1000 + (int) (Math.random() * 1000));
//
//		ingredients.setInputs(VanillaTypes.FLUID, Arrays.asList(water, lava));

		ingredients.setInput(VanillaTypes.ITEM, new ItemStack(Items.STICK));

		ingredients.setInputLists(DebugIngredient.TYPE, Collections.singletonList(
			Arrays.asList(new DebugIngredient(0), new DebugIngredient(1))
		));

		ingredients.setOutputs(DebugIngredient.TYPE, Arrays.asList(
			new DebugIngredient(2),
			new DebugIngredient(3)
		));
	}

//	public List<FluidStack> getFluidInputs() {
//		return Arrays.asList(
//				new FluidStack(FluidRegistry.WATER, 1000 + (int) (Math.random() * 1000)),
//				new FluidStack(FluidRegistry.LAVA, 1000 + (int) (Math.random() * 1000))
//		);
//	}

	@Override
	public void draw(DebugRecipe recipe, double mouseX, double mouseY) {
		IJeiRuntime runtime = JeiDebugPlugin.jeiRuntime;
		if (runtime != null) {
			IIngredientFilter ingredientFilter = runtime.getIngredientFilter();
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.fontRenderer.drawString(ingredientFilter.getFilterText(), 20, 52, 0);

			IIngredientListOverlay ingredientListOverlay = runtime.getIngredientListOverlay();
			Object ingredientUnderMouse = ingredientListOverlay.getIngredientUnderMouse();
			if (ingredientUnderMouse != null) {
				drawIngredientName(minecraft, ingredientUnderMouse);
			}
		}

		GuiButtonExt button = recipe.getButton();
		button.render((int) mouseX, (int) mouseY, 0);
	}

	private <T> void drawIngredientName(Minecraft minecraft, T ingredient) {
		IIngredientManager ingredientManager = JeiDebugPlugin.ingredientManager;
		if (ingredientManager != null) {
			IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient);
			String jeiUid = ingredientHelper.getUniqueId(ingredient);
			minecraft.fontRenderer.drawString(jeiUid, 50, 52, 0);
		}
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, DebugRecipe recipe, IIngredients ingredients) {
		IGuiItemStackGroup guiItemStacks = recipeLayout.getItemStacks();

		guiItemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
			if (input) {
				tooltip.add(slotIndex + " Input itemStack");
			} else {
				tooltip.add(slotIndex + " Output itemStack");
			}
		});

		guiItemStacks.init(0, false, 70, 0);
		guiItemStacks.init(1, true, 110, 0);
		guiItemStacks.set(0, new ItemStack(Items.WATER_BUCKET));
		guiItemStacks.set(1, new ItemStack(Items.LAVA_BUCKET));

//		IGuiFluidStackGroup guiFluidStacks = recipeLayout.getFluidStacks();
//		guiFluidStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
//			if (input) {
//				tooltip.add(slotIndex + " Input fluidStack");
//			} else {
//				tooltip.add(slotIndex + " Output fluidStack");
//			}
//		});
//
//		guiFluidStacks.init(0, false, 90, 0, 16, 58, 16000, false, tankOverlay);
//		guiFluidStacks.init(1, true, 24, 0, 12, 47, 2000, true, null);
//
//		guiFluidStacks.setBackground(0, tankBackground);
//
//		List<FluidStack> fluidInputs = recipeWrapper.getFluidInputs();
//		guiFluidStacks.set(0, fluidInputs.get(0));
//		guiFluidStacks.set(1, fluidInputs.get(1));

		IGuiIngredientGroup<DebugIngredient> debugIngredientsGroup = recipeLayout.getIngredientsGroup(DebugIngredient.TYPE);
		debugIngredientsGroup.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
			if (input) {
				tooltip.add(slotIndex + " Input DebugIngredient");
			} else {
				tooltip.add(slotIndex + " Output DebugIngredient");
			}
		});

		debugIngredientsGroup.init(0, true, 40, 0);
		debugIngredientsGroup.init(1, false, 40, 16);
		debugIngredientsGroup.init(2, false, 40, 32);

		debugIngredientsGroup.set(ingredients);
	}

	@Override
	public List<String> getTooltipStrings(DebugRecipe recipe, double mouseX, double mouseY) {
		List<String> tooltipStrings = new ArrayList<>();
		tooltipStrings.add("Debug Recipe Category Tooltip");

		if (recipe.checkHover(mouseX, mouseY)) {
			tooltipStrings.add("button tooltip!");
		} else {
			tooltipStrings.add(TextFormatting.BOLD + "tooltip debug");
		}
		tooltipStrings.add(mouseX + ", " + mouseY);
		return tooltipStrings;
	}

	@Override
	public boolean handleClick(DebugRecipe recipe, double mouseX, double mouseY, int mouseButton) {
		GuiButtonExt button = recipe.getButton();
		if (mouseButton == 0 && button.mouseClicked(mouseX, mouseY, mouseButton)) {
			Minecraft minecraft = Minecraft.getInstance();
			EntityPlayerSP player = minecraft.player;
			if (player != null) {
				GuiScreen screen = new GuiInventory(player);
				minecraft.displayGuiScreen(screen);
			}
			IJeiRuntime runtime = JeiDebugPlugin.jeiRuntime;
			if (runtime != null) {
				IIngredientFilter ingredientFilter = runtime.getIngredientFilter();
				String filterText = ingredientFilter.getFilterText();
				ingredientFilter.setFilterText(filterText + " test");

				IRecipeManager recipeManager = runtime.getRecipeManager();
				if (!hiddenRecipes) {
					recipeManager.hideRecipeCategory(VanillaRecipeCategoryUid.CRAFTING);
					hiddenRecipes = true;
				} else {
					recipeManager.unhideRecipeCategory(VanillaRecipeCategoryUid.CRAFTING);
					hiddenRecipes = false;
				}
			}
			return true;
		}
		return false;
	}
}
