/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.video.h264;

import net.sf.fmj.media.AbstractCodec;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.util.Logger;

import java.util.*;

import javax.media.*;
import javax.media.format.VideoFormat;

import static org.atalk.impl.neomedia.codec.video.h264.H264.NAL_PREFIX;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kFuA;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kFuAHeaderSize;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kIdr;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kNalHeaderSize;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kPps;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kSei;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kSps;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kStapA;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kStapAHeaderSize;
import static org.atalk.impl.neomedia.codec.video.h264.H264.kTypeMask;
import static org.atalk.impl.neomedia.codec.video.h264.H264.verifyStapANaluLengths;

/**
 * Implements <tt>Codec</tt> to represent a depacketizer of H.264 RTP packets into
 * Network Abstraction Layer (NAL) units.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class DePacketizer extends AbstractCodec2
{
    /**
     * The <tt>Logger</tt> used by the <tt>DePacketizer</tt> class and its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DePacketizer.class);

    /**
     * The indicator which determines whether incomplete NAL units are output
     * from the H.264 <tt>DePacketizer</tt> to the decoder. It is advisable to
     * output incomplete NAL units because the FFmpeg H.264 decoder is able to
     * decode them. If <tt>false</tt>, incomplete NAL units will be discarded
     * and, consequently, the video quality will be worse (e.g. if the last RTP
     * packet of a fragmented NAL unit carrying a keyframe does not arrive from
     * the network, the whole keyframe will be discarded and thus all NAL units
     * up to the next keyframe will be useless).
     */
    private static final boolean OUTPUT_INCOMPLETE_NAL_UNITS = true;

    /**
     * The interval of time in milliseconds between two consecutive requests for a key frame from
     * the remote peer associated with the {@link #keyFrameControl} of <tt>DePacketizer</tt>.
     */
    private static final long TIME_BETWEEN_REQUEST_KEY_FRAME = 500;

    /**
     * The interval of time in milliseconds from the time of the last received
     * key frame to the time at which a key frame will be requested from the
     * remote peer associated with the {@link #keyFrameControl} of
     * <tt>DePacketizer</tt>. The value at the time of this writing is the
     * default time between two consecutive key frames generated by
     * {@link JNIDecoder} with an addition of a certain fraction of that time in
     * the role of a leeway to prevent <tt>DePacketizer</tt> from requesting key
     * frames from <tt>JNIEncoder</tt> in the scenario of perfect transmission.
     */
    private static final long TIME_FROM_KEY_FRAME_TO_REQUEST_KEY_FRAME
            = ((JNIEncoder.DEFAULT_KEYINT * 4L) / (JNIEncoder.DEFAULT_FRAME_RATE * 3L)) * 1000L;

    /**
     * The Unspecified <tt>nal_unit_type</tt> as defined by the ITU-T Recommendation for H.264.
     */
    private static final int UNSPECIFIED_NAL_UNIT_TYPE = 0;

    /**
     * The indicator which determines whether this <tt>DePacketizer</tt> has successfully
     * processed an RTP packet with payload representing a "Fragmentation Unit (FU)" with its
     * Start bit set and has not encountered one with its End bit set.
     */
    private boolean fuaStartedAndNotEnded = false;

    /**
     * The <tt>KeyFrameControl</tt> used by this <tt>DePacketizer</tt> to control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * The time stamp of the last received key frame.
     */
    private long lastKeyFrameTime = -1;

    /**
     * The time of the last request for a key frame from the remote peer associated with
     * {@link #keyFrameControl} performed by this <tt>DePacketizer</tt>.
     */
    private long lastRequestKeyFrameTime = -1;

    /**
     * Keeps track of last (input) sequence number in order to avoid inconsistent data.
     */
    private long lastSequenceNumber = -1;

    /**
     * The <tt>nal_unit_type</tt> as defined by the ITU-T Recommendation for H.264 of the last
     * NAL unit given to this <tt>DePacketizer</tt> for processing. In the case of processing a
     * fragmentation unit, the value is equal to the <tt>nal_unit_type</tt> of the fragmented NAL unit.
     */
    private int nal_unit_type;

    /**
     * The size of the padding at the end of the output data of this <tt>DePacketizer</tt>
     * expected by the H.264 decoder.
     */
    private final int outputPaddingSize = FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE;

    /**
     * The indicator which determines whether this <tt>DePacketizer</tt> is to request a key
     * frame from the remote peer associated with {@link #keyFrameControl}.
     */
    private boolean requestKeyFrame = false;

    /**
     * The <tt>Thread</tt> which is to asynchronously request key frames from
     * the remote peer associated with {@link #keyFrameControl} on behalf of
     * this <tt>DePacketizer</tt> and in accord with {@link #requestKeyFrame}.
     */
    private Thread requestKeyFrameThread;

    /**
     * Initializes a new <tt>DePacketizer</tt> instance which is to depacketize H.264 RTP packets into NAL units.
     */
    public DePacketizer()
    {
        super("H264 DePacketizer",
                VideoFormat.class,
                new VideoFormat[]{new VideoFormat(Constants.H264)});

        List<Format> inputFormats = new ArrayList<>();
        inputFormats.add(new VideoFormat(Constants.H264_RTP));
        /*
         * Apart from the generic Constants.H264_RTP VideoFormat, add the possible respective
         * ParameterizedVideoFormats because ParameterizedVideoFormat will not match every
         * VideoFormat due to the fact that a missing packetization-mode format parameter is
         * interpreted as it having a value of 0.
         */
        Collections.addAll(inputFormats, Packetizer.SUPPORTED_OUTPUT_FORMATS);
        this.inputFormats = inputFormats.toArray(EMPTY_FORMATS);
    }

    /**
     * Extracts a fragment of a NAL unit from a specific FU-A RTP packet payload.
     *
     * @param in the payload of the RTP packet from which a FU-A fragment of a NAL unit is to be extracted
     * @param inOffset the offset in <tt>in</tt> at which the payload begins
     * @param inLength the length of the payload in <tt>in</tt> beginning at <tt>inOffset</tt>
     * @param outBuffer the <tt>Buffer</tt> which is to receive the extracted FU-A fragment of a NAL unit
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by {@link #process(Buffer, Buffer)}
     */
    private int dePacketizeFUA(byte[] in, int inOffset, int inLength, Buffer outBuffer)
    {
        byte fu_indicator = in[inOffset];
        inOffset++;
        inLength--;

        byte fu_header = in[inOffset];
        inOffset++;
        inLength--;

        int nal_unit_type = fu_header & 0x1F;
        this.nal_unit_type = nal_unit_type;

        boolean start_bit = (fu_header & 0x80) != 0;
        boolean end_bit = (fu_header & 0x40) != 0;
        int outOffset = outBuffer.getOffset();
        int newOutLength = inLength;
        int octet;

        if (start_bit) {
            /*
             * The Start bit and End bit MUST NOT both be set in the same FU header.
             */
            if (end_bit) {
                outBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            fuaStartedAndNotEnded = true;
            newOutLength += NAL_PREFIX.length + 1 /* octet */;
            octet = (fu_indicator & 0xE0) /* forbidden_zero_bit & NRI */
                    | nal_unit_type;
        }
        else if (!fuaStartedAndNotEnded) {
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        else {
            int outLength = outBuffer.getLength();
            outOffset += outLength;
            newOutLength += outLength;
            octet = 0; // Ignored later on.
        }

        byte[] out = validateByteArraySize(
                outBuffer, outBuffer.getOffset() + newOutLength + outputPaddingSize, true);

        if (start_bit) {
            // Copy in the NAL start sequence and the (reconstructed) octet.
            System.arraycopy(NAL_PREFIX, 0, out, outOffset, NAL_PREFIX.length);
            outOffset += NAL_PREFIX.length;

            out[outOffset] = (byte) (octet & 0xFF);
            outOffset++;
        }
        System.arraycopy(in, inOffset, out, outOffset, inLength);
        outOffset += inLength;

        padOutput(out, outOffset);
        outBuffer.setLength(newOutLength);

        if (end_bit) {
            fuaStartedAndNotEnded = false;
            return BUFFER_PROCESSED_OK;
        }
        else
            return OUTPUT_BUFFER_NOT_FILLED;
    }

    /**
     * Extract a single (complete) NAL unit from RTP payload.
     *
     * @param nal_unit_type unit type of NAL
     * @param in the payload of the RTP packet
     * @param inOffset the offset in <tt>in</tt> at which the payload begins
     * @param inLength the length of the payload in <tt>in</tt> beginning at <tt>inOffset</tt>
     * @param outBuffer the <tt>Buffer</tt> which is to receive the extracted NAL unit
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by {@link #process(Buffer, Buffer)}
     */
    private int dePacketizeSingleNALUnitPacket(
            int nal_unit_type, byte[] in, int inOffset, int inLength, Buffer outBuffer)
    {
        this.nal_unit_type = nal_unit_type;

        int outOffset = outBuffer.getOffset();
        int newOutLength = NAL_PREFIX.length + inLength;
        byte[] out = validateByteArraySize(
                outBuffer, outOffset + newOutLength + outputPaddingSize, true);

        System.arraycopy(NAL_PREFIX, 0, out, outOffset, NAL_PREFIX.length);
        outOffset += NAL_PREFIX.length;

        System.arraycopy(in, inOffset, out, outOffset, inLength);
        outOffset += inLength;

        padOutput(out, outOffset);
        outBuffer.setLength(newOutLength);
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Close the <tt>Codec</tt>.
     */
    @Override
    protected synchronized void doClose()
    {
        // If requestKeyFrameThread is running, tell it to perish.
        requestKeyFrameThread = null;
        notifyAll();
    }

    /**
     * Opens this <tt>Codec</tt> and acquires the resources that it needs to
     * operate. A call to {@link PlugIn#open()} on this instance will result in
     * a call to <tt>doOpen</tt> only if {@link AbstractCodec#opened} is
     * <tt>false</tt>. All required input and/or output formats are assumed to
     * have been set on this <tt>Codec</tt> before <tt>doOpen</tt> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <tt>Codec</tt> needs to operate cannot be acquired
     */
    @Override
    protected synchronized void doOpen()
            throws ResourceUnavailableException
    {
        fuaStartedAndNotEnded = false;
        lastKeyFrameTime = -1;
        lastRequestKeyFrameTime = -1;
        lastSequenceNumber = -1;
        nal_unit_type = UNSPECIFIED_NAL_UNIT_TYPE;
        requestKeyFrame = false;
        requestKeyFrameThread = null;
    }

    /**
     * Processes (depacketizes) a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully processed
     */
    @Override
    @SuppressWarnings("fallthrough")
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        /*
         * We'll only be depacketizing, we'll not act as an H.264 parser. Consequently, we'll
         * only care about the rules of packetizing/depacketizing. For example, we'll have to
         * make sure that no packets are lost and no other packets are received when
         * depacketizing FU-A Fragmentation Units (FUs).
         */
        long sequenceNumber = inBuffer.getSequenceNumber();
        int ret;
        boolean requestKeyFrame = (lastKeyFrameTime == -1);

        if ((lastSequenceNumber != -1)
                && ((sequenceNumber - lastSequenceNumber) != 1)) {
            /*
             * Even if (the new) sequenceNumber is less than lastSequenceNumber,
             * we have to use it because the received sequence numbers may have
             * reached their maximum value and wrapped around starting from
             * their minimum value again.
             */
            if (logger.isTraceEnabled())
                logger.trace("Dropped RTP packets up to sequenceNumber " + lastSequenceNumber
                        + " and continuing with sequenceNumber " + sequenceNumber);

            /*
             * If a frame has been lost, then we may be in a need of a key frame.
             */
            requestKeyFrame = true;
            ret = reset(outBuffer);
            if ((ret & OUTPUT_BUFFER_NOT_FILLED) == 0) {
                /*
                 * TODO Do we have to reset the nal_unit_type field of this
                 * DePacketizer to UNSPECIFIED_NAL_UNIT_TYPE here? If ret contains
                 * INPUT_BUFFER_NOT_CONSUMED, it seems safe to not reset it because the input
                 * Buffer will be returned for processing during the next call.
                 */
                setRequestKeyFrame(requestKeyFrame);
                return ret;
            }
        }

        /*
         * Ignore the RTP time stamp reported by JMF because it is not the actual RTP packet time
         * stamp send by the remote peer but some locally calculated JMF value.
         */
        lastSequenceNumber = sequenceNumber;
        byte[] in = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();
        byte octet = in[inOffset];

        /*
         * NRI equal to the binary value 00 indicates that the content of the NAL unit is not
         * used to reconstruct reference pictures for inter picture prediction. Such NAL units
         * can be discarded without risking the integrity of the reference pictures. However, it
         * is not the place of the DePacketizer to take the decision to discard them but of the
         * H.264 decoder.
         */

        /*
         * The nal_unit_type of the NAL unit given to this DePacketizer for processing. In the
         * case of processing a fragmentation unit, the value is equal to the nal_unit_type of
         * the fragmentation unit, not the fragmented NAL unit and is thus in contrast with the
         * value of the nal_unit_type field of this DePacketizer.
         */
        int nal_unit_type = octet & 0x1F;

        // Single NAL Unit Packet
        if ((nal_unit_type >= 1) && (nal_unit_type <= 23)) {
            fuaStartedAndNotEnded = false;
            ret = dePacketizeSingleNALUnitPacket(nal_unit_type, in, inOffset, inBuffer.getLength(), outBuffer);
        }
        else if (nal_unit_type == 28) { // FU-A Fragmentation unit (FU)
            ret = dePacketizeFUA(in, inOffset, inBuffer.getLength(), outBuffer);
            if (outBuffer.isDiscard())
                fuaStartedAndNotEnded = false;
        }
        else {
            logger.warn("Dropping NAL unit of unsupported type " + nal_unit_type);
            this.nal_unit_type = nal_unit_type;

            fuaStartedAndNotEnded = false;
            outBuffer.setDiscard(true);
            ret = BUFFER_PROCESSED_OK;
        }
        outBuffer.setSequenceNumber(sequenceNumber);

        /*
         * The RTP marker bit is set for the very last packet of the access unit indicated by the
         * RTP time stamp to allow an efficient playout buffer handling. Consequently, we have
         * to output it as well.
         */
        if ((inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
            outBuffer.setFlags(outBuffer.getFlags() | Buffer.FLAG_RTP_MARKER);

        // Should we request a key frame.
        switch (this.nal_unit_type) {
            case 5 /* Coded slice of an IDR picture */:
                lastKeyFrameTime = System.currentTimeMillis();
                // Do fall through to prevent the request of a key frame.

                /*
                 * While it seems natural to not request a key frame in the presence of
                 * 5, 7 and 8 often seem to be followed by 5 so do not request a key
                 * frame if either 7 or 8 is present.
                 */
            case 7 /* Sequence parameter set */:
            case 8 /* Picture parameter set */:
                requestKeyFrame = false;
                break;
            default:
                break;
        }
        setRequestKeyFrame(requestKeyFrame);
        return ret;
    }

    /**
     * Returns true if the buffer contains a H264 key frame at offset <tt>offset</tt>.
     *
     * @param buf the byte buffer to check
     * @param off the offset in the byte buffer where the actual data starts
     * @param len the length of the data in the byte buffer
     * @return true if the buffer contains a H264 key frame at offset <tt>offset</tt>.
     */
    public static boolean isKeyFrame(byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + Math.max(len, 1)) {
            return false;
        }

        int nalType = buf[off] & kTypeMask;
        // Single NAL Unit Packet
        if (nalType == kFuA) {
            // Fragmented NAL units (FU-A).
            return parseFuaNaluForKeyFrame(buf, off, len);
        }
        else {
            return parseSingleNaluForKeyFrame(buf, off, len);
        }
    }

    /**
     * Checks if a a fragment of a NAL unit from a specific FU-A RTP packet payload is keyframe or not
     */
    private static boolean parseFuaNaluForKeyFrame(byte[] buf, int off, int len)
    {
        if (len < kFuAHeaderSize) {
            return false;
        }
        return ((buf[off + 1] & kTypeMask) == kIdr);
    }

    /**
     * Checks if a a fragment of a NAL unit from a specific FU-A RTP packet payload is keyframe or not
     */
    private static boolean parseSingleNaluForKeyFrame(byte[] buf, int off, int len)
    {
        int naluStart = off + kNalHeaderSize;
        int naluLength = len - kNalHeaderSize;
        int nalType = buf[off] & kTypeMask;
        if (nalType == kStapA) {
            // Skip the StapA header (StapA nal type + length).
            if (len <= kStapAHeaderSize) {
                logger.error("StapA header truncated.");
                return false;
            }
            if (!verifyStapANaluLengths(buf, naluStart, naluLength)) {
                logger.error("StapA packet with incorrect NALU packet lengths.");
                return false;
            }
            nalType = buf[off + kStapAHeaderSize] & kTypeMask;
        }
        return (nalType == kIdr || nalType == kSps ||
                nalType == kPps || nalType == kSei);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Makes sure that the format parameters of a <tt>ParameterizedVideoFormat</tt> input which
     * are of no concern to this <tt>DePacketizer</tt> get passed on through the output to the next
     * <tt>Codec</tt> in the codec chain (i.e. <tt>JNIDecoder</tt>).
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        Format[] matchingOutputFormats = super.getMatchingOutputFormats(inputFormat);

        if ((matchingOutputFormats != null)
                && (matchingOutputFormats.length != 0)
                && (inputFormat instanceof ParameterizedVideoFormat)) {
            Map<String, String> fmtps = ((ParameterizedVideoFormat) inputFormat).getFormatParameters();

            if (fmtps != null) {
                fmtps.remove(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP);
                if (!fmtps.isEmpty()) {
                    for (int i = 0; i < matchingOutputFormats.length; i++) {
                        matchingOutputFormats[i] = new ParameterizedVideoFormat(Constants.H264, fmtps);
                    }
                }
            }
        }
        return matchingOutputFormats;
    }

    /**
     * Appends {@link #outputPaddingSize} number of bytes to <tt>out</tt>
     * beginning at index <tt>outOffset</tt>. The specified <tt>out</tt> is
     * expected to be large enough to accommodate the mentioned number of bytes.
     *
     * @param out the buffer in which <tt>outputPaddingSize</tt> number of bytes are to be written
     * @param outOffset the index in <tt>outOffset</tt> at which the writing of
     * <tt>outputPaddingSize</tt> number of bytes is to begin
     */
    private void padOutput(byte[] out, int outOffset)
    {
        Arrays.fill(out, outOffset, outOffset + outputPaddingSize, (byte) 0);
    }

    /**
     * Requests a key frame from the remote peer associated with this
     * <tt>DePacketizer</tt> using the logic of <tt>DePacketizer</tt>.
     *
     * @param urgent <tt>true</tt> if the caller has determined that the need
     * for a key frame is urgent and should not obey all constraints with
     * respect to time between two subsequent requests for key frames
     * @return <tt>true</tt> if a key frame was indeed requested in response to
     * the call; otherwise, <tt>false</tt>
     */
    public synchronized boolean requestKeyFrame(boolean urgent)
    {
        lastKeyFrameTime = -1;
        setRequestKeyFrame(true);
        return true;
    }

    /**
     * Resets the states of this <tt>DePacketizer</tt> and a specific output
     * <tt>Buffer</tt> so that they are ready to have this <tt>DePacketizer</tt>
     * process input RTP payloads. If the specified output <tt>Buffer</tt>
     * contains an incomplete NAL unit, its forbidden_zero_bit will be turned on
     * and the NAL unit in question will be output by this <tt>DePacketizer</tt>.
     *
     * @param outBuffer the output <tt>Buffer</tt> to be reset
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by
     * {@link #process(Buffer, Buffer)}
     */
    private int reset(Buffer outBuffer)
    {
        /*
         * We need the octet at the very least. Additionally, it does not make
         * sense to output a NAL unit with zero payload because such NAL units
         * are only given meaning for the purposes of the network and not the H.264 decoder.
         */
        if (OUTPUT_INCOMPLETE_NAL_UNITS
                && fuaStartedAndNotEnded
                && (outBuffer.getLength() >= (NAL_PREFIX.length + 1 + 1))) {
            Object outData = outBuffer.getData();

            if (outData instanceof byte[]) {
                byte[] out = (byte[]) outData;
                int octetIndex = outBuffer.getOffset() + NAL_PREFIX.length;

                out[octetIndex] |= 0x80; // Turn on the forbidden_zero_bit.
                fuaStartedAndNotEnded = false;
                return (BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED);
            }
        }
        fuaStartedAndNotEnded = false;
        outBuffer.setLength(0);
        return OUTPUT_BUFFER_NOT_FILLED;
    }

    /**
     * Requests key frames from the remote peer associated with
     * {@link #keyFrameControl} in {@link #requestKeyFrameThread}.
     */
    private void runInRequestKeyFrameThread()
    {
        while (true) {
            synchronized (this) {
                if (requestKeyFrameThread != Thread.currentThread())
                    break;

                long now = System.currentTimeMillis();
                long timeout;

                if (requestKeyFrame) {
                    /*
                     * If we have received at least one key frame, we may
                     * receive a new one later. So allow a certain amount of
                     * time for the new key frame to arrive without DePacketizer
                     * requesting it.
                     */
                    long nextKeyFrameTime = lastKeyFrameTime + TIME_FROM_KEY_FRAME_TO_REQUEST_KEY_FRAME;

                    if (now >= nextKeyFrameTime) {
                        /*
                         * In order to not have the requests for key frames
                         * overwhelm the remote peer, make sure two consecutive
                         * requests are separated by a certain amount of time.
                         */
                        long nextRequestKeyFrameTime = lastRequestKeyFrameTime + TIME_BETWEEN_REQUEST_KEY_FRAME;

                        if (now >= nextRequestKeyFrameTime) {
                            // Request a key frame from the remote peer now.
                            timeout = -1;
                        }
                        else {
                            /*
                             * Too little time has passed from our last attempt
                             * to request a key frame from the remote peer. If
                             * we do not wait, we risk intruding.
                             */
                            timeout = nextRequestKeyFrameTime - now;
                        }
                    }
                    else {
                        /*
                         * Too little time has passed from the last receipt of a
                         * key frame to make us think that the remote peer will
                         * not send a key frame without us requesting it.
                         */
                        timeout = nextKeyFrameTime - now;
                    }
                }
                else {
                    /*
                     * This DePacketizer has not expressed its desire to request
                     * a key frame from the remote peer so we will have to wait
                     * until it expresses the desire in question.
                     */
                    timeout = 0;
                }

                if (timeout >= 0) {
                    try {
                        wait(timeout);
                    } catch (InterruptedException ie) {
                    }
                    continue;
                }
            }

            KeyFrameControl keyFrameControl = this.keyFrameControl;

            if (keyFrameControl != null) {
                List<KeyFrameControl.KeyFrameRequester> keyFrameRequesters
                        = keyFrameControl.getKeyFrameRequesters();

                if (keyFrameRequesters != null) {
                    for (KeyFrameControl.KeyFrameRequester keyFrameRequester : keyFrameRequesters) {
                        try {
                            if (keyFrameRequester.requestKeyFrame())
                                break;
                        } catch (Exception e) {
                            /*
                             * A KeyFrameRequester has malfunctioned, do not let it interfere with the others.
                             */
                        }
                    }
                }
            }
            lastRequestKeyFrameTime = System.currentTimeMillis();
        }
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this <tt>DePacketizer</tt> as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this <tt>DePacketizer</tt>
     * as a means of control over its key frame-related logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        this.keyFrameControl = keyFrameControl;
    }

    /**
     * Sets the indicator which determines whether this <tt>DePacketizer</tt> is to request a key
     * frame from the remote peer associated with {@link #keyFrameControl}.
     *
     * @param requestKeyFrame <tt>true</tt> if this <tt>DePacketizer</tt> is to request a key frame
     * from the remote peer associated with {@link #keyFrameControl}
     */
    private synchronized void setRequestKeyFrame(boolean requestKeyFrame)
    {
        if (this.requestKeyFrame != requestKeyFrame) {
            this.requestKeyFrame = requestKeyFrame;

            if ((this.requestKeyFrame) && (requestKeyFrameThread == null)) {
                requestKeyFrameThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        try {
                            runInRequestKeyFrameThread();
                        } finally {
                            synchronized (DePacketizer.this) {
                                if (requestKeyFrameThread == Thread.currentThread())
                                    requestKeyFrameThread = null;
                            }
                        }
                    }
                };
                requestKeyFrameThread.start();
            }
            notifyAll();
        }
    }
}
