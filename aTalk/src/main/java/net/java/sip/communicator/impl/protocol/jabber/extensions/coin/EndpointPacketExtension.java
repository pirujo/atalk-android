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
 * Endpoint packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class EndpointPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the endpoint data.
     */
    public static final String ELEMENT_NAME = "endpoint";

    /**
     * The namespace that endpoint belongs to.
     */
    public static final String NAMESPACE = null;

    /**
     * Entity attribute name.
     */
    public static final String ENTITY_ATTR_NAME = "entity";

    /**
     * Entity attribute name.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Status element name.
     */
    public static final String ELEMENT_STATUS = "status";

    /**
     * Disconnection element name.
     */
    public static final String ELEMENT_DISCONNECTION = "disconnection-method";

    /**
     * Joining element name.
     */
    public static final String ELEMENT_JOINING = "joining-method";

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Status.
     */
    private EndpointStatusType status = null;

    /**
     * Disconnection type.
     */
    private DisconnectionType disconnectionType = null;

    /**
     * Joining type.
     */
    private JoiningType joiningType = null;

    /**
     * Constructor.
     *
     * @param entity entity
     */
    public EndpointPacketExtension(String entity)
    {
        super(ELEMENT_NAME, NAMESPACE);
        setAttribute("entity", entity);
    }

    /**
     * Set the display text.
     *
     * @param displayText display text
     */
    public void setDisplayText(String displayText)
    {
        this.displayText = displayText;
    }

    /**
     * Set status.
     *
     * @param status status
     */
    public void setStatus(EndpointStatusType status)
    {
        this.status = status;
    }

    /**
     * Set disconnection type.
     *
     * @param disconnectionType disconnection type.
     */
    public void setDisconnectionType(DisconnectionType disconnectionType)
    {
        this.disconnectionType = disconnectionType;
    }

    /**
     * Set joining type.
     *
     * @param joiningType joining type.
     */
    public void setJoiningType(JoiningType joiningType)
    {
        this.joiningType = joiningType;
    }

    /**
     * Get display text.
     *
     * @return display text
     */
    public String getDisplayText()
    {
        return displayText;
    }

    /**
     * Get status.
     *
     * @return status.
     */
    public EndpointStatusType getStatus()
    {
        return status;
    }

    /**
     * Get disconnection type.
     *
     * @return disconnection type.
     */
    public DisconnectionType getDisconnectionType()
    {
        return disconnectionType;
    }

    /**
     * Get joining type.
     *
     * @return joining type.
     */
    public JoiningType getJoiningType()
    {
        return joiningType;
    }

    /**
     * Returns an XML representation of this extension.
     *
     * @return an XML representation of this extension.
     */
    @Override
    public XmlStringBuilder toXML()
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.prelude(getElementName(), getNamespace());

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.append(">");

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_STATUS, status);
        xml.optElement(ELEMENT_DISCONNECTION, disconnectionType);
        xml.optElement(ELEMENT_JOINING, joiningType);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML());
        }
        xml.closeElement(ELEMENT_NAME);
        return xml;
    }
}
