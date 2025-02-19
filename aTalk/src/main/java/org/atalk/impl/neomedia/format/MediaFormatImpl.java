/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format;

import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.service.neomedia.format.AudioMediaFormat;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.util.MediaType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

/**
 * Implements <code>MediaFormat</code> for the JMF <code>Format</code>.
 *
 * @param <T> the type of the wrapped <code>Format</code>
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class MediaFormatImpl<T extends Format>
        implements MediaFormat
{
    /**
     * The name of the <code>clockRate</code> property of <code>MediaFormatImpl</code>.
     */
    public static final String CLOCK_RATE_PNAME = "clockRate";

    /**
     * The value of the <code>formatParameters</code> property of <code>MediaFormatImpl</code> when no
     * codec-specific parameters have been received via SIP/SDP or XMPP/Jingle. Explicitly defined
     * in order to reduce unnecessary allocations.
     */
    static final Map<String, String> EMPTY_FORMAT_PARAMETERS = Collections.emptyMap();

    /**
     * The name of the <code>encoding</code> property of <code>MediaFormatImpl</code>.
     */
    public static final String ENCODING_PNAME = "encoding";

    /**
     * The name of the <code>formatParameters</code> property of <code>MediaFormatImpl</code>.
     */
    public static final String FORMAT_PARAMETERS_PNAME = "fmtps";

    /**
     * The attribute name of the <code>formatParameter</code> property of <code>MediaFormatImpl</code>.
     * Negotiation of Generic Image Attributes in the Session Description Protocol (SDP); https://tools.ietf.org/html/rfc6236
     * 3.2.  Considerations
     * 3.2.1.  No imageattr in First Offer
     * When the initial offer does not contain the 'imageattr' attribute, the rules
     * in Section 3.1.1.2 require the attribute to be absent in the answer.
     */
    public static final String FORMAT_PARAMETER_ATTR_IMAGEATTR = "imageattr";

    /**
     * Creates a new <code>MediaFormat</code> instance for a specific JMF <code>Format</code>.
     *
     * @param format the JMF <code>Format</code> the new instance is to provide an implementation of <code>MediaFormat</code>
     * @return a new <code>MediaFormat</code> instance for the specified JMF <code>Format</code>
     */
    public static MediaFormat createInstance(Format format)
    {
        MediaFormat mediaFormat = MediaUtils.getMediaFormat(format);

        if (mediaFormat == null) {
            if (format instanceof AudioFormat)
                mediaFormat = new AudioMediaFormatImpl((AudioFormat) format);
            else if (format instanceof VideoFormat)
                mediaFormat = new VideoMediaFormatImpl((VideoFormat) format);
        }
        return mediaFormat;
    }

    /**
     * Creates a new <code>MediaFormat</code> instance for a specific JMF <code>Format</code> and
     * assigns its specific clock rate and set of format-specific parameters.
     *
     * @param format the JMF <code>Format</code> the new instance is to provide an implementation of <code>MediaFormat</code>
     * @param clockRate the clock rate of the new instance
     * @param formatParameters the set of format-specific parameters of the new instance
     * @param advancedAttributess advanced attributes of the new instance
     * @return a new <code>MediaFormat</code> instance for the specified JMF <code>Format</code> and with
     * the specified clock rate and set of format-specific parameters
     */
    public static MediaFormatImpl<? extends Format> createInstance(Format format, double clockRate,
            Map<String, String> formatParameters, Map<String, String> advancedAttributess)
    {
        if (format instanceof AudioFormat) {
            AudioFormat audioFormat = (AudioFormat) format;
            AudioFormat clockRateAudioFormat = new AudioFormat(
                    audioFormat.getEncoding(),
                    clockRate,
                    audioFormat.getSampleSizeInBits(),
                    audioFormat.getChannels());

            return new AudioMediaFormatImpl(
                    (AudioFormat) clockRateAudioFormat.intersects(audioFormat),
                    formatParameters,
                    advancedAttributess);
        }
        else if (format instanceof VideoFormat) {
            return new VideoMediaFormatImpl(
                    (VideoFormat) format,
                    clockRate,
                    -1,
                    formatParameters,
                    advancedAttributess);
        }
        else
            return null;
    }

    /**
     * Determines whether a specific set of format parameters is equal to another set of format
     * parameters in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are <code>String</code> s, presumes that a value of <code>null</code> is
     * equal to the empty <code>String</code>.
     * <p>
     * The two <code>Map</code> instances of format parameters to be checked for equality are presumed
     * to be modifiable in the sense that if the lack of a format parameter in a given <code>Map</code>
     * is equivalent to it having a specific value, an association of the format parameter to the
     * value in question may be added to or removed from the respective <code>Map</code> instance for
     * the purposes of determining equality.
     * </p>
     *
     * @param encoding the encoding (name) related to the two sets of format parameters to be tested for equality
     * @param fmtps1 the first set of format parameters to be tested for equality
     * @param fmtps2 the second set of format parameters to be tested for equality
     * @return <code>true</code> if the specified sets of format parameters are equal; <code>false</code>, otherwise
     */
    public static boolean formatParametersAreEqual(String encoding, Map<String, String> fmtps1,
            Map<String, String> fmtps2)
    {
        if (fmtps1 == null)
            return (fmtps2 == null) || fmtps2.isEmpty();
        if (fmtps2 == null)
            return (fmtps1 == null) || fmtps1.isEmpty();
        if (fmtps1.size() == fmtps2.size()) {
            for (Map.Entry<String, String> fmtp1 : fmtps1.entrySet()) {
                String key1 = fmtp1.getKey();

                if (!fmtps2.containsKey(key1))
                    return false;

                String value1 = fmtp1.getValue();
                String value2 = fmtps2.get(key1);

                /*
                 * Since the values are strings, allow null to be equal to the empty string.
                 */
                if ((value1 == null) || (value1.length() == 0)) {
                    if ((value2 != null) && (value2.length() != 0))
                        return false;
                }
                else if (!value1.equals(value2))
                    return false;
            }
            return true;
        }
        else
            return false;
    }

    /**
     * The advanced parameters of this instance which have been received via SIP/SDP or XMPP/Jingle.
     */
    private final Map<String, String> advancedAttributes;

    /**
     * The additional codec settings.
     */
    private Map<String, String> codecSettings = EMPTY_FORMAT_PARAMETERS;

    /**
     * The JMF <code>Format</code> this instance wraps and provides an implementation of <code>MediaFormat</code> for.
     */
    protected final T format;

    /**
     * The codec-specific parameters of this instance which have been received via SIP/SDP or XMPP/Jingle.
     */
    private final Map<String, String> formatParameters;

    /**
     * Initializes a new <code>MediaFormatImpl</code> instance which is to provide an implementation of
     * <code>MediaFormat</code> for a specific <code>Format</code>.
     *
     * @param format the JMF <code>Format</code> the new instance is to provide an implementation of <code>MediaFormat</code>
     */
    protected MediaFormatImpl(T format)
    {
        this(format, null, null);
    }

    /**
     * Initializes a new <code>MediaFormatImpl</code> instance which is to provide an implementation of
     * <code>MediaFormat</code> for a specific <code>Format</code> and which is to have a specific set of
     * codec-specific parameters.
     *
     * @param format the JMF <code>Format</code> the new instance is to provide an implementation of <code>MediaFormat</code>
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttributes any parameters that have been received via SIP/SDP or XMPP/Jingle
     */
    protected MediaFormatImpl(T format, Map<String, String> formatParameters, Map<String, String> advancedAttributes)
    {
        if (format == null)
            throw new NullPointerException("format");

        this.format = format;
        this.formatParameters = ((formatParameters == null) || formatParameters.isEmpty())
                ? EMPTY_FORMAT_PARAMETERS
                : new HashMap<>(formatParameters);
        this.advancedAttributes = ((advancedAttributes == null) || advancedAttributes.isEmpty())
                ? EMPTY_FORMAT_PARAMETERS
                : new HashMap<>(advancedAttributes);
    }

    /**
     * Determines whether a specific set of advanced attributes is equal to another set of advanced
     * attributes in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are <code>String</code>s, presumes that a value of <code>null</code> is
     * equal to the empty <code>String</code>.
     * <p>
     *
     * @param adv the first set of advanced attributes to be tested for equality
     * @param adv2 the second set of advanced attributes to be tested for equality
     * @return <code>true</code> if the specified sets of advanced attributes equal; <code>false</code>, otherwise
     */
    public boolean advancedAttributesAreEqual(Map<String, String> adv, Map<String, String> adv2)
    {
        if (adv == null && adv2 != null || adv != null && adv2 == null)
            return false;

        if (adv == null && adv2 == null)
            return true;

        if (adv.size() != adv2.size())
            return false;

        for (Map.Entry<String, String> a : adv.entrySet()) {
            String value = adv2.get(a.getKey());
            if (value == null)
                return false;
            else if (!value.equals(a.getValue()))
                return false;
        }
        return true;
    }

    /**
     * Implements MediaFormat#equals(Object) and actually compares the encapsulated JMF <code>Format</code> instances.
     *
     * @param mediaFormat the object that we'd like to compare <code>this</code> one to. 8*
     * @return <code>true</code> if the JMF <code>Format</code> instances encapsulated by this class are
     * equal and <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object mediaFormat)
    {
        if (this == mediaFormat)
            return true;

        if (!getClass().isInstance(mediaFormat))
            return false;

        @SuppressWarnings("unchecked")
        MediaFormatImpl<T> mediaFormatImpl = (MediaFormatImpl<T>) mediaFormat;

        return getFormat().equals(mediaFormatImpl.getFormat())
                && formatParametersAreEqual(getFormatParameters(),
                mediaFormatImpl.getFormatParameters());
    }

    /**
     * Determines whether a specific set of format parameters is equal to another set of format
     * parameters in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are <code>String</code> s, presumes that a value of <code>null</code> is
     * equal to the empty <code>String</code>.
     * <p>
     * The two <code>Map</code> instances of format parameters to be checked for equality are presumed
     * to be modifiable in the sense that if the lack of a format parameter in a given <code>Map</code>
     * is equivalent to it having a specific value, an association of the format parameter to the
     * value in question may be added to or removed from the respective <code>Map</code> instance for
     * the purposes of determining equality.
     * </p>
     *
     * @param fmtps1 the first set of format parameters to be tested for equality
     * @param fmtps2 the second set of format parameters to be tested for equality
     * @return <code>true</code> if the specified sets of format parameters are equal; <code>false</code>, otherwise
     */
    protected boolean formatParametersAreEqual(Map<String, String> fmtps1, Map<String, String> fmtps2)
    {
        return formatParametersAreEqual(getEncoding(), fmtps1, fmtps2);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default implementation of <code>MediaFormatImpl</code> always returns <code>true</code> because
     * format parameters in general do not cause the distinction of payload types.
     * </p>
     */
    public boolean formatParametersMatch(Map<String, String> fmtps)
    {
        return true;
    }

    /**
     * Returns additional codec settings.
     *
     * @return additional settings represented by a map.
     */
    public Map<String, String> getAdditionalCodecSettings()
    {
        return codecSettings;
    }

    /**
     * Implements MediaFormat#getAdvancedAttributes(). Returns a copy of the attribute
     * properties of this instance. Modifications to the returned Map do no affect the format
     * properties of this instance.
     *
     * @return a copy of the attribute properties of this instance. Modifications to the returned
     * Map do no affect the format properties of this instance.
     */
    public Map<String, String> getAdvancedAttributes()
    {
        return new HashMap<>(advancedAttributes);
    }

    /**
     * Check to see if advancedAttributes contains the specific parameter name-value pair
     *
     * @param parameterName the key of the <parameter/> name-value pair
     * @return true if the <parameter/> contains the specified key name
     */
    public boolean hasParameter(String parameterName)
    {
        return advancedAttributes.containsKey(parameterName);
    }

    /**
     * Remove the specific parameter name-value pair from advancedAttributes
     *
     * @param parameterName the key of the <parameter/> name-value pair to be removed
     * @see #FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    public void removeParameter(String parameterName)
    {
        advancedAttributes.remove(parameterName);
    }

    /**
     * Returns a <code>String</code> representation of the clock rate associated with this
     * <code>MediaFormat</code> making sure that the value appears as an integer (i.e. its long-casted
     * value is equal to its original one) unless it is actually a non integer.
     *
     * @return a <code>String</code> representation of the clock rate associated with this <code>MediaFormat</code>.
     */
    public String getClockRateString()
    {
        double clockRate = getClockRate();
        long clockRateL = (long) clockRate;

        if (clockRateL == clockRate)
            return Long.toString(clockRateL);
        else
            return Double.toString(clockRate);
    }

    /**
     * Implements MediaFormat#getEncoding() and returns the encoding of the JMF <code>Format</code>
     * that we are encapsulating here but it is the RFC-known encoding and not the internal JMF encoding.
     *
     * @return the RFC-known encoding of the JMF <code>Format</code> that we are encapsulating
     */
    public String getEncoding()
    {
        String jmfEncoding = getJMFEncoding();
        String encoding = MediaUtils.jmfEncodingToEncoding(jmfEncoding);

        if (encoding == null) {
            encoding = jmfEncoding;

            int encodingLength = encoding.length();
            if (encodingLength > 3) {
                int rtpPos = encodingLength - 4;

                if ("/rtp".equalsIgnoreCase(encoding.substring(rtpPos)))
                    encoding = encoding.substring(0, rtpPos);
            }
        }
        return encoding;
    }

    /**
     * Returns the JMF <code>Format</code> instance that we are wrapping here.
     *
     * @return a reference to that JMF <code>Format</code> instance that this class is wrapping.
     */
    public T getFormat()
    {
        return format;
    }

    /**
     * Implements MediaFormat#getFormatParameters(). Returns a copy of the format properties of this instance.
     * Modifications to the returned Map do no affect the format properties of this instance.
     *
     * @return a copy of the format properties of this instance. Modifications to the returned Map
     * do no affect the format properties of this instance.
     */
    public Map<String, String> getFormatParameters()
    {
        return (formatParameters == EMPTY_FORMAT_PARAMETERS)
                ? EMPTY_FORMAT_PARAMETERS
                : new HashMap<String, String>(formatParameters);
    }

    /**
     * Gets the encoding of the JMF <code>Format</code> represented by this instance as it is known to
     * JMF (in contrast to its RFC name).
     *
     * @return the encoding of the JMF <code>Format</code> represented by this instance as it is known
     * to JMF (in contrast to its RFC name)
     */
    public String getJMFEncoding()
    {
        return format.getEncoding();
    }

    /**
     * Returns a <code>String</code> representation of the real used clock rate associated with this
     * <code>MediaFormat</code> making sure that the value appears as an integer (i.e. contains no
     * decimal point) unless it is actually a non integer. This function corrects the problem of
     * the G.722 codec which advertises its clock rate to be 8 kHz while 16 kHz is really used to
     * encode the stream (that's an error noted in the respective RFC and kept for the sake of compatibility.).
     *
     * @return a <code>String</code> representation of the real used clock rate associated with this
     * <code>MediaFormat</code>.
     */
    public String getRealUsedClockRateString()
    {
        // RFC 1890 erroneously assigned 8 kHz to the RTP clock rate for the
        // G722 payload format. The actual sampling rate for G.722 audio is 16 kHz.
        if (this.getEncoding().equalsIgnoreCase("G722")) {
            return "16000";
        }
        return this.getClockRateString();
    }

    /**
     * Gets the RTP payload type (number) of this <code>MediaFormat</code> as it is known in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @return the RTP payload type of this <code>MediaFormat</code> if it is known in RFC 3551 "RTP
     * Profile for Audio and Video Conferences with Minimal Control"; otherwise, {@link #RTP_PAYLOAD_TYPE_UNKNOWN}
     * @see MediaFormat#getRTPPayloadType()
     */
    public byte getRTPPayloadType()
    {
        return MediaUtils.getRTPPayloadType(getJMFEncoding(), getClockRate());
    }

    /**
     * Overrides Object#hashCode() because Object#equals(Object) is overridden.
     *
     * @return a hash code value for this <code>MediaFormat</code>.
     */
    @Override
    public int hashCode()
    {
        /*
         * XXX We've experienced a case of JMF's VideoFormat#hashCode() returning different values
         * for instances which are reported equal by VideoFormat#equals(Object) which is
         * inconsistent with the protocol covering the two methods in question and causes problems,
         * for example, with Map. While jmfEncoding is more generic than format, it still
         * provides a relatively good distribution given that we do not have a lot of instances
         * with one and the same jmfEncoding.
         */
        return getJMFEncoding().hashCode() | getFormatParameters().hashCode();
    }

    /**
     * Determines whether this <code>MediaFormat</code> matches properties of a specific
     * <code>MediaFormat</code>, such as <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> for <code>MediaFormat</code>s with <code>mediaType</code> equal to {@link MediaType#AUDIO}.
     *
     * @param format the {@link MediaFormat} whose properties we'd like to examine and compare with ours.
     */
    public boolean matches(MediaFormat format)
    {
        if (format == null)
            return false;

        MediaType mediaType = format.getMediaType();
        String encoding = format.getEncoding();
        double clockRate = format.getClockRate();
        int channels = MediaType.AUDIO.equals(mediaType)
                ? ((AudioMediaFormat) format).getChannels()
                : MediaFormatFactory.CHANNELS_NOT_SPECIFIED;
        Map<String, String> fmtps = format.getFormatParameters();

        return matches(mediaType, encoding, clockRate, channels, fmtps);
    }

    /**
     * Determines whether this <code>MediaFormat</code> has specific values for its properties
     * <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and <code>channels</code> for
     * <code>MediaFormat</code>s with <code>mediaType</code> equal to {@link MediaType#AUDIO}.
     *
     * @param mediaType the type we expect {@link MediaFormat} to have
     * @param encoding the encoding we are looking for.
     * @param clockRate the clock rate that we'd like the format to have.
     * @param channels the number of channels that expect to find in this format
     * @param fmtps the format parameters expected to match these of the specified <code>format</code>
     * @return <code>true</code> if the specified <code>format</code> has specific values for its
     * properties <code>mediaType</code>, <code>encoding</code>, <code>clockRate</code> and <code>channels</code>;
     * otherwise, <code>false</code>
     */
    public boolean matches(MediaType mediaType, String encoding, double clockRate, int channels,
            Map<String, String> fmtps)
    {
        // mediaType encoding
        if (!getMediaType().equals(mediaType) || !getEncoding().equals(encoding))
            return false;

        // clockRate
        if (clockRate != MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED) {
            double formatClockRate = getClockRate();

            if ((formatClockRate != MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED)
                    && (formatClockRate != clockRate))
                return false;
        }

        // channels
        if (MediaType.AUDIO.equals(mediaType)) {
            if (channels == MediaFormatFactory.CHANNELS_NOT_SPECIFIED)
                channels = 1;

            int formatChannels = ((AudioMediaFormat) this).getChannels();
            if (formatChannels == MediaFormatFactory.CHANNELS_NOT_SPECIFIED)
                formatChannels = 1;
            if (formatChannels != channels)
                return false;
        }
        // formatParameters
        return formatParametersMatch(fmtps);
    }

    /**
     * Sets additional codec settings.
     *
     * @param settings additional settings represented by a map.
     */
    public void setAdditionalCodecSettings(Map<String, String> settings)
    {
        codecSettings = ((settings == null) || settings.isEmpty())
                ? EMPTY_FORMAT_PARAMETERS : settings;
    }

    /**
     * Returns a <code>String</code> representation of this <code>MediaFormat</code> containing, among
     * other things, its encoding and clockrate values.
     *
     * @return a <code>String</code> representation of this <code>MediaFormat</code>.
     */
    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();

        str.append("rtpmap:");
        str.append(getRTPPayloadType());
        str.append(' ');
        str.append(getEncoding());
        str.append('/');
        str.append(getClockRateString());

        /*
         * If the number of channels is 1, it does not have to be mentioned because it is the default.
         */
        if (MediaType.AUDIO.equals(getMediaType())) {
            int channels = ((AudioFormat) getFormat()).getChannels();
            if (channels != 1) {
                str.append('/');
                str.append(channels);
            }
        }

        Map<String, String> formatParameters = getFormatParameters();

        if (!formatParameters.isEmpty()) {
            str.append(" fmtp:");

            boolean prependSeparator = false;
            for (Map.Entry<String, String> formatParameter : formatParameters.entrySet()) {
                if (prependSeparator)
                    str.append(';');
                else
                    prependSeparator = true;
                str.append(formatParameter.getKey());
                str.append('=');
                str.append(formatParameter.getValue());
            }
        }
        return str.toString();
    }
}
