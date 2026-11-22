package mcb.env;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;

/**
 * Minecraft 26.2: the parts of the vanilla client's behaviour that {@link BotClient} transcribes and that differ between
 * game versions. Every version has its own copy of this class (adapter/versions/&lt;mc&gt;/server/src), with the same
 * members; {@link BotClient} calls it where the versions part ways. See adapter/versions/README.md.
 */
final class ClientProtocol {
    private ClientProtocol() { }

    /** ClientPacketListener.handleMovePlayer does not stop a block break in 26.2 (26.3 does). */
    static final boolean STOP_BREAK_ON_TELEPORT = false;
    /** MultiPlayerGameMode.continueDestroyBlock does not report a change of the face being broken in 26.2 (26.3 does). */
    static final boolean REPORTS_BREAK_FACE = false;

    /**
     * The arm swing of Minecraft.startAttack and Minecraft.continueAttack: LocalPlayer.swing(MAIN_HAND), which sends
     * ServerboundSwingPacket(MAIN_HAND).
     */
    static void attackSwing(ServerGamePacketListenerImpl net) {
        net.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    /**
     * The arm swing after Minecraft.startUseItem used the held item on a block: LocalPlayer.swing(hand), a
     * ServerboundSwingPacket. (Vanilla swings only when the use succeeded with a client-side swing; the bot has always
     * sent it after every use on a block, which changes nothing but the arm's animation.)
     */
    static void useSwing(ServerGamePacketListenerImpl net) {
        net.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    /**
     * ClientPacketListener.handleMovePlayer's confirmation of a teleport: ServerboundAcceptTeleportationPacket(id). (The
     * 26.2 client then also sends its new position as a move packet; the server-side body has no client copy of the
     * position to report, so that one is not sent.)
     */
    static void acceptTeleport(ServerGamePacketListenerImpl net, int id, ServerPlayer player) {
        net.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
    }

    /** 26.2 has no CHANGE_DESTROY_DIRECTION action; never called while {@link #REPORTS_BREAK_FACE} is false. */
    static void breakFaceChanged(ServerGamePacketListenerImpl net, BlockPos pos, Direction face) {
        throw new UnsupportedOperationException("Minecraft 26.2 has no CHANGE_DESTROY_DIRECTION");
    }
}
