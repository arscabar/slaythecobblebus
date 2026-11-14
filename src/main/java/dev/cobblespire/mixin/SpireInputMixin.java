package dev.cobblespire.mixin;
import dev.cobblespire.client.WorldBattleView;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SpireInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void spire$lock(boolean slow, float scale, CallbackInfo ci) {
        if (WorldBattleView.locksMovement()) {
            forwardImpulse=0; leftImpulse=0;
            up=down=left=right=jumping=shiftKeyDown=false;
        }
    }
}
