/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording;

import org.atalk.impl.neomedia.transform.*;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.RawPacket;

import java.util.*;

/**
 * A <code>TransformEngine</code> and <code>PacketTransformer</code> which implement a fixed-size buffer.
 * The class is specific to video recording. Buffered are only VP8 RTP packets, and they are places
 * in different buffers according to their SSRC.
 *
 * @author Boris Grozev
 */
public class PacketBuffer implements TransformEngine, PacketTransformer
{
	/**
	 * A <code>Comparator</code> implementation for RTP sequence numbers. Compares the sequence numbers
	 * <code>a</code> and <code>b</code> of <code>pkt1</code> and <code>pkt2</code>, taking into account the wrap at
	 * 2^16.
	 *
	 * IMPORTANT: This is a valid <code>Comparator</code> implementation only if used for subsets of [0,
	 * 2^16) which don't span more than 2^15 elements.
	 *
	 * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000]) Doesn't work for: [0, 2^15]
	 * and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
	 */
	private static final Comparator<? super RawPacket> seqNumComparator
			= new Comparator<RawPacket>()
	{
		@Override
		public int compare(RawPacket pkt1, RawPacket pkt2)
		{
			long a = pkt1.getSequenceNumber();
			long b = pkt2.getSequenceNumber();

			if (a == b)
				return 0;
			else if (a > b) {
				if (a - b < 32768)
					return 1;
				else
					return -1;
			}
			else // a < b
			{
				if (b - a < 32768)
					return -1;
				else
					return 1;
			}
		}
	};

    /**
     * The <code>ConfigurationService</code> used to load buffering configuration.
     */
    private final static ConfigurationService cfg =
            LibJitsi.getConfigurationService();

    /**
	 * The payload type for VP8. TODO: make this configurable.
	 */
	private static int VP8_PAYLOAD_TYPE = 100;

    /**
     * The parameter name for the packet buffer size
     */
    private static final String PACKET_BUFFER_SIZE_PNAME =
            PacketBuffer.class.getCanonicalName() + ".SIZE";
    /**
     * The size of the buffer for each SSRC.
     */
    private static int SIZE = cfg.getInt(PACKET_BUFFER_SIZE_PNAME, 300);

    /**
     * The map of actual <code>Buffer</code> instances, one for each SSRC that this
     * <code>PacketBuffer</code> buffers in each instant.
     */
    private final Map<Long, Buffer> buffers = new HashMap<>();

	/**
	 * Implements {@link PacketTransformer#close()}.
	 */
	@Override
	public void close()
	{

	}

    /**
     * Implements
     * {@link PacketTransformer#reverseTransform(RawPacket[])}.
     *
     * Replaces each packet in the input with a packet (or null) from the
     * <code>Buffer</code> instance for the packet's SSRC.
     *
     * @param pkts the transformed packets to be restored.
     * @return
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        for (int i = 0; i<pkts.length; i++)
        {
            RawPacket pkt = pkts[i];

            // Drop padding packets. We assume that any packets with padding
            // are no-payload probing packets.
            if (pkt != null && pkt.getPaddingSize() != 0)
                pkts[i] = null;
            pkt = pkts[i];

            if (willBuffer(pkt))
            {
                Buffer buffer = getBuffer(pkt.getSSRCAsLong());
                pkts[i] = buffer.insert(pkt);
            }

		}
		return pkts;
	}

	/**
	 * Implements {@link PacketTransformer#transform(RawPacket[])}.
	 */
	@Override
	public RawPacket[] transform(RawPacket[] pkts)
	{
		return pkts;
	}

	/**
	 * Implements {@link TransformEngine#getRTPTransformer()}.
	 */
	@Override
	public PacketTransformer getRTPTransformer()
	{
		return this;
	}

	/**
	 * Implements {@link TransformEngine#getRTCPTransformer()}.
	 */
	@Override
	public PacketTransformer getRTCPTransformer()
	{
		return null;
	}

	/**
	 * Checks whether a particular <code>RawPacket</code> will be buffered or not by this instance.
	 * Currently we only buffer VP8 packets, recognized by their payload type number.
	 * 
	 * @param pkt
	 *        the packet for which to check.
	 * @return
	 */
	private boolean willBuffer(RawPacket pkt)
	{
		return (pkt != null && pkt.getPayloadType() == VP8_PAYLOAD_TYPE);
	}

