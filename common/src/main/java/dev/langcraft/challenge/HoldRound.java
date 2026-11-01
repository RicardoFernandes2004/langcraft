package dev.langcraft.challenge;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Decide uma rodada do Pegue na mão: segurar o item pedido por 1 segundo vence, e segurar outro por 1 segundo perde.
 * Mão vazia e o item que já estava na mão no início não contam, porque o jogador ainda não escolheu nada.
 */
public final class HoldRound<T> {
	public enum Verdict {
		NONE,
		WON,
		LOST
	}

	private final T target;
	private final @Nullable T startingItem;
	private final HoldTracker rightHold = new HoldTracker();
	private final HoldTracker wrongHold = new HoldTracker();
	private @Nullable T lastHeld;

	public HoldRound(T target, @Nullable T startingItem) {
		this.target = target;
		this.startingItem = startingItem;
		this.lastHeld = startingItem;
	}

	public Verdict tick(@Nullable T held) {
		if (!Objects.equals(held, lastHeld)) {
			lastHeld = held;
			wrongHold.reset();
		}
		if (target.equals(held)) {
			wrongHold.reset();
			return rightHold.tick(true) ? Verdict.WON : Verdict.NONE;
		}
		rightHold.reset();
		boolean counts = held != null && !held.equals(startingItem);
		return wrongHold.tick(counts) ? Verdict.LOST : Verdict.NONE;
	}
}
