/**
 *
 * Copyright 2003-2007 Jive Software, 2014-2021 Florian Schmaus
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
package org.jivesoftware.smackx.jingle.element;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IqBuilder;
import org.jivesoftware.smack.packet.IqData;
import org.jivesoftware.smack.packet.id.StandardStanzaIdSource;
import org.jivesoftware.smack.util.Objects;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.jingle_rtp.element.SessionInfo;
import org.jxmpp.jid.FullJid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Jingle element.
 * @see <a href="https://xmpp.org/extensions/xep-0166.html">XEP-0166 Jingle  1.1.2 (2018-09-19)</a>
 * @see <a href="https://xmpp.org/extensions/xep-0166.html">XEP-0167: Jingle RTP Sessions 1.2.1 (2020-09-29)</a>
 *
 * @author Florian Schmaus
 * @author Eng Chong Meng
 */
public final class Jingle extends IQ {
    public static final String ELEMENT = "jingle";

    public static final String NAMESPACE = "urn:xmpp:jingle:1";

    public static final String ATTR_ACTION = "action";

    public static final String ATTR_INITIATOR = "initiator";

    public static final String ATTR_RESPONDER = "responder";

    public static final String ATTR_SESSION_ID = "sid";

    /**
     * The session ID related to this session. The session ID is a unique identifier generated by the initiator. This
     * should match the XML Nmtoken production so that XML character escaping is not needed for characters such as &.
     */
    private final String sessionId;

    /**
     * The jingle action. This attribute is required.
     */
    private final JingleAction action;

    private FullJid initiator;

    /**
     * The full Jid of the entity that replies to a Jingle initiation. The <code>responder</code> can be
     * different from the 'to' address on the IQ-set. Only present when the <code>JingleAction</code> is
     * <code>session-accept</code>.
     */
    private final FullJid responder;

    /**
     * The <code>reason</code> extension in a <code>jingle</code> IQ providers machine and possibly
     * human-readable information about the reason for the action.
     */
    private final JingleReason reason;

    private List<JingleContent> contents;

    /**
     * Any session info extensions that this packet may contain.
     */
    private final SessionInfo sessionInfo;

    private Jingle(Builder builder, String sessionId, JingleAction action, FullJid initiator, FullJid responder,
            JingleReason reason, SessionInfo sessionInfo, List<JingleContent> contents) {
        super(builder, ELEMENT, NAMESPACE);
        this.sessionId = StringUtils.requireNotNullNorEmpty(sessionId, "Jingle session ID must not be null");
        this.action = Objects.requireNonNull(action, "Jingle action must not be null");
        this.initiator = initiator;
        this.responder = responder;
        this.reason = reason;
        this.sessionInfo = sessionInfo;

        if (contents != null) {
            // aTalk needs a modifiableList contents
            // this.contents = Collections.unmodifiableList(contents);
            this.contents = contents;
        } else {
            this.contents = Collections.emptyList();
        }
        setType(Type.set);
    }

    /**
     * Get the initiator. The initiator will be the full JID of the entity that has initiated the flow (which may be
     * different to the "from" address in the IQ)
     *
     * @return the initiator
     */
    public FullJid getInitiator() {
        return initiator;
    }

    /**
     * Get the responder. The responder is the full JID of the entity that has replied to the initiation (which may be
     * different to the "to" address in the IQ).
     *
     * @return the responder
     */
    public FullJid getResponder() {
        return responder;
    }

    /**
     * Returns the session ID related to the session. The session ID is a unique identifier generated by the initiator.
     * This should match the XML Nmtoken production so that XML character escaping is not needed for characters such as
     * &amp;.
     *
     * @return Returns the session ID related to the session.
     */
    public String getSid() {
        return sessionId;
    }

    /**
     * Get the action specified in the jingle IQ.
     *
     * @return the action.
     */
    public JingleAction getAction() {
        return action;
    }

    public JingleReason getReason() {
        return reason;
    }

    /**
     * Returns a {@link SessionInfo} if this <code>Jingle</code> contains one and <code>null</code> otherwise.
     *
     * @return a {@link SessionInfo} if this <code>Jingle</code> contains one and <code>null</code> otherwise.
     */
    public SessionInfo getSessionInfo() {
        return this.sessionInfo;
    }

    /**
     * Get a List of the contents.
     *
     * @return the contents.
     */
    public List<JingleContent> getContents() {
        return contents;
    }

    /**
     * Get the only jingle content if one exists, or <code>null</code>. This method will throw an
     * {@link IllegalStateException} if there is more than one jingle content.
     *
     * @return a JingleContent instance or <code>null</code>.
     * @throws IllegalStateException if there is more than one jingle content.
     */
    public JingleContent getSoleContentOrThrow() {
        if (contents.isEmpty()) {
            return null;
        }

        if (contents.size() > 1) {
            throw new IllegalStateException();
        }

        return contents.get(0);
    }

