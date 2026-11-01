package dev.langcraft.client.ui;

import dev.langcraft.LangCraft;
import dev.langcraft.client.study.WorldFlashcards;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Os visuais do mod. Cada mundo tem o seu, escolhido ao entrar nele pela primeira vez. */
public enum UiTheme {
	CLASSIC("classico", 0, false, false, false, false,
			new Palette(0xE0181A20, 0xE0601A1A, 0xE01A5A2C, 0xFFFFFFFF, 0xFFB8BCC4, 0xFFFFD466, 0xFFFF5F56, 0xFF5FE07A, 0xFF3A3D45, 0xFF5FE07A, 0xFFFFD466, 0xFFFF5F56, true),
			new Buttons(0xFFFFFFFF, true, 0xFFA0A0A0, 0xFFFF5F56),
			new Wheel(new int[] {0xFF4CAF6A, 0xFFE0B23C, 0xFFE58A34, 0xFFD6543C, 0xFF8E1F2B}, new int[] {0xFF6FA8DC, 0xFF4CAF6A, 0xFF9CCC4C, 0xFFE0B23C, 0xFFF4D35E}, 0xFF20222A, 0xFFFFFFFF)),
	INVENTORY("inventario", 3, true, false, false, false,
			new Palette(0xFFC6C6C6, 0xFFDBA79F, 0xFFA6D09F, 0xFF2B2B2B, 0xFF555555, 0xFF6E4600, 0xFF9C1616, 0xFF14632A, 0xFF8B8B8B, 0xFF2F8F3A, 0xFFB98A12, 0xFFB3261E, false),
			new Buttons(0xFFFFFFFF, true, 0xFFA0A0A0, 0xFFFF8A80),
			new Wheel(new int[] {0xFF70B919, 0xFFF8C627, 0xFFF07613, 0xFFA02722, 0xFF1D1C21}, new int[] {0xFF3AAFD9, 0xFF70B919, 0xFFB5D84A, 0xFFF8C627, 0xFFFFE36B}, 0xFF373737, 0xFFFFFFFF)),
	CHERRY("cerejeira", 3, true, true, true, false,
			new Palette(0xFF2C1522, 0xFF6E1F2C, 0xFF27502E, 0xFFFFF1F4, 0xFFDCB8C5, 0xFFFFB3CD, 0xFFFF7A7A, 0xFF9BE38A, 0xFF4D2A3C, 0xFF9BE38A, 0xFFFFD56B, 0xFFFF7A7A, true),
			new Buttons(0xFF4A1F33, false, 0xFFDCB8C5, 0xFFFFB0B0),
			new Wheel(new int[] {0xFFF7C6D6, 0xFFEF9AB5, 0xFFD96A90, 0xFFA93B66, 0xFF4F1533}, new int[] {0xFFD6F0C2, 0xFFA7DC8E, 0xFFF7C6D6, 0xFFEF9AB5, 0xFFFFD56B}, 0xFF2C1522, 0xFFFFF1F4)) {
		@Override
		void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
			blit(graphics, "flower", x - 2, y - 2, 7, 7);
			blit(graphics, "flower", x + width - 5, y - 3, 7, 7);
		}
	},
	ANCIENT_CITY("cidade", 2, true, true, true, true,
			new Palette(0xFF06161B, 0xCC781432, 0xCC0C645A, 0xFFD6FBFF, 0xFF86BCC3, 0xFF39E6F2, 0xFFFF6D8A, 0xFF5DF2B0, 0xFF0E2B33, 0xFF39E6F2, 0xFFFFC04D, 0xFFFF6D8A, true),
			new Buttons(0xFFD6FBFF, true, 0xFF86BCC3, 0xFFFF6D8A),
			new Wheel(new int[] {0xFF0F5A66, 0xFF1A8A96, 0xFF39C9D6, 0xFF7A2F6B, 0xFFD02C5A}, new int[] {0xFF0F5A66, 0xFF1A8A96, 0xFF39C9D6, 0xFF7DF0F7, 0xFFE4FFFF}, 0xFF06161B, 0xFF39E6F2)) {
		@Override
		void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
			blit(graphics, "veins", x, y + height - 1, width, 3);
		}
	},
	HANJI("hanji", 3, true, true, true, false,
			new Palette(0xFFEEE3C9, 0x42B3261E, 0x472F6B3C, 0xFF1C1712, 0xFF5E5343, 0xFF2B4A72, 0xFFA8261C, 0xFF245C32, 0xFFCFC2A3, 0xFF2F6B3C, 0xFFB5851C, 0xFFA8261C, false),
			new Buttons(0xFF1C1712, false, 0xFF5E5343, 0xFFA8261C),
			new Wheel(new int[] {0xFFE3D7BB, 0xFFB9AB8C, 0xFF7D715D, 0xFF3F372C, 0xFFA8261C}, new int[] {0xFF8FB59A, 0xFF5F8F6F, 0xFFC9A24A, 0xFFD0762F, 0xFFA8261C}, 0xFF1C1712, 0xFFA8261C)) {
		@Override
		void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
			blit(graphics, "seal", x + width - 12, y - 4, 14, 14);
		}
	},
	DANCHEONG("dancheong", 2, true, true, true, false,
			new Palette(0xFF0E3F3A, 0xFF7A1F18, 0xFF1F6B3A, 0xFFFFF6E0, 0xFFBCD9CF, 0xFFF4C542, 0xFFFF8A70, 0xFF8FE388, 0xFF082A27, 0xFF8FE388, 0xFFF4C542, 0xFFFF6A4D, true),
			new Buttons(0xFFFFF6E0, true, 0xFFBCD9CF, 0xFFFFB0A0),
			new Wheel(new int[] {0xFF2F6FB3, 0xFFF4C542, 0xFFE6E1D3, 0xFFC8321F, 0xFF141414}, new int[] {0xFFE6E1D3, 0xFF2F6FB3, 0xFF1F8A6B, 0xFFC8321F, 0xFFF4C542}, 0xFF0E3F3A, 0xFFF4C542)) {
		@Override
		void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
			blit(graphics, "band", x - 2, y - 3, width + 4, 5);
			blit(graphics, "band", x - 2, y + height - 2, width + 4, 5);
		}
	},
	FESTIVAL("festival", 1, true, true, true, false,
			new Palette(0xFF141B3F, 0xD18C1E1E, 0xD1146446, 0xFFFFF4DC, 0xFFB3BADE, 0xFFFFB84D, 0xFFFF8176, 0xFF8EE6A0, 0xFF0A0E26, 0xFF8EE6A0, 0xFFFFB84D, 0xFFFF6B5E, true),
			new Buttons(0xFF3A1A05, false, 0xFFB3BADE, 0xFFFF9A8F),
			new Wheel(new int[] {0xFF2A3A8C, 0xFF5A4FB0, 0xFFF0A93A, 0xFFE8652F, 0xFFC22A20}, new int[] {0xFF2A3A8C, 0xFF3F8FBF, 0xFF8EE6A0, 0xFFF0A93A, 0xFFFFE08A}, 0xFF141B3F, 0xFFFF8176)) {
		@Override
		void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
			blit(graphics, "lintel", x - 6, y - 4, width + 12, 6);
		}
	},
	/** O visual fixo das telas de estudo. Não aparece na escolha de tema. */
	NOTEBOOK("caderno", 3, true, true, false, false,
			new Palette(0xFFFBF6E6, 0x42B3261E, 0x472F6B3C, 0xFF1F2F5C, 0xFF6A6658, 0xFF1F6F6B, 0xFFB3261E, 0xFF1F7A3A, 0xFFDCD3B8, 0xFF1F7A3A, 0xFFB5851C, 0xFFB3261E, false),
			new Buttons(0xFF1F2F5C, false, 0xFF6A6658, 0xFFB3261E),
			new Wheel(new int[] {0xFFF7E58A}, new int[] {0xFFF7E58A}, 0xFF1F2F5C, 0xFF1F2F5C));

	/** O tema marcado de início na tela de escolha. */
	public static final UiTheme DEFAULT = CHERRY;

	private static final Identifier VANILLA_BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier VANILLA_BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier VANILLA_BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
	private static @Nullable UiTheme preview;
	private static boolean notebook;

	/** {@code panel} é a cor do fundo, e {@code wrong} e {@code correct} são pintadas por cima dele. */
	record Palette(int panel, int wrong, int correct, int ink, int muted, int accent, int red, int green, int track, int timerHigh, int timerMid, int timerLow, boolean shadow) {
	}

	record Buttons(int ink, boolean shadow, int disabledInk, int wrongInk) {
	}

	record Wheel(int[] punishment, int[] reward, int hub, int pointer) {
	}

	private final String id;
	private final int frameInset;
	private final boolean panelSprite;
	private final boolean buttonSprites;
	private final boolean pips;
	private final boolean segmentedTimer;
	private final Palette palette;
	private final Buttons buttons;
	private final Wheel wheel;

	UiTheme(String id, int frameInset, boolean panelSprite, boolean buttonSprites, boolean pips, boolean segmentedTimer, Palette palette, Buttons buttons, Wheel wheel) {
		this.id = id;
		this.frameInset = frameInset;
		this.panelSprite = panelSprite;
		this.buttonSprites = buttonSprites;
		this.pips = pips;
		this.segmentedTimer = segmentedTimer;
		this.palette = palette;
		this.buttons = buttons;
		this.wheel = wheel;
	}

	public static UiTheme current() {
		if (notebook) {
			return NOTEBOOK;
		}
		return preview != null ? preview : byId(WorldFlashcards.themeId());
	}

	/** Os temas que o jogador pode escolher. */
	public static List<UiTheme> selectable() {
		return Arrays.stream(values()).filter(theme -> theme != NOTEBOOK).toList();
	}

	public static UiTheme byId(@Nullable String id) {
		for (UiTheme theme : selectable()) {
			if (theme.id.equals(id)) {
				return theme;
			}
		}
		return DEFAULT;
	}

	static void useNotebook(boolean active) {
		notebook = active;
	}

	/** Enquanto a tela de escolha está aberta, o tema marcado vale para tudo, sem ser salvo. */
	static void setPreview(@Nullable UiTheme theme) {
		preview = theme;
	}

	public String id() {
		return id;
	}

	Palette palette() {
		return palette;
	}

	Buttons buttons() {
		return buttons;
	}

	Wheel wheel() {
		return wheel;
	}

	boolean hasPips() {
		return pips;
	}

	boolean segmentedTimer() {
		return segmentedTimer;
	}

	/** Com fundo opaco, os textos soltos da roleta precisam de uma placa atrás para serem lidos. */
	boolean textNeedsPlate() {
		return panelSprite;
	}

	int frameInset() {
		return frameInset;
	}

	boolean drawPanelBackground(GuiGraphics graphics, int x, int y, int width, int height) {
		if (panelSprite) {
			blit(graphics, "panel", x, y, width, height);
			return true;
		}
		return false;
	}

	void drawButton(GuiGraphics graphics, int x, int y, int width, int height, boolean active, boolean hovered) {
		Identifier sprite;
		if (buttonSprites) {
			sprite = sprite(!active ? "button_off" : hovered ? "button_hover" : "button");
		} else {
			sprite = !active ? VANILLA_BUTTON_OFF : hovered ? VANILLA_BUTTON_HOVER : VANILLA_BUTTON;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	void drawPip(GuiGraphics graphics, boolean filled, int x, int y) {
		blit(graphics, filled ? "pip_on" : "pip_off", x, y, UiDraw.PIP_SIZE, UiDraw.PIP_SIZE);
	}

	/** Enfeites que passam um pouco da borda do painel. */
	void decorate(GuiGraphics graphics, int x, int y, int width, int height) {
	}

	final void blit(GuiGraphics graphics, String name, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, width, height);
	}

	private Identifier sprite(String name) {
		return LangCraft.id("theme/" + id + "/" + name);
	}
}
