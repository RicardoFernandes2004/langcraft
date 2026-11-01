package dev.langcraft.client.ui;

import dev.langcraft.LangCraft;
import dev.langcraft.client.voice.Microphones;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Lista os microfones. Sem uma tela anterior para voltar, ela só fecha com uma escolha. */
public final class MicrophoneScreen extends Screen {
	private static final int MAX_BUTTON_WIDTH = 360;
	private static final int ROW_HEIGHT = 24;
	private static final int TOP = 44;
	private static final int BOTTOM_MARGIN = 54;

	private final @Nullable Screen parent;
	private final Consumer<String> onChosen;
	/** O primeiro é sempre o padrão do sistema. */
	private final List<Microphones.Device> devices = new ArrayList<>();
	private int page;
	private int panelBottom;

	public MicrophoneScreen(@Nullable Screen parent, Consumer<String> onChosen) {
		super(Component.translatable("langcraft.microphone.title"));
		this.parent = parent;
		this.onChosen = onChosen;
		devices.add(new Microphones.Device(Microphones.SYSTEM_DEFAULT, ""));
		devices.addAll(Microphones.devices());
	}

	private int rowsPerPage() {
		return Math.max(2, (height - TOP - BOTTOM_MARGIN) / ROW_HEIGHT);
	}

	@Override
	protected void init() {
		int rows = rowsPerPage();
		int pages = (devices.size() + rows - 1) / rows;
		page = Math.min(page, pages - 1);
		int buttonWidth = Math.min(MAX_BUTTON_WIDTH, width - 20);
		int x = (width - buttonWidth) / 2;
		int y = TOP;
		for (int i = page * rows; i < Math.min(devices.size(), (page + 1) * rows); i++) {
			Microphones.Device device = devices.get(i);
			addRenderableWidget(new ThemedButton(x, y, buttonWidth, 20, label(device), button -> onChosen.accept(device.id())));
			y += ROW_HEIGHT;
		}
		panelBottom = y + 4;
		if (pages > 1) {
			int navY = height - 28;
			addRenderableWidget(new ThemedButton(width / 2 - 44, navY, 40, 20, Component.literal("<"), button -> changePage(-1))).active = page > 0;
			addRenderableWidget(new ThemedButton(width / 2 + 4, navY, 40, 20, Component.literal(">"), button -> changePage(1))).active = page < pages - 1;
			panelBottom = navY + 24;
		}
	}

	private void changePage(int delta) {
		page += delta;
		rebuildWidgets();
	}

	private Component label(Microphones.Device device) {
		Component text = device.id().equals(Microphones.SYSTEM_DEFAULT) ? Component.translatable(Microphones.defaultLabelKey()) : Component.literal(device.label());
		return device.id().equals(LangCraft.config.microphone) ? Component.literal("✔ ").append(text) : text;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		UiTheme.Palette palette = UiDraw.palette();
		int panelWidth = Math.min(MAX_BUTTON_WIDTH + 16, width - 4);
		UiDraw.panel(graphics, (width - panelWidth) / 2, 8, panelWidth, panelBottom - 8);
		super.render(graphics, mouseX, mouseY, partialTick);
		UiDraw.centered(graphics, font, title, width / 2, 16, palette.ink());
		Component hint = Component.translatable(devices.size() > 1 ? "langcraft.microphone.hint" : "langcraft.microphone.none");
		UiDraw.centered(graphics, font, UiDraw.fit(font, hint, panelWidth - 12), width / 2, 28, devices.size() > 1 ? palette.muted() : palette.red());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return parent != null;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
