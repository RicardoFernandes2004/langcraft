package dev.linguacraft.client.lang;

import dev.linguacraft.challenge.SubjectType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/** Nomes de itens e mobs, lidos dos arquivos de idioma do próprio jogo e dos mods instalados. */
public final class GameNames {
	private static final String PORTUGUESE = "pt_br";
	private static final String FALLBACK = "en_us";

	private static final Map<String, ClientLanguage> LANGUAGES = new HashMap<>();
	private static final Map<String, List<String>> ALL_NAMES = new HashMap<>();

	private GameNames() {
	}

	/** Limpa o cache. Os idiomas são recarregados na próxima consulta. */
	public static void clear() {
		LANGUAGES.clear();
		ALL_NAMES.clear();
	}

	public static Optional<String> name(String language, SubjectType type, String subject) {
		return translate(language, key(type, subject));
	}

	public static String nativeName(SubjectType type, String subject) {
		String key = key(type, subject);
		return language(PORTUGUESE).getOrDefault(key, subject);
	}

	public static ItemStack icon(SubjectType type, String subject) {
		Identifier id = Identifier.parse(subject);
		if (type == SubjectType.ITEM) {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
		}
		SpawnEggItem egg = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.getValue(id));
		return egg == null ? new ItemStack(Items.BARRIER) : new ItemStack(egg);
	}

	/** Todos os nomes do mesmo tipo, para as alternativas erradas. */
	public static List<String> allNames(String language, SubjectType type) {
		return ALL_NAMES.computeIfAbsent(language + "/" + type, cacheKey -> {
			List<String> names = new ArrayList<>();
			if (type == SubjectType.ITEM) {
				for (Item item : BuiltInRegistries.ITEM) {
					if (item != Items.AIR) {
						translate(language, item.getDescriptionId()).ifPresent(names::add);
					}
				}
			} else {
				for (SpawnEggItem egg : SpawnEggItem.eggs()) {
					EntityType<?> entityType = egg.getType(new ItemStack(egg));
					translate(language, entityType.getDescriptionId()).ifPresent(names::add);
				}
			}
			return names;
		});
	}

	private static String key(SubjectType type, String subject) {
		Identifier id = Identifier.parse(subject);
		return type == SubjectType.ITEM
				? BuiltInRegistries.ITEM.getValue(id).getDescriptionId()
				: BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId();
	}

	private static Optional<String> translate(String language, String key) {
		ClientLanguage loaded = language(language);
		return loaded.has(key) ? Optional.of(loaded.getOrDefault(key, key)) : Optional.empty();
	}

	private static ClientLanguage language(String code) {
		// O português usa o inglês como reserva para nomes de mods sem tradução.
		List<String> codes = code.equals(PORTUGUESE) ? List.of(FALLBACK, PORTUGUESE) : List.of(code);
		return LANGUAGES.computeIfAbsent(code, unused ->
				ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), codes, false));
	}
}
