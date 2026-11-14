package dev.cobblespire.mixin;
import dev.cobblespire.client.BattleHudState;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SpireKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void spire$keys(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        if (BattleHudState.handleKey(key,action)) ci.cancel();
    }
}
