/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.atalk.impl.neomedia.*;
import org.atalk.impl.neomedia.codec.video.h264.Packetizer;
import org.atalk.service.neomedia.RawPacket;
import org.bouncycastle.tls.*;
import org.ice4j.ice.Component;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import timber.log.Timber;

/**
 * Implements {@link DatagramTransport} in order to integrate the Bouncy Castle Crypto APIs in
 * libjitsi for the purposes of implementing DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DatagramTransportImpl implements DatagramTransport
{
    /**
     * The ID of the component which this instance works for/is associated with.
     */
    private final int componentID;

    /**
     * The <code>RTPConnector</code> which represents and implements the actual <code>DatagramSocket</code>
     * adapted by this instance.
     */
    private AbstractRTPConnector connector;

    /**
     * The pool of <code>RawPacket</code>s instances to reduce their allocations and garbage collection.
     */
    private final Queue<RawPacket> rawPacketPool = new LinkedBlockingQueue<>(RTPConnectorOutputStream.POOL_CAPACITY);

    /**
     * The queue of <code>RawPacket</code>s which have been received from the network are awaiting to be
     * received by the application through this <code>DatagramTransport</code>.
     */
    private final ArrayBlockingQueue<RawPacket> receiveQ;

    /**
     * The capacity of {@link #receiveQ}.
     */
    private final int receiveQCapacity;

    /**
     * The <code>byte</code> buffer which represents a datagram to be sent. It may consist of multiple
     * DTLS records which are simple encoded consecutively.
     */
    private byte[] sendBuf;

    /**
     * The length in <code>byte</code>s of {@link #sendBuf} i.e. the number of <code>sendBuf</code> elements
     * which constitute actual DTLS records.
     */
    private int sendBufLength;

    /**
     * The <code>Object</code> that synchronizes the access to {@link #sendBuf}, {@link #sendBufLength}.
     */
    private final Object sendBufSyncRoot = new Object();

    /**
     * Initializes a new <code>DatagramTransportImpl</code>.
     *
     * @param componentID {@link Component#RTP} if the new instance is to work on data/RTP packets or
     * {@link Component#RTCP} if the new instance is to work on control/RTCP packets
     */
    public DatagramTransportImpl(int componentID)
    {
        switch (componentID) {
            case DtlsTransformEngine.COMPONENT_RTCP:
            case DtlsTransformEngine.COMPONENT_RTP:
                this.componentID = componentID;
                break;
            default:
                throw new IllegalArgumentException("componentID");
        }

        receiveQCapacity = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;
        receiveQ = new ArrayBlockingQueue<>(receiveQCapacity);
    }

    private AbstractRTPConnector assertNotClosed()
            throws IOException
    {
        AbstractRTPConnector connector = this.connector;
        if (connector == null) {
            throw new IOException(getClass().getName() + " is closed!");
        }
        else {
            return connector;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        setConnector(null);
    }

    private void doSend(byte[] buf, int off, int len)
            throws IOException
    {
        // Do preserve the sequence of sends.
        flush();

        AbstractRTPConnector connector = assertNotClosed();
        RTPConnectorOutputStream outputStream;

        switch (componentID) {
            case DtlsTransformEngine.COMPONENT_RTCP:
                outputStream = connector.getControlOutputStream();
                break;
            case DtlsTransformEngine.COMPONENT_RTP:
                outputStream = connector.getDataOutputStream();
                break;
            default:
                String msg = "componentID";
                IllegalStateException ise = new IllegalStateException(msg);
                Timber.e(ise, "%s", msg);
                throw ise;
        }

        // Write synchronously in order to avoid our packet getting stuck in the
        // write queue (in case it is blocked waiting for DTLS to finish, for example).
        if (outputStream != null) {
            outputStream.syncWrite(buf, off, len);
        }
    }

    private void flush()
            throws IOException
    {
        assertNotClosed();
        byte[] buf;
        int len;

        synchronized (sendBufSyncRoot) {
            if ((sendBuf != null) && (sendBufLength != 0)) {
                buf = sendBuf;
                sendBuf = null;
                len = sendBufLength;
                sendBufLength = 0;
            }
            else {
                buf = null;
                len = 0;
            }
        }
        if (buf != null) {
            doSend(buf, 0, len);

            // Attempt to reduce allocations and garbage collection.
            synchronized (sendBufSyncRoot) {
                if (sendBuf == null)
                    sendBuf = buf;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReceiveLimit()
    {
        AbstractRTPConnector connector = this.connector;
        int receiveLimit = (connector == null) ? -1 : connector.getReceiveBufferSize();

        if (receiveLimit <= 0)
            receiveLimit = RTPConnectorInputStream.PACKET_RECEIVE_BUFFER_LENGTH;
        return receiveLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSendLimit()
    {
        AbstractRTPConnector connector = this.connector;
        int sendLimit = (connector == null) ? -1 : connector.getSendBufferSize();

        if (sendLimit <= 0) {
            /*
             * XXX The estimation bellow is wildly inaccurate and hardly related but we have to start somewhere.
             */
            sendLimit = DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH + Packetizer.MAX_PAYLOAD_SIZE;
        }
        return sendLimit;
    }

    /**
     * Queues a packet received from the network to be received by the application through this <code>DatagramTransport</code>.
     *
     * @param buf the array of <code>byte</code>s which contains the packet to be queued
     * @param off the offset within <code>buf</code> at which the packet to be queued starts
     * @param len the length within <code>buf</code> starting at <code>off</code> of the packet to be queued
     */
    void queueReceive(byte[] buf, int off, int len)
    {
        if (len > 0) {
            synchronized (receiveQ) {
                try {
                    assertNotClosed();
                } catch (IOException ioe) {
                    throw new IllegalStateException(ioe);
                }

                RawPacket pkt = rawPacketPool.poll();
                byte[] pktBuf;

                if ((pkt == null) || (pkt.getBuffer().length < len)) {
                    pktBuf = new byte[len];
                    pkt = new RawPacket(pktBuf, 0, len);
                }
                else {
                    pktBuf = pkt.getBuffer();
                    pkt.setLength(len);
                    pkt.setOffset(0);
                }
                System.arraycopy(buf, off, pktBuf, 0, len);

                if (receiveQ.size() == receiveQCapacity) {
                    RawPacket oldPkt = receiveQ.remove();

                    rawPacketPool.offer(oldPkt);
                }
                receiveQ.add(pkt);
                receiveQ.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int receive(byte[] buf, int off, int len, int waitMillis)
            throws IOException
    {
        long enterTime = System.currentTimeMillis();

        /*
         * If this DatagramTransportImpl is to be received from, then what is to be received may be
         * a response to a request that was earlier scheduled for send.
         */
        /*
         * XXX However, it may unnecessarily break up a flight into multiple datagrams. Since we have
         * implemented the recognition of the end of flights, it should be fairly safe to rely on it alone.
         */
        // flush();

        /*
         * If no datagram is received at all and the specified waitMillis expires, a negative value
         * is to be returned in order to have the outbound flight retransmitted.
         */
        int received = -1;
        boolean interrupted = false;

        while (received < len) {
            long timeout;

            if (waitMillis > 0) {
                timeout = waitMillis - System.currentTimeMillis() + enterTime;
                if (timeout == 0 /* wait forever */)
                    timeout = -1 /* do not wait */;
            }
            else {
                timeout = waitMillis;
            }

            synchronized (receiveQ) {
                assertNotClosed();

                RawPacket pkt = receiveQ.peek();
                if (pkt != null) {
                    /*
                     * If a datagram has been received and even if it carries no/zero bytes, a
                     * non-negative value is to be returned in order to distinguish the case with
                     * that of no received datagram. If the received bytes do not represent a DTLS
                     * record, the record layer may still not retransmit the outbound flight. But
                     * that should not be much of a concern because we queue DTLS records into
                     * DatagramTransportImpl.
                     */
                    if (received < 0)
                        received = 0;

                    int toReceive = len - received;
                    boolean toReceiveIsPositive = (toReceive > 0);

                    if (toReceiveIsPositive) {
                        int pktLength = pkt.getLength();
                        int pktOffset = pkt.getOffset();

                        if (toReceive > pktLength) {
                            toReceive = pktLength;
                            toReceiveIsPositive = (toReceive > 0);
                        }
                        if (toReceiveIsPositive) {
                            System.arraycopy(pkt.getBuffer(), pktOffset, buf, off + received, toReceive);
                            received += toReceive;
                        }
                        if (toReceive == pktLength) {
                            receiveQ.remove();
                            rawPacketPool.offer(pkt);
                        }
                        else {
                            pkt.setLength(pktLength - toReceive);
                            pkt.setOffset(pktOffset + toReceive);
                        }
                        if (toReceiveIsPositive) {
                            /*
                             * The specified buf has received toReceive bytes and we do not concatenate RawPackets.
                             */
                            break;
                        }
                    }
                    else {
                        // The specified buf has received at least len bytes.
                        break;
                    }
                }

                if (receiveQ.isEmpty()) {
                    if (timeout >= 0) {
                        try {
                            receiveQ.wait(timeout);
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    }
                    else {
                        // The specified waitMillis has been exceeded.
                        break;
                    }
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        return received;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(byte[] buf, int offset, int len)
            throws IOException
    {
        assertNotClosed();

        // If possible, construct a single datagram from multiple DTLS records.
        if (len >= DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH) {
            short type = TlsUtils.readUint8(buf, offset);
            boolean endOfFlight = false;

            switch (type) {
                case ContentType.handshake:
                    // message_type is the first byte of the record layer fragment (encrypted after 'change_cipher_spec')
                    short msg_type = TlsUtils.readUint8(buf,
                            offset + DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH);
                    switch (msg_type) {
                        case HandshakeType.certificate:
                        case HandshakeType.certificate_request:
                        case HandshakeType.certificate_verify:
                        case HandshakeType.client_key_exchange:
                        case HandshakeType.server_hello:
                        case HandshakeType.server_key_exchange:
                        case HandshakeType.new_session_ticket:
                        case HandshakeType.supplemental_data:
                            endOfFlight = false;
                            break;
                        case HandshakeType.client_hello:
                        case HandshakeType.finished:
                        case HandshakeType.hello_request:
                        case HandshakeType.hello_verify_request:
                        case HandshakeType.server_hello_done:
                            endOfFlight = true;
                            break;
                        default:
                            /*
                             * See DTLSRecordLayer#sendRecord(): When handling HandshakeType.finished message,
                             * it uses TlsAEADCipher#encodePlaintext() which seems not copy the sent buffer info;
                             * hence the msg_type will be random value
                             */
                            Timber.w("Received DTLS 'HandshakeType.finished' or unknown message type: %s", msg_type);
                            endOfFlight = true;
                            break;
                    }
                    // Do fall through!
                case ContentType.change_cipher_spec:
                    synchronized (sendBufSyncRoot) {
                        int newSendBufLength = sendBufLength + len;
                        int sendLimit = getSendLimit();

                        if (newSendBufLength <= sendLimit) {
                            if (sendBuf == null) {
                                sendBuf = new byte[sendLimit];
                                sendBufLength = 0;
                            }
                            else if (sendBuf.length < sendLimit) {
                                byte[] oldSendBuf = sendBuf;

                                sendBuf = new byte[sendLimit];
                                System.arraycopy(oldSendBuf, 0, sendBuf, 0,
                                        Math.min(sendBufLength, sendBuf.length));
                            }

                            System.arraycopy(buf, offset, sendBuf, sendBufLength, len);
                            sendBufLength = newSendBufLength;

                            if (endOfFlight)
                                flush();
                        }
                        else {
                            if (endOfFlight) {
                                doSend(buf, offset, len);
                            }
                            else {
                                flush();
                                send(buf, offset, len);
                            }
                        }
                    }
                    break;

                case ContentType.alert:
                case ContentType.application_data:
                default:
                    doSend(buf, offset, len);
                    break;
            }
        }
        else {
            doSend(buf, offset, len);
        }
    }

    /**
     * Sets the <code>RTPConnector</code> which represents and implements the actual
     * <code>DatagramSocket</code> to be adapted by this instance.
     *
     * @param connector the <code>RTPConnector</code> which represents and implements the actual
     * <code>DatagramSocket</code> to be adapted by this instance
     */
    void setConnector(AbstractRTPConnector connector)
    {
        synchronized (receiveQ) {
            this.connector = connector;
            receiveQ.notifyAll();
        }
    }
}
