package dev.linguacraft.client.ui;

import dev.linguacraft.client.ChallengeClient;
import dev.linguacraft.client.ChallengeView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Caixa para digitar a resposta do alerta de fala. Esc volta ao jogo, e o tempo continua correndo. */
public final class TypeAnswerScreen extends Screen {
	private static final int BOX_WIDTH = 240;

	private final ChallengeView view;
	private EditBox box;

	public TypeAnswerScreen(ChallengeView view) {
		super(Component.translatable("key.linguacraft.type_answer"));
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
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			ChallengeClient.submitTyped(view, box.getValue());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		Component heading = Component.translatable("linguacraft.type.title", Component.translatable("linguacraft.language." + view.language));
		graphics.drawCenteredString(font, heading, width / 2, height / 2 - 14, UiColors.WHITE);
		graphics.drawCenteredString(font, Component.translatable("linguacraft.type.hint"), width / 2, height / 2 + 26, UiColors.MUTED);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Sem fundo: o jogo e o alerta precisam continuar visíveis.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
