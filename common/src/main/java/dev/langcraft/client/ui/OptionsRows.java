package dev.langcraft.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Lista com rolagem em duas colunas, com títulos entre os grupos. */
final class OptionsRows extends ContainerObjectSelectionList<OptionsRows.Row> {
	static final int COLUMN_WIDTH = 150;
	private static final int ROW_HEIGHT = 25;
	private static final int COLUMN_GAP = 10;
	private static final int ROW_WIDTH = COLUMN_WIDTH * 2 + COLUMN_GAP;

	OptionsRows(Minecraft minecraft, int width, int height, int top, int bottom) {
		super(minecraft, width, height, top, bottom, ROW_HEIGHT);
	}

	void addHeader(Component title) {
		addEntry(new Row(title, null, null));
	}

	void addRow(AbstractWidget left, @Nullable AbstractWidget right) {
		addEntry(new Row(null, left, right));
	}

	@Override
	public int getRowWidth() {
		return ROW_WIDTH;
	}

	@Override
	protected int getScrollbarPosition() {
		return width / 2 + ROW_WIDTH / 2 + 8;
	}

	static final class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final @Nullable Component title;
		private final List<AbstractWidget> widgets = new ArrayList<>();

		private Row(@Nullable Component title, @Nullable AbstractWidget left, @Nullable AbstractWidget right) {
			this.title = title;
			if (left != null) {
				widgets.add(left);
			}
			if (right != null) {
				widgets.add(right);
			}
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (title != null) {
				graphics.drawCenteredString(Minecraft.getInstance().font, title, left + width / 2, top + (height - 9) / 2, 0xFFFFFFFF);
			}
			int x = left;
			for (AbstractWidget widget : widgets) {
				widget.setX(x);
				widget.setY(top);
				widget.render(graphics, mouseX, mouseY, partialTick);
				x += COLUMN_WIDTH + COLUMN_GAP;
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
		}
	}
}
