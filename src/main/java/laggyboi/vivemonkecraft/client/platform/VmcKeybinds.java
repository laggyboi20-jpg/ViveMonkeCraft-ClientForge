package laggyboi.vivemonkecraft.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//? if <1.21.6
/*import net.minecraftforge.eventbus.api.EventPriority;*/

// =====================================================================
// KEYBINDS                                                  [FORGE BODY]
// =====================================================================
//
// The KeyMapping itself is constructed identically on every loader (it's vanilla);
// only how it gets REGISTERED differs, which is what init() hides. Forge wants it
// registered from RegisterKeyMappingsEvent on the mod bus (see ForgeBus for how
// that bus is reached per Forge version).
//
// Bound to real keys by default so the Vivecraft radial menu can assign them
// immediately (Vivecraft can only put KEYBOARD keys on radial slots). The user
// can rebind in Options -> Controls -> Miscellaneous.
// =====================================================================
public final class VmcKeybinds {

    private VmcKeybinds() {}

    // Default keys: V = toggle, B = cycle preset. Shared on all branches — the
    // translation key is the identity Vivecraft's radial menu shows.
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.vivemonkecraft.toggle",
            InputConstants.KEY_V,
            KeyMapping.Category.MISC);

    public static final KeyMapping CYCLE_MODE = new KeyMapping(
            "key.vivemonkecraft.cyclemode",
            InputConstants.KEY_B,
            KeyMapping.Category.MISC);

    /** Hand the keybinds to the loader. Called once from the bootstrap. */
    public static void init() {
        //? if >=26.3 {
        // Migrate stale GLFW_KEY_UNKNOWN (-1) saved in options.txt from pre-26.3;
        // -1 is not a valid SDL scancode and crashes Vivecraft's radial menu.
        InputConstants.Key staleUnbound = InputConstants.Type.KEYBOARD.getOrCreate(-1);
        if (TOGGLE.matches(staleUnbound)) {
            TOGGLE.setKey(InputConstants.UNKNOWN);
        }
        if (CYCLE_MODE.matches(staleUnbound)) {
            CYCLE_MODE.setKey(InputConstants.UNKNOWN);
        }
        //?}

        //? if >=1.21.9 {
        RegisterKeyMappingsEvent.BUS.addListener(VmcKeybinds::register);
        //?} else if >=1.21.6 {
        /*RegisterKeyMappingsEvent.getBus(ForgeBus.mod()).addListener(VmcKeybinds::register);
        *///?} else {
        /*ForgeBus.mod().addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class,
                VmcKeybinds::register);
        *///?}
    }

    private static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(CYCLE_MODE);
    }
}
