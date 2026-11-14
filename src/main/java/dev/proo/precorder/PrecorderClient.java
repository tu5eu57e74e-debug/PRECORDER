package dev.proo.precorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PrecorderClient implements ClientModInitializer {
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.precorder.toggle",
                GLFW.GLFW_KEY_F9,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                RecorderState.toggle();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String status = RecorderState.isRecording() ? "armed (capture backend not connected)" : "stopped";
                    mc.player.displayClientMessage(Component.literal("[PRECORDER] " + status), true);
                }
            }
        });
    }
}
