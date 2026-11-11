package dev.langcraft.client.ui;

import com.mojang.math.Axis;
import dev.langcraft.net.TreasurePayload;
import dev.langcraft.treasure.TreasureTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Revela um tesouro. O baú treme cada vez mais, soltando faíscas, enquanto o brilho sobe de faixa em faixa até
 * parar na que saiu. Na revelação, um clarão, uma explosão de brilho e o item saltando no meio de raios de luz.
 * As chances ficam atrás do botão de informação, para a tela não virar uma tabela.
 */
public final class TreasureScreen extends Screen {
	private static final int OPEN_TICKS = 60;
	private static final float ICON_SCALE = 4f;
	private static final int BURST = 48;
	private static final int TWINKLES = 18;
	private static final int INFO_SIZE = 14;

	private final TreasurePayload payload;
	private final ItemStack stack;
	private final TreasureTier tier;
	private final @Nullable String word;
	private final String nativeName;
	private final float[] burstAngle = new float[BURST];
	private final float[] burstSpeed = new float[BURST];
	private final float[] twinklePhase = new float[TWINKLES];
	private int ticks;
	private int shownTier = -1;

	public TreasureScreen(TreasurePayload payload, ItemStack stack, @Nullable String word, String nativeName) {
		super(Component.translatable("langcraft.treasure.title"));
		this.payload = payload;
		this.stack = stack;
		TreasureTier found = TreasureTier.byId(payload.tier());
		this.tier = found == null ? TreasureTier.COMMON : found;
		this.word = word;
		this.nativeName = nativeName;
		Random random = new Random(payload.item().hashCode());
		for (int i = 0; i < BURST; i++) {
			burstAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
			burstSpeed[i] = 2f + random.nextFloat() * 4f;
		}
		for (int i = 0; i < TWINKLES; i++) {
			twinklePhase[i] = random.nextFloat() * 20f;
		}
	}

	private boolean revealed() {
		return ticks >= OPEN_TICKS;
	}

