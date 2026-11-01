package dev.langcraft.client.ui;

import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.study.WorldFlashcards;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Lista os temas e aplica na hora o que estiver marcado, inclusive nesta tela. Só salva ao confirmar.
 * Vale só para o mundo aberto. Na primeira entrada em um mundo não há como cancelar, porque o restante da configuração espera a escolha.
 */
public final class ThemeScreen extends Screen {
	private static final int LIST_WIDTH = 130;
	private static final int MAX_PREVIEW_WIDTH = 250;
	private static final int PREVIEW_HEIGHT = 150;
	private static final int GAP = 8;
	private static final int ROW_GAP = 2;
	private static final int TOP = 38;
	private static final int PLATE_HEIGHT = 30;
	private static final int BOTTOM_BUTTON_WIDTH = 120;
	private static final int PADDING = 10;
	private static final float SAMPLE_SCALE = 2f;
	private static final ItemStack SAMPLE_ITEM = new ItemStack(Items.COBBLESTONE);
	private static final List<Furigana.Segment> SAMPLE_KOREAN = List.of(new Furigana.Segment("조약돌", null));
	private static final List<Furigana.Segment> SAMPLE_JAPANESE = List.of(new Furigana.Segment("丸石", "まるいし"));

	private final @Nullable Screen parent;
	private final @Nullable Runnable onFirstChoice;
	private UiTheme selected;
	private boolean confirmed;

	/** Abre a escolha opcional, que volta para {@code parent}. */
	public ThemeScreen(@Nullable Screen parent) {
		this(parent, null);
	}

	/** Abre a escolha obrigatória, que chama {@code onFirstChoice} ao confirmar. */
	public ThemeScreen(Runnable onFirstChoice) {
		this(null, onFirstChoice);
	}

	private ThemeScreen(@Nullable Screen parent, @Nullable Runnable onFirstChoice) {
		super(Component.translatable("langcraft.theme.title"));
		this.parent = parent;
		this.onFirstChoice = onFirstChoice;
		this.selected = UiTheme.current();
		UiTheme.setPreview(selected);
	}

	public UiTheme selected() {
		return selected;
	}

	private int previewWidth() {
		return Math.min(MAX_PREVIEW_WIDTH, width - LIST_WIDTH - GAP - 4);
	}

	private int left() {
		return (width - LIST_WIDTH - GAP - previewWidth()) / 2;
	}

	@Override
	protected void init() {
		List<UiTheme> themes = UiTheme.selectable();
		int rowHeight = Math.clamp((height - TOP - 32) / themes.size() - ROW_GAP, 14, 20);
		int y = TOP;
		for (UiTheme theme : themes) {
			Component name = Component.translatable(nameKey(theme));
			Component label = theme == selected ? Component.literal("✔ ").append(name) : name;
			addRenderableWidget(new ThemedButton(left(), y, LIST_WIDTH, rowHeight, label, button -> select(theme)));
			y += rowHeight + ROW_GAP;
		}
		int bottom = height - 26;
		Component confirm = Component.translatable("langcraft.theme.confirm");
		if (onFirstChoice != null) {
			addRenderableWidget(new ThemedButton((width - BOTTOM_BUTTON_WIDTH) / 2, bottom, BOTTOM_BUTTON_WIDTH, 20, confirm, button -> confirm()));
		} else {
			addRenderableWidget(new ThemedButton(width / 2 - BOTTOM_BUTTON_WIDTH - 4, bottom, BOTTOM_BUTTON_WIDTH, 20, confirm, button -> confirm()));
			addRenderableWidget(new ThemedButton(width / 2 + 4, bottom, BOTTOM_BUTTON_WIDTH, 20, Component.translatable("langcraft.theme.cancel"), button -> onClose()));
		}
	}

	private static String nameKey(UiTheme theme) {
		return "langcraft.theme." + theme.id();
	}

	private void select(UiTheme theme) {
		selected = theme;
		UiTheme.setPreview(theme);
		rebuildWidgets();
	}

