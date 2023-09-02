package me.jellysquid.mods.sodium.mixin.core;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.util.Window;
import net.minecraft.util.Util;
import net.minecraftforge.fml.loading.progress.EarlyProgressVisualization;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;


@Mixin(Window.class)
public class MixinWindow {

    @Unique
    private boolean noContextSupported = true;

    @Unique
    private void setupWorkaround() {
        if(Util.getOperatingSystem() == Util.OperatingSystem.LINUX) {
            String session = System.getenv("XDG_SESSION_TYPE");

            if (Objects.equals(session, "wayland")) {
                noContextSupported = false;
            }
        }
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/loading/progress/EarlyProgressVisualization;handOffWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long wrapGlfwCreateWindow(EarlyProgressVisualization instance, IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        setupWorkaround();

        if (SodiumClientMod.options().performance.useNoErrorGLContext &&
                noContextSupported) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
        }

        return instance.handOffWindow(width, height, title, monitor);
    }
}
