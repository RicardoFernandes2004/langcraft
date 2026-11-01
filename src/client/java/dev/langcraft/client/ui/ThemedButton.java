package dev.langcraft.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botão desenhado com a aparência do tema atual. */
class ThemedButton extends Button {
	private static final int TEXT_MARGIN = 4;

	ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		UiTheme.Buttons style = UiTheme.current().buttons();
		Font font = Minecraft.getInstance().font;
		String text = UiDraw.fit(font, getMessage(), width - TEXT_MARGIN * 2);
		int textX = getX() + (width - font.width(text)) / 2;
		int textY = getY() + (height - font.lineHeight) / 2 + 1;
		graphics.drawString(font, text, textX, textY, active ? style.ink() : style.disabledInk(), active && style.shadow());
	}

	final void renderBackground(GuiGraphics graphics) {
		UiTheme.current().drawButton(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
	}
}
