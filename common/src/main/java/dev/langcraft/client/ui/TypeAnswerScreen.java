package dev.langcraft.client.ui;

import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.ChallengeView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Caixa para digitar a resposta do alerta de fala. Esc volta ao jogo, e o tempo continua correndo. */
public final class TypeAnswerScreen extends Screen {
	private static final int BOX_WIDTH = 240;

	private final ChallengeView view;
	private EditBox box;

	public TypeAnswerScreen(ChallengeView view) {
		super(Component.translatable("key.langcraft.type_answer"));
		this.view = view;
	}

	@Override
	protected void init() {
		box = new EditBox(font, (width - BOX_WIDTH) / 2, height / 2, BOX_WIDTH, 20, title);
		box.setMaxLength(64);
		addRenderableWidget(box);
		setInitialFocus(box);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			ChallengeClient.submitTyped(view, box.getValue());
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		UiDraw.panel(graphics, (width - BOX_WIDTH) / 2 - 10, height / 2 - 22, BOX_WIDTH + 20, 60);
		super.render(graphics, mouseX, mouseY, partialTick);
		Component heading = Component.translatable("langcraft.type.title", Component.translatable("langcraft.language." + view.language));
		UiDraw.centered(graphics, font, heading, width / 2, height / 2 - 14, palette.ink());
		UiDraw.centered(graphics, font, Component.translatable("langcraft.type.hint"), width / 2, height / 2 + 26, palette.muted());
	}

	@Override
	public void renderBackground(GuiGraphics graphics) {
		// Sem fundo: o jogo e o alerta precisam continuar visíveis.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
