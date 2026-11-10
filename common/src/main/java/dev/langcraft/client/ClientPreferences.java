package dev.langcraft.client;

import dev.langcraft.challenge.ChallengeKind;
import dev.langcraft.net.PreferencesPayload;
import java.util.HashSet;
import java.util.Set;

/** Os desafios que o jogador desligou, como o servidor guardou. Fica vazio até o servidor mandar. */
public final class ClientPreferences {
	private static final Set<String> disabled = new HashSet<>();
	private static boolean received;

	private ClientPreferences() {
	}

	public static void onSync(PreferencesPayload payload) {
		disabled.clear();
		disabled.addAll(payload.disabledChallenges());
		received = true;
	}

	/** Falso fora de um mundo ou num servidor sem o mod, onde não há o que ligar ou desligar. */
	public static boolean received() {
		return received;
	}

	public static boolean enabled(ChallengeKind kind) {
		return !disabled.contains(kind.id());
	}

	/** Atualiza na hora, para a tela não esperar a resposta do servidor. */
	public static void set(ChallengeKind kind, boolean enabled) {
		if (enabled) {
			disabled.remove(kind.id());
		} else {
			disabled.add(kind.id());
		}
	}

	public static void clear() {
		disabled.clear();
		received = false;
	}
}
