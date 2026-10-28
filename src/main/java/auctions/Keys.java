package auctions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keys {

    public static final KeyMapping TOGGLE_DB = new KeyMapping(
            "Start/Close Database",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KeyMapping.Category.MULTIPLAYER);

    private Keys() {}

}
