package dev.langcraft.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.langcraft.client.ui.LangConfigScreen;

/** Só é carregada pelo Mod Menu. Sem ele, o resto do mod não depende desta classe. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LangConfigScreen::new;
	}
}
