package laggyboi.vivemonkecraft.client.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// =====================================================================
// PLATFORM — loader paths + mod queries                      [FORGE BODY]
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
     * "cloth-config" on Fabric but "cloth_config" on Forge/NeoForge, whose mod
     * ids can't contain hyphens. Callers pass BOTH spellings, so this needs no
     * translation — but don't "tidy" the caller into checking only one.
     */
    // NOTE: unlike NeoForge (ModList.get().isLoaded(...)), Forge 26.x's ModList is
    // entirely STATIC — there is no get(). Every method below is wrapped in a
    // try/catch because these are also called from the debug logger, which must
    // never throw: it can run before the mod list is populated.
    public static boolean isModLoaded(String id) {
        try {
            return ModList.isLoaded(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Friendly version string for a mod id, or "absent" when it isn't installed. */
    public static String modVersion(String id) {
        try {
            return ModList.getModContainerById(id)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("absent");
        } catch (Throwable t) {
            return "absent";
        }
    }

    /** The loader's own id/version, for the debug env dump header. */
    public static String loaderName() {
        return "forge " + modVersion("forge");
    }

    /** Total loaded mod count — debug env dump. */
    public static int modCount() {
        try {
            return ModList.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** "<id> <version>" for every loaded mod, sorted by id — debug env dump. */
    public static List<String> allMods() {
        try {
            return ModList.getMods().stream()
                    .sorted(java.util.Comparator.comparing(IModInfo::getModId))
                    .map(m -> m.getModId() + " " + m.getVersion().toString())
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            return List.of();
        }
    }
}
