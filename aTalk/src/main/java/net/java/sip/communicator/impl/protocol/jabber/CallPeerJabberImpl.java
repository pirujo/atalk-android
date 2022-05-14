/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractConferenceMember;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.ConferenceMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.JingleMessageSessionImpl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.coin.CoinExtension;
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContent.Senders;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle.element.JingleReason.Reason;
import org.jivesoftware.smackx.jingle_rtp.JingleCallSessionImpl;
import org.jivesoftware.smackx.jingle_rtp.JingleUtils;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSource;
import org.jivesoftware.smackx.jingle_rtp.element.SdpTransfer;
import org.jivesoftware.smackx.jingle_rtp.element.SdpTransferred;
import org.jivesoftware.smackx.jingle_rtp.element.SessionInfo;
import org.jivesoftware.smackx.jingle_rtp.element.SessionInfoType;
import org.jivesoftware.smackx.jitsimeet.SSRCInfoExtension;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

/**
 * Implements a Jabber <code>CallPeer</code>.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */

public class CallPeerJabberImpl
        extends MediaAwareCallPeer<CallJabberImpl, CallPeerMediaHandlerJabberImpl, ProtocolProviderServiceJabberImpl>
{
    /**
     * If the call is cancelled before session-initiate is sent.
     */
    private boolean cancelled = false;

    /**
     * Synchronization object for candidates available.
     */
    private final Object candSyncRoot = new Object();

    /**
     * If the content-add does not contain candidates.
     */
    private boolean contentAddWithNoCands = false;

    /**
     * If we have processed the session initiate.
     */
    private boolean sessionInitiateProcessed = false;

    /**
     * If we have processed the session accept.
     */
    private boolean sessionAcceptProcessed = false;

    /**
     * Synchronization object. Jingle transport-info processes are hold in waiting state until
     * session-initiate is completed (notifyAll).
     */
    private final Object sessionInitiateSyncRoot = new Object();

    /**
     * Synchronization object for SID.
     */
    private final Object sidSyncRoot = new Object();

    /**
     * The current value of the 'senders' field of the audio content in the Jingle session with this
     * <code>CallPeer</code>. <code>null</code> should be interpreted as 'both', which is the default in
     * Jingle if the XML attribute is missing.
     */
    private Senders audioSenders = Senders.none;

    /**
     * The current value of the 'senders' field of the video content in the Jingle session with this
     * <code>CallPeer</code>. <code>null</code> should be interpreted as 'both', which is the default in
     * Jingle if the XML attribute is missing.
     */
    private Senders videoSenders = Senders.none;

    /**
     * Any discovery information that we have for this peer.
     */
    private DiscoverInfo discoverInfo;

    private final OperationSetBasicTelephonyJabberImpl mBasicTelephony;

    /*
     * Jingle Call Session get initialized when started a call initiator or
     * in respond to an incoming call.
     */
    private JingleCallSessionImpl mJingleSession;

    private final JingleUtil jutil;

    /**
     * The indicator which determines whether this peer has initiated the session.
     */
    private boolean initiator = false;

    /**
     * The jabber address of this peer
     */
    private FullJid mPeerJid;

    /**
     * The {@link IQ} that created the session that this call represents.
     */
    private Jingle sessionInitIQ;

    // contains a list of mediaType for session-initiate
    private final List<String> contentMedias = new ArrayList<>();

    /**
     * Initiate a new call with the given remote <code>peerAddress</code>.
     *
     * @param peerAddress the Jabber address of the new call peer.
     * @param owningCall the call that contains this call peer.
     */
    public CallPeerJabberImpl(FullJid peerAddress, CallJabberImpl owningCall)
    {
        super(owningCall);
        mBasicTelephony = (OperationSetBasicTelephonyJabberImpl) mPPS.getOperationSet(OperationSetBasicTelephony.class);
        jutil = new JingleUtil(mConnection);

        mPeerJid = peerAddress;
        setMediaHandler(new CallPeerMediaHandlerJabberImpl(this));
    }

    /**
     * Creates a new call peer with address <code>peerAddress</code>, in response to an incoming call (session-initiate).
     *
     * @param owningCall the call that contains this call peer.
     * @param sessionIQ The session-initiate <code>Jingle</code> which was received from <code>peerAddress</code>
     * @param session current active jingle call session.
     * and caused the creation of this <code>CallPeerJabberImpl</code>
     */
    public CallPeerJabberImpl(CallJabberImpl owningCall, Jingle sessionIQ, JingleCallSessionImpl session)
    {
        this(session.getRemote(), owningCall);
        sessionInitIQ = sessionIQ;
        mJingleSession = session;
    }

    /**
     * Send a session-accept <code>Jingle</code> to this <code>CallPeer</code>
     */
    public synchronized void answer()
            throws OperationFailedException
    {
        Iterable<JingleContent> jingleContents;
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        // cmeng: added to end ring tone when call from Conversations
        setState(CallPeerState.CONNECTING_INCOMING_CALL);
        try {
            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            jingleContents = mediaHandler.generateSessionAccept();
            for (JingleContent c : jingleContents) {
                setSenders(getMediaType(c), c.getSenders());
            }
        } catch (Exception exc) {
            Timber.e(exc, "Failed to answer an incoming call");

            // send an error response
            String reasonText = "Error: " + exc.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.failed_application, reasonText);
            return;
        }

        // Send the session-accept first and start the stream later in case the
        // media relay needs to see it before letting hole punching techniques through.
        // Timber.w(new Exception("Create session accept"));
        Jingle response = jutil.createSessionAccept(sessionInitIQ, jingleContents);
        try {
            mConnection.sendStanza(response);
        } catch (NotConnectedException | InterruptedException e1) {
            throw new OperationFailedException("Could not send session-accept",
                    OperationFailedException.REGISTRATION_REQUIRED, e1);
        }

        try {
            mediaHandler.start();
        } catch (UndeclaredThrowableException e) {
            Throwable exc = e.getUndeclaredThrowable();
            Timber.i(exc, "Failed to establish a connection");

            // send an error response
            String reasonText = "Error: " + exc.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.general_error, reasonText);
            return;
        }
        // tell everyone we are connected so that the audio notifications would stop
        setState(CallPeerState.CONNECTED);
    }

    /**
     * Returns the session ID of the Jingle session associated with this call.
     *
     * @return the session ID of the Jingle session associated with this call.
     */
    public String getSid()
    {
        // cmeng: (2016/09/14) if (sid == null) means some implementation problem => cause smack exception.
        return (sessionInitIQ != null) ? sessionInitIQ.getSid() : null;
    }

    /**
     * Returns the IQ ID of the Jingle session-initiate packet associated with this call.
     *
     * @return the IQ ID of the Jingle session-initiate packet associated with this call.
     */
    public Jingle getSessionIQ()
    {
        return sessionInitIQ;
    }

    /**
     * Returns the session ID of the Jingle session associated with this call.
     *
     * @return the session ID of the Jingle session associated with this call.
     */
    public List<String> getContentMedia()
    {
        return contentMedias;
    }

    /**
     * End the call with this <code>CallPeer</code>. Depending on the state of the peer the method
     * would send a cancel, success, or busy message and set the new state to DISCONNECTED.
     *
     * @param failed indicates if the hangup is following to a call failure or simply a disconnect
     * @param reasonText the text, if any, to be set on the <code>JingleReason</code> as the value of its
     * @param reasonExtension the <code>ExtensionElement</code>, if any, to be set on the <code>JingleReason</code>
     * as the value of its <code>otherExtension</code> property; OR  or <code>JingleReason</code> to be sent unmodified
     */
    public void hangup(boolean failed, String reasonText, Object reasonExtension)
            throws NotConnectedException, InterruptedException
    {
        CallPeerState prevPeerState = getState();

        // do nothing if the call is already ended
        if (CallPeerState.DISCONNECTED.equals(prevPeerState)
                || CallPeerState.FAILED.equals(prevPeerState)) {
            Timber.d("Ignoring a request to hangup a call peer that is already DISCONNECTED");
            return;
        }

        // User hang up call, set reason code == NORMAL_CALL_CLEARING to stop missed call notification fired.
        setState(failed ? CallPeerState.FAILED : CallPeerState.DISCONNECTED, reasonText,
                CallPeerChangeEvent.NORMAL_CALL_CLEARING);

        JingleReason.Reason reason = null;
        if (prevPeerState.equals(CallPeerState.CONNECTED)
                || CallPeerState.isOnHold(prevPeerState)) {
            reason = Reason.success;
            reasonText = "Nice talking to you!";
        }
        else if (CallPeerState.CONNECTING.equals(prevPeerState)
                || CallPeerState.CONNECTING_WITH_EARLY_MEDIA.equals(prevPeerState)
                || CallPeerState.ALERTING_REMOTE_SIDE.equals(prevPeerState)) {
            String jingleSID = getSid();

            if (jingleSID == null) {
                synchronized (sidSyncRoot) {
                    // we cancelled the call too early because the jingleSID is null (i.e. the
                    // session-initiate has not been created) and no need to send the session-terminate
                    cancelled = true;
                    return;
                }
            }
            reason = Reason.cancel;
            reasonText = "Call Retract!";
        }
        else if (prevPeerState.equals(CallPeerState.INCOMING_CALL)) {
            reason = Reason.busy;
        }
        else if (prevPeerState.equals(CallPeerState.BUSY)
                || prevPeerState.equals(CallPeerState.FAILED)) {
            // For FAILED and BUSY we only need to update CALL_STATUS as everything else has been done already.
        }
        else {
            Timber.i("Could not determine call peer state!");
        }

        if (reason != null) {
            Jingle responseIQ;
            if (reasonExtension instanceof JingleReason) {
                responseIQ = jutil.createSessionTerminate(mPeerJid, getSid(), (JingleReason) reasonExtension);
            }
            else {
                JingleReason jingleReason = new JingleReason(reason, reasonText, (ExtensionElement) reasonExtension);
                responseIQ = jutil.createSessionTerminate(mPeerJid, getSid(), jingleReason);
            }
            mConnection.sendStanza(responseIQ);
            mJingleSession.unregisterJingleSessionHandler();
        }
    }

    /**
     * Creates and sends a session-initiate {@link Jingle}.
     *
     * @param sessionInitiateExtensions a collection of additional and optional <code>ExtensionElement</code>s
     * to be added to the <code>session-initiate</code>;
     * {@link Jingle} which is to initiate the session with this <code>CallPeerJabberImpl</code>
     * @param sid The session-initiate sid, must be the same as in Jingle Message id if call is init from 'proceed'
     * @throws OperationFailedException exception
     */
    protected synchronized void initiateSession(Iterable<ExtensionElement> sessionInitiateExtensions, String sid)
            throws OperationFailedException
    {
        initiator = false;
        contentMedias.clear();

        // Create the media description that we'd like to send to the other side.
        List<JingleContent> offer = getMediaHandler().createContentList();
        for (JingleContent contentSI : offer) {
            contentMedias.add(contentSI.getName());
        }

        synchronized (sidSyncRoot) {
            sessionInitIQ = jutil.createSessionInitiate(mPeerJid, sid, offer);

            if (cancelled) {
                // we cancelled the call too early so no need to send the session-initiate to peer
                getMediaHandler().getTransportManager().close();
                return;
            }
        }

        if (sessionInitiateExtensions != null) {
            for (ExtensionElement sessionInitiateExtension : sessionInitiateExtensions) {
                sessionInitIQ.addExtension(sessionInitiateExtension);
            }
        }

        try {
            // Only do it here, so it will get unregistered when caller cancel the call
            mJingleSession = new JingleCallSessionImpl(mConnection, mPeerJid, sid, mBasicTelephony);
            mConnection.sendStanza(sessionInitIQ);

            // Sending of JingleMessage retract not further required once session-initiate has started.
            JingleMessageSessionImpl.setAllowSendRetract(false);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_CREATE_CALL_FAILED),
                    OperationFailedException.REGISTRATION_REQUIRED);
        }
    }

    /**
     * Notifies this instance that a specific <code>ColibriConferenceIQ</code> has been received.
     * This <code>CallPeerJabberImpl</code> uses the part of the information provided in the specified
     * <code>conferenceIQ</code> which concerns it only.
     *
     * @param conferenceIQ the <code>ColibriConferenceIQ</code> which has been received
     */
    void processColibriConferenceIQ(ColibriConferenceIQ conferenceIQ)
    {
        /*
         * CallPeerJabberImpl does not itself/directly know the specifics related to the channels
         * allocated on the Jitsi Videobridge server. The channels contain transport and
         * media-related information so forward the notification to CallPeerMediaHandlerJabberImpl.
         */
        getMediaHandler().processColibriConferenceIQ(conferenceIQ);
    }

    /**
     * Processes the content-accept {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer has accepted
     */
    public void processContentAccept(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        List<JingleContent> contents = content.getContents();
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        try {
            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            mediaHandler.processSessionAcceptContent(contents);
            for (JingleContent c : contents)
                setSenders(getMediaType(c), c.getSenders());
        } catch (Exception e) {
            Timber.w(e, "Failed to process a content-accept");

            // Send an error response.
            String reasonText = "Error: " + e.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.incompatible_parameters, reasonText);
            return;
        }
        mediaHandler.start();
    }

    /**
     * Processes the content-add {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be added
     */
    public void processContentAdd(final Jingle content)
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        List<JingleContent> contents = content.getContents();
        Iterable<JingleContent> answerContents = null;
        Jingle contentIQ;
        boolean noCands = false;
        MediaStream oldVideoStream = mediaHandler.getStream(MediaType.VIDEO);

        Timber.i("Looking for candidates in content-add.");
        try {
            if (!contentAddWithNoCands) {
                mediaHandler.processOffer(contents);

                // Jingle transport will not put candidate in session-initiate and content-add.
                for (JingleContent c : contents) {
                    if (JingleUtils.getFirstCandidate(c, 1) == null) {
                        contentAddWithNoCands = true;
                        noCands = true;
                    }
                }
            }
            // if no candidates are present, launch a new Thread which will process and wait for the
            // connectivity establishment (otherwise the existing thread will be blocked and thus
            // cannot receive transport-info with candidates
            if (noCands) {
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        try {
                            synchronized (candSyncRoot) {
                                candSyncRoot.wait(1000);
                                contentAddWithNoCands = false;
                            }
                            processContentAdd(content);
                        } catch (InterruptedException | NotConnectedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }.start();
                Timber.i("No candidates found in content-add, started new thread.");
                return;
            }

            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            Timber.i("Wrapping up connectivity establishment");
            answerContents = mediaHandler.generateSessionAccept();
            contentIQ = null;
        } catch (Exception e) {
            Timber.w(e, "Exception occurred");
            contentIQ = jutil.createContentReject(mPeerJid, getSid(), answerContents);
        }

        if (contentIQ == null) {
            /* send content-accept */
            contentIQ = jutil.createContentAccept(mPeerJid, getSid(), answerContents);
            for (JingleContent c : answerContents)
                setSenders(getMediaType(c), c.getSenders());
        }
        mConnection.sendStanza(contentIQ);
        mediaHandler.start();

        /*
         * If a remote peer turns her video on in a conference which is hosted by the local peer and
         * the local peer is not streaming her local video, re-invite the other remote peers to
         * enable RTP translation.
         */
        if (oldVideoStream == null) {
            MediaStream newVideoStream = mediaHandler.getStream(MediaType.VIDEO);

            if ((newVideoStream != null) && mediaHandler.isRTPTranslationEnabled(MediaType.VIDEO)) {
                try {
                    getCall().modifyVideoContent();
                } catch (OperationFailedException ofe) {
                    Timber.e(ofe, "Failed to enable RTP translation");
                }
            }
        }
    }

    /**
     * Processes the content-modify {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be modified
     */
    public void processContentModify(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        JingleContent ext = content.getContents().get(0);
        MediaType mediaType = getMediaType(ext);

        try {
            boolean modify = (ext.getFirstChildElement(RtpDescription.class) != null);
            getMediaHandler().reinitContent(ext.getName(), ext, modify);
            setSenders(mediaType, ext.getSenders());

            if (MediaType.VIDEO.equals(mediaType))
                getCall().modifyVideoContent();
        } catch (Exception e) {
            Timber.i(e, "Failed to process an incoming content-modify");

            // Send an error response.
            String reasonText = "Error: " + e.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.incompatible_parameters, reasonText);
        }
    }

    /**
     * Processes the content-reject {@link Jingle}.
     *
     * @param content The {@link Jingle}
     */
    public void processContentReject(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        if (content.getContents().isEmpty()) {
            // send an error response;
            String reasonText = "Error: content rejected";
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.incompatible_parameters, reasonText);
        }
    }

    /**
     * Processes the content-remove {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be removed
     */
    public void processContentRemove(Jingle content)
    {
        List<JingleContent> contents = content.getContents();
        boolean videoContentRemoved = false;

        if (!contents.isEmpty()) {
            CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

            for (JingleContent c : contents) {
                mediaHandler.removeContent(c.getName());

                MediaType mediaType = getMediaType(c);
                setSenders(mediaType, Senders.none);

                if (MediaType.VIDEO.equals(mediaType))
                    videoContentRemoved = true;
            }
            /*
             * TODO XEP-0166: Jingle says: If the content-remove results in zero content definitions
             * for the session, the entity that receives the content-remove SHOULD send a
             * session-terminate action to the other party (since a session with no content
             * definitions is void).
             */
        }

        if (videoContentRemoved) {
            // removing of the video content might affect the other sessions in the call
            try {
                getCall().modifyVideoContent();
            } catch (Exception e) {
                Timber.w("Failed to update Jingle sessions");
            }
        }
    }

    /**
     * Processes a session-accept {@link Jingle}.
     *
     * @param jingleSA The session-accept {@link Jingle} to process.
     */
    public void processSessionAccept(Jingle jingleSA)
            throws NotConnectedException, InterruptedException
    {
        if (sessionAcceptProcessed) {
            Timber.w("Ignore multiple session-accept received from: %s", this);
            return;
        }

        this.sessionInitIQ = jingleSA;
        /*
         * Session-accept contentList request may contains both audio and video requests e.g.
         * <content creator='initiator' name='audio'>
         * <content creator='initiator' name='video' senders='both'>
         */
        List<JingleContent> contentList = jingleSA.getContents();
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        try {
            TransportManagerJabberImpl transportManager = mediaHandler.getTransportManager();
            if (transportManager == null)
                throw new Exception("No available transport manager to process session-accept!");

            sessionAcceptProcessed = true;
            transportManager.wrapupConnectivityEstablishment();
            mediaHandler.processSessionAcceptContent(contentList);
            for (JingleContent c : contentList)
                setSenders(getMediaType(c), c.getSenders());
        } catch (Exception exc) {
            Timber.w(exc, "Failed to process a session-accept");

            // send an error response;
            String reasonText = "Error: " + exc.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.incompatible_parameters, reasonText);
            return;
        }

        // tell everyone we are connected so that the audio notifications would stop
        setState(CallPeerState.CONNECTED);
        mediaHandler.start();

        /*
         * If video was added to the call after we sent the session-initiate to this peer, it needs
         * to be added to this peer's session with a content-add.
         */
        sendModifyVideoContent();
    }

    /**
     * Handles the specified session <code>info</code> packet according to its content.
     *
     * @param info the {@link SessionInfo} that we just received.
     */
    public void processSessionInfo(SessionInfo info)
            throws NotConnectedException, InterruptedException
    {
        switch (info.getType()) {
            case ringing:
                setState(CallPeerState.ALERTING_REMOTE_SIDE);
                break;
            case hold:
                getMediaHandler().setRemotelyOnHold(true);
                reevalRemoteHoldStatus();
                break;
            case unhold:
            case active:
                getMediaHandler().setRemotelyOnHold(false);
                reevalRemoteHoldStatus();
                break;
            default:
                Timber.w("Received SessionInfoExtensionElement of unknown type");
        }
    }

    /**
     * Processes the session initiation {@link Jingle} that we have received, passing its
     * content to the media handler and then sends either a "session-info/ringing" or a
     * "session-terminate" response.
     *
     * @param sessionInitIQ The {@link Jingle} that created the session that we are handling here.
     */
    protected synchronized void processSessionInitiate(Jingle sessionInitIQ)
            throws NotConnectedException, InterruptedException
    {
        // Do initiate the session.
        this.sessionInitIQ = sessionInitIQ;
        this.initiator = true;

        FullJid recipient = sessionInitIQ.getInitiator();
        String sessionId = sessionInitIQ.getSid();

        // This is the SDP offer that came from the initial session-initiate.
        // Contrary to SIP, we are guaranteed to have content because
        // XEP-0166 says: "A session consists of at least one content type at a time."
        List<JingleContent> offer = sessionInitIQ.getContents();
        try {
            getMediaHandler().processOffer(offer);

            CoinExtension coin = null;
            for (ExtensionElement ext : sessionInitIQ.getExtensions()) {
                if (ext.getElementName().equals(CoinExtension.ELEMENT)) {
                    coin = (CoinExtension) ext;
                    break;
                }
            }

            /* Does the call peer acts as a conference focus ? */
            if (coin != null) {
                setConferenceFocus(coin.isFocus());
            }
        } catch (Exception ex) {
            Timber.w(ex, "Failed to process an incoming session initiate");

            // send an error response;
            String reasonText = "Error: " + ex.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.incompatible_parameters, reasonText);
            return;
        }

        // If we do not get the info about the remote peer yet. Get it right now.
        if (this.getDiscoveryInfo() == null) {
            Jid calleeURI = sessionInitIQ.getFrom();
            retrieveDiscoveryInfo(calleeURI);
        }

        // send a ringing response; cmeng??? what about auto-answer\
        if (!CallPeerState.DISCONNECTED.equals(getState())) {
            mConnection.sendStanza(jutil.createSessionInfo(recipient, sessionId, SessionInfoType.ringing));
        }

        // set flag to indicate that session-initiate process has completed.
        synchronized (sessionInitiateSyncRoot) {
            sessionInitiateProcessed = true;
            // cmeng - Importance: must notifyAll as there are multiple transport-info's on waiting
            sessionInitiateSyncRoot.notifyAll();
        }

        // if this is a 3264 initiator, let's give them an early peek at our answer so that they could
        // start ICE (SIP-2-Jingle gateways won't be able to send their candidates unless they have this)
        DiscoverInfo discoverInfo = getDiscoveryInfo();
        if ((discoverInfo != null)
                && discoverInfo.containsFeature(ProtocolProviderServiceJabberImpl.URN_IETF_RFC_3264)) {
            mConnection.sendStanza(jutil.createDescriptionInfo(
                    sessionInitIQ, getMediaHandler().getLocalContentList()));
        }
        // process members if any
        processSourceAdd(sessionInitIQ);
    }

    /**
     * Puts this peer into a {@link CallPeerState#DISCONNECTED}, indicating a reason to the user, if there is one.
     *
     * @param jingle the {@link Jingle} that's terminating our session.
     */
    public void processSessionTerminate(Jingle jingle)
    {
        String reasonStr = "Call ended by remote side.";
        JingleReason jingleReason = jingle.getReason();

        if (jingleReason != null) {
            Reason reason = jingleReason.asEnum();
            if (reason != null)
                reasonStr += "\nReason: " + reason + ".";

            String text = jingleReason.getText();
            if (text != null)
                reasonStr += "\n" + text;
        }
        setState(CallPeerState.DISCONNECTED, reasonStr);
        mJingleSession.unregisterJingleSessionHandler();
    }

    /**
     * Processes a specific "XEP-0251: Jingle Session Transfer" <code>transfer</code> stanza (extension).
     *
     * @param transfer the "XEP-0251: Jingle Session Transfer" transfer stanza (extension) to process
     * @throws OperationFailedException if anything goes wrong while processing the specified
     * <code>transfer</code> stanza (extension)
     */
    public void processTransfer(SdpTransfer transfer, Jingle jingleIQ)
            throws OperationFailedException
    {
        FullJid calleeJid = transfer.getTo().asFullJidIfPossible();
        if (calleeJid == null) {
            throw new OperationFailedException("Session unattended transfer must contain a 'to' attribute value.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        Jid attendantJid = jingleIQ.getFrom();
        if (attendantJid == null) {
            throw new OperationFailedException("Session transfer source is unknown.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        // Checks if the transfer remote peer is contained by the roster of this account.
        Roster roster = Roster.getInstanceFor(mConnection);
        if (!roster.contains(calleeJid.asBareJid())) {
            String failedMessage = "Transfer not possible:\nAccount roster does not contain transfer peer: " + calleeJid;
            Timber.w(failedMessage);
            setState(CallPeerState.FAILED, failedMessage);
            return;
        }

        /*
         * calleeTransfer content depends on unattended or attended transfer request
         * <a href="https://xmpp.org/extensions/xep-0251.html#unattended">XEP-0251 § 2. Unattended Transfer</a>
         * <a href="https://xmpp.org/extensions/xep-0251.html#attended ">XEP-0251 § 3. Attended Transfer</a>
         * Attended call transfer sid must not be null
        */
        SdpTransfer calleeTransfer;
        if (transfer.getSid() != null) {
            // Attended transfer; just forward the received transfer
            calleeTransfer = transfer;
        } else {
            // Unattended transfer, must init the from attribute to attendant Jid
            calleeTransfer = SdpTransfer.getBuilder().setFrom(attendantJid).build();
        }

        // Transfer jingle session-initiate must use new Sid; perform init JingleCallSessionImpl() in initiateSession
        CallJabberImpl calleeCall = new CallJabberImpl(mBasicTelephony, JingleManager.randomId());
        mBasicTelephony.createOutgoingCall(calleeCall, calleeJid, Arrays.asList(new ExtensionElement[]{calleeTransfer}));
    }

    /**
     * Processes the offered remote <code>transport-info</code> {@link Jingle}.
     * The transport-info is used to exchange transport candidates for mediaHandler.
     * cmeng: The wait control is now at OperationSetBasicTelephonyJabberImpl#processTransportInfo(CallPeerJabberImpl, Jingle)
     *
     * @param jingle containing the <code>transport-info</code> {@link Jingle} to be processed.
     */
    public void processOfferTransportInfo(Jingle jingle)
            throws NotConnectedException, InterruptedException
    {
        try {
            // Wait (1000ms max) for session-accept to arrive before start processing any transport-info.
            if (isInitiator()) {
                synchronized (sessionInitiateSyncRoot) {
                    if (!sessionInitiateProcessed) {
                        try {
                            sessionInitiateSyncRoot.wait(10);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
            List<JingleContent> jingleContents = jingle.getContents();
            List<String> media = new ArrayList<>();
            for (JingleContent jingleContent : jingleContents) {
                media.add(jingleContent.getName());
            }
            Timber.d("### Processing Jingle (transport-info) for media: %s : %s", media, jingle.getStanzaId());
            getMediaHandler().processTransportInfo(jingleContents);
        } catch (OperationFailedException ofe) {
            Timber.w(ofe, "Failed to process an incoming transport-info");

            // send an error response
            String reasonText = "Error: " + ofe.getMessage();
            setState(CallPeerState.FAILED, reasonText);
            mJingleSession.terminateSessionAndUnregister(Reason.failed_transport, reasonText);
            return;
        }

        synchronized (candSyncRoot) {
            candSyncRoot.notifyAll();
        }
    }

    /**
     * Puts the <code>CallPeer</code> represented by this instance on or off hold.
     *
     * @param onHold <code>true</code> to have the <code>CallPeer</code> put on hold; <code>false</code>, otherwise
     * @throws OperationFailedException if we fail to construct or send the INVITE request putting the
     * remote side on/off hold.
     */
    public void putOnHold(boolean onHold)
            throws OperationFailedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        mediaHandler.setLocallyOnHold(onHold);
        SessionInfoType type;
        if (onHold)
            type = SessionInfoType.hold;
        else {
            type = SessionInfoType.unhold;
            getMediaHandler().reinitAllContents();
        }

        // we are now on hold and need to realize this before potentially
        // spoiling it all with an exception while sending the packet.
        reevalLocalHoldStatus();
        Jingle onHoldIQ = jutil.createSessionInfo(mPeerJid, getSid(), type);
        try {
            mConnection.sendStanza(onHoldIQ);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send session info",
                    OperationFailedException.REGISTRATION_REQUIRED, e);
        }
    }

    /**
     * Send a <code>content-add</code> to add video setup.
     */
    private void sendAddVideoContent()
            throws NotConnectedException, InterruptedException
    {
        List<JingleContent> contents;
        try {
            contents = getMediaHandler().createContentList(MediaType.VIDEO);
        } catch (Exception exc) {
            Timber.w(exc, "Failed to gather content for video type");
            return;
        }

        Jingle contentIQ = jutil.createContentAdd(mPeerJid, getSid(), contents);
        mConnection.sendStanza(contentIQ);
    }

    /**
     * Sends a <code>content</code> message to reflect changes in the setup such as the local peer/user
     * becoming a conference focus.
     */
    public void sendCoinSessionInfo()
            throws NotConnectedException, InterruptedException
    {
        Jingle sessionInfo = jutil.createSessionInfo(mPeerJid, getSid());
        CoinExtension coinExt = CoinExtension.getBuilder()
                .setFocus(getCall().isConferenceFocus())
                .build();

        sessionInfo.addExtension(coinExt);
        mConnection.sendStanza(sessionInfo);
    }

    /**
     * Returns the <code>MediaDirection</code> that should be set for the content of type <code>mediaType</code>
     * in the Jingle session for this <code>CallPeer</code>. If we are the focus of a conference and are doing
     * RTP translation, takes into account the other <code>CallPeer</code>s in the <code>Call</code>.
     *
     * @param mediaType the <code>MediaType</code> for which to return the <code>MediaDirection</code>.
     * Only use by MediaType.VIDEO currently.
     * @return the <code>MediaDirection</code> that should be used for the content of type
     * <code>mediaType</code> in the Jingle session for this <code>CallPeer</code>.
     */
    private MediaDirection getDirectionForJingle(MediaType mediaType)
    {
        MediaDirection direction = MediaDirection.INACTIVE;
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        Senders senders = getSenders(mediaType);

        //  cmeng (20210321): Approach aborted due to complexity and NewReceiveStreamEvent not alway gets triggered:
        // If we are streaming the media, the direction should allow sending; device orientation change does not
        // constitute to a content-modify, see https://xmpp.org/extensions/xep-0166.html#def-action-content-modify
//        boolean orientationChange = (PreviewStream.previewPortrait != aTalkApp.isPortrait);
//        if ((MediaType.AUDIO == mediaType && mediaHandler.isLocalAudioTransmissionEnabled())
//                || ((MediaType.VIDEO == mediaType) && (isLocalVideoStreaming()
//                || (orientationChange && (senders == Senders.both
//                || (initiator && senders == Senders.responder)
//                || (!initiator && senders == Senders.initiator))))))

        if ((MediaType.AUDIO == mediaType && mediaHandler.isLocalAudioTransmissionEnabled())
                || ((MediaType.VIDEO == mediaType) && isLocalVideoStreaming()))
            direction = direction.or(MediaDirection.SENDONLY);

        // If we are receiving media from this CallPeer, the direction should allow receiving
        if (senders == null || senders == Senders.both
                || (isInitiator() && senders == Senders.initiator)
                || (!isInitiator() && senders == Senders.responder))
            direction = direction.or(MediaDirection.RECVONLY);

        // If we are the focus of a conference, and we are receiving media from
        // another CallPeer in the same Call, the direction should allow sending
        CallJabberImpl call = getCall();
        if (call != null && call.isConferenceFocus()) {
            for (CallPeerJabberImpl peer : call.getCallPeerList()) {
                if (peer != this) {
                    senders = peer.getSenders(mediaType);
                    if (senders == null || senders == Senders.both
                            || (peer.isInitiator() && senders == Senders.initiator)
                            || (!peer.isInitiator() && senders == Senders.responder)) {
                        direction = direction.or(MediaDirection.SENDONLY);
                        break;
                    }
                }
            }
        }

        // Timber.w("Media Sender direction %s <= %s || (%s && %s) %s", direction, isLocalVideoStreaming(), orientationChange, senders, initiator);
        return direction;
    }

    /**
     * Send, if necessary, a jingle <code>content</code> message to reflect change in the video setup.
     * Whether the jingle session should have a video content, and if so, the value of the
     * <code>senders</code> field is determined based on whether we are streaming local video and, if we
     * are the focus of a conference, on the other peers in the conference. The message can be
     * content-modify if video content exists (and the <code>senders</code> field changes), content-add
     * or content-remove.
     *
     * @return <code>true</code> if a jingle <code>content</code> message was sent.
     */
    public boolean sendModifyVideoContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        MediaDirection direction = getDirectionForJingle(MediaType.VIDEO);

        JingleContent remoteContent = mediaHandler.getLocalContent(MediaType.VIDEO.toString());
        if (remoteContent == null) {
            if (direction == MediaDirection.INACTIVE) {
                // no video content, none needed
                return false;
            }
            else { // no remote video and local video added
                if (getState() == CallPeerState.CONNECTED) {
                    Timber.i("Adding video content for %s", this);
                    sendAddVideoContent();
                    return true;
                }
                return false;
            }
        }
        else {
            if (direction == MediaDirection.INACTIVE) {
                sendRemoveVideoContent();
                return true;
            }
        }

        Senders senders = getSenders(MediaType.VIDEO);
        if (senders == null)
            senders = JingleContent.Senders.both;

        Senders newSenders = Senders.none;
        if (MediaDirection.SENDRECV == direction)
            newSenders = Senders.both;
        else if (MediaDirection.RECVONLY == direction)
            newSenders = isInitiator() ? Senders.initiator : Senders.responder;
        else if (MediaDirection.SENDONLY == direction)
            newSenders = isInitiator() ? Senders.responder : Senders.initiator;

        /*
         * Send Content-Modify
         */
        String remoteContentName = remoteContent.getName();
        JingleContent content = JingleContent.getBuilder()
                .setCreator(remoteContent.getCreator())
                .setName(remoteContentName)
                .setSenders(newSenders)
                .build();

        // cmeng (2016/9/14) only send content-modify if there is a change in own video streaming state
        if (newSenders != senders) {
            Timber.i("Sending content modify, senders: %s -> %s", senders, newSenders);

            // cmeng: must update local videoSenders for content-modify
            setSenders(MediaType.VIDEO, newSenders);

            Jingle contentIQ = jutil.createContentModify(mPeerJid, getSid(), content);
            mConnection.sendStanza(contentIQ);
        }

        try {
            mediaHandler.reinitContent(remoteContentName, content, false);
            mediaHandler.start();
        } catch (Exception e) {
            Timber.w(e, "Exception occurred during media reinitialization");
        }
        return (newSenders != senders);
    }

    /**
     * Send a <code>content</code> message to reflect change in the video setup (start or stop).
     */
    public void sendModifyVideoResolutionContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        JingleContent remoteContent = mediaHandler.getRemoteContent(MediaType.VIDEO.toString());
        JingleContent content;
        Timber.i("send modify-content to change resolution");

        // send content-modify with RTP description
        // create content list with resolution
        try {
            content = mediaHandler.createContentForMedia(MediaType.VIDEO);
        } catch (Exception e) {
            Timber.w(e, "Failed to gather content for video type");
            return;
        }

        // if we are only receiving video senders is null
        Senders senders = remoteContent.getSenders();
        if (senders != null)
            content.setSenders(senders);

        Jingle contentIQ = jutil.createContentModify(mPeerJid, getSid(), content);
        mConnection.sendStanza(contentIQ);

        try {
            mediaHandler.reinitContent(remoteContent.getName(), content, false);
            mediaHandler.start();
        } catch (Exception e) {
            Timber.w(e, "Exception occurred when media reinitialization");
        }
    }

    /**
     * Send a <code>content-remove</code> to remove video setup.
     */
    private void sendRemoveVideoContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        JingleContent remoteContent = mediaHandler.getRemoteContent(MediaType.VIDEO.toString());
        if (remoteContent == null)
            return;

        String remoteContentName = remoteContent.getName();
        JingleContent content = JingleContent.getBuilder()
                .setCreator(remoteContent.getCreator())
                .setName(remoteContentName)
                .setSenders(remoteContent.getSenders())
                .build();

        Jingle contentIQ = jutil.createContentRemove(mPeerJid, getSid(), Collections.singletonList(content));
        mConnection.sendStanza(contentIQ);
        mediaHandler.removeContent(remoteContentName);
        setSenders(MediaType.VIDEO, Senders.none);
    }

    /**
     * Sends local candidate addresses from the local peer to the remote peer using the
     * <code>transport-info</code> {@link Jingle}.
     *
     * @param contents the local candidate addresses to be sent from the local peer to the remote peer using
     * the <code>transport-info</code> {@link Jingle}
     */
    protected void sendTransportInfo(Iterable<JingleContent> contents)
            throws NotConnectedException, InterruptedException
    {
        // if the call is canceled or disconnected, do not start sending candidates in transport-info.
        if (cancelled || CallPeerState.DISCONNECTED.equals(getState()))
            return;

        Jingle transportInfo = jutil.createTransportInfo(mPeerJid, getSid(), contents);
        StanzaCollector collector = mConnection.createStanzaCollectorAndSend(transportInfo);
        try {
            collector.nextResult();
        } finally {
            collector.cancel();
        }
    }

    @Override
    public void setState(CallPeerState newState, String reason, int reasonCode)
    {
        CallPeerState oldState = getState();
        try {
            /*
             * We need to dispose of the transport manager before the 'call' field is set to null,
             * because if Jitsi Videobridge is in use, it (the call) is needed in order to expire
             * the Videobridge channels.
             */
            if (CallPeerState.DISCONNECTED.equals(newState) || CallPeerState.FAILED.equals(newState)) {
                CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
                if (mediaHandler != null) {
                    TransportManagerJabberImpl transportManager = mediaHandler.getTransportManager();
                    if (transportManager != null) {
                        transportManager.close();
                    }
                }
            }
        } finally {
            super.setState(newState, reason, reasonCode);
        }

        if (CallPeerState.isOnHold(oldState) && CallPeerState.CONNECTED.equals(newState)) {
            try {
                getCall().modifyVideoContent();
            } catch (OperationFailedException ofe) {
                Timber.e("Failed to update call video state after 'hold' status removed for %s", this);
            }
        }
    }

    /**
     * Transfer (in the sense of call transfer) this <code>CallPeer</code> to a specific callee address
     * which may optionally be participating in an active <code>Call</code>.
     *
     * @param to the address of the callee to transfer this <code>CallPeer</code> to
     * @param sid the Jingle session ID of the active <code>Call</code> between the local peer and the
     * callee in the case of attended transfer; <code>null</code> in the case of unattended transfer
     * @throws OperationFailedException if something goes wrong
     */
    protected void transfer(Jid to, String sid)
            throws OperationFailedException
    {
        // Attended transfer needs all the attrs to have values.
        SdpTransfer.Builder transferBuilder = SdpTransfer.getBuilder().setTo(to);
        if (sid != null) {
            transferBuilder
                    .setFrom(mPPS.getOurJID())
                    .setSid(sid);

            // Puts on hold the 2 calls before making the attended transfer.
            CallPeerJabberImpl callPeer = mBasicTelephony.getActiveCallPeer(sid);
            if (callPeer != null) {
                if (!CallPeerState.isOnHold(callPeer.getState())) {
                    callPeer.putOnHold(true);
                }
            }
            if (!CallPeerState.isOnHold(this.getState())) {
                this.putOnHold(true);
            }
        }

        Jingle transferSessionInfo = jutil.createSessionInfo(mPeerJid, getSid());
        transferSessionInfo.addExtension(transferBuilder.build());
        try {
            StanzaCollector collector = mConnection.createStanzaCollectorAndSend(transferSessionInfo);
            try {
                collector.nextResultOrThrow();
            } finally {
                collector.cancel();
            }
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send transfer session info",
                    OperationFailedException.REGISTRATION_REQUIRED, e);
        } catch (XMPPException.XMPPErrorException e1) {
            // Log the failed transfer call and notify the user.
            throw new OperationFailedException ("Remote peer does not support call 'transfer'. "
                    + e1.getStanzaError(), OperationFailedException.ILLEGAL_ARGUMENT);
        } catch (SmackException.NoResponseException e1) {
            // Log the failed transfer call and notify the user.
            throw new OperationFailedException("No response to 'transfer' request.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        String message = aTalkApp.getResString(R.string.gui_call_transfer_msg,
                (sid == null) ? "Unattended" : "Attended", to);
        try {
            // Implements the SIP behavior: once the transfer is accepted, the current call is closed.
            hangup(false, message, new JingleReason(Reason.success, message, SdpTransferred.getBuilder().build()));
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send transfer", 0, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getEntity()
    {
        return getAddress();
    }

    /**
     * {@inheritDoc}
     *
     * In Jingle there isn't an actual "direction" parameter. We use the <code>senders</code> field to
     * calculate the direction.
     */
    @Override
    public MediaDirection getDirection(MediaType mediaType)
    {
        Senders senders = getSenders(mediaType);

        if (senders == Senders.none) {
            return MediaDirection.INACTIVE;
        }
        else if (senders == null || senders == Senders.both) {
            return MediaDirection.SENDRECV;
        }
        else if (senders == Senders.initiator) {
            return isInitiator() ? MediaDirection.RECVONLY : MediaDirection.SENDONLY;
        }
        else { // senders == Senders.responder
            return isInitiator() ? MediaDirection.SENDONLY : MediaDirection.RECVONLY;
        }
    }

    /**
     * Gets the current value of the <code>senders</code> field of the content with name
     * <code>mediaType</code> in the Jingle session with this <code>CallPeer</code>.
     *
     * @param mediaType the <code>MediaType</code> for which to get the current value of the <code>senders</code> field.
     * @return the current value of the <code>senders</code> field of the content with name
     * <code>mediaType</code> in the Jingle session with this <code>CallPeer</code>.
     */
    public Senders getSenders(MediaType mediaType)
    {
        switch (mediaType) {
            case AUDIO:
                return audioSenders;
            case VIDEO:
                return videoSenders;
            default:
                return Senders.none;
        }
    }

    /**
     * Set the current value of the <code>senders</code> field of the content with name
     * <code>mediaType</code> in the Jingle session with this <code>CallPeer</code>
     *
     * @param mediaType the <code>MediaType</code> for which to get the current value of the <code>senders</code> field.
     * @param senders the value to set.
     */
    private void setSenders(MediaType mediaType, Senders senders)
    {
        switch (mediaType) {
            case AUDIO:
                this.audioSenders = senders;
                break;
            case VIDEO:
                this.videoSenders = senders;
                break;
            default:
                throw new IllegalArgumentException("mediaType");
        }
    }

    /**
     * Gets the <code>MediaType</code> of <code>content</code>. If <code>content</code> does not have a
     * <code>description</code> child and therefore not <code>MediaType</code> can be associated with it,
     * tries to take the <code>MediaType</code> from the session's already established contents with the
     * same name as <code>content</code>
     *
     * @param content the <code>JingleContent</code> for which to get the <code>MediaType</code>
     * @return the <code>MediaType</code> of <code>content</code>.
     */
    public MediaType getMediaType(JingleContent content)
    {
        String contentName = content.getName();
        if (contentName == null)
            return null;

        MediaType mediaType = JingleUtils.getMediaType(content);
        if (mediaType == null) {
            CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
            for (MediaType m : MediaType.values()) {
                JingleContent sessionContent = mediaHandler.getRemoteContent(m.toString());
                if (sessionContent == null)
                    sessionContent = mediaHandler.getLocalContent(m.toString());

                if (sessionContent != null && contentName.equals(sessionContent.getName())) {
                    mediaType = m;
                    break;
                }
            }
        }
        return mediaType;
    }

    /**
     * Processes the source-add {@link Jingle} action used in Jitsi-Meet.
     * For now processing only audio, as we use single ssrc for audio and
     * using multiple ssrcs for video. ConferenceMember currently support single
     * ssrc for audio and video and adding multiple ssrcs will need a large refactor.
     *
     * @param content The {@link Jingle} that contains content that remote
     * peer wants to be added
     */
    public void processSourceAdd(final Jingle content)
    {
        for (JingleContent c : content.getContents()) {
            // we are parsing only audio
            if (!MediaType.AUDIO.equals(JingleUtils.getMediaType(c))) {
                continue;
            }

            RtpDescription rtpDesc = c.getFirstChildElement(RtpDescription.class);

            // for (MediaPresenceExtension.Source src : rtpDesc.getChildExtensionsOfType(MediaPresenceExtension.Source.class)) {
            for (SdpSource src : rtpDesc.getChildElements(SdpSource.class)) {
                SSRCInfoExtension ssrcInfo = src.getFirstChildElement(SSRCInfoExtension.class);
                if (ssrcInfo == null)
                    continue;

                Jid owner = ssrcInfo.getOwner();
                if (owner == null)
                    continue;

                AbstractConferenceMember member = findConferenceMemberByAddress(owner);
                if (member == null) {
                    member = new AbstractConferenceMember(this, owner.toString());
                    this.addConferenceMember(member);
                }
                member.setAudioSsrc(src.getSSRC());
            }
        }
    }

    /**
     * Processes the source-remove {@link Jingle} action used in Jitsi-Meet.
     * For now processing only audio, as we use single ssrc for audio and
     * using multiple ssrcs for video. ConferenceMember currently support single
     * ssrc for audio and video and adding multiple ssrcs will need a large refactor.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be removed
     */
    public void processSourceRemove(final Jingle content)
    {
        for (JingleContent c : content.getContents()) {
            // we are parsing only audio
            if (!MediaType.AUDIO.equals(JingleUtils.getMediaType(c))) {
                continue;
            }

            RtpDescription rtpDesc = c.getFirstChildElement(RtpDescription.class);
            for (SdpSource src : rtpDesc.getChildElements(SdpSource.class)) {
                SSRCInfoExtension ssrcInfo = src.getFirstChildElement(SSRCInfoExtension.class);

                if (ssrcInfo == null)
                    continue;

                Jid owner = ssrcInfo.getOwner();
                if (owner == null)
                    continue;

                ConferenceMember member = findConferenceMemberByAddress(owner);
                if (member != null)
                    this.removeConferenceMember(member);
            }
        }
    }

    /**
     * Finds <code>ConferenceMember</code> by its address.
     *
     * @param address the address to look for
     * @return <code>ConferenceMember</code> with <code>address</code> or null if not found.
     */
    private AbstractConferenceMember findConferenceMemberByAddress(Jid address)
    {
        for (ConferenceMember member : getConferenceMembers()) {
            if (member.getAddress().equals(address.toString())) {
                return (AbstractConferenceMember) member;
            }
        }
        return null;
    }

    /**
     * Returns a String locator for that peer.
     *
     * @return the peer's address or phone number.
     */
    public String getAddress()
    {
        return mPeerJid.toString();
    }

    public Jid getPeerJid()
    {
        return mPeerJid;
    }

    /**
     * Returns the contact corresponding to this peer or null if no particular contact has been associated.
     *
     * @return the <code>Contact</code> corresponding to this peer or null if no particular contact has been associated.
     */
    public Contact getContact()
    {
        OperationSetPresence presence = getProtocolProvider().getOperationSet(OperationSetPresence.class);
        return (presence == null) ? null : presence.findContactByJid(mPeerJid);
    }

    /**
     * Returns the service discovery information that we have for this peer.
     *
     * @return the service discovery information that we have for this peer.
     */
    public DiscoverInfo getDiscoveryInfo()
    {
        return discoverInfo;
    }

    /**
     * Returns a human readable name representing this peer.
     *
     * @return a String containing a name for that peer.
     */
    public String getDisplayName()
    {
        if (getCall() != null) {
            Contact contact = getContact();
            if (contact != null)
                return contact.getDisplayName();
        }
        return mPeerJid.toString();
    }

    /**
     * Returns full URI of the address.
     *
     * @return full URI of the address
     */
    public String getURI()
    {
        return "xmpp:" + mPeerJid;
    }

    /**
     * Determines whether this peer initiated the session. Note that if this
     * peer is the initiator of the session, then we are the responder!
     *
     * @return <code>true</code> if this peer initiated the session; <code>false</code>, otherwise
     * (i.e. if _we_ initiated the session).
     */
    public boolean isInitiator()
    {
        return initiator;
    }

    /**
     * Retrieves the DiscoverInfo for a given peer identified by its URI.
     *
     * @param calleeURI The URI of the call peer.
     */
    private void retrieveDiscoveryInfo(Jid calleeURI)
    {
        try {
            DiscoverInfo discoveryInfo = mPPS.getDiscoveryManager().discoverInfo(calleeURI);
            if (discoveryInfo != null)
                setDiscoveryInfo(discoveryInfo);
        } catch (XMPPException
                | InterruptedException
                | SmackException.NoResponseException
                | NotConnectedException xmppex) {
            Timber.w(xmppex, "Could not retrieve info for %s", calleeURI);
        }
    }

    /**
     * Specifies the address, phone number, or other protocol specific
     * identifier that represents this call peer. This method is to be
     * used by service users and MUST NOT be called by the implementation.
     *
     * @param address The address of this call peer.
     */
    public void setAddress(FullJid address)
    {
        if (!mPeerJid.equals(address)) {
            String oldAddress = getAddress();
            mPeerJid = address;
            fireCallPeerChangeEvent(CallPeerChangeEvent.CALL_PEER_ADDRESS_CHANGE, oldAddress, address);
        }
    }

    /**
     * Sets the service discovery information that we have for this peer.
     *
     * @param discoverInfo the discovery information that we have obtained for this peer.
     */
    public void setDiscoveryInfo(DiscoverInfo discoverInfo)
    {
        this.discoverInfo = discoverInfo;
    }

    /**
     * Returns the IQ StanzaId of the Jingle session-initiate associated with this call.
     *
     * @return the IQ StanzaId of the Jingle session-initiate associated with this call.
     */
    public String getJingleIQStanzaId()
    {
        return sessionInitIQ != null ? sessionInitIQ.getStanzaId() : null;
    }
}
