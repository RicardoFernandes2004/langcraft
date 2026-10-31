package dev.linguacraft.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Fatias e sorteio das roletas de punição e de recompensa. */
public final class Roulette {
	/** Uma fatia configurada. {@code percent} igual a 0 significa "1 item". */
	public record SliceSpec(int percent, int weight) {
	}

	public record Slice(int amount, int weight) {
	}

	private Roulette() {
	}

	/**
	 * Calcula a quantidade real de cada fatia para uma pilha. As porcentagens arredondam para cima,
	 * e fatias que dariam a mesma quantidade são unidas, somando as chances.
	 */
	public static List<Slice> buildSlices(int stackCount, List<SliceSpec> specs) {
		List<Slice> slices = new ArrayList<>();
		for (SliceSpec spec : specs) {
			if (spec.weight() <= 0) {
				continue;
			}
			int amount = spec.percent() <= 0 ? 1 : (int) Math.ceil(stackCount * spec.percent() / 100.0);
			amount = Math.max(1, Math.min(stackCount, amount));
			int existing = indexOfAmount(slices, amount);
			if (existing >= 0) {
				Slice merged = slices.get(existing);
				slices.set(existing, new Slice(amount, merged.weight() + spec.weight()));
			} else {
				slices.add(new Slice(amount, spec.weight()));
			}
		}
		return slices;
	}

	public static int draw(List<Slice> slices, RandomGenerator random) {
		int total = slices.stream().mapToInt(Slice::weight).sum();
		int roll = random.nextInt(total);
		for (int i = 0; i < slices.size(); i++) {
			roll -= slices.get(i).weight();
			if (roll < 0) {
				return i;
			}
		}
		throw new IllegalStateException("Sorteio fora das fatias");
	}

	private static int indexOfAmount(List<Slice> slices, int amount) {
		for (int i = 0; i < slices.size(); i++) {
			if (slices.get(i).amount() == amount) {
				return i;
			}
		}
		return -1;
	}
}
