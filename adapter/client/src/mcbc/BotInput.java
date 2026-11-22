package mcbc;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** The player's movement input while the bot drives: the bot's key states in place of the keyboard's (as KeyboardInput reads them). */
public final class BotInput extends ClientInput {
    public volatile boolean forward, back, left, right, jump, sneak, sprint;

    private static float impulse(boolean pos, boolean neg) { return pos == neg ? 0.0F : pos ? 1.0F : -1.0F; }

    @Override
    public void tick() {
        keyPresses = new Input(forward, back, left, right, jump, sneak, sprint);
        moveVector = new Vec2(impulse(left, right), impulse(forward, back)).normalized();
    }

    public void release() { forward = back = left = right = jump = sneak = sprint = false; }
}
