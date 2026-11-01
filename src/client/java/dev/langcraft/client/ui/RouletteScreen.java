package dev.langcraft.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.langcraft.LangCraft;
import dev.langcraft.client.ChallengeClient;
import dev.langcraft.net.RouletteSpinPayload;
import dev.langcraft.net.RouletteStartPayload;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** As roletas de punição e de recompensa. O resultado vem do servidor, e aqui só é mostrado. */
public final class RouletteScreen extends Screen {
	private static final Identifier WHEEL_TEXTURE = LangCraft.id("dynamic/roulette");
	private static final int TEXTURE_SIZE = 512;
	private static final int SPIN_TURNS = 5;
	private static final int RESULT_DISPLAY_TICKS = 50;
	/** Reserva o título em cima e, embaixo, o aviso e o botão, sem que um encoste no outro. */
	private static final int WHEEL_MARGIN = 84;
	private static final int PLATE_PADDING = 10;

	private final RouletteStartPayload payload;
	private final ItemStack icon;
	private final int[] sliceStarts;
	private final int totalWeight;

	private DynamicTexture texture;
	private Button spinButton;
	private int ticksUntilAutoSpin;
	private boolean requested;
	private int sliceIndex = -1;
	private int spinTicks;
	private int spinElapsed;
	private float targetRotation;
	private int lastPointerSlice = -1;

