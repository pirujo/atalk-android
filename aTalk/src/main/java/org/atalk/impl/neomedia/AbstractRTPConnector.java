/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.StreamConnector;

import java.io.IOException;
import java.net.InetAddress;

import javax.media.rtp.RTPConnector;
import javax.media.rtp.SessionAddress;

import timber.log.Timber;

/**
 * Provides a base/default implementation of <code>RTPConnector</code> which has factory methods for its
 * control and data input and output streams and has an associated <code>StreamConnector</code>.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class AbstractRTPConnector implements RTPConnector
{
    /**
     * The pair of datagram sockets for RTP and RTCP traffic that this instance uses in the form of
     * a <code>StreamConnector</code>.
     */
    protected final StreamConnector connector;

    /**
     * RTCP packet input stream used by <code>RTPManager</code>.
     */
    private RTPConnectorInputStream<?> controlInputStream;

    /**
     * RTCP packet output stream used by <code>RTPManager</code>.
     */
    private RTPConnectorOutputStream controlOutputStream;

    /**
     * RTP packet input stream used by <code>RTPManager</code>.
     */
    private RTPConnectorInputStream<?> dataInputStream;

    /**
     * RTP packet output stream used by <code>RTPManager</code>.
     */
    private RTPConnectorOutputStream dataOutputStream;

    /**
     * Initializes a new <code>AbstractRTPConnector</code> which is to use a given pair of datagram
     * sockets for RTP and RTCP traffic specified in the form of a <code>StreamConnector</code>.
     *
     * @param connector the pair of datagram sockets for RTP and RTCP traffic the new instance is to use
     */
    public AbstractRTPConnector(StreamConnector connector)
    {
        if (connector == null)
            throw new NullPointerException("connector");

        this.connector = connector;
    }

    /**
     * Add a stream target. A stream target is the destination address which this RTP session will
     * send its data to. For a single session, we can add multiple SessionAddresses, and for each
     * address, one copy of data will be sent to.
     *
     * @param target Destination target address
     * @throws IOException if there was a socket-related error while adding the specified target
     */
    public void addTarget(SessionAddress target)
            throws IOException
    {
        InetAddress controlAddress = target.getControlAddress();

        if (controlAddress != null) {
            getControlOutputStream().addTarget(controlAddress, target.getControlPort());
        }

        getDataOutputStream().addTarget(target.getDataAddress(), target.getDataPort());
    }

    /**
     * Closes all sockets, stream, and the <code>StreamConnector</code> that this <code>RTPConnector</code>
     * is using.
     */
    public void close()
    {
        if (dataOutputStream != null) {
            dataOutputStream.close();
            dataOutputStream = null;
        }
        if (controlOutputStream != null) {
            controlOutputStream.close();
            controlOutputStream = null;
        }
        if (dataInputStream != null) {
            dataInputStream.close();
            dataInputStream = null;
        }
        if (controlInputStream != null) {
            controlInputStream.close();
            controlInputStream = null;
        }

        connector.close();
    }

    /**
     * Creates the RTCP packet input stream to be used by <code>RTPManager</code>.
     *
     * @return a new RTCP packet input stream to be used by <code>RTPManager</code>
     * @throws IOException if an error occurs during the creation of the RTCP packet input stream
     */
    protected abstract RTPConnectorInputStream<?> createControlInputStream()
            throws IOException;

    /**
     * Creates the RTCP packet output stream to be used by <code>RTPManager</code>.
     *
     * @return a new RTCP packet output stream to be used by <code>RTPManager</code>
     * @throws IOException if an error occurs during the creation of the RTCP packet output stream
     */
    protected abstract RTPConnectorOutputStream createControlOutputStream()
            throws IOException;

    /**
     * Creates the RTP packet input stream to be used by <code>RTPManager</code>.
     *
     * @return a new RTP packet input stream to be used by <code>RTPManager</code>
     * @throws IOException if an error occurs during the creation of the RTP packet input stream
     */
    protected abstract RTPConnectorInputStream<?> createDataInputStream()
            throws IOException;

    /**
     * Creates the RTP packet output stream to be used by <code>RTPManager</code>.
     *
     * @return a new RTP packet output stream to be used by <code>RTPManager</code>
     * @throws IOException if an error occurs during the creation of the RTP packet output stream
     */
    protected abstract RTPConnectorOutputStream createDataOutputStream()
            throws IOException;

    /**
     * Gets the <code>StreamConnector</code> which represents the pair of datagram sockets for RTP and
     * RTCP traffic used by this instance.
     *
     * @return the <code>StreamConnector</code> which represents the pair of datagram sockets for RTP
     * and RTCP traffic used by this instance
     */
    public final StreamConnector getConnector()
    {
        return connector;
    }

    /**
     * Returns the input stream that is handling incoming RTCP packets.
     *
     * @return the input stream that is handling incoming RTCP packets.
     * @throws IOException if an error occurs during the creation of the RTCP packet input stream
     */
    public RTPConnectorInputStream<?> getControlInputStream()
            throws IOException
    {
        return getControlInputStream(true);
    }

    /**
     * Gets the <code>PushSourceStream</code> which gives access to the RTCP data received from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create <code>true</code> to create the <code>PushSourceStream</code> which gives access to the RTCP
     * data received from the remote targets if it does not exist yet; otherwise, <code>false</code>
     * @return the <code>PushBufferStream</code> which gives access to the RTCP data received from the
     * remote targets; <code>null</code> if it does not exist yet and <code>create</code> is <code>false</code>
     * @throws IOException if creating the <code>PushSourceStream</code> fails
     */
    protected RTPConnectorInputStream<?> getControlInputStream(boolean create)
            throws IOException
    {
        if ((controlInputStream == null) && create)
            controlInputStream = createControlInputStream();
        return controlInputStream;
    }

    /**
     * Returns the input stream that is handling outgoing RTCP packets.
     *
     * @return the input stream that is handling outgoing RTCP packets.
     * @throws IOException if an error occurs during the creation of the RTCP packet output stream
     */
    public RTPConnectorOutputStream getControlOutputStream()
            throws IOException
    {
        return getControlOutputStream(true);
    }

    /**
     * Gets the <code>OutputDataStream</code> which is used to write RTCP data to be sent to from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create <code>true</code> to create the <code>OutputDataStream</code> which is to be used to write
     * RTCP data to be sent to the remote targets if it does not exist yet; otherwise, <code>false</code>
     * @return the <code>OutputDataStream</code> which is used to write RTCP data to be sent to the
     * remote targets; <code>null</code> if it does not exist yet and <code>create</code> is <code>false</code>
     * @throws IOException if creating the <code>OutputDataStream</code> fails
     */
    protected RTPConnectorOutputStream getControlOutputStream(boolean create)
            throws IOException
    {
        if ((controlOutputStream == null) && create)
            controlOutputStream = createControlOutputStream();
        return controlOutputStream;
    }

    /**
     * Returns the input stream that is handling incoming RTP packets.
     *
     * @return the input stream that is handling incoming RTP packets.
     * @throws IOException if an error occurs during the creation of the RTP packet input stream
     */
    public RTPConnectorInputStream<?> getDataInputStream()
            throws IOException
    {
        return getDataInputStream(true);
    }

    /**
     * Gets the <code>PushSourceStream</code> which gives access to the RTP data received from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create <code>true</code> to create the <code>PushSourceStream</code> which gives access to the RTP
     * data received from the remote targets if it does not exist yet; otherwise, <code>false</code>
     * @return the <code>PushBufferStream</code> which gives access to the RTP data received from the
     * remote targets; <code>null</code> if it does not exist yet and <code>create</code> is <code>false</code>
     * @throws IOException if creating the <code>PushSourceStream</code> fails
     */
    protected RTPConnectorInputStream<?> getDataInputStream(boolean create)
            throws IOException
    {
        if ((dataInputStream == null) && create)
            dataInputStream = createDataInputStream();
        return dataInputStream;
    }

    /**
     * Returns the input stream that is handling outgoing RTP packets.
     *
     * @return the input stream that is handling outgoing RTP packets.
     * @throws IOException if an error occurs during the creation of the RTP
     */
    public RTPConnectorOutputStream getDataOutputStream()
            throws IOException
    {
        return getDataOutputStream(true);
    }

    /**
     * Gets the <code>OutputDataStream</code> which is used to write RTP data to be sent to from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create <code>true</code> to create the <code>OutputDataStream</code> which is to be used to write RTP
     * data to be sent to the remote targets if it does not exist yet; otherwise, <code>false</code>
     * @return the <code>OutputDataStream</code> which is used to write RTP data to be sent to the
     * remote targets; <code>null</code> if it does not exist yet and <code>create</code> is <code>false</code>
     * @throws IOException if creating the <code>OutputDataStream</code> fails
     */
    public RTPConnectorOutputStream getDataOutputStream(boolean create)
            throws IOException
    {
        if ((dataOutputStream == null) && create)
            dataOutputStream = createDataOutputStream();
        return dataOutputStream;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#getReceiveBufferSize()} that always returns <code>-1</code>.
     */
    public int getReceiveBufferSize()
    {
        // Not applicable
        return -1;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#getRTCPBandwidthFraction()} that
     * always returns <code>-1</code>.
     */
    public double getRTCPBandwidthFraction()
    {
        // Not applicable
        return -1;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#getRTCPSenderBandwidthFraction()} that
     * always returns <code>-1</code>.
     */
    public double getRTCPSenderBandwidthFraction()
    {
        // Not applicable
        return -1;
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#getSendBufferSize()} that always returns <code>-1</code>.
     */
    public int getSendBufferSize()
    {
        // Not applicable
        return -1;
    }

    /**
     * Removes a target from our session. If a target is removed, there will be no data sent to that address.
     *
     * @param target Destination target to be removed
     */
    public void removeTarget(SessionAddress target)
    {
        if (controlOutputStream != null)
            controlOutputStream.removeTarget(target.getControlAddress(), target.getControlPort());

        if (dataOutputStream != null)
            dataOutputStream.removeTarget(target.getDataAddress(), target.getDataPort());
    }

    /**
     * Remove all stream targets. After this operation is done. There will be no targets receiving
     * data, so no data will be sent.
     */
    public void removeTargets()
    {
        if (controlOutputStream != null)
            controlOutputStream.removeTargets();

        if (dataOutputStream != null)
            dataOutputStream.removeTargets();
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#setReceiveBufferSize(int)}.
     *
     * @param size ignored.
     */
    public void setReceiveBufferSize(int size)
            throws IOException
    {
        // Nothing should be done here :-)
    }

    /**
     * Provides a dummy implementation to {@link RTPConnector#setSendBufferSize(int)}.
     *
     * @param size ignored.
     */
    public void setSendBufferSize(int size)
            throws IOException
    {
        // Nothing should be done here :-)
    }

    /**
     * Configures this <code>AbstractRTPConnector</code> to allow RTP in the specified direction. That
     * is, enables/disables the input and output data streams according to <code>direction</code>.
     *
     * Note that the control (RTCP) streams are not affected (they are always kept enabled).
     *
     * @param direction Specifies how to configure the data streams of this <code>AbstractRTPConnector</code>. The
     * input stream will be enabled or disabled depending on whether <code>direction</code>
     * allows receiving. The output stream will be enabled or disabled depending on whether
     * <code>direction</code> allows sending.
     */
    public void setDirection(MediaDirection direction)
    {
        boolean receive = direction.allowsReceiving();
        boolean send = direction.allowsSending();

        Timber.d("setDirection %s", direction);
        try {
            // Forcing the stream to be created causes problems.
            RTPConnectorInputStream<?> dataInputStream = getDataInputStream(false);
            if (dataInputStream != null)
                dataInputStream.setEnabled(receive);
        } catch (IOException ioe) {
            Timber.e("Failed to %s data input stream.", (receive ? "enable" : "disable"));
        }

        try {
            // Forcing the stream to be created causes problems.
            RTPConnectorOutputStream dataOutputStream = getDataOutputStream(false);
            if (dataOutputStream != null)
                dataOutputStream.setEnabled(send);
        } catch (IOException ioe) {
            Timber.e("Failed to %s data output stream.", (send ? "enable" : "disable"));

        }
    }
}
