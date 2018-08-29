/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Map;

/**
 * State packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class StatePacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the state data.
     */
    public static final String ELEMENT_NAME = "conference-state";

    /**
     * The namespace that state belongs to.
     */
    public static final String NAMESPACE = null;

    /**
     * Users count element name.
     */
    public static final String ELEMENT_USER_COUNT = "user-count";

    /**
     * Active element name.
     */
    public static final String ELEMENT_ACTIVE = "active";

    /**
     * Locked element name.
     */
    public static final String ELEMENT_LOCKED = "locked";

    /**
     * User count.
     */
    private int userCount = 0;

    /**
     * Active state.
     */
    private int active = -1;

    /**
     * Locked state.
     */
    private int locked = -1;

    /**
     * Constructor.
     */
    public StatePacketExtension()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Set the user count.
     *
     * @param userCount user count
     */
    public void setUserCount(int userCount)
    {
        this.userCount = userCount;
    }

    /**
     * Set the active state.
     *
     * @param active state
     */
    public void setActive(int active)
    {
        this.active = active;
    }

    /**
     * Set the locked state.
     *
     * @param locked locked state
     */
    public void setLocked(int locked)
    {
        this.locked = locked;
    }

    /**
     * Get the user count.
     *
     * @return user count
     */
    public int getUserCount()
    {
        return userCount;
    }

    /**
     * Get the active state.
     *
     * @return active state
     */
    public int getActive()
    {
        return active;
    }

    /**
     * Get the locked state.
     *
     * @return locked state
     */
    public int getLocked()
    {
        return locked;
    }

    /**
     * Get an XML string representation.
     *
     * @return XML string representation
     */
    @Override
    public XmlStringBuilder toXML(String enclosingNamespace)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.prelude(getElementName(), getNamespace());

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.append(">");

        // XEP-0298 version 0.2 2015-07-02
        // <xs:complexType name="conference-state-type">
        // <xs:sequence>
        // <xs:element name="user-count" type="xs:unsignedInt"
        // minOccurs="0"/>
        // <xs:element name="active" type="xs:boolean"
        // minOccurs="0"/>
        // <xs:element name="locked" type="xs:boolean"
        // minOccurs="0"/>
        // <xs:any namespace="##other" processContents="lax"
        // minOccurs="0" maxOccurs="unbounded"/>
        // </xs:sequence>
        // <xs:anyAttribute namespace="##other" processContents="lax"/>
        // </xs:complexType>

        // cmeng? - does not confirm to XEP-0298
        if (userCount != 0)
            xml.optIntElement(ELEMENT_USER_COUNT, userCount);

        if (active != -1)
            xml.optElement(ELEMENT_ACTIVE, Boolean.toString(active > 0));

        if (locked != -1)
            xml.optElement(ELEMENT_LOCKED, Boolean.toString(locked > 0));

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(null));
        }
        xml.closeElement(getElementName());
        return xml;
    }
}
