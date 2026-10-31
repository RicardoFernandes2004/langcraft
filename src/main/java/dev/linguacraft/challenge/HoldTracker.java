package dev.linguacraft.challenge;

/** Conta por quantos ticks seguidos uma condição se mantém, para o minigame Pegue na mão. */
public final class HoldTracker {
	public static final int REQUIRED_TICKS = 20;

	private int heldTicks;

	/** Retorna verdadeiro no tick em que a condição completa o tempo exigido. */
	public boolean tick(boolean holding) {
		if (!holding) {
			heldTicks = 0;
			return false;
		}
		heldTicks++;
		return heldTicks == REQUIRED_TICKS;
	}

	public void reset() {
		heldTicks = 0;
	}
}
