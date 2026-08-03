package laggyboi.vivemonkecraft.client.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// =====================================================================
// PLATFORM — loader paths + mod queries                    [FABRIC BODY]
// =====================================================================
//
// One of the four small classes that isolate everything loader-specific.
// The PUBLIC API here is identical on the Fabric, NeoForge and Forge dev-test
// branches — only the bodies differ. Every other file in the mod calls this and
// therefore compiles unchanged on all three loaders.
//
// When you add a feature on the base (Fabric) branch and merge it into the
// loader branches, the only files that can conflict are these four plus the
// bootstrap and the config-screen entry. Keep the signatures below stable.
// =====================================================================
public final class VmcPlatform {

    private VmcPlatform() {}

    /** Where vivemonkecraft.properties lives. */
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** Game root — the debug log is written to <gameDir>/logs. */
    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    /** True if a mod with this id is present (used for Cloth Config / Vivecraft checks). */
    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    /** Friendly version string for a mod id, or "absent" when it isn't installed. */
    public static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("absent");
    }

    /** The loader's own id/version, for the debug env dump header. */
    public static String loaderName() {
        return "fabric " + modVersion("fabricloader");
    }

    /** Total loaded mod count — debug env dump. */
    public static int modCount() {
        return FabricLoader.getInstance().getAllMods().size();
    }

    /** "<id> <version>" for every loaded mod, sorted by id — debug env dump. */
    public static List<String> allMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(c -> c.getMetadata())
                .sorted(java.util.Comparator.comparing(m -> m.getId()))
                .map(m -> m.getId() + " " + m.getVersion().getFriendlyString())
                .collect(Collectors.toList());
    }
}
