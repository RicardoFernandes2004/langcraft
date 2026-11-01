package dev.langcraft.challenge;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Mostra o título e o ranking com recursos do próprio jogo, que todos os jogadores enxergam. O título é o
 * prefixo de um time só daquele jogador. O número ao lado do nome, na lista do Tab, é o total de acertos.
 * A lista ordena pelo nome do time, então o nome carrega os acertos, e quem acertou mais fica em cima.
 * Quem já está em um time de outra origem fica sem título, para não quebrar nada do servidor.
 */
final class TitleTeams {
	private static final String TEAM_PREFIX = "lc_";
	private static final String OBJECTIVE = "lc_correct";
	private static final int MAX_SORTED_SCORE = 9_999_999;

	private TitleTeams() {
	}

	static void apply(MinecraftServer server, ServerPlayer player, int totalCorrect, boolean enabled) {
		ServerScoreboard scoreboard = server.getScoreboard();
		if (enabled) {
			updateScore(scoreboard, player, totalCorrect);
		} else {
			hideScore(scoreboard);
		}
		String name = player.getScoreboardName();
		PlayerTeam current = scoreboard.getPlayersTeam(name);
		if (current != null && !current.getName().startsWith(TEAM_PREFIX)) {
			return;
		}
		if (!enabled) {
			if (current != null) {
				leave(scoreboard, name, current);
			}
			return;
		}
		String teamName = teamName(totalCorrect, player.getUUID());
		if (current != null && current.getName().equals(teamName)) {
			return;
		}
		PlayerTeam team = team(scoreboard, teamName, PlayerTitle.of(totalCorrect));
		if (current != null) {
			leave(scoreboard, name, current);
		}
		scoreboard.addPlayerToTeam(name, team);
	}

	/** Mais acertos resultam em um nome que vem antes na ordem alfabética. */
	static String teamName(int totalCorrect, UUID player) {
		int sorted = MAX_SORTED_SCORE - Math.min(totalCorrect, MAX_SORTED_SCORE);
		return String.format(Locale.ROOT, "%s%07d_%s", TEAM_PREFIX, sorted, player.toString().substring(0, 8));
	}

	private static PlayerTeam team(ServerScoreboard scoreboard, String teamName, PlayerTitle title) {
		PlayerTeam team = scoreboard.getPlayerTeam(teamName);
		if (team == null) {
			team = scoreboard.addPlayerTeam(teamName);
			team.setPlayerPrefix(Component.empty().append(title.tag()).append(" "));
		}
		return team;
	}

	private static void leave(ServerScoreboard scoreboard, String name, PlayerTeam team) {
		scoreboard.removePlayerFromTeam(name, team);
		if (team.getPlayers().isEmpty()) {
			scoreboard.removePlayerTeam(team);
		}
	}

	private static void updateScore(ServerScoreboard scoreboard, ServerPlayer player, int totalCorrect) {
		Objective objective = scoreboard.getObjective(OBJECTIVE);
		if (objective == null) {
			objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY, Component.translatableWithFallback("langcraft.score.ranking", "Correct"),
					ObjectiveCriteria.RenderType.INTEGER, false, null);
		}
		// Se outro mod ou o dono do servidor já usa o número da lista, ele continua no lugar.
		if (scoreboard.getDisplayObjective(DisplaySlot.LIST) == null) {
			scoreboard.setDisplayObjective(DisplaySlot.LIST, objective);
		}
		scoreboard.getOrCreatePlayerScore(player, objective).set(totalCorrect);
	}

	private static void hideScore(ServerScoreboard scoreboard) {
		Objective objective = scoreboard.getObjective(OBJECTIVE);
		if (objective != null && scoreboard.getDisplayObjective(DisplaySlot.LIST) == objective) {
			scoreboard.setDisplayObjective(DisplaySlot.LIST, null);
		}
	}
}
