package laggyboi.vivemonkecraft.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

// =====================================================================
// KEYBINDS                                                 [FABRIC BODY]
// =====================================================================
//
// The KeyMapping itself is constructed identically on every loader (it's vanilla);
// only how it gets REGISTERED differs, which is what init() hides.
//
// UNBOUND by default (no key out of the box). To toggle from Vivecraft's radial
// menu you must first bind it to a real keyboard key in Options -> Controls ->
// Miscellaneous, then assign that key to a radial slot — Vivecraft can only put
// KEYBOARD keys on radial slots. See the "How-to" page in the config screen.
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
        KeyMappingHelper.registerKeyMapping(TOGGLE);
    }
}
