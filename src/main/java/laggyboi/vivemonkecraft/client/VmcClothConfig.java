package laggyboi.vivemonkecraft.client;

import net.neoforged.fml.ModList;

// =====================================================================
// CLOTH CONFIG PRESENCE CHECK
// =====================================================================
//
// The config screen (VmcConfigScreen) is built entirely out of Cloth Config
// types. Cloth is OPTIONAL — declared compileOnly in build.gradle and only
// "optional" in neoforge.mods.toml — so this class must NOT import or reference
// any Cloth type: it only asks the loader whether the mod is installed.
//
// VivemonkecraftClient gates the IConfigScreenFactory registration on present();
// only when this returns true does anything ever touch VmcConfigScreen, so a
// client without Cloth never triggers NoClassDefFoundError on the Cloth classes.
//
// NOTE the id spelling: Cloth Config is "cloth_config" on NeoForge (mod ids can't
// contain hyphens) vs "cloth-config" on Fabric.
// =====================================================================
public final class VmcClothConfig {

    private VmcClothConfig() {}

    public static boolean present() {
        ModList ml = ModList.get();
        return ml != null && ml.isLoaded("cloth_config");
    }
}
