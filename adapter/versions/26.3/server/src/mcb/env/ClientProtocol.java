package mcb.env;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Minecraft 26.3: the parts of the vanilla client's behaviour that {@link BotClient} transcribes and that differ between
 * game versions. Every version has its own copy of this class (adapter/versions/&lt;mc&gt;/server/src), with the same
 * members; {@link BotClient} calls it where the versions part ways. See adapter/versions/README.md.
 */
final class ClientProtocol {
    private ClientProtocol() { }

    /** ClientPacketListener.handleMovePlayer ends with gameMode.stopDestroyBlock(): a teleport aborts a block break. */
    static final boolean STOP_BREAK_ON_TELEPORT = true;
    /**
     * MultiPlayerGameMode.continueDestroyBlock remembers the face it is breaking (destroyDirection, set by
     * startDestroyBlock, cleared when the block breaks) and, on a tick that does not finish the block, sends
     * CHANGE_DESTROY_DIRECTION when the face under the crosshair is another one.
     */
    static final boolean REPORTS_BREAK_FACE = true;

    /**
     * The arm swing of Minecraft.startAttack and Minecraft.continueAttack: 26.3 swings locally
     * (player.swing(MAIN_HAND, animation, false), which sends nothing) and then sends ServerboundPunchPacket.INSTANCE
     * (ServerboundSwingPacket is gone).
     */
    static void attackSwing(ServerGamePacketListenerImpl net) {
        net.handlePunch(ServerboundPunchPacket.INSTANCE);
    }

    /**
     * The arm swing after Minecraft.startUseItem used the held item on a block: in 26.3 only a local animation
     * (player.swing(hand, animation, false) when the result's swing source is PREDICTED); no packet. The server swings
     * the arm itself in handleUseItemOn.
     */
    static void useSwing(ServerGamePacketListenerImpl net) { }

    /**
     * ClientPacketListener.handleMovePlayer's confirmation of a teleport: since 26.3 the confirmation carries the
     * player's position and rotation after the teleport was applied (and no separate move packet follows). The
     * server-side body already stands where the server put it, so that is what is reported.
     *
     * <p>The server now treats that position like a move packet, and its "moved too quickly" check measures from where
     * the player stood when its last tick began (ServerGamePacketListenerImpl.resetPosition, called by tickPlayer). A
     * real client's answer always arrives after such a tick, which has the player at the teleport's destination. The
     * bot's connection is never ticked by the server (BotClient ticks the body itself) and the bot answers at once, so
     * that reset is done here first; without it every long teleport would be answered with another one, forever.
     */
    static void acceptTeleport(ServerGamePacketListenerImpl net, int id, ServerPlayer player) {
        net.resetPosition();
        net.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id, player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot()));
    }

    /** MultiPlayerGameMode.continueDestroyBlock: the face being broken changed (no sequence number, no prediction). */
    static void breakFaceChanged(ServerGamePacketListenerImpl net, BlockPos pos, Direction face) {
        net.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.CHANGE_DESTROY_DIRECTION, pos, face));
    }
}
