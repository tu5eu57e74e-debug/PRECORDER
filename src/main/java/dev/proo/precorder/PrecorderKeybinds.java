package dev.proo.precorder;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PrecorderKeybinds {
    public static KeyBinding openMenu;
    public static KeyBinding toggleRecording;
    public static KeyBinding pauseRecording;

    private PrecorderKeybinds() {}

    public static void register() {
        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of("precorder", "main")
        );

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.precorder.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, category
        ));
        toggleRecording = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.precorder.toggle_recording", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, category
        ));
        pauseRecording = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.precorder.pause_recording", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, category
        ));
    }
}
