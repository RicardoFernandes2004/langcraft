package dev.langcraft.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;

/** Sorteio do mob por categoria e nascimento em cima do jogador. */
public final class MobPunishment {
	public record Tier(String name, int weight, List<String> mobs) {
	}

	private static final int HEIGHT_ABOVE_HEAD = 3;
	private static final int SEARCH_RADIUS = 4;

	private MobPunishment() {
	}

	/** Sorteia a categoria pela chance e depois um mob dela. Categorias sem mob permitido saem do sorteio. */
	public static Optional<String> draw(List<Tier> tiers, Predicate<String> allowed, RandomGenerator random) {
		List<Tier> usable = new ArrayList<>();
		for (Tier tier : tiers) {
			List<String> mobs = tier.mobs().stream().filter(allowed).toList();
			if (tier.weight() > 0 && !mobs.isEmpty()) {
				usable.add(new Tier(tier.name(), tier.weight(), mobs));
			}
		}
		if (usable.isEmpty()) {
			return Optional.empty();
		}
		int roll = random.nextInt(usable.stream().mapToInt(Tier::weight).sum());
		for (Tier tier : usable) {
			roll -= tier.weight();
			if (roll < 0) {
				return Optional.of(tier.mobs().get(random.nextInt(tier.mobs().size())));
			}
		}
		throw new IllegalStateException("Sorteio fora das categorias");
	}

	/** Só entram mobs registrados, com ovo de invocação, e que não somem na dificuldade atual. */
	public static boolean canSpawn(String mobId, ServerLevel level) {
		Identifier id = Identifier.tryParse(mobId);
		if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			return false;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		if (SpawnEggItem.byId(type) == null) {
			return false;
		}
		return level.getDifficulty() != Difficulty.PEACEFUL || type.getCategory() != MobCategory.MONSTER;
	}

	public static void spawnAbove(ServerPlayer player, String mobId) {
		ServerLevel level = player.level();
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(mobId));
		type.spawn(level, findSpawnPos(player, type), EntitySpawnReason.MOB_SUMMONED);
	}

	/** Procura primeiro descendo a partir de cima da cabeça, depois em volta do jogador. */
	private static BlockPos findSpawnPos(ServerPlayer player, EntityType<?> type) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		int top = (int) Math.ceil(player.getBoundingBox().maxY) + HEIGHT_ABOVE_HEAD;
		for (int y = top; y >= feet.getY(); y--) {
			BlockPos pos = new BlockPos(feet.getX(), y, feet.getZ());
			if (fits(level, type, pos)) {
				return pos;
			}
		}
		for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
			for (int dy = 2; dy >= -2; dy--) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
							BlockPos pos = feet.offset(dx, dy, dz);
							if (fits(level, type, pos)) {
								return pos;
							}
						}
					}
				}
			}
		}
		return feet;
	}

	private static boolean fits(ServerLevel level, EntityType<?> type, BlockPos pos) {
		return level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}
}