	/**
	 * Disables the <code>Buffer</code> for a specific SSRC.
	 * 
	 * @param ssrc
	 */
	void disable(long ssrc)
	{
		getBuffer(ssrc).disabled = true;
	}

    /**
     * Resets the buffer for a particular SSRC (effectively re-enabling it if
     * it was disabled).
     * @param ssrc
     */
    void reset(long ssrc)
    {
        synchronized (buffers)
        {
            buffers.remove(ssrc);
        }
    }

    /**
     * Gets the <code>Buffer</code> instance responsible for buffering packets with
     * SSRC <code>ssrc</code>. Creates it if necessary, always returns non-null.
     * @param ssrc the SSRC for which go get a <code>Buffer</code>.
     * @return the <code>Buffer</code> instance responsible for buffering packets with
     * SSRC <code>ssrc</code>. Creates it if necessary, always returns non-null.
     */
    private Buffer getBuffer(long ssrc)
    {
        synchronized (buffers)
        {
            Buffer buffer = buffers.get(ssrc);
            if (buffer == null)
            {
                buffer = new Buffer(SIZE, ssrc);
                buffers.put(ssrc, buffer);
            }
            return buffer;
        }
    }

	/**
	 * Empties the <code>Buffer</code> for a specific SSRC, and returns its contents as an ordered (by
	 * RTP sequence number) array.
	 * 
	 * @param ssrc
	 *        the SSRC for which to empty the <code>Buffer</code>.
	 * @return the contents of the <code>Buffer</code> for SSRC, or an empty array, if there is no
	 *         buffer for SSRC.
	 */
	RawPacket[] emptyBuffer(long ssrc)
    {
        Buffer buffer;
        synchronized (buffers)
        {
            buffer = buffers.get(ssrc);
        }
        if (buffer != null)
        {
            return buffer.empty();
        }

        return new RawPacket[0];
    }

	/**
	 * Represents a buffer for <code>RawPacket</code>s.
	 */
	private static class Buffer
	{
		/**
		 * The actual contents of this <code>Buffer</code>.
		 */
		private final SortedSet<RawPacket> buffer;

		/**
		 * The maximum capacity of this <code>Buffer</code>.
		 */
		private final int capacity;

		/**
		 * The SSRC that this <code>Buffer</code> is associated with.
		 */
		private long ssrc;

		/**
		 * Whether this buffer is disabled or not. If disabled, it will drop incoming packets, and
		 * output 'null'.
		 */
		private boolean disabled = false;

		/**
		 * Constructs a <code>Buffer</code> with the given capacity and SSRC.
		 * 
		 * @param capacity
		 *        the capacity.
		 * @param ssrc
		 *        the SSRC.
		 */
		Buffer(int capacity, long ssrc)
		{
			buffer = new TreeSet<RawPacket>(seqNumComparator);
			this.capacity = capacity;
			this.ssrc = ssrc;
		}

		/**
		 * Inserts a specific <code>RawPacket</code> in this <code>Buffer</code>. If, after the insertion,
		 * the number of elements stored in the buffer is more than <code>this.capacity</code>, removes
		 * from the buffer and returns the 'first' packet in the buffer. Otherwise, return null.
		 *
		 * @param pkt
		 *        the packet to insert.
		 * @return Either the 'first' packet in the buffer, or null, according to whether the buffer
		 *         capacity has been reached after the insertion of <code>pkt</code>.
		 */
		RawPacket insert(RawPacket pkt)
		{
			if (disabled)
				return null;

			RawPacket ret = null;
			synchronized (buffer) {
				buffer.add(pkt);
				if (buffer.size() > capacity) {
					ret = buffer.first();
					buffer.remove(ret);
				}
			}

			return ret;
		}

		/**
		 * Empties this <code>Buffer</code>, returning all its contents.
		 * 
		 * @return the contents of this <code>Buffer</code>.
		 */
		RawPacket[] empty()
		{
			synchronized (buffer) {
				RawPacket[] ret = buffer.toArray(new RawPacket[buffer.size()]);
				buffer.clear();

				return ret;
			}
		}
	}
}
