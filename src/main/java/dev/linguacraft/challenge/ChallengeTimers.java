package dev.linguacraft.challenge;

/**
 * Os dois intervalos de um jogador. Só avançam quando {@link #tick} é chamado, o que acontece apenas com o
 * jogador livre: durante um desafio, uma roleta ou a preparação da voz, nada se acumula.
 */
public final class ChallengeTimers {
	public enum Due {
		NONE,
		ITEM,
		MOB
	}

	private int itemTicks;
	private int mobTicks;
	private int restTicks;

	public ChallengeTimers(int itemTicks, int mobTicks) {
		this.itemTicks = itemTicks;
		this.mobTicks = mobTicks;
	}

	public void restart(int itemTicks, int mobTicks) {
		this.itemTicks = itemTicks;
		this.mobTicks = mobTicks;
	}

	/**
	 * Conta um tick. Os máximos fazem a mudança de um intervalo na configuração valer logo, em vez de só
	 * depois que o intervalo sorteado antes dela terminar.
	 */
	public Due tick(boolean items, boolean mobs, int maxItemTicks, int maxMobTicks) {
		if (restTicks > 0) {
			restTicks--;
			return Due.NONE;
		}
		if (items) {
			itemTicks = Math.max(0, Math.min(itemTicks, maxItemTicks) - 1);
			if (itemTicks == 0) {
				return Due.ITEM;
			}
		}
		if (mobs) {
			mobTicks = Math.max(0, Math.min(mobTicks, maxMobTicks) - 1);
			if (mobTicks == 0) {
				return Due.MOB;
			}
		}
		return Due.NONE;
	}

	/** Agenda o próximo desafio de item e afasta o de mob, para os dois não virem um atrás do outro. */
	public void itemStarted(int nextItemTicks, int mobAtLeast) {
		itemTicks = nextItemTicks;
		mobTicks = Math.max(mobTicks, mobAtLeast);
	}

	/** Agenda o próximo desafio de mob e afasta o de item, para os dois não virem um atrás do outro. */
	public void mobStarted(int nextMobTicks, int itemAtLeast) {
		mobTicks = nextMobTicks;
		itemTicks = Math.max(itemTicks, itemAtLeast);
	}

	/** Tenta de novo em breve, sem esperar um intervalo inteiro. */
	public void retryItem(int ticks) {
		itemTicks = ticks;
	}

	/** Tenta de novo em breve, sem esperar um intervalo inteiro. */
	public void retryMob(int ticks) {
		mobTicks = ticks;
	}

	/** Pausa os dois intervalos depois que um desafio termina. */
	public void rest(int ticks) {
		restTicks = ticks;
	}
}
