package dev.cobblespire.mixin;
import dev.cobblespire.client.BattleHudState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class SpireHandMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void spire$hand(Camera camera,float delta,Matrix4f matrix,CallbackInfo ci) {
        if (BattleHudState.isActive()) ci.cancel();
    }
}