	private void confirm() {
		confirmed = true;
		UiTheme.setPreview(null);
		WorldFlashcards.saveThemeId(selected.id());
		if (onFirstChoice != null) {
			onFirstChoice.run();
		} else {
			minecraft.setScreen(parent);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		int plateWidth = Math.min(width - 8, 300);
		UiDraw.panel(graphics, (width - plateWidth) / 2, 4, plateWidth, PLATE_HEIGHT);
		super.render(graphics, mouseX, mouseY, partialTick);
		UiDraw.centered(graphics, font, title, width / 2, 9, palette.ink());
		UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.theme.hint"), plateWidth - 16), width / 2, 21, palette.muted());
		renderPreview(graphics, palette);
	}

	/** Mostra uma amostra das peças que mudam com o tema. */
	private void renderPreview(GuiGraphics graphics, UiTheme.Palette palette) {
		int previewWidth = previewWidth();
		int x = left() + LIST_WIDTH + GAP;
		int y = TOP;
		int right = x + previewWidth - PADDING;
		int inner = previewWidth - PADDING * 2;
		int center = x + previewWidth / 2;
		UiDraw.panel(graphics, x, y, previewWidth, PREVIEW_HEIGHT);

		UiDraw.centered(graphics, font, Component.translatable(nameKey(selected)), center, y + 7, palette.accent());
		UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable(nameKey(selected) + ".tag"), inner), center, y + 18, palette.muted());

		int wordY = y + 30;
		graphics.renderItem(SAMPLE_ITEM, x + PADDING, wordY + 11);
		FuriganaText.draw(graphics, font, SAMPLE_KOREAN, x + PADDING + 20, wordY + 11, SAMPLE_SCALE, palette.ink(), palette.shadow());
		FuriganaText.draw(graphics, font, SAMPLE_JAPANESE, right - FuriganaText.width(font, SAMPLE_JAPANESE, SAMPLE_SCALE), wordY, SAMPLE_SCALE, palette.ink(), palette.shadow());

		UiDraw.text(graphics, font, UiDraw.fit(font, Component.translatable("langcraft.hud.speak"), inner), x + PADDING, y + 62, palette.muted());
		UiDraw.text(graphics, font, Component.translatable("langcraft.hud.heard", "화강암"), x + PADDING, y + 73, palette.red());
		UiDraw.streak(graphics, font, 3, 5, right, y + 73);
		UiDraw.timerBar(graphics, x + PADDING, y + 85, inner, 4, 0.62f);

		int buttonWidth = (inner - 4) / 2;
		UiTheme theme = UiTheme.current();
		UiTheme.Buttons buttons = theme.buttons();
		theme.drawButton(graphics, x + PADDING, y + 94, buttonWidth, 18, true, false);
		graphics.drawString(font, "조약돌", x + PADDING + (buttonWidth - font.width("조약돌")) / 2, y + 99, buttons.ink(), buttons.shadow());
		int offX = right - buttonWidth;
		theme.drawButton(graphics, offX, y + 94, buttonWidth, 18, false, false);
		int wrongWidth = font.width("화강암");
		int wrongX = offX + (buttonWidth - wrongWidth) / 2;
		graphics.drawString(font, "화강암", wrongX, y + 99, buttons.wrongInk(), false);
		graphics.fill(wrongX - 2, y + 103, wrongX + wrongWidth + 2, y + 104, buttons.wrongInk());

		UiDraw.text(graphics, font, Component.translatable("langcraft.theme.wheel"), x + PADDING, y + 118, palette.muted());
		int[] slices = theme.wheel().punishment();
		for (int i = 0; i < slices.length; i++) {
			int sliceX = x + PADDING + inner * i / slices.length;
			graphics.fill(sliceX, y + 129, x + PADDING + inner * (i + 1) / slices.length, y + 141, slices[i]);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return onFirstChoice == null;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		if (!confirmed) {
			UiTheme.setPreview(null);
		}
	}
}
