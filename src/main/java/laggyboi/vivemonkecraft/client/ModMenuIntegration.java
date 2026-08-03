package laggyboi.vivemonkecraft.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// =====================================================================
// MOD MENU ENTRY                                           [FABRIC ONLY]
// =====================================================================
//
// Puts a config button on ViveMonkeCraft's row in the "Mods" screen. Mod Menu is
// Fabric-only, so this thin adapter is the Fabric branch's version of the config
// entry point; the NeoForge and Forge branches replace this one file with an
// IConfigScreenFactory registration instead.
//
// The screen ITSELF (all ~700 lines of pages and presets) lives in the shared
// VmcConfigScreen and is identical on every loader.
//
// OPTIONAL: only does anything if Mod Menu + Cloth Config are installed (both are
// under "suggests" in fabric.mod.json). The .properties file works either way.
// =====================================================================
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // No Cloth Config -> offer no screen rather than crashing on click.
        if (!VmcConfigScreen.clothPresent()) {
            return parent -> null;
        }
        return VmcConfigScreen::create;
    }
}