    /**
     * Returns the XML string of this Jingle IQ's "section" sub-element.
     *
     * Extensions of this class must override this method.
     *
     * @return the child element section of the IQ XML.
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
        xml.optAttribute(ATTR_INITIATOR, getInitiator());
        xml.optAttribute(ATTR_RESPONDER, getResponder());
        xml.optAttribute(ATTR_ACTION, getAction());
        xml.optAttribute(ATTR_SESSION_ID, getSid());
        xml.rightAngleBracket();

        xml.optElement(reason);
        xml.optElement(sessionInfo);
        xml.append(contents);

        return xml;
    }

    /**
     * Sets the full Jid of the entity that has initiated the session flow. Only present when the
     * <code>JingleAction</code> is <code>session-accept</code>.
     *
     * @param initiator the full JID of the initiator.
     */
    public void setInitiator(FullJid initiator) {
        this.initiator = initiator;
    }

    /**
     * Add <code>contentPacket</code> to this Jingle IQ's content list. Use to build up the full Jingle stanza that has
     * both the <description/> and <transport/> elements before the actual processing of the Jingle
     * <code>session_initiate</code> or <code>session-accept</code>.
     *
     * @param content the content packet extension we'd like to add to this element's content list.
     */
    public void addJingleContent(JingleContent content) {
        if (contents == null) {
            contents = new ArrayList<>(1);
        }
        // synchronized (contents) {
        contents.add(content);
        // }
    }

    /**
     * Deprecated, do not use.
     *
     * @return a builder.
     * @deprecated use {@link #builder(XMPPConnection)} instead.
     */
    @Deprecated
    // TODO: Remove in Smack 4.6.
    public static Builder getBuilder() {
        return builder(StandardStanzaIdSource.DEFAULT.getNewStanzaId());
    }

    public static Builder builder(XMPPConnection connection) {
        return new Builder(connection);
    }

    public static Builder builder(IqData iqData) {
        return new Builder(iqData);
    }

    public static Builder builder(String stanzaId) {
        return new Builder(stanzaId);
    }

    public static final class Builder extends IqBuilder<Builder, Jingle> {
        private String sid;

        private JingleAction action;

        private FullJid initiator;

        private FullJid responder;

        private JingleReason reason;

        private SessionInfo sessionInfo;

        private List<JingleContent> contents;

        Builder(IqData iqCommon) {
            super(iqCommon);
        }

        Builder(XMPPConnection connection) {
            super(connection);
        }

        Builder(String stanzaId) {
            super(stanzaId);
        }

        public Builder setSessionId(String sessionId) {
            StringUtils.requireNotNullNorEmpty(sessionId, "Session ID must not be null nor empty");
            this.sid = sessionId;
            return this;
        }

        public Builder setAction(JingleAction action) {
            this.action = action;
            return this;
        }

        /**
         * Sets the full JID of the entity that has initiated the session flow. Only present when the
         * <code>JingleAction</code> is <code>session-accept</code>.
         *
         * @param initiator the full JID of the initiator.
         * @return builder instance
         */
        public Builder setInitiator(FullJid initiator) {
            this.initiator = initiator;
            return this;
        }

        public Builder setResponder(FullJid responder) {
            this.responder = responder;
            return this;
        }

        /**
         * Adds <code>contentPacket</code> to this IQ's content list.
         *
         * @param content the content packet extension we'd like to add to this element's content list.
         * @return builder instance
         */
        public Builder addJingleContent(JingleContent content) {
            if (contents == null) {
                contents = new ArrayList<>(1);
            }
            contents.add(content);
            return this;
        }

        /**
         * Specifies this IQ's <code>reason</code> extension. The <code>reason</code> extension in a <code>jingle</code> IQ
         * provides machine and possibly human -readable information about the reason for the action.
         *
         * @param reason this IQ's <code>reason</code> extension.
         * @return builder instance
         */
        public Builder setReason(JingleReason.Reason reason) {
            this.reason = new JingleReason(reason);
            return this;
        }

        /**
         * Specifies this IQ's <code>JingleReason</code> extension. The <code>JingleReason</code> extension in a <code>jingle</code> IQ
         * provides machine and possibly human -readable information about the reason for the action.
         *
         * @param reason this IQ's <code>JingleReason</code> extension.
         * @return builder instance
         */
        public Builder setReason(JingleReason reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets <code>si</code> as the session info extension for this packet.
         *
         * @param si a {@link SessionInfo} that we'd like to add here.
         * @return builder instance
         */
        public Builder setSessionInfo(SessionInfo si) {
            this.sessionInfo = si;
            return this;
        }

        @Override
        public Jingle build() {
            return new Jingle(this, sid, action, initiator, responder, reason, sessionInfo, contents);
        }

        @Override
        public Builder getThis() {
            return this;
        }
    }
}
