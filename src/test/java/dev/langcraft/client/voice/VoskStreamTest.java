package dev.langcraft.client.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class VoskStreamTest {
	/** Saída real do Vosk com o sistema em pt-BR. */
	private static final String PT_BR = """
			{
			  "alternatives" : [{
			      "confidence" : 154,531036,
			      "text" : "돌, 1,5"
			    }, {
			      "confidence" : -3,25,
			      "text" : "사과"
			    }]
			}
			""";

	@Test
	void decimalCommasBecomeValidJson() {
		JsonObject parsed = JsonParser.parseString(VoskStream.fixDecimalCommas(PT_BR)).getAsJsonObject();
		JsonObject first = parsed.getAsJsonArray("alternatives").get(0).getAsJsonObject();
		assertEquals(154.531036, first.get("confidence").getAsDouble(), 1e-6);
		assertEquals(-3.25, parsed.getAsJsonArray("alternatives").get(1).getAsJsonObject().get("confidence").getAsDouble(), 1e-6);
	}

	@Test
	void textIsNotChanged() {
		JsonObject parsed = JsonParser.parseString(VoskStream.fixDecimalCommas(PT_BR)).getAsJsonObject();
		assertEquals("돌, 1,5", parsed.getAsJsonArray("alternatives").get(0).getAsJsonObject().get("text").getAsString());
	}

	@Test
	void validJsonStaysTheSame() {
		String valid = "{\"alternatives\" : [{\"confidence\" : 154.531036, \"text\" : \"돌\"}]}";
		assertEquals(valid, VoskStream.fixDecimalCommas(valid));
		String partial = "{\n  \"partial\" : \"돌, 사과\"\n}";
		assertEquals(partial, VoskStream.fixDecimalCommas(partial));
	}
}
