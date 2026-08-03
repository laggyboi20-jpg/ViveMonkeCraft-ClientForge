package laggyboi.vivemonkecraft.client;

import laggyboi.vivemonkecraft.client.platform.VmcPlatform;

// =====================================================================
// "IS CLOTH CONFIG INSTALLED?" — deliberately a separate class
// =====================================================================
//
// This MUST NOT import or reference anything from me.shedaniel.clothconfig2.
//
// WHY IT EXISTS: this check used to live in VmcConfigScreen as a static method.
// That crashed the game on startup whenever Cloth Config was absent:
//
//   java.lang.NoClassDefFoundError: me/shedaniel/clothconfig2/api/AbstractConfigListEntry
//       at ...platform.VmcBootstrap.<init>
//
// Calling ANY static member of VmcConfigScreen makes the JVM load and verify that
// class, and verification resolves the Cloth types in its method signatures — so
// the very guard that was supposed to prevent touching Cloth was itself what
// touched it. The guard has to live in a class the verifier can load without
// Cloth on the classpath, which is this one.
//
// Rule for anyone editing the config screen wiring: the ONLY place a Cloth type
// may be reached is inside a lambda/method-ref that is created *after* present()
// has already returned true.
// =====================================================================
public final class VmcClothConfig {

    private VmcClothConfig() {}

    /**
     * Whether Cloth Config is installed. If it isn't, the loader's config-screen
     * entry must not offer a screen at all — that way clicking the config button
     * does nothing instead of crashing, and players use the .properties file, the
     * keybind, or /vmc instead.
     */
    public static boolean present() {
        // All three spellings on purpose: Cloth's mod id is "cloth-config" (and
        // historically "cloth-config2") on Fabric, but "cloth_config" on
        // Forge/NeoForge, whose mod ids can't contain hyphens. Checking all of them
        // is what keeps this file identical across the loader branches — so don't
        // "tidy" it down to the one spelling this branch happens to need.
        return VmcPlatform.isModLoaded("cloth-config")
            || VmcPlatform.isModLoaded("cloth-config2")
            || VmcPlatform.isModLoaded("cloth_config");
    }
}
