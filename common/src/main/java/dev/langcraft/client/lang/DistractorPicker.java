package dev.langcraft.client.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Escolhe alternativas erradas com tamanho parecido, para não ficarem óbvias. */
public final class DistractorPicker {
	private static final int CLOSEST_POOL_SIZE = 30;

	private DistractorPicker() {
	}

	public static List<String> pick(String correct, Collection<String> names, int count, Random random) {
		List<String> pool = new ArrayList<>(new LinkedHashSet<>(names));
		pool.remove(correct);
		// Embaralhar antes evita que nomes de mesmo tamanho apareçam sempre na mesma ordem.
		Collections.shuffle(pool, random);
		pool.sort(Comparator.comparingInt(name -> Math.abs(name.length() - correct.length())));
		List<String> closest = new ArrayList<>(pool.subList(0, Math.min(CLOSEST_POOL_SIZE, pool.size())));
		Collections.shuffle(closest, random);
		return closest.subList(0, Math.min(count, closest.size()));
	}

	/** Junta a resposta certa com as erradas, em ordem aleatória. */
	public static List<String> options(String correct, Collection<String> names, int distractors, Random random) {
		List<String> options = new ArrayList<>(pick(correct, names, distractors, random));
		options.add(correct);
		Collections.shuffle(options, random);
		return options;
	}
}
