/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Implements <tt>AbstractExtensionElement</tt> for the <tt>fingerprint</tt> element defined by
 * XEP-0320: Use of DTLS-SRTP in Jingle Sessions.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DtlsFingerprintExtensionElement extends AbstractExtensionElement
{
    /**
     * The XML name of the <tt>fingerprint</tt> element defined by XEP-0320: Use of DTLS-SRTP in
     * Jingle Sessions.
     */
    public static final String ELEMENT = "fingerprint";

    /**
     * The XML name of the <tt>fingerprint</tt> element's attribute which specifies the hash
     * function utilized to calculate the fingerprint.
     */
    private static final String HASH_ATTR_NAME = "hash";

    /**
     * The XML namespace of the <tt>fingerprint</tt> element defined by XEP-0320: Use of DTLS-SRTP
     * in Jingle Sessions.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:dtls:0";

    /**
     * The <tt>required</tt> attribute has been removed in version 0.2 of XEP-0320: Use of DTLS-SRTP
     * in Jingle Sessions.
     */
    private static final String REQUIRED_ATTR_NAME = "required";

    /**
     * The XML name of the <tt>fingerprint</tt> element's attribute which specifies setup role that
     * indicates which of the end points should initiate the connection establishment. Correct
     * values:<br/>
     * <li>
     * 'active': The endpoint will initiate an outgoing connection.</li><li>
     * 'passive': The endpoint will accept an incoming connection.</li><li>
     * 'actpass': The endpoint is willing to accept an incoming connection or to initiate an
     * outgoing connection.</li><li>
     * 'holdconn': The endpoint does not want the connection to be established for the time being.</li>
     */
    private static final String SETUP_ATTR_NAME = "setup";

    /**
     * Initializes a new <tt>DtlsFingerprintExtensionElement</tt> instance.
     */
    public DtlsFingerprintExtensionElement()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Gets the fingerprint carried/represented by this instance.
     *
     * @return the fingerprint carried/represented by this instance
     */
    public String getFingerprint()
    {
        return getText();
    }

    /**
     * Gets the hash function utilized to calculate the fingerprint carried/represented by this instance.
     *
     * @return the hash function utilized to calculate the fingerprint carried/represented by this instance
     */
    public String getHash()
    {
        return getAttributeAsString(HASH_ATTR_NAME);
    }

    /**
     * The <tt>required</tt> attribute has been removed in version 0.2 of XEP-0320: Use of DTLS-SRTP
     * in Jingle Sessions.
     */
    public boolean getRequired()
    {
        String attr = getAttributeAsString(REQUIRED_ATTR_NAME);
        return (attr != null) && Boolean.parseBoolean(attr);
    }

    /**
     * Returns value of 'setup' attribute. See {@link #SETUP_ATTR_NAME} for more info.
     */
    public String getSetup()
    {
        return getAttributeAsString(SETUP_ATTR_NAME);
    }

    /**
     * Sets the fingerprint to be carried/represented by this instance.
     *
     * @param fingerprint the fingerprint to be carried/represented by this instance
     */
    public void setFingerprint(String fingerprint)
    {
        setText(fingerprint);
    }

    /**
     * Sets the hash function utilized to calculate the fingerprint carried/represented by this instance.
     *
     * @param hash the hash function utilized to calculate the fingerprint carried/represented by this instance
     */
    public void setHash(String hash)
    {
        setAttribute(HASH_ATTR_NAME, hash);
    }

    /**
     * The <tt>required</tt> attribute has been removed in version 0.2 of XEP-0320: Use of DTLS-SRTP
     * in Jingle Sessions.
     */
    public void setRequired(boolean required)
    {
        setAttribute(REQUIRED_ATTR_NAME, required);
    }

    /**
     * Sets new value for 'setup' attribute.
     *
     * @param setup see {@link #SETUP_ATTR_NAME} for the list of allowed values.
     */
    public void setSetup(String setup)
    {
        setAttribute(SETUP_ATTR_NAME, setup);
    }
}