	@Override
	public void tick() {
		ticks++;
		// O brilho sobe uma faixa por vez, com uma nota mais aguda a cada uma.
		int climbing = Math.min(tier.ordinal(), ticks * (tier.ordinal() + 1) / OPEN_TICKS);
		if (!revealed() && climbing != shownTier) {
			shownTier = climbing;
			play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f + 0.2f * climbing);
		}
		if (ticks == OPEN_TICKS) {
			shownTier = tier.ordinal();
			play(revealSound(), 1f);
		}
	}

	private SoundEvent revealSound() {
		return switch (tier) {
			case COMMON -> SoundEvents.PLAYER_LEVELUP;
			case RARE -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case EPIC -> SoundEvents.PLAYER_LEVELUP;
			case LEGENDARY, MYTHIC -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
		};
	}

	private void play(SoundEvent sound, float pitch) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}

	/** Um clique durante a abertura pula para a revelação. Depois dela, um clique fecha. */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (revealed()) {
			onClose();
		} else {
			ticks = OPEN_TICKS - 1;
		}
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float time = ticks + partialTick;
		float sinceReveal = time - OPEN_TICKS;
		int centerX = width / 2;
		int centerY = height / 2 - 16;
		int color = colorOf(TreasureTier.values()[Math.max(0, shownTier)], time);
		int rgb = color & 0xFFFFFF;

		vignette(graphics);
		float power = revealed() ? 1f : 0.35f + 0.65f * time / OPEN_TICKS;
		rays(graphics, centerX, centerY, time, rgb, power);
		glow(graphics, centerX, centerY, time, rgb, power);

		if (revealed()) {
			twinkles(graphics, centerX, centerY, time, rgb);
			burst(graphics, centerX, centerY, sinceReveal, rgb);
			float pop = sinceReveal < 10 ? easeOutBack(sinceReveal / 10f) : 1f;
			float bob = Mth.sin(time / 9f) * 2f;
			drawItem(graphics, stack, centerX, centerY + bob, ICON_SCALE * pop);
			labels(graphics, centerX, centerY, time);
		} else {
			float shake = Mth.sin(time * 2.1f) * Math.min(4f, time / 12f);
			float hop = time > OPEN_TICKS - 12 ? -Math.abs(Mth.sin(time * 1.3f)) * 3f : 0f;
			sparksFromChest(graphics, centerX, centerY, time, rgb);
			drawItem(graphics, new ItemStack(Items.CHEST), centerX + shake, centerY + hop, ICON_SCALE);
		}
		if (revealed() && sinceReveal < 6) {
			int alpha = (int) (0xC0 * (1 - sinceReveal / 6f));
			graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
		}
		info(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** O mítico passa por todas as cores, devagar. */
	private static int colorOf(TreasureTier tier, float time) {
		if (tier != TreasureTier.MYTHIC) {
			return tier.color();
		}
		return 0xFF000000 | Mth.hsvToRgb((time / 80f) % 1f, 0.55f, 1f);
	}

	private void vignette(GuiGraphics graphics) {
		graphics.fillGradient(0, 0, width, height / 2, 0x90000000, 0x50000000);
		graphics.fillGradient(0, height / 2, width, height, 0x50000000, 0x90000000);
	}

	/** Raios de luz girando atrás do item. Quanto mais rara a faixa, mais raios e mais compridos. */
	private void rays(GuiGraphics graphics, int centerX, int centerY, float time, int rgb, float power) {
		int count = 10 + 4 * Math.max(0, shownTier);
		int length = (int) ((70 + 22 * Math.max(0, shownTier)) * power);
		float spin = time * (revealed() ? 0.6f : 1.6f);
		for (int i = 0; i < count; i++) {
			float angle = spin + 360f * i / count;
			push(graphics, centerX, centerY);
			rotate(graphics, angle);
			for (int segment = 0; segment < 4; segment++) {
				int alpha = (int) ((0x50 - segment * 0x12) * power);
				int start = 12 + segment * length / 4;
				int half = 1 + segment;
				graphics.fill(start, -half, start + length / 4, half, (Math.max(0, alpha) << 24) | rgb);
			}
			pop(graphics);
		}
	}

	/** Um brilho redondo, feito de quadrados girados um sobre o outro, pulsando. */
	private void glow(GuiGraphics graphics, int centerX, int centerY, float time, int rgb, float power) {
		float pulse = 1f + 0.08f * Mth.sin(time / 4f);
		for (int layer = 0; layer < 5; layer++) {
			int size = (int) ((44 - layer * 7) * pulse * (0.6f + 0.4f * power));
			int alpha = (int) ((0x18 + layer * 0x0E) * power);
			for (int turn = 0; turn < 3; turn++) {
				push(graphics, centerX, centerY);
				rotate(graphics, 30f * turn + time * 0.5f);
				graphics.fill(-size, -size, size, size, (alpha << 24) | rgb);
				pop(graphics);
			}
		}
	}

	/** Faíscas subindo do baú, cada vez mais, enquanto ele treme. */
	private void sparksFromChest(GuiGraphics graphics, int centerX, int centerY, float time, int rgb) {
		int count = (int) (4 + 16 * time / OPEN_TICKS);
		for (int i = 0; i < count; i++) {
			float life = (time * 0.7f + i * 7.3f) % 24f;
			float x = centerX + Mth.sin(i * 12.9f) * 26f;
			float y = centerY + 20 - life * 3.2f;
			int alpha = (int) (0xFF * (1 - life / 24f));
			sparkle(graphics, x, y, i % 3 == 0 ? 0xFFFFFF : rgb, alpha, 1);
		}
	}

	/** A explosão da revelação: brilhos voando para fora e sumindo. */
	private void burst(GuiGraphics graphics, int centerX, int centerY, float sinceReveal, int rgb) {
		if (sinceReveal > 40) {
			return;
		}
		for (int i = 0; i < BURST; i++) {
			float distance = burstSpeed[i] * sinceReveal * (1.2f - sinceReveal / 80f) + 10;
			float x = centerX + Mth.cos(burstAngle[i]) * distance;
			float y = centerY + Mth.sin(burstAngle[i]) * distance + sinceReveal * sinceReveal * 0.02f;
			int alpha = (int) (0xFF * (1 - sinceReveal / 40f));
			sparkle(graphics, x, y, i % 4 == 0 ? 0xFFFFFF : rgb, alpha, i % 5 == 0 ? 2 : 1);
		}
	}

	/** Brilhinhos piscando em volta do item depois da revelação. */
	private void twinkles(GuiGraphics graphics, int centerX, int centerY, float time, int rgb) {
		for (int i = 0; i < TWINKLES; i++) {
			float angle = i * 2.4f + time * 0.01f;
			float radius = 30 + (i * 37 % 40);
			float shine = Mth.sin((time + twinklePhase[i]) / 5f);
			if (shine > 0.2f) {
				sparkle(graphics, centerX + Mth.cos(angle) * radius, centerY + Mth.sin(angle) * radius, i % 2 == 0 ? 0xFFFFFF : rgb,
						(int) (0xFF * shine), shine > 0.85f ? 2 : 1);
			}
		}
	}

	/** Uma cruz pequena, que é o brilho clássico dos jogos em pixel. */
	private static void sparkle(GuiGraphics graphics, float x, float y, int rgb, int alpha, int arm) {
		if (alpha <= 8) {
			return;
		}
		int color = (Math.min(255, alpha) << 24) | rgb;
		int px = Math.round(x);
		int py = Math.round(y);
		graphics.fill(px - arm, py, px + arm + 1, py + 1, color);
		graphics.fill(px, py - arm, px + 1, py + arm + 1, color);
	}

	private void drawItem(GuiGraphics graphics, ItemStack item, float centerX, float centerY, float scale) {
		if (scale <= 0.01f) {
			return;
		}
		push(graphics, centerX, centerY);
		scale(graphics, scale);
		graphics.renderItem(item, -8, -8);
		pop(graphics);
	}

	private void labels(GuiGraphics graphics, int centerX, int centerY, float time) {
		UiTheme.Palette palette = UiDraw.palette();
		int y = centerY + 44;
		Component tierName = Component.translatable("langcraft.treasure.tier." + tier.id());
		push(graphics, centerX, y);
		scale(graphics, 2f);
		graphics.drawString(font, tierName, -font.width(tierName) / 2, 0, colorOf(tier, time), true);
		pop(graphics);
		Component name = word != null ? StudyFont.of(word) : stack.getHoverName();
		UiDraw.centered(graphics, font, Component.translatable("langcraft.treasure.amount", payload.count(), name), centerX, y + 24, 0xFFFFFFFF);
		int next = y + 36;
		if (!nativeName.isEmpty() && word != null) {
			UiDraw.centered(graphics, font, nativeName, centerX, next, 0xFFBBBBBB);
			next += 12;
		}
		if (word != null) {
			UiDraw.centered(graphics, font, Component.translatable("langcraft.treasure.notebook"), centerX, next, palette.accent());
		}
		int alpha = (int) (0x80 + 0x7F * Mth.sin(time / 6f));
		UiDraw.centered(graphics, font, Component.translatable("langcraft.treasure.continue"), centerX, height - 20, (Math.max(0x30, alpha) << 24) | 0xDDDDDD);
	}

	/** O botão de informação no canto. Passar o mouse mostra as chances desta abertura e a sorte acumulada. */
	private void info(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = width - INFO_SIZE - 8;
		int y = 8;
		boolean hovered = mouseX >= x && mouseX < x + INFO_SIZE && mouseY >= y && mouseY < y + INFO_SIZE;
		graphics.fill(x, y, x + INFO_SIZE, y + INFO_SIZE, hovered ? 0xC0FFFFFF : 0x80000000);
		graphics.renderOutline(x, y, INFO_SIZE, INFO_SIZE, 0xFFFFFFFF);
		graphics.drawString(font, "?", x + (INFO_SIZE - font.width("?")) / 2 + 1, y + 3, hovered ? 0xFF000000 : 0xFFFFFFFF, false);
		if (!hovered) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("langcraft.treasure.chances"));
		TreasureTier[] tiers = TreasureTier.values();
		for (int i = tiers.length - 1; i >= 0; i--) {
			int chance = i < payload.chances().size() ? payload.chances().get(i) : 0;
			TreasureTier line = tiers[i];
			lines.add(Component.translatable("langcraft.treasure.tier." + line.id()).withStyle(style -> style.withColor(line.color() & 0xFFFFFF))
					.append(Component.literal("  " + chance + "%").withStyle(style -> style.withColor(0xFFFFFF))));
		}
		lines.add(Component.translatable("langcraft.treasure.luck", payload.luckPercent()).withStyle(style -> style.withColor(0xAAAAAA)));
		tooltip(graphics, lines, mouseX, mouseY);
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}

	// O que muda de uma versão do Minecraft para outra fica nestes métodos.

	private static void push(GuiGraphics graphics, float x, float y) {
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0f);
	}

	private static void rotate(GuiGraphics graphics, float degrees) {
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
	}

	private static void scale(GuiGraphics graphics, float scale) {
		graphics.pose().scale(scale, scale, 1f);
	}

	private static void pop(GuiGraphics graphics) {
		graphics.pose().popPose();
	}

	private void tooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
		graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
