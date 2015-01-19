package com.asteria.network.impl;

import java.util.logging.Logger;

import com.asteria.game.character.player.PlayerIO;
import com.asteria.network.DataBuffer;
import com.asteria.network.ServerSelectionEvent;
import com.asteria.network.ServerSelectionKey;
import com.asteria.network.packet.PacketDecoder;
import com.asteria.utility.Settings;
import com.asteria.utility.Utility;

/**
 * The server selection event that decodes and handles all incoming packets.
 * 
 * @author lare96 <http://www.rune-server.org/members/lare96/>
 */
public final class DecodePacketServerEvent extends ServerSelectionEvent {

    /**
     * Creates a new {@link DecodePacketServerEvent}.
     */
    public DecodePacketServerEvent() {
        super(false);
    }

    /**
     * The logger that will print important information.
     */
    private static Logger logger = Utility.getLogger(DecodePacketServerEvent.class);

    @Override
    public void executeEvent(ServerSelectionKey key) throws Exception {
        PlayerIO session = (PlayerIO) key.getKey().attachment();
        if (session == null)
            return;
        if (session.getChannel().read(session.getInData()) == -1) {
            session.disconnect();
            return;
        }
        session.getInData().flip();
        while (session.getInData().hasRemaining()) {
            session.getTimeout().reset();
            if (session.getChain().hasNext()) {
                session.getChain().next();
                break;
            }
            if (session.getPacketOpcode() == -1) {
                session.setPacketOpcode(session.getInData().get() & 0xff);
                session.setPacketOpcode(session.getPacketOpcode() - session.getDecryptor().getKey() & 0xff);
            }
            if (session.getPacketSize() == -1) {
                session.setPacketSize(PacketDecoder.PACKET_SIZES[session.getPacketOpcode()]);
                if (session.getPacketSize() == -1) {
                    if (!session.getInData().hasRemaining()) {
                        session.getInData().flip();
                        session.getInData().compact();
                        break;
                    }
                    session.setPacketSize(session.getInData().get() & 0xff);
                }
            }
            if (session.getInData().remaining() >= session.getPacketSize()) {
                int positionBefore = session.getInData().position();
                PacketDecoder packet = PacketDecoder.PACKETS[session.getPacketOpcode()];
                try {
                    if (packet != null) {
                        packet.decode(session.getPlayer(), session.getPacketOpcode(), session.getPacketSize(), DataBuffer
                            .create(session.getInData()));
                    } else {
                        if (Settings.DEBUG)
                            logger.info(session.getPlayer() + " unhandled packet " + session.getPacketOpcode());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    session.getInData().get(
                        new byte[(session.getPacketSize() - (session.getInData().position() - positionBefore))]);
                }
                session.setPacketOpcode(-1);
                session.setPacketSize(-1);
            } else {
                session.getInData().flip();
                session.getInData().compact();
                break;
            }
        }
        session.getInData().clear();
    }

    @Override
    public void onThrowable(Throwable t, ServerSelectionKey key) {
        PlayerIO session = (PlayerIO) key.getKey().attachment();
        session.disconnect();
    }
}