package laggyboi.vivemonkecraft.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

// =====================================================================
// KEYBINDS                                                  [FORGE BODY]
// =====================================================================
//
// The KeyMapping itself is constructed identically on every loader (it's vanilla);
// only how it gets REGISTERED differs, which is what init() hides.
//
// UNBOUND by default (no key out of the box). To toggle from Vivecraft's radial
// menu you must first bind it to a real keyboard key in Options -> Controls ->
// Miscellaneous, then assign that key to a radial slot — Vivecraft can only put
// KEYBOARD keys on radial slots. See the "How-to" page in the config screen
// (Fabric/NeoForge) or the README (Forge, which has no Cloth Config screen).
//
// The keybind lands in the same vanilla Options.keyMappings array on every loader,
// which is what Vivecraft's radial menu reads — so the radial-slot workflow is
// unchanged from Fabric.
// =====================================================================
public final class VmcKeybinds {

    private VmcKeybinds() {}

    // Shared on all branches — do not diverge, the translation key is the identity
    // Vivecraft's radial menu shows.
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.vivemonkecraft.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            // 1.21.9 replaced the String category constants (CATEGORY_MISC) with
            // KeyMapping.Category record objects.
            KeyMapping.Category.MISC);

    /** Hand the keybind to the loader. Called once from the bootstrap. */
    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(TOGGLE));
    }
}
