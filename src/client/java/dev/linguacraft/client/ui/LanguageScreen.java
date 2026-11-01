package dev.linguacraft.client.ui;

import dev.linguacraft.data.PlayerProgress;
import dev.linguacraft.data.StudyLanguage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Dá para marcar um ou mais idiomas. Na primeira entrada do jogador no mundo, não fecha sem uma escolha. */
public final class LanguageScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 3;
	private static final int PANEL_WIDTH = 260;
	private static final int PANEL_HALF_HEIGHT = 90;

	private final Consumer<String> onChosen;
	private final boolean required;
	private final @Nullable Screen parent;
	private final Set<String> selected = new LinkedHashSet<>();

	/** A escolha obrigatória da primeira entrada. */
	public LanguageScreen(Consumer<String> onChosen) {
		this(onChosen, true, null, List.of());
	}

	/** A troca de idioma, que começa com os idiomas atuais marcados e pode ser cancelada com Esc. */
	public LanguageScreen(@Nullable Screen parent, Collection<String> current, Consumer<String> onChosen) {
		this(onChosen, false, parent, current);
	}

	private LanguageScreen(Consumer<String> onChosen, boolean required, @Nullable Screen parent, Collection<String> current) {
		super(Component.translatable("linguacraft.setup.title"));
		this.onChosen = onChosen;
		this.required = required;
		this.parent = parent;
		this.selected.addAll(current);
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 - 66;
		for (StudyLanguage language : StudyLanguage.values()) {
			Component name = Component.translatable("linguacraft.setup." + language.commandName());
			Component label = selected.contains(language.code()) ? Component.literal("✔ ").append(name) : name;
			addRenderableWidget(new ThemedButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label, button -> toggle(language)));
			y += BUTTON_HEIGHT + BUTTON_GAP;
		}
		ThemedButton confirm = new ThemedButton(x, y + 2, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("linguacraft.setup.confirm"), button -> confirm());
		confirm.active = !selected.isEmpty();
		addRenderableWidget(confirm);
	}

	private void toggle(StudyLanguage language) {
		if (!selected.remove(language.code())) {
			selected.add(language.code());
		}
		rebuildWidgets();
	}

	/** Salva sempre na ordem do cadastro, para a mesma escolha ter sempre o mesmo valor. */
	private void confirm() {
		List<String> ordered = new ArrayList<>(StudyLanguage.codes());
		ordered.retainAll(selected);
		onChosen.accept(PlayerProgress.choiceOf(ordered));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		UiTheme.Palette palette = UiDraw.palette();
		UiDraw.panel(graphics, (width - PANEL_WIDTH) / 2, height / 2 - PANEL_HALF_HEIGHT, PANEL_WIDTH, PANEL_HALF_HEIGHT * 2);
		super.render(graphics, mouseX, mouseY, partialTick);
		UiDraw.centered(graphics, font, title, width / 2, height / 2 - 80, palette.ink());
		UiDraw.centered(graphics, font, UiDraw.fit(font, Component.translatable("linguacraft.setup.hint"), PANEL_WIDTH - 12), width / 2, height / 2 + 77, palette.muted());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !required;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
