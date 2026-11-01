package dev.langcraft.challenge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** Coloca os mobs de outros mods nas categorias de sorteio, sem precisar listá-los na configuração. */
public final class ModMobs {
	static final String HARMLESS = "harmless";
	static final String NEUTRAL = "neutral";
	static final String HOSTILE = "hostile";
	static final String DANGEROUS = "dangerous";
	static final String EXTREME = "extreme";

	private static final String GAME_NAMESPACE = "minecraft";
	private static final double SMALL_PASSIVE_HEALTH = 20;
	private static final double COMMON_HOSTILE_HEALTH = 40;
	private static final double DANGEROUS_HOSTILE_HEALTH = 100;

	private ModMobs() {
	}

	/** A referência é o jogo: uma vaca tem 10 de vida, um golem de ferro 100, um zumbi 20, um devastador 100 e o Warden 500. */
	public static String tierFor(boolean hostile, double maxHealth) {
		if (!hostile) {
			return maxHealth <= SMALL_PASSIVE_HEALTH ? HARMLESS : NEUTRAL;
		}
		if (maxHealth <= COMMON_HOSTILE_HEALTH) {
			return HOSTILE;
		}
		return maxHealth <= DANGEROUS_HOSTILE_HEALTH ? DANGEROUS : EXTREME;
	}

	/** Procura os mobs com ovo de invocação de outros mods. Os mobs do próprio jogo são escolhidos na configuração. */
	public static Map<String, String> installed() {
		Map<String, String> mobs = new LinkedHashMap<>();
		for (SpawnEggItem egg : SpawnEggItem.eggs()) {
			EntityType<?> type = egg.getType(new ItemStack(egg));
			var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (!id.getNamespace().equals(GAME_NAMESPACE) && DefaultAttributes.hasSupplier(type)) {
				mobs.put(id.toString(), tierFor(type.getCategory() == MobCategory.MONSTER, maxHealth(type)));
			}
		}
		return mobs;
	}

	@SuppressWarnings("unchecked")
	private static double maxHealth(EntityType<?> type) {
		return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type).getBaseValue(Attributes.MAX_HEALTH);
	}

	/**
	 * Junta os mobs instalados às categorias de mesmo nome. Quem já está em uma categoria fica onde foi
	 * colocado, e uma categoria que não existe na configuração simplesmente não recebe ninguém.
	 */
	public static List<MobPunishment.Tier> merge(List<MobPunishment.Tier> tiers, Map<String, String> installed, Collection<String> blocked) {
		Set<String> listed = new HashSet<>(blocked);
		tiers.forEach(tier -> listed.addAll(tier.mobs()));
		List<MobPunishment.Tier> merged = new ArrayList<>();
		for (MobPunishment.Tier tier : tiers) {
			List<String> mobs = new ArrayList<>(tier.mobs());
			installed.forEach((mob, tierName) -> {
				if (tierName.equals(tier.name()) && !listed.contains(mob)) {
					mobs.add(mob);
				}
			});
			merged.add(new MobPunishment.Tier(tier.name(), tier.weight(), mobs));
		}
		return merged;
	}
}
