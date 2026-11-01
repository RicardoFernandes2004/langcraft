package dev.langcraft.client.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.vosk.LibVosk;
import org.vosk.Model;

/**
 * Um reconhecedor do Vosk que troca texto com a biblioteca nativa sempre em UTF-8.
 *
 * A classe {@code Recognizer} do Vosk usa a codificação padrão do sistema, que no Windows costuma ser
 * cp1252, e isso corrompe coreano e japonês. Trocar essa codificação globalmente não serve, porque o
 * caminho do modelo precisa dela para funcionar em pastas com acento.
 */
public final class VoskStream implements AutoCloseable {
	/** O mesmo nome com que {@link LibVosk} carrega a biblioteca em cada sistema. */
	private static final String LIBRARY_NAME = Platform.isWindows() ? "libvosk" : "vosk";
	private static final Pattern DECIMAL_COMMA = Pattern.compile("(\"\\s*:\\s*-?\\d+),(\\d+)");

	private final Pointer recognizer;

	/** Sem {@code grammar}, reconhece fala livre. Com ela, só as frases da lista JSON. */
	public VoskStream(Model model, float sampleRate, @Nullable String grammar) throws IOException {
		recognizer = grammar == null
				? function("vosk_recognizer_new").invokePointer(new Object[] {model.getPointer(), sampleRate})
				: function("vosk_recognizer_new_grm").invokePointer(new Object[] {model.getPointer(), sampleRate, utf8(grammar)});
		if (recognizer == null) {
			throw new IOException("O Vosk não criou o reconhecedor");
		}
	}

	/** Se a palavra está no vocabulário do modelo. O Vosk devolve -1 quando não está. */
	public static boolean knowsWord(Model model, String word) {
		return function("vosk_model_find_word").invokeInt(new Object[] {model.getPointer(), utf8(word)}) >= 0;
	}

	public void setMaxAlternatives(int alternatives) {
		function("vosk_recognizer_set_max_alternatives").invokeVoid(new Object[] {recognizer, alternatives});
	}

	/** Retorna verdadeiro quando uma frase terminou e pode ser lida com {@link #result()}. */
	public boolean accept(byte[] audio, int length) {
		return function("vosk_recognizer_accept_waveform").invokeInt(new Object[] {recognizer, audio, length}) != 0;
	}

	/** As hipóteses da frase, da mais provável para a menos provável. */
	public List<String> result() {
		return hypotheses(read("vosk_recognizer_result"));
	}

	/** O que foi entendido até agora da frase em andamento. */
	public String partial() {
		return VoskGrammar.clean(read("vosk_recognizer_partial_result").get("partial").getAsString());
	}

	@Override
	public void close() {
		function("vosk_recognizer_free").invokeVoid(new Object[] {recognizer});
	}

	private static List<String> hypotheses(JsonObject result) {
		List<String> texts = new ArrayList<>();
		if (result.has("alternatives")) {
			for (JsonElement alternative : result.getAsJsonArray("alternatives")) {
				texts.add(VoskGrammar.clean(alternative.getAsJsonObject().get("text").getAsString()));
			}
		} else {
			texts.add(VoskGrammar.clean(result.get("text").getAsString()));
		}
		return texts;
	}

	private JsonObject read(String name) {
		String json = function(name).invokePointer(new Object[] {recognizer}).getString(0, "UTF-8");
		return JsonParser.parseString(fixDecimalCommas(json)).getAsJsonObject();
	}

	/**
	 * A biblioteca do Vosk escreve números no formato do sistema: em pt-BR, sai "confidence" : 0,5, que não
	 * é JSON válido. Só troca a vírgula de números logo depois de uma chave, sem tocar no texto reconhecido.
	 */
	static String fixDecimalCommas(String json) {
		return DECIMAL_COMMA.matcher(json).replaceAll("$1.$2");
	}

	private static Function function(String name) {
		return NativeLibrary.getInstance(LIBRARY_NAME, LibVosk.class.getClassLoader()).getFunction(name);
	}

	private static byte[] utf8(String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return Arrays.copyOf(bytes, bytes.length + 1);
	}
}
