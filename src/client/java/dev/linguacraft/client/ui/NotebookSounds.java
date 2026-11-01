package dev.linguacraft.client.ui;

import dev.linguacraft.client.ChallengeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Os sons do caderno. Compara a tela a cada tick, para funcionar de qualquer jeito que ela abra ou feche,
 * inclusive quando uma roleta toma o lugar dela. Ir de uma tela do caderno para outra só vira a página.
 */
public final class NotebookSounds {
	private static final float VOLUME = 1f;
	private static final float OPEN_PITCH = 1f;
	private static final float CLOSE_PITCH = 0.9f;
	private static final float TURN_PITCH = 1.2f;

	private static @Nullable Screen lastNotebook;

	private NotebookSounds() {
	}

	public static void tick(Minecraft minecraft) {
		Screen current = minecraft.screen instanceof NotebookScreen ? minecraft.screen : null;
		if (current == lastNotebook) {
			return;
		}
		if (lastNotebook == null) {
			ChallengeClient.play(SoundEvents.BOOK_PAGE_TURN, OPEN_PITCH, VOLUME);
		} else if (current == null) {
			ChallengeClient.play(SoundEvents.BOOK_PUT, CLOSE_PITCH, VOLUME);
		} else {
			ChallengeClient.play(SoundEvents.BOOK_PAGE_TURN, TURN_PITCH, VOLUME);
		}
		lastNotebook = current;
	}
}
