package laggyboi.vivemonkecraft.client.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// =====================================================================
// PLATFORM — loader paths + mod queries                  [NEOFORGE BODY]
// =====================================================================
//
// One of the four small classes that isolate everything loader-specific.
// The PUBLIC API here is identical on the Fabric, NeoForge and Forge dev-test
// branches — only the bodies differ. Every other file in the mod calls this and
// therefore compiles unchanged on all three loaders.
//
// When a feature is added on the base (Fabric) branch and merged into this one,
// the only files that can conflict are these four plus the bootstrap and the
// config-screen entry. Keep the signatures below stable.
// =====================================================================
public final class VmcPlatform {

    private VmcPlatform() {}

    /** Where vivemonkecraft.properties lives. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** Game root — the debug log is written to <gameDir>/logs. */
    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /**
     * True if a mod with this id is present.
     *
     * NOTE the id spelling difference between loaders: Cloth Config is
     * "cloth-config" on Fabric but "cloth_config" on NeoForge (NeoForge mod ids
     * can't contain hyphens). Callers pass BOTH spellings, so this needs no
     * translation — but don't "tidy" the caller into checking only one.
     */
    public static boolean isModLoaded(String id) {
        return ModList.get() != null && ModList.get().isLoaded(id);
    }

    /** Friendly version string for a mod id, or "absent" when it isn't installed. */
    public static String modVersion(String id) {
        if (ModList.get() == null) return "absent";
        return ModList.get().getModContainerById(id)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("absent");
    }

    /** The loader's own id/version, for the debug env dump header. */
    public static String loaderName() {
        return "neoforge " + modVersion("neoforge");
    }

    /** Total loaded mod count — debug env dump. */
    public static int modCount() {
        return ModList.get() == null ? 0 : ModList.get().getMods().size();
    }

    /** "<id> <version>" for every loaded mod, sorted by id — debug env dump. */
    public static List<String> allMods() {
        if (ModList.get() == null) return List.of();
        return ModList.get().getMods().stream()
                .sorted(java.util.Comparator.comparing(m -> m.getModId()))
                .map(m -> m.getModId() + " " + m.getVersion().toString())
                .collect(Collectors.toList());
    }
}
