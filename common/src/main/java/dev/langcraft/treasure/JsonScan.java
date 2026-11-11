package dev.langcraft.treasure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Lê receitas, loot e conquistas direto do JSON, sem entender o formato de cada mod. Assim, um altar ou
 * ritual de um mod que o LangCraft não conhece também entra na conta. Uma tag aparece com # na frente.
 */
public final class JsonScan {
	private static final String TAG_PREFIX = "#";

	private JsonScan() {
	}

	/** {@code inputs} tem um item ou uma tag por ingrediente, na ordem em que aparecem. */
	public record Mentions(Set<String> outputs, List<String> inputs) {
	}

	public record Advancement(@Nullable String parent, Set<String> items) {
	}

	/**
	 * O que está em campos com "result" ou "output" no nome é resultado, e o resto é ingrediente. Condições de
	 * carregamento, que citam itens só para conferir se um mod está instalado, ficam de fora.
	 */
	public static Mentions recipe(JsonElement json, Predicate<String> isItem) {
		Mentions mentions = new Mentions(new LinkedHashSet<>(), new ArrayList<>());
		walkRecipe(json, false, isItem, mentions);
		return mentions;
	}

	private static void walkRecipe(JsonElement json, boolean output, Predicate<String> isItem, Mentions mentions) {
		if (json.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				String key = entry.getKey().toLowerCase(Locale.ROOT);
				if (key.equals("type") || key.contains("condition")) {
					continue;
				}
				boolean isOutput = output || key.contains("result") || key.contains("output");
				JsonElement value = entry.getValue();
				if (key.equals("tag") && isString(value)) {
					add(isOutput, TAG_PREFIX + value.getAsString(), mentions);
				} else {
					walkRecipe(value, isOutput, isItem, mentions);
				}
			}
		} else if (json.isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray()) {
				walkRecipe(element, output, isItem, mentions);
			}
		} else if (isString(json)) {
			String reference = reference(json.getAsString(), isItem);
			if (reference != null) {
				add(output, reference, mentions);
			}
		}
	}

	private static void add(boolean output, String reference, Mentions mentions) {
		// Uma tag no resultado não diz qual item sai, então não conta.
		if (!output) {
			mentions.inputs().add(reference);
		} else if (!isTag(reference)) {
			mentions.outputs().add(reference);
		}
	}

	/** Os itens e tags que uma tabela de loot pode dar. Condições e funções, como a ferramenta exigida, ficam de fora. */
	public static Set<String> loot(JsonElement json, Predicate<String> isItem) {
		Set<String> found = new LinkedHashSet<>();
		walkLoot(json, isItem, found);
		return found;
	}

	private static void walkLoot(JsonElement json, Predicate<String> isItem, Set<String> found) {
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			if (isString(object.get("type")) && object.get("type").getAsString().endsWith(":tag") && isString(object.get("name"))) {
				found.add(TAG_PREFIX + object.get("name").getAsString());
				return;
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				String key = entry.getKey().toLowerCase(Locale.ROOT);
				if (key.equals("type") || key.contains("condition") || key.contains("function")) {
					continue;
				}
				walkLoot(entry.getValue(), isItem, found);
			}
		} else if (json.isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray()) {
				walkLoot(element, isItem, found);
			}
		} else if (isString(json)) {
			String reference = reference(json.getAsString(), isItem);
			if (reference != null) {
				found.add(reference);
			}
		}
	}

	/** O pai e os itens dos critérios de "ter no inventário". */
	public static Advancement advancement(JsonElement json, Predicate<String> isItem) {
		JsonObject object = json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		String parent = isString(object.get("parent")) ? object.get("parent").getAsString() : null;
		Set<String> items = new LinkedHashSet<>();
		if (object.has("criteria") && object.get("criteria").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("criteria").entrySet()) {
				JsonElement criterion = entry.getValue();
				if (criterion.isJsonObject() && isString(criterion.getAsJsonObject().get("trigger"))
						&& criterion.getAsJsonObject().get("trigger").getAsString().endsWith("inventory_changed")
						&& criterion.getAsJsonObject().has("conditions")) {
					collect(criterion.getAsJsonObject().get("conditions"), isItem, items);
				}
			}
		}
		return new Advancement(parent, items);
	}

	private static void collect(JsonElement json, Predicate<String> isItem, Set<String> found) {
		if (json.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals("tag") && isString(entry.getValue())) {
					found.add(TAG_PREFIX + entry.getValue().getAsString());
				} else {
					collect(entry.getValue(), isItem, found);
				}
			}
		} else if (json.isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray()) {
				collect(element, isItem, found);
			}
		} else if (isString(json)) {
			String reference = reference(json.getAsString(), isItem);
			if (reference != null) {
				found.add(reference);
			}
		}
	}

	private static @Nullable String reference(String text, Predicate<String> isItem) {
		if (text.startsWith(TAG_PREFIX) && text.indexOf(':') > 0) {
			return text;
		}
		return isItem.test(text) ? text : null;
	}

	private static boolean isString(@Nullable JsonElement json) {
		return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString();
	}

	public static boolean isTag(String reference) {
		return reference.startsWith(TAG_PREFIX);
	}

	public static String tagId(String reference) {
		return reference.substring(TAG_PREFIX.length());
	}
}
