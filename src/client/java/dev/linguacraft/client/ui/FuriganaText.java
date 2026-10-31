package dev.linguacraft.client.ui;

import dev.linguacraft.client.lang.Furigana;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Desenha um texto com a leitura pequena sobre os kanjis. */
final class FuriganaText {
	private static final float READING_RATIO = 0.5f;
	private static final int READING_GAP = 1;

	private FuriganaText() {
	}

	static int width(Font font, List<Furigana.Segment> segments, float scale) {
		float width = 0;
		for (Furigana.Segment segment : segments) {
			width += segmentWidth(font, segment, scale);
		}
		return (int) Math.ceil(width);
	}

	/** Altura total do texto. A linha da leitura só existe quando há kanji. */
	static int height(Font font, List<Furigana.Segment> segments, float scale) {
		return (int) Math.ceil(font.lineHeight * scale) + readingRowHeight(font, segments, scale);
	}

	static int readingRowHeight(Font font, List<Furigana.Segment> segments, float scale) {
		return Furigana.hasReadings(segments) ? (int) Math.ceil(font.lineHeight * scale * READING_RATIO) + READING_GAP : 0;
	}

	static void draw(GuiGraphics graphics, Font font, List<Furigana.Segment> segments, int x, int y, float scale, int color, boolean shadow) {
		float cursor = x;
		int textY = y + readingRowHeight(font, segments, scale);
		for (Furigana.Segment segment : segments) {
			float width = segmentWidth(font, segment, scale);
			drawScaled(graphics, font, segment.text(), cursor + (width - font.width(segment.text()) * scale) / 2, textY, scale, color, shadow);
			if (segment.reading() != null) {
				float readingScale = scale * READING_RATIO;
				drawScaled(graphics, font, segment.reading(), cursor + (width - font.width(segment.reading()) * readingScale) / 2, y, readingScale, color, false);
			}
			cursor += width;
		}
	}

	/** Se a leitura for mais larga que o kanji, o trecho inteiro alarga para não encostar no vizinho. */
	private static float segmentWidth(Font font, Furigana.Segment segment, float scale) {
		float textWidth = font.width(segment.text()) * scale;
		return segment.reading() == null ? textWidth : Math.max(textWidth, font.width(segment.reading()) * scale * READING_RATIO);
	}

	private static void drawScaled(GuiGraphics graphics, Font font, String text, float x, float y, float scale, int color, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}
}
