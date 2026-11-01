package dev.langcraft.client.lang;

import dev.langcraft.LangCraft;
import dev.langcraft.challenge.SubjectType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

/** Nomes de itens e mobs, lidos dos arquivos de idioma do próprio jogo, dos mods instalados e dos jogadores. */
public final class GameNames {
	private static final String FALLBACK = "en_us";

	private static final Map<String, ClientLanguage> LANGUAGES = new HashMap<>();
	private static final Map<String, List<String>> ALL_NAMES = new HashMap<>();
	private static final Map<String, CommunityNames> COMMUNITY = new HashMap<>();

	private GameNames() {
	}

	/** Limpa o cache. Os idiomas são recarregados na próxima consulta. */
	public static void clear() {
		LANGUAGES.clear();
		ALL_NAMES.clear();
		COMMUNITY.clear();
	}

	public static Optional<String> name(String language, SubjectType type, String subject) {
		return translate(language, key(type, subject));
	}

	/**
	 * O nome no idioma do jogo do jogador, que é o significado da palavra estudada. Fica vazio quando o idioma
	 * estudado é o próprio idioma do jogo, porque mostraria a resposta.
	 */
	public static String nativeName(SubjectType type, String subject, String studiedLanguage) {
		String selected = Minecraft.getInstance().getLanguageManager().getSelected();
		if (selected.equals(studiedLanguage)) {
			return "";
		}
		ClientLanguage language = LANGUAGES.computeIfAbsent("native/" + selected, unused ->
				ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(FALLBACK, selected), false));
		return language.getOrDefault(key(type, subject), subject);
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

	/** O arquivo dos jogadores vem primeiro, para também dar para corrigir uma tradução ruim de um mod. */
	private static Optional<String> translate(String language, String key) {
		Optional<String> community = community(language).translation(key);
		if (community.isPresent()) {
			return community;
		}
		ClientLanguage loaded = language(language);
		return loaded.has(key) ? Optional.of(loaded.getOrDefault(key, key)) : Optional.empty();
	}

	/** Se o nome mostrado vem do arquivo dos jogadores, e não do jogo ou de um mod. */
	public static boolean isCommunityName(String language, SubjectType type, String subject) {
		return community(language).translation(key(type, subject)).isPresent();
	}

	/** Coloca no arquivo do idioma tudo o que está sem tradução e retorna o arquivo atualizado. */
	public static CommunityNames exportMissing(String language) {
		ClientLanguage english = language(FALLBACK);
		Map<String, String> missing = new LinkedHashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			addIfMissing(missing, language, item.getDescriptionId(), english, item != Items.AIR);
		}
		for (SpawnEggItem egg : SpawnEggItem.eggs()) {
			addIfMissing(missing, language, egg.getType(new ItemStack(egg)).getDescriptionId(), english, true);
		}
		CommunityNames names = community(language);
		names.addMissing(missing);
		return names;
	}

	private static void addIfMissing(Map<String, String> missing, String language, String key, ClientLanguage english, boolean include) {
		if (include && translate(language, key).isEmpty()) {
			missing.put(key, english.getOrDefault(key, key));
		}
	}

	private static CommunityNames community(String language) {
		return COMMUNITY.computeIfAbsent(language, code ->
				new CommunityNames(LangCraft.platform.configDir().resolve("langcraft/translations").resolve(code + ".json")));
	}

	private static ClientLanguage language(String code) {
		return LANGUAGES.computeIfAbsent(code, unused ->
				ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false));
	}
}
