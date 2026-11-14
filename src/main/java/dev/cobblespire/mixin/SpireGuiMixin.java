package dev.cobblespire.mixin;
import dev.cobblespire.client.BattleHudState;
import dev.cobblespire.client.BattleHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class SpireGuiMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void spire$hud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (BattleHudState.isActive()) {
            if (Minecraft.getInstance().screen == null) BattleHudRenderer.render(graphics);
            ci.cancel();
        }
    }
}