	public RouletteScreen(RouletteStartPayload payload) {
		super(Component.translatable(payload.kind() == RouletteStartPayload.Kind.PUNISHMENT
				? "langcraft.roulette.punishment_title" : "langcraft.roulette.reward_title"));
		this.payload = payload;
		this.icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(payload.item())));
		this.ticksUntilAutoSpin = payload.autoSpinTicks();
		List<Integer> weights = payload.weights();
		this.sliceStarts = new int[weights.size()];
		int sum = 0;
		for (int i = 0; i < weights.size(); i++) {
			sliceStarts[i] = sum;
			sum += weights.get(i);
		}
		this.totalWeight = sum;
	}

	public int id() {
		return payload.id();
	}

	public RouletteStartPayload.Kind kind() {
		return payload.kind();
	}

	/** Quantidade da fatia sorteada, ou -1 se a roleta ainda não girou. */
	public int resultAmount() {
		return sliceIndex < 0 ? -1 : payload.amounts().get(sliceIndex);
	}

	@Override
	protected void init() {
		if (texture == null) {
			texture = new DynamicTexture(() -> "langcraft roulette", paintWheel());
			texture.upload();
			minecraft.getTextureManager().register(WHEEL_TEXTURE, texture);
		}
		spinButton = addRenderableWidget(new ThemedButton(width / 2 - 50, height - 26, 100, 20, Component.translatable("langcraft.roulette.spin"), button -> requestSpin()));
		spinButton.active = !requested && sliceIndex < 0;
	}

	private NativeImage paintWheel() {
		NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
		int[] colors = sliceColors();
		int hub = UiTheme.current().wheel().hub();
		double center = (TEXTURE_SIZE - 1) / 2.0;
		double radius = TEXTURE_SIZE / 2.0 - 1;
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				double dx = x - center;
				double dy = y - center;
				double distance = Math.sqrt(dx * dx + dy * dy);
				int color = 0;
				if (distance <= radius) {
					double degrees = (Math.toDegrees(Math.atan2(dx, -dy)) + 360) % 360;
					int slice = sliceAt(degrees);
					color = colors[colorIndex(slice, colors.length)];
					if (distance >= radius - 6 || distance <= radius * 0.24 || isOnBoundary(degrees, distance)) {
						color = hub;
					}
				}
				image.setPixel(x, y, color);
			}
		}
		return image;
	}

	private int[] sliceColors() {
		UiTheme.Wheel wheel = UiTheme.current().wheel();
		return payload.kind() == RouletteStartPayload.Kind.PUNISHMENT ? wheel.punishment() : wheel.reward();
	}

	/** Espalha as cores do início ao fim da paleta, para que a última fatia seja sempre a mais extrema. */
	private int colorIndex(int slice, int colorCount) {
		int slices = payload.amounts().size();
		return slices <= 1 ? 0 : Math.round((float) slice * (colorCount - 1) / (slices - 1));
	}

	private boolean isOnBoundary(double degrees, double distance) {
		for (int start : sliceStarts) {
			double boundary = start * 360.0 / totalWeight;
			double difference = Math.abs(degrees - boundary);
			difference = Math.min(difference, 360 - difference);
			if (Math.toRadians(difference) * distance < 2.5) {
				return true;
			}
		}
		return false;
	}

	private int sliceAt(double degrees) {
		double position = degrees / 360.0 * totalWeight;
		for (int i = sliceStarts.length - 1; i >= 0; i--) {
			if (position >= sliceStarts[i]) {
				return i;
			}
		}
		return 0;
	}

	private void requestSpin() {
		if (!requested && sliceIndex < 0) {
			requested = true;
			spinButton.active = false;
			ClientPlayNetworking.send(new RouletteSpinPayload(payload.id()));
		}
	}

	/** Começa a animação, que termina com o ponteiro dentro da fatia sorteada. */
	public void startSpin(int slice, int ticks) {
		sliceIndex = slice;
		spinTicks = ticks;
		spinElapsed = 0;
		requested = true;
		if (spinButton != null) {
			spinButton.active = false;
		}
		float sliceStart = sliceStarts[slice] * 360f / totalWeight;
		float sliceSpan = payload.weights().get(slice) * 360f / totalWeight;
		float landing = sliceStart + sliceSpan * (0.15f + new Random().nextFloat() * 0.7f);
		targetRotation = SPIN_TURNS * 360f - landing;
	}

	@Override
	public void tick() {
		if (sliceIndex < 0) {
			if (ticksUntilAutoSpin > 0) {
				ticksUntilAutoSpin--;
			}
			return;
		}
		spinElapsed++;
		if (spinElapsed == spinTicks) {
			if (payload.kind() == RouletteStartPayload.Kind.PUNISHMENT) {
				ChallengeClient.play(SoundEvents.ANVIL_LAND, 1.2f);
			} else {
				ChallengeClient.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
			}
		}
		if (spinElapsed >= spinTicks + RESULT_DISPLAY_TICKS) {
			minecraft.setScreen(null);
		}
	}

	private float rotation(float partialTick) {
		if (sliceIndex < 0) {
			return 0;
		}
		float progress = Math.min(1f, (spinElapsed + partialTick) / spinTicks);
		float inverse = 1f - progress;
		return targetRotation * (1f - inverse * inverse * inverse);
	}

	private void playPointerClicks(float rotation) {
		if (sliceIndex < 0 || spinElapsed >= spinTicks) {
			return;
		}
		double underPointer = ((-rotation % 360) + 360) % 360;
		int slice = sliceAt(underPointer);
		if (slice != lastPointerSlice) {
			lastPointerSlice = slice;
			ChallengeClient.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		boolean punishment = payload.kind() == RouletteStartPayload.Kind.PUNISHMENT;
		UiTheme.Palette palette = UiDraw.palette();
		Component stack = Component.translatable("langcraft.roulette.stack", payload.stackCount(), icon.getHoverName());

		plate(graphics, Math.max(font.width(title), font.width(stack)), 3, 29);
		UiDraw.centered(graphics, font, title, width / 2, 8, punishment ? palette.red() : palette.accent());
		UiDraw.centered(graphics, font, stack, width / 2, 20, palette.ink());

		int radius = Math.max(40, Math.min((height - WHEEL_MARGIN) / 2, 100));
		int centerX = width / 2;
		int centerY = 36 + radius;
		float rotation = rotation(partialTick);
		playPointerClicks(rotation);

		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().rotate((float) Math.toRadians(rotation));
		graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL_TEXTURE, -radius, -radius, 0f, 0f, radius * 2, radius * 2,
				TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
		drawSliceLabels(graphics, radius);
		graphics.pose().popMatrix();

		drawPointer(graphics, centerX, centerY - radius);

		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - 12f, centerY - 12f);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.renderItem(icon, 0, 0);
		graphics.pose().popMatrix();

		Component footer = footer();
		if (footer != null) {
			int footerY = centerY + radius + 4;
			plate(graphics, font.width(footer), footerY - 3, 14);
			UiDraw.centered(graphics, font, footer, width / 2, footerY, footerColor());
		}
	}

	/** Temas com fundo claro não dariam leitura direto sobre o mundo escurecido. */
	private void plate(GuiGraphics graphics, int textWidth, int y, int height) {
		if (UiTheme.current().textNeedsPlate()) {
			int plateWidth = textWidth + PLATE_PADDING * 2;
			UiDraw.plainPanel(graphics, (width - plateWidth) / 2, y, plateWidth, height, UiDraw.PanelState.NORMAL);
		}
	}

	private void drawSliceLabels(GuiGraphics graphics, int radius) {
		List<Integer> amounts = payload.amounts();
		int[] colors = sliceColors();
		for (int i = 0; i < amounts.size(); i++) {
			float middle = (sliceStarts[i] + payload.weights().get(i) / 2f) * 360f / totalWeight;
			boolean light = isLight(colors[colorIndex(i, colors.length)]);
			graphics.pose().pushMatrix();
			graphics.pose().rotate((float) Math.toRadians(middle));
			graphics.pose().translate(0, -radius * 0.68f);
			graphics.pose().rotate((float) Math.toRadians(-middle));
			String label = String.valueOf(amounts.get(i));
			graphics.drawString(font, label, -font.width(label) / 2, -4, light ? 0xFF14110D : UiTheme.CLASSIC.palette().ink(), !light);
			graphics.pose().popMatrix();
		}
	}

	private static boolean isLight(int color) {
		int red = color >> 16 & 0xFF;
		int green = color >> 8 & 0xFF;
		int blue = color & 0xFF;
		return red * 0.299 + green * 0.587 + blue * 0.114 > 150;
	}

	private void drawPointer(GuiGraphics graphics, int centerX, int tipY) {
		int color = UiTheme.current().wheel().pointer();
		for (int row = 0; row < 10; row++) {
			int halfWidth = 7 - row * 7 / 10;
			graphics.fill(centerX - halfWidth - 1, tipY - 6 + row, centerX + halfWidth + 2, tipY - 5 + row, 0xFF000000);
			graphics.fill(centerX - halfWidth, tipY - 6 + row, centerX + halfWidth + 1, tipY - 5 + row, color);
		}
	}

	private Component footer() {
		if (sliceIndex >= 0 && spinElapsed >= spinTicks) {
			String key = payload.kind() == RouletteStartPayload.Kind.PUNISHMENT
					? "langcraft.roulette.result_lost" : "langcraft.roulette.result_gained";
			return Component.translatable(key, Component.translatable("langcraft.roulette.stack", payload.amounts().get(sliceIndex), icon.getHoverName()));
		}
		if (sliceIndex < 0 && !requested) {
			return Component.translatable("langcraft.roulette.auto", Math.max(1, (int) Math.ceil(ticksUntilAutoSpin / 20.0)));
		}
		return null;
	}

	private int footerColor() {
		UiTheme.Palette palette = UiDraw.palette();
		if (sliceIndex >= 0 && spinElapsed >= spinTicks) {
			return payload.kind() == RouletteStartPayload.Kind.PUNISHMENT ? palette.red() : palette.green();
		}
		return palette.muted();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_SPACE) {
			requestSpin();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (texture != null) {
			minecraft.getTextureManager().release(WHEEL_TEXTURE);
			texture = null;
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
