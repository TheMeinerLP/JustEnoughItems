package mezz.jei.gui.recipes;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.gui.TooltipRenderer;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.common.util.StringUtil;
import mezz.jei.gui.GuiProperties;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class RecipesGui extends Screen implements IRecipesGui, IRecipeFocusSource, IRecipeLogicStateListener {
	private static final int borderPadding = 6;
	private static final int minRecipePadding = 4;
	private static final int navBarPadding = 2;
	private static final int titleInnerPadding = 14;
	private static final int buttonWidth = 13;
	private static final int buttonHeight = 13;
	private static final int minGuiWidth = 198;

	private final IRecipeTransferManager recipeTransferManager;
	private final IModIdHelper modIdHelper;
	private final IClientConfig clientConfig;
	private final IInternalKeyMappings keyBindings;
	private final IRecipeManager recipeManager;
	private final Textures textures;
	private final BookmarkList bookmarks;
	private final IGuiHelper guiHelper;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;

	private int headerHeight;

	/* Internal logic for the gui, handles finding recipes */
	private final IRecipeGuiLogic logic;

	/* List of RecipeLayout to display */
	private final List<IRecipeLayoutDrawable<?>> recipeLayouts = new ArrayList<>();

	private String pageString = "1/1";
	private Component title = CommonComponents.EMPTY;
	private final DrawableNineSliceTexture background;

	private final RecipeCatalysts recipeCatalysts;
	private final RecipeGuiTabs recipeGuiTabs;

	private final Map<IRecipeLayoutDrawable<?>, RecipeTransferButton> recipeTransferButtons;
	private final Map<IRecipeLayoutDrawable<?>, RecipeBookmarkButton> recipeBookmarkButtons;

	private final GuiIconButtonSmall nextRecipeCategory;
	private final GuiIconButtonSmall previousRecipeCategory;
	private final GuiIconButtonSmall nextPage;
	private final GuiIconButtonSmall previousPage;

	@Nullable
	private Screen parentScreen;
	/**
	 * The GUI tries to size itself to this ideal area.
	 * This is a stable place to anchor buttons so that
	 * they don't move when the GUI resizes.
	 */
	private ImmutableRect2i idealArea = ImmutableRect2i.EMPTY;
	/**
	 * This is the actual are of the GUI, which temporarily
	 * stretches to fit large recipes.
	 */
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private ImmutableRect2i titleArea = ImmutableRect2i.EMPTY;
	private ImmutableRect2i titleStringArea = ImmutableRect2i.EMPTY;

	private boolean init = false;

	public RecipesGui(
		IRecipeManager recipeManager,
		IRecipeTransferManager recipeTransferManager,
		IIngredientManager ingredientManager,
		IModIdHelper modIdHelper,
		IClientConfig clientConfig,
		Textures textures,
		IInternalKeyMappings keyBindings,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		IGuiHelper guiHelper
	) {
		super(Component.literal("Recipes"));
		this.recipeManager = recipeManager;
		this.textures = textures;
		this.bookmarks = bookmarks;
		this.guiHelper = guiHelper;
		this.recipeTransferButtons = new HashMap<>();
		this.recipeBookmarkButtons = new HashMap<>();
		this.recipeTransferManager = recipeTransferManager;
		this.ingredientManager = ingredientManager;
		this.modIdHelper = modIdHelper;
		this.clientConfig = clientConfig;
		this.keyBindings = keyBindings;
		this.logic = new RecipeGuiLogic(recipeManager, recipeTransferManager, this, focusFactory);
		this.recipeCatalysts = new RecipeCatalysts(textures, recipeManager);
		this.recipeGuiTabs = new RecipeGuiTabs(this.logic, textures, recipeManager, guiHelper);
		this.focusFactory = focusFactory;
		this.minecraft = Minecraft.getInstance();

		IDrawableStatic arrowNext = textures.getArrowNext();
		IDrawableStatic arrowPrevious = textures.getArrowPrevious();

		nextRecipeCategory = new GuiIconButtonSmall(0, 0, buttonWidth, buttonHeight, arrowNext, b -> logic.nextRecipeCategory(), textures);
		previousRecipeCategory = new GuiIconButtonSmall(0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> logic.previousRecipeCategory(), textures);
		nextPage = new GuiIconButtonSmall(0, 0, buttonWidth, buttonHeight, arrowNext, b -> logic.nextPage(), textures);
		previousPage = new GuiIconButtonSmall(0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> logic.previousPage(), textures);

		background = textures.getRecipeGuiBackground();
	}

	private static void drawCenteredStringWithShadow(GuiGraphics guiGraphics, Font font, String string, ImmutableRect2i area) {
		ImmutableRect2i textArea = MathUtil.centerTextArea(area, font, string);
		guiGraphics.drawString(font, string, textArea.getX(), textArea.getY(), 0xFFFFFFFF);
	}

	private static void drawCenteredStringWithShadow(GuiGraphics guiGraphics, Font font, Component text, ImmutableRect2i area) {
		ImmutableRect2i textArea = MathUtil.centerTextArea(area, font, text);
		guiGraphics.drawString(font, text, textArea.getX(), textArea.getY(), 0xFFFFFFFF);
	}

	public ImmutableRect2i getArea() {
		return this.area;
	}

	public int getRecipeCatalystExtraWidth() {
		if (recipeCatalysts.isEmpty()) {
			return 0;
		}
		return recipeCatalysts.getWidth();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void init() {
		super.init();

		final int xSize = minGuiWidth;
		int ySize;
		if (this.clientConfig.isCenterSearchBarEnabled()) {
			ySize = this.height - 76;
		} else {
			ySize = this.height - 58;
		}
		int extraSpace = 0;
		final int maxHeight = this.clientConfig.getMaxRecipeGuiHeight();
		if (ySize > maxHeight) {
			extraSpace = ySize - maxHeight;
			ySize = maxHeight;
		}

		final int guiLeft = (this.width - xSize) / 2;
		final int guiTop = RecipeGuiTab.TAB_HEIGHT + 21 + (extraSpace / 2);

		this.idealArea = new ImmutableRect2i(guiLeft, guiTop, xSize, ySize);
		this.area = this.idealArea;

		final int rightButtonX = guiLeft + xSize - borderPadding - buttonWidth;
		final int leftButtonX = guiLeft + borderPadding;

		int titleHeight = font.lineHeight + borderPadding;
		int recipeClassButtonTop = guiTop + titleHeight - buttonHeight + navBarPadding;
		nextRecipeCategory.setX(rightButtonX);
		nextRecipeCategory.setY(recipeClassButtonTop);
		previousRecipeCategory.setX(leftButtonX);
		previousRecipeCategory.setY(recipeClassButtonTop);

		int pageButtonTop = recipeClassButtonTop + buttonHeight + navBarPadding;
		nextPage.setX(rightButtonX);
		nextPage.setY(pageButtonTop);
		previousPage.setX(leftButtonX);
		previousPage.setY(pageButtonTop);

		this.headerHeight = (pageButtonTop + buttonHeight) - guiTop;
		this.titleArea = MathUtil.union(previousRecipeCategory.getArea(), nextRecipeCategory.getArea())
			.cropLeft(previousRecipeCategory.getWidth() + titleInnerPadding)
			.cropRight(nextRecipeCategory.getWidth() + titleInnerPadding);

		this.addRenderableWidget(nextRecipeCategory);
		this.addRenderableWidget(previousRecipeCategory);
		this.addRenderableWidget(nextPage);
		this.addRenderableWidget(previousPage);

		this.init = true;
		updateLayout();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (minecraft == null) {
			return;
		}
		renderTransparentBackground(guiGraphics);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		this.background.draw(guiGraphics, area);

		RenderSystem.disableBlend();

		guiGraphics.fill(
			RenderType.gui(),
			previousRecipeCategory.getX() + previousRecipeCategory.getWidth(),
			previousRecipeCategory.getY(),
			nextRecipeCategory.getX(),
			nextRecipeCategory.getY() + nextRecipeCategory.getHeight(),
			0x30000000
		);
		guiGraphics.fill(
			RenderType.gui(),
			previousPage.getX() + previousPage.getWidth(),
			previousPage.getY(),
			nextPage.getX(),
			nextPage.getY() + nextPage.getHeight(),
			0x30000000
		);

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		drawCenteredStringWithShadow(guiGraphics, font, title, titleArea);

		ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
		drawCenteredStringWithShadow(guiGraphics, font, pageString, pageArea);

		nextRecipeCategory.render(guiGraphics, mouseX, mouseY, partialTicks);
		previousRecipeCategory.render(guiGraphics, mouseX, mouseY, partialTicks);
		nextPage.render(guiGraphics, mouseX, mouseY, partialTicks);
		previousPage.render(guiGraphics, mouseX, mouseY, partialTicks);

		Optional<IRecipeLayoutDrawable<?>> hoveredRecipeLayout = drawLayouts(guiGraphics, mouseX, mouseY);
		Optional<IRecipeSlotDrawable> hoveredRecipeCatalyst = recipeCatalysts.draw(guiGraphics, mouseX, mouseY);

		recipeGuiTabs.draw(minecraft, guiGraphics, mouseX, mouseY, modIdHelper, partialTicks);

		for (RecipeTransferButton button : recipeTransferButtons.values()) {
			button.drawToolTip(guiGraphics, mouseX, mouseY);
		}
		for (RecipeBookmarkButton button : recipeBookmarkButtons.values()) {
			button.drawToolTip(guiGraphics, mouseX, mouseY);
		}
		RenderSystem.disableBlend();

		hoveredRecipeLayout.ifPresent(l -> l.drawOverlays(guiGraphics, mouseX, mouseY));
		hoveredRecipeCatalyst.ifPresent(h -> h.drawHoverOverlays(guiGraphics));

		hoveredRecipeCatalyst.ifPresent(h ->
			h.getDisplayedIngredient()
				.ifPresent(i -> {
					List<Component> tooltip = h.getTooltip();
					tooltip = modIdHelper.addModNameToIngredientTooltip(tooltip, i);
					TooltipRenderer.drawHoveringText(guiGraphics, tooltip, mouseX, mouseY, i, ingredientManager);
				})
		);
		RenderSystem.enableDepthTest();

		if (titleStringArea.contains(mouseX, mouseY) && !logic.hasAllCategories()) {
			MutableComponent showAllRecipesString = Component.translatable("jei.tooltip.show.all.recipes");
			TooltipRenderer.drawHoveringText(guiGraphics, List.of(showAllRecipesString), mouseX, mouseY);
		}

		if (DebugConfig.isDebugGuisEnabled()) {
			guiGraphics.fill(
				RenderType.gui(),
				idealArea.getX(),
				idealArea.getY(),
				idealArea.getX() + idealArea.getWidth(),
				idealArea.getY() + idealArea.getHeight(),
				0x4400FF00
			);

			guiGraphics.fill(
				RenderType.gui(),
				area.getX(),
				area.getY(),
				area.getX() + area.getWidth(),
				area.getY() + area.getHeight(),
				0x44990044
			);

			ImmutableRect2i recipeLayoutsArea = getRecipeLayoutsArea();
			guiGraphics.fill(
				RenderType.gui(),
				recipeLayoutsArea.getX(),
				recipeLayoutsArea.getY(),
				recipeLayoutsArea.getX() + recipeLayoutsArea.getWidth(),
				recipeLayoutsArea.getY() + recipeLayoutsArea.getHeight(),
				0x44228844
			);
		}
	}

	private void updateAreaToFitLayouts() {
		if (recipeLayouts.isEmpty()) {
			return;
		}
		final int padding = 2 * borderPadding;
		int width = minGuiWidth - padding;

		IRecipeLayoutDrawable<?> recipeLayout = recipeLayouts.getFirst();
		int recipeWidth = layoutWidthWithButtons(recipeLayout);
		width = Math.max(recipeWidth, width);

		final int newWidth = width + padding;
		final int newX = (this.width - newWidth) / 2;

		this.area = new ImmutableRect2i(
			newX,
			this.area.getY(),
			newWidth,
			this.area.getHeight()
		);
	}

	private int layoutWidthWithButtons(IRecipeLayoutDrawable<?> recipeLayout) {
		Rect2i area = recipeLayout.getRectWithBorder();
		int width = area.getWidth();

		RecipeTransferButton recipeTransferButton = recipeTransferButtons.get(recipeLayout);
		if (recipeTransferButton != null && recipeTransferButton.visible) {
			Rect2i buttonArea = recipeLayout.getRecipeTransferButtonArea();
			int buttonRight = buttonArea.getWidth() + buttonArea.getX();
			width = Math.max(buttonRight - area.getX(), width);
		}

		RecipeBookmarkButton recipeBookmarkButton = recipeBookmarkButtons.get(recipeLayout);
		if (recipeBookmarkButton != null && recipeBookmarkButton.visible) {
			Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
			int buttonRight = buttonArea.getWidth() + buttonArea.getX();
			width = Math.max(buttonRight - area.getX(), width);
		}

		return width;
	}

	private Optional<IRecipeLayoutDrawable<?>> drawLayouts(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		IRecipeLayoutDrawable<?> hoveredLayout = null;
		for (IRecipeLayoutDrawable<?> recipeLayout : recipeLayouts) {
			if (recipeLayout.isMouseOver(mouseX, mouseY)) {
				hoveredLayout = recipeLayout;
			}
			recipeLayout.drawRecipe(guiGraphics, mouseX, mouseY);
		}

		Minecraft minecraft = Minecraft.getInstance();
		DeltaTracker deltaTracker = minecraft.getTimer();
		float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
		for (RecipeTransferButton button : recipeTransferButtons.values()) {
			button.render(guiGraphics, mouseX, mouseY, partialTicks);
		}
		for (RecipeBookmarkButton button : recipeBookmarkButtons.values()) {
			button.render(guiGraphics, mouseX, mouseY, partialTicks);
		}
		RenderSystem.disableBlend();
		return Optional.ofNullable(hoveredLayout);
	}

	@Override
	public void tick() {
		super.tick();

		Optional.ofNullable(minecraft)
			.map(minecraft -> minecraft.player)
			.ifPresent(localPlayer -> {
				AbstractContainerMenu container = getParentContainer().orElse(null);
				for (RecipeTransferButton button : this.recipeTransferButtons.values()) {
					button.update(recipeTransferManager, container, localPlayer);
				}
			});
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (minecraft != null && minecraft.screen == this) {
			if (this.area.contains(mouseX, mouseY)) {
				return true;
			}
			for (IRecipeLayoutDrawable<?> recipeLayout : this.recipeLayouts) {
				if (recipeLayout.isMouseOver(mouseX, mouseY)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (isOpen()) {
			return Stream.concat(
				recipeCatalysts.getIngredientUnderMouse(mouseX, mouseY),
				getRecipeLayoutsIngredientUnderMouse(mouseX, mouseY)
			);
		}
		return Stream.empty();
	}

	private Stream<IClickableIngredientInternal<?>> getRecipeLayoutsIngredientUnderMouse(double mouseX, double mouseY) {
		return this.recipeLayouts.stream()
			.map(recipeLayout -> getRecipeLayoutIngredientUnderMouse(recipeLayout, mouseX, mouseY))
			.flatMap(Optional::stream);
	}

	private static Optional<IClickableIngredientInternal<?>> getRecipeLayoutIngredientUnderMouse(IRecipeLayoutDrawable<?> recipeLayout, double mouseX, double mouseY) {
		return recipeLayout.getRecipeSlotUnderMouse(mouseX, mouseY)
			.flatMap(recipeSlot -> getClickedIngredient(recipeLayout, recipeSlot));
	}

	private static Optional<IClickableIngredientInternal<?>> getClickedIngredient(IRecipeLayoutDrawable<?> recipeLayout, IRecipeSlotDrawable recipeSlot) {
		return recipeSlot.getDisplayedIngredient()
			.map(displayedIngredient -> {
				ImmutableRect2i area = absoluteClickedArea(recipeLayout, recipeSlot.getRect());
				IElement<?> element = new IngredientElement<>(displayedIngredient);
				return new ClickableIngredientInternal<>(element, area, false, true);
			});
	}

	/**
	 * Converts from relative recipeLayout coordinates to absolute screen coordinates
	 */
	private static ImmutableRect2i absoluteClickedArea(IRecipeLayoutDrawable<?> recipeLayout, Rect2i area) {
		Rect2i layoutArea = recipeLayout.getRect();

		return new ImmutableRect2i(
			area.getX() + layoutArea.getX(),
			area.getY() + layoutArea.getY(),
			area.getWidth(),
			area.getHeight()
		);
	}

	@Override
	public boolean mouseScrolled(double scrollX, double scrollY, double scrollDeltaX, double scrollDeltaY) {
		final double x = MouseUtil.getX();
		final double y = MouseUtil.getY();
		if (isMouseOver(x, y)) {
			if (scrollDeltaY < 0) {
				logic.nextPage();
				return true;
			} else if (scrollDeltaY > 0) {
				logic.previousPage();
				return true;
			}
		}
		return super.mouseScrolled(scrollX, scrollY, scrollDeltaX, scrollDeltaY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		boolean handled = UserInput.fromVanilla(mouseX, mouseY, mouseButton, InputType.IMMEDIATE)
			.map(this::handleInput)
			.orElse(false);

		if (handled) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		UserInput input = UserInput.fromVanilla(keyCode, scanCode, modifiers, InputType.IMMEDIATE);
		return handleInput(input);
	}

	private boolean handleInput(UserInput input) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		if (isMouseOver(mouseX, mouseY)) {
			if (titleStringArea.contains(mouseX, mouseY)) {
				if (input.is(keyBindings.getLeftClick()) && logic.showAllRecipes()) {
					return true;
				}
			} else {
				for (IRecipeLayoutDrawable<?> recipeLayout : recipeLayouts) {
					if (handleRecipeLayoutInput(recipeLayout, input)) {
						return true;
					}
				}
			}
		}

		IUserInputHandler handler = recipeGuiTabs.getInputHandler();
		if (handler.handleUserInput(this, input, keyBindings).isPresent()) {
			return true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (input.is(keyBindings.getCloseRecipeGui()) || input.is(minecraft.options.keyInventory)) {
			onClose();
			return true;
		} else if (input.is(keyBindings.getRecipeBack())) {
			back();
			return true;
		} else if (input.is(keyBindings.getNextCategory())) {
			logic.nextRecipeCategory();
			return true;
		} else if (input.is(keyBindings.getPreviousCategory())) {
			logic.previousRecipeCategory();
			return true;
		} else if (input.is(keyBindings.getNextRecipePage())) {
			logic.nextPage();
			return true;
		} else if (input.is(keyBindings.getPreviousRecipePage())) {
			logic.previousPage();
			return true;
		}
		return false;
	}

	private <R> boolean handleRecipeLayoutInput(IRecipeLayoutDrawable<R> recipeLayout, UserInput input) {
		if (!recipeLayout.isMouseOver(input.getMouseX(), input.getMouseY())) {
			return false;
		}

		Rect2i recipeArea = recipeLayout.getRect();
		double recipeMouseX = input.getMouseX() - recipeArea.getX();
		double recipeMouseY = input.getMouseY() - recipeArea.getY();
		R recipe = recipeLayout.getRecipe();
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		if (recipeCategory.handleInput(recipe, recipeMouseX, recipeMouseY, input.getKey())) {
			return true;
		}

		if (input.is(keyBindings.getCopyRecipeId())) {
			return handleCopyRecipeId(recipeLayout);
		}
		return false;
	}

	private <R> boolean handleCopyRecipeId(IRecipeLayoutDrawable<R> recipeLayout) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		ResourceLocation registryName = recipeCategory.getRegistryName(recipe);
		if (registryName == null) {
			MutableComponent message = Component.translatable("jei.message.copy.recipe.id.failure");
			if (player != null) {
				player.displayClientMessage(message, false);
			}
			return false;
		}
		String recipeId = registryName.toString();
		minecraft.keyboardHandler.setClipboard(recipeId);
		MutableComponent message = Component.translatable("jei.message.copy.recipe.id.success", Component.literal(recipeId));
		if (player != null) {
			player.displayClientMessage(message, false);
		}
		return true;
	}

	public boolean isOpen() {
		return minecraft != null && minecraft.screen == this;
	}

	private void open() {
		if (minecraft != null) {
			if (!isOpen()) {
				parentScreen = minecraft.screen;
			}
			minecraft.setScreen(this);
		}
	}

	@Override
	public void onClose() {
		if (isOpen() && minecraft != null) {
			minecraft.setScreen(parentScreen);
			parentScreen = null;
			logic.clearHistory();
			return;
		}
		super.onClose();
	}

	@Override
	public void show(List<IFocus<?>> focuses) {
		IFocusGroup checkedFocuses = focusFactory.createFocusGroup(focuses);
		if (logic.showFocus(checkedFocuses)) {
			open();
		}
	}

	@Override
	public void showTypes(List<RecipeType<?>> recipeTypes) {
		ErrorUtil.checkNotEmpty(recipeTypes, "recipeTypes");

		if (logic.showCategories(recipeTypes)) {
			open();
		}
	}

	@Override
	public <T> void showRecipes(IRecipeCategory<T> recipeCategory, List<T> recipes, List<IFocus<?>> focuses) {
		ErrorUtil.checkNotNull(recipeCategory, "recipeCategory");
		ErrorUtil.checkNotEmpty(recipes, "recipes");
		IFocusGroup checkedFocuses = focusFactory.createFocusGroup(focuses);

		IFocusedRecipes<T> focusedRecipes = new StaticFocusedRecipes<>(recipeCategory, recipes);
		if (logic.showRecipes(focusedRecipes, checkedFocuses)) {
			open();
		}
	}

	@Override
	public <T> Optional<T> getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		double x = MouseUtil.getX();
		double y = MouseUtil.getY();

		return getIngredientUnderMouse(x, y)
			.map(IClickableIngredientInternal::getTypedIngredient)
			.flatMap(i -> i.getIngredient(ingredientType).stream())
			.findFirst();
	}

	public void back() {
		logic.back();
	}

	private void updateLayout() {
		if (!init) {
			return;
		}

		IRecipeCategory<?> recipeCategory = logic.getSelectedRecipeCategory();

		int availableHeight = getRecipeLayoutsArea().getHeight();

		final ImmutableRect2i layoutRect = logic.getRecipeLayoutSizeWithBorder()
			.orElseGet(() -> new ImmutableRect2i(0, 0, recipeCategory.getWidth(), recipeCategory.getHeight()));

		final int recipeHeight = layoutRect.getHeight();

		int recipesPerPage = 1 + ((availableHeight - recipeHeight) / (recipeHeight + minRecipePadding));

		if (recipesPerPage <= 0) {
			availableHeight = recipeHeight;
			recipesPerPage = 1;
		}

		logic.setRecipesPerPage(recipesPerPage);

		title = StringUtil.stripStyling(recipeCategory.getTitle());
		final int availableTitleWidth = titleArea.getWidth();
		if (font.width(title) > availableTitleWidth) {
			title = StringUtil.truncateStringToWidth(title, availableTitleWidth, font);
		}
		this.titleStringArea = MathUtil.centerTextArea(this.titleArea, font, title);

		recipeLayouts.clear();
		recipeLayouts.addAll(logic.getRecipeLayouts());

		addRecipeButtons(recipeLayouts);

		updateAreaToFitLayouts();
		ImmutableRect2i layoutsArea = getRecipeLayoutsArea();

		final int recipeXOffset = getRecipeXOffset(layoutRect, layoutsArea);
		final int recipeHeightTotal = recipesPerPage * layoutRect.getHeight();
		final int remainingHeight = availableHeight - recipeHeightTotal;
		final int recipeSpacing = remainingHeight / (recipesPerPage + 1);

		final int spacingY = layoutRect.getHeight() + recipeSpacing;
		int recipeYOffset = layoutsArea.getY() + recipeSpacing;
		for (IRecipeLayoutDrawable<?> recipeLayout : recipeLayouts) {
			Rect2i rectWithBorder = recipeLayout.getRectWithBorder();
			Rect2i rect = recipeLayout.getRect();
			recipeLayout.setPosition(
				recipeXOffset - rectWithBorder.getX() + rect.getX(),
				recipeYOffset - rectWithBorder.getY() + rect.getY()
			);
			recipeYOffset += spacingY;
		}

		updateRecipeButtonPositions();

		nextPage.active = previousPage.active = logic.hasMultiplePages();
		nextRecipeCategory.active = previousRecipeCategory.active = logic.hasMultipleCategories();

		pageString = logic.getPageString();

		List<ITypedIngredient<?>> recipeCatalystIngredients = logic.getRecipeCatalysts().toList();
		recipeCatalysts.updateLayout(recipeCatalystIngredients, this.area);
		recipeGuiTabs.initLayout(this.idealArea);
	}

	private int getRecipeXOffset(ImmutableRect2i layoutRect, ImmutableRect2i layoutsArea) {
		final int recipeWidth = layoutRect.getWidth();
		final int recipeWidthWithButtons;
		if (recipeLayouts.isEmpty()) {
			recipeWidthWithButtons = layoutRect.getWidth();
		} else {
			recipeWidthWithButtons = layoutWidthWithButtons(recipeLayouts.getFirst());
		}

		final int buttonSpace = recipeWidthWithButtons - recipeWidth;

		final int availableArea = layoutsArea.getWidth();
		if (availableArea > recipeWidth + (2 * buttonSpace)) {
			// we have enough room to nicely draw the recipe centered with the buttons off to the side
			return layoutsArea.getX() + (layoutsArea.getWidth() - recipeWidth) / 2;
		} else {
			// we can just barely fit, center the recipe and buttons all together in the available area
			return layoutsArea.getX() + (layoutsArea.getWidth() - recipeWidthWithButtons) / 2;
		}
	}

	private ImmutableRect2i getRecipeLayoutsArea() {
		return new ImmutableRect2i(
			area.getX() + borderPadding,
			area.getY() + headerHeight + navBarPadding,
			area.getWidth() - (2 * borderPadding),
			area.getHeight() - (headerHeight + borderPadding + navBarPadding)
		);
	}

	private void addRecipeButtons(List<IRecipeLayoutDrawable<?>> recipeLayouts) {
		if (minecraft == null) {
			return;
		}
		Player player = minecraft.player;
		if (player == null) {
			return;
		}

		for (GuiEventListener button : this.recipeTransferButtons.values()) {
			removeWidget(button);
		}
		this.recipeTransferButtons.clear();

		for (GuiEventListener button : this.recipeBookmarkButtons.values()) {
			removeWidget(button);
		}
		this.recipeBookmarkButtons.clear();

		AbstractContainerMenu container = getParentContainer().orElse(null);

		for (IRecipeLayoutDrawable<?> recipeLayout : recipeLayouts) {
			{
				Rect2i buttonArea = recipeLayout.getRecipeTransferButtonArea();
				Rect2i layoutArea = recipeLayout.getRect();
				buttonArea.setX(buttonArea.getX() + layoutArea.getX());
				buttonArea.setY(buttonArea.getY() + layoutArea.getY());

				IDrawable icon = textures.getRecipeTransfer();
				RecipeTransferButton button = new RecipeTransferButton(icon, recipeLayout, textures, this::onClose);
				button.setArea(buttonArea);
				button.update(recipeTransferManager, container, player);
				addRenderableWidget(button);
				this.recipeTransferButtons.put(recipeLayout, button);
			}
			{
				RecipeBookmarkButton.create(recipeLayout, ingredientManager, bookmarks, textures, recipeManager, guiHelper)
					.ifPresent(button -> {
						addRenderableWidget(button);
						this.recipeBookmarkButtons.put(recipeLayout, button);
					});
			}
		}
	}

	private void updateRecipeButtonPositions() {
		for (IRecipeLayoutDrawable<?> recipeLayout : recipeLayouts) {
			{
				RecipeTransferButton button = recipeTransferButtons.get(recipeLayout);
				if (button != null) {
					Rect2i buttonArea = recipeLayout.getRecipeTransferButtonArea();
					Rect2i layoutArea = recipeLayout.getRect();
					buttonArea.setX(buttonArea.getX() + layoutArea.getX());
					buttonArea.setY(buttonArea.getY() + layoutArea.getY());
					button.setArea(buttonArea);
				}
			}
			{
				RecipeBookmarkButton button = recipeBookmarkButtons.get(recipeLayout);
				if (button != null) {
					Rect2i buttonArea = recipeLayout.getRecipeBookmarkButtonArea();
					Rect2i layoutArea = recipeLayout.getRect();
					buttonArea.setX(buttonArea.getX() + layoutArea.getX());
					buttonArea.setY(buttonArea.getY() + layoutArea.getY());
					button.setArea(buttonArea);
				}
			}
		}
	}

	private Optional<AbstractContainerMenu> getParentContainer() {
		if (parentScreen instanceof AbstractContainerScreen<?> screen) {
			AbstractContainerMenu menu = screen.getMenu();
			return Optional.of(menu);
		}
		return Optional.empty();
	}

	@Override
	public void onStateChange() {
		updateLayout();
	}

	@Nullable
	public IGuiProperties getProperties() {
		if (width <= 0 || height <= 0) {
			return null;
		}
		int extraWidth = getRecipeCatalystExtraWidth();
		ImmutableRect2i recipeArea = getArea();
		int guiXSize = recipeArea.getWidth() + extraWidth;
		int guiYSize = recipeArea.getHeight();
		if (guiXSize <= 0 || guiYSize <= 0) {
			return null;
		}
		return new GuiProperties(
			getClass(),
			recipeArea.getX() - extraWidth,
			recipeArea.getY(),
			guiXSize,
			guiYSize,
			width,
			height
		);
	}
}
