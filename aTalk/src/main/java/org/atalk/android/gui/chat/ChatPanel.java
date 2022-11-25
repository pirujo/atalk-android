/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.Intent;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.chat.conference.AdHocChatRoomWrapper;
import org.atalk.android.gui.chat.conference.ConferenceChatManager;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chat.filetransfer.FileTransferActivator;
import org.atalk.android.plugin.textspeech.TTSService;
import org.atalk.persistance.FileBackend;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * The <code>ChatPanel</code>, <code>ChatActivity</code>, <code>ChatController</code> and <code>ChatFragment</code>
 * together formed the frontend interface to the user, where users can write and send messages
 * (ChatController), view received and sent messages (ChatFragment). A ChatPanel is created for a
 * contact or for a group of contacts in case of a chat conference. There is always one default
 * contact for the chat, which is the first contact which was added to the chat. Each "Chat GUI'
 * constitutes a fragment page access/scroll to view via the ChatPagerAdapter.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatPanel implements Chat, MessageListener
{
    /**
     * Number of history messages to be returned from loadHistory call.
     * Limits the amount of messages being loaded at one time.
     */
    private static final int HISTORY_CHUNK_SIZE = 30;

    /**
     * The underlying <code>MetaContact</code>, we're chatting with.
     */
    private final MetaContact mMetaContact;

    // An object reference containing either a MetaContact or ChatRoomWrapper
    private final Object mDescriptor;

    /**
     * The chatType for which the message will be send for method not using Transform process
     * i.e. OTR. This is also the master copy where other will update or refer to.
     * The state may also be change by the under lying signal protocol based on the current
     * signalling condition.
     */
    private int mChatType;

    /**
     * The current chat transport.
     */
    private ChatTransport mCurrentChatTransport;

    /**
     * The chat history filter for retrieving history messages.
     */
    private final String[] chatHistoryFilter = ChatSession.chatHistoryFilter;

    /**
     * Messages cache used by this session; to cache msg arrived when the chatFragment
     * is not in view e.g. standby, while in contactList view or when scroll out of view.
     *
     * Use CopyOnWriteArrayList instead to avoid ChatFragment#prependMessages ConcurrentModificationException
     * private List<ChatMessage> msgCache = new LinkedList<>();
     */
    private final List<ChatMessage> msgCache = new CopyOnWriteArrayList<>();

    /**
     * Synchronization root for messages cache.
     */
    private final Object cacheLock = new Object();

    /**
     * Current chat session type: mChatSession can either be one of the following:
     * MetaContactChatSession, ConferenceChatSession or AdHocConferenceChatSession
     */
    private ChatSession mChatSession;

    /**
     * Flag indicates if the history has been loaded (it must be done only once;
     * and all the next messages are cached through the listeners mechanism).
     */
    private boolean historyLoaded = false;

    /**
     * Blocked caching of the next new message if sent via normal sendMessage().
     * Otherwise there will have duplicated display messages
     */
    private boolean cacheBlocked = false;

    /**
     * Registered chatFragment to be informed of any messageReceived event
     */
    private final List<ChatSessionListener> msgListeners = new ArrayList<>();

    /**
     * Current chatSession TTS is active if true
     */
    private boolean isChatTtsEnable = false;

    private int ttsDelay = 1200;

    /**
     * Field used by the <code>ChatController</code> to keep track of last edited message content.
     */
    private String editedText;

    /**
     * Field used by the <code>ChatController</code> (input text) to remember if user was
     * making correction to the earlier sent message.
     */
    private String correctionUID;

    /**
     * ConferenceChatSession Subject - inform user if changed
     */
    private static String chatSubject = "";

    /**
     * Creates a chat session with the given MetaContact or ChatRoomWrapper.
     *
     * @param descriptor the transport object we're chatting with
     */
    public ChatPanel(Object descriptor)
    {
        mDescriptor = descriptor;

        if (descriptor instanceof MetaContact) {
            mMetaContact = (MetaContact) descriptor;
        }
        // Conference
        else {
            mMetaContact = null;
        }
    }

    /**
     * Sets the chat session to associate to this chat panel.
     *
     * @param chatSession the chat session to associate to this chat panel
     */
    public void setChatSession(ChatSession chatSession)
    {
        if (mChatSession != null) {
            // remove any old listener if present.
            mCurrentChatTransport.removeInstantMessageListener(this);
            mCurrentChatTransport.removeSmsMessageListener(this);
        }

        mChatSession = chatSession;
        mCurrentChatTransport = mChatSession.getCurrentChatTransport();
        mCurrentChatTransport.addInstantMessageListener(this);
        mCurrentChatTransport.addSmsMessageListener(this);
        updateChatTtsOption();
    }

    /**
     * Returns the protocolProvider of the user associated with this chat panel.
     *
     * @return the protocolProvider associated with this chat panel.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return mCurrentChatTransport.getProtocolProvider();
    }

    /**
     * Returns the chat session associated with this chat panel.
     *
     * @return the chat session associated with this chat panel.
     */
    public ChatSession getChatSession()
    {
        return mChatSession;
    }

    /**
     * Returns the underlying <code>MetaContact</code>, we're chatting with in metaContactChatSession
     *
     * @return the underlying <code>MetaContact</code>, we're chatting with
     */
    public MetaContact getMetaContact()
    {
        return mMetaContact;
    }

    /**
     * Stores current chatType.
     *
     * @param chatType selected chatType e.g. MSGTYPE_NORMAL.
     **/
    public void setChatType(int chatType)
    {
        mChatType = chatType;
    }

    public int getChatType()
    {
        return mChatType;
    }

    /**
     * Check if current chat is set to OMEMO crypto mode
     *
     * @return return <code>true</code> if OMEMO crypto chat is selected.
     */
    public boolean isOmemoChat()
    {
        return ((mChatType == ChatFragment.MSGTYPE_OMEMO)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UA)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UT));
    }

    /**
     * Check if current chat is set to OTR crypto mode
     *
     * @return return <code>true</code> if OMEMO crypto chat is selected
     */
    public boolean isOTRChat()
    {
        return ((mChatType == ChatFragment.MSGTYPE_OTR)
                || (mChatType == ChatFragment.MSGTYPE_OTR_UA));
    }

    /**
     * Stores recently edited message text.
     *
     * @param editedText recently edited message text.
     */
    public void setEditedText(String editedText)
    {
        this.editedText = editedText;
    }

    /**
     * Returns recently edited message text.
     *
     * @return recently edited message text.
     */
    public String getEditedText()
    {
        return editedText;
    }

    /**
     * Stores the UID of recently corrected message.
     *
     * @param correctionUID the UID of recently corrected message.
     */
    public void setCorrectionUID(String correctionUID)
    {
        this.correctionUID = correctionUID;
    }

    /**
     * Gets the UID of recently corrected message.
     *
     * @return the UID of recently corrected message.
     */
    public String getCorrectionUID()
    {
        return correctionUID;
    }

    /**
     * Runs clean-up for associated resources which need explicit disposal (e.g.
     * listeners keeping this instance alive because they were added to the
     * model which operationally outlives this instance).
     */
    public void dispose()
    {
        mCurrentChatTransport.removeInstantMessageListener(this);
        mCurrentChatTransport.removeSmsMessageListener(this);
        mChatSession.dispose();
    }

    /**
     * Adds the given <code>ChatSessionListener</code> to listen for message events in this chat session.
     *
     * @param msgListener the <code>ChatSessionListener</code> to add
     */
    public void addMessageListener(ChatSessionListener msgListener)
    {
        if (!msgListeners.contains(msgListener))
            msgListeners.add(msgListener);
    }

    /**
     * Removes the given <code>ChatSessionListener</code> from this chat session.
     *
     * @param msgListener the <code>ChatSessionListener</code> to remove
     */
    public void removeMessageListener(ChatSessionListener msgListener)
    {
        msgListeners.remove(msgListener);
    }

    /**
     * Adds the given <code>ChatStateNotificationsListener</code> to listen for chat state events
     * in this chat session (Contact or ChatRoom).
     *
     * @param l the <code>ChatStateNotificationsListener</code> to add
     */
    public void addChatStateListener(ChatStateNotificationsListener l)
    {
        OperationSetChatStateNotifications chatStateOpSet
                = mCurrentChatTransport.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

        if (chatStateOpSet != null) {
            chatStateOpSet.addChatStateNotificationsListener(l);
        }
    }

    /**
     * Removes the given <code>ChatStateNotificationsListener</code> from this chat session (Contact or ChatRoom)..
     *
     * @param l the <code>ChatStateNotificationsListener</code> to remove
     */
    public void removeChatStateListener(ChatStateNotificationsListener l)
    {
        OperationSetChatStateNotifications chatStateOpSet
                = mCurrentChatTransport.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

        if (chatStateOpSet != null) {
            chatStateOpSet.removeChatStateNotificationsListener(l);
        }
    }

    /**
     * Adds the given <code>ContactPresenceStatusListener</code> to listen for message events
     * in this chat session.
     *
     * @param l the <code>ContactPresenceStatusListener</code> to add
     */
    public void addContactStatusListener(ContactPresenceStatusListener l)
    {
        if (mMetaContact == null)
            return;

        Iterator<Contact> protoContacts = mMetaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.addContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Removes the given <code>ContactPresenceStatusListener</code> from this chat session.
     *
     * @param l the <code>ContactPresenceStatusListener</code> to remove
     */
    public void removeContactStatusListener(ContactPresenceStatusListener l)
    {
        if (mMetaContact == null)
            return;

        Iterator<Contact> protoContacts = mMetaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.removeContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Returns a collection of newly fetched last messages from store; merged with msgCache.
     *
     * @return a collection of last messages.
     */
    public List<ChatMessage> getHistory(boolean init)
    {
        // If chatFragment is initializing (or onResume) AND we have already cached the messages
        // i.e. (historyLoaded == true), then just return the current msgCache content.
        if (init && historyLoaded) {
            return msgCache;
        }

        // If the MetaHistoryService is not registered we have nothing to do here. The history store
        // could be "disabled" by the user via Chat History Logging option.
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();
        if (metaHistory == null)
            return msgCache;

        // descriptor can either be metaContact or chatRoomWrapper=>ChatRoom, from whom the history to be loaded
        Object descriptor = mDescriptor;
        if (descriptor instanceof ChatRoomWrapper) {
            descriptor = ((ChatRoomWrapper) descriptor).getChatRoom();

        }

        Collection<Object> history;
        // first time fetch, so read in last HISTORY_CHUNK_SIZE of history messages
        if (msgCache.isEmpty()) {
            // TODO: mamQuery(descriptor);
            history = metaHistory.findLast(chatHistoryFilter, descriptor, HISTORY_CHUNK_SIZE);
            historyLoaded = true;
        }
        // Read in HISTORY_CHUNK_SIZE records earlier than the 'last fetch date' i.e. top of the msgCache
        else {
            Date lastOldestMessageDate;
            synchronized (cacheLock) {
                lastOldestMessageDate = msgCache.get(0).getDate();
            }
            history = metaHistory.findLastMessagesBefore(chatHistoryFilter, descriptor,
                    lastOldestMessageDate, HISTORY_CHUNK_SIZE);

            // retrieve the history records from DB again; need to take care when
            // a. any history record is deleted
            // b. Message delivery receipt status
            // All currently are handle in updateCacheMessage(); do this just in case implementation not complete
            history.addAll(metaHistory.findByStartDate(chatHistoryFilter, descriptor, lastOldestMessageDate));
        }
        // Use CopyOnWriteArrayList instead to avoid ChatFragment#prependMessages ConcurrentModificationException
        List<ChatMessage> msgHistory = new CopyOnWriteArrayList<>();

        // Convert events into messages for display
        for (Object o : history) {
            if (o instanceof MessageDeliveredEvent) {
                msgHistory.add(ChatMessageImpl.getMsgForEvent((MessageDeliveredEvent) o));
            }
            else if (o instanceof MessageReceivedEvent) {
                msgHistory.add(ChatMessageImpl.getMsgForEvent((MessageReceivedEvent) o));
            }
            else if (o instanceof ChatRoomMessageDeliveredEvent) {
                msgHistory.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageDeliveredEvent) o));
            }
            else if (o instanceof ChatRoomMessageReceivedEvent) {
                msgHistory.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageReceivedEvent) o));
            }
            else if (o instanceof FileRecord) {
                msgHistory.add(ChatMessageImpl.getMsgForEvent((FileRecord) o));
            }
            else {
                Timber.e("Unexpected event in history: %s", o);
            }
        }

        synchronized (cacheLock) {
            // We have something cached and we need to merge it with the history.
            if (!msgCache.isEmpty()) {
                int msgAdded = mergeCachedMessage(msgHistory, msgCache);
                msgCache.clear();
                Timber.d("Number of new cached messages added: %s", msgAdded);
            }
            // The final message records are always in msgHistory
            msgCache.addAll(msgHistory);
        }
        return msgCache;
    }

    /**
     * TODO
     * @param descriptor
     */
    private void mamQuery(Object descriptor)
    {
        MamManager mamManager;
        XMPPConnection connection = getProtocolProvider().getConnection();
        OmemoManager omemoManager = OmemoManager.getInstanceFor(connection);
        EntityBareJid jid;

        try {
            if (descriptor instanceof ChatRoom) {
                mamManager = MamManager.getInstanceFor(((ChatRoom) descriptor).getMultiUserChat());
                jid = ((ChatRoom) descriptor).getIdentifier();

            }
            else {
                mamManager = MamManager.getInstanceFor(connection, null);
                jid = ((MetaContact) descriptor).getDefaultContact().getJid().asEntityBareJidIfPossible();
            }
            if (mamManager.isSupported()) {
                MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
                        .limitResultsToJid(jid)
                        // .limitResultsBefore(new Date())
                        .setResultPageSizeTo(10)
                        .queryLastPage()
                        .build();

                // Prevent OmemoManager from automatically decrypting MAM messages.
                // OmemoManager.getInstanceFor(connection).stopStanzaAndPEPListeners();

                MamManager.MamQuery query = mamManager.queryArchive(mamQueryArgs);
                List<Message> messages = query.getMessages();
                for (Message msg : messages) {
                    Timber.d("Message body: %s", msg.toXML());
                }

//                if (query.getMessageCount() > 0) {
//                    List<MessageOrOmemoMessage> decryptedMamQuery = omemoManager.decryptMamQueryResult(query);
//                    if (!decryptedMamQuery.isEmpty()) {
//                        decryptedMamQuery.get(decryptedMamQuery.size() - 1).getOmemoMessage().getBody();
//                    }
//                }
            }
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException // | IOException
                | SmackException.NotConnectedException | InterruptedException | SmackException.NotLoggedInException e) {
            Timber.e("MAM query: %s", e.getMessage());
        }
    }

    /**
     * Merge any new messages found in the cache to the history list; merging is done in reverse order
     * When historyLog is disabled, all the incoming/outgoing messages are only added to the msgCache;
     * These include the http upload and download messages but exclude file transfer messages
     * These must be retrieved and merged with the newly retrieve history record from DB.
     *
     * @param history contains the newly fetched history messages
     * @param cache contains the previous cached messages
     * @return Number of new messages found in cache
     */
    private int mergeCachedMessage(List<ChatMessage> history, List<ChatMessage> cache)
    {
        int count = 0;

        int cacheIdx = cache.size() - 1;
        int insertIdx = history.size();
        Date mergeDate = (insertIdx > 0) ? history.get(insertIdx - 1).getDate() : new Date();

        while (cacheIdx >= 0) {
            ChatMessage cacheMsg = cache.get(cacheIdx);
            if (cacheMsg.getDate().after(mergeDate)) {
                history.add(insertIdx, cacheMsg);
                cacheIdx--;
                count++;
            }
            else {
                // Must use the cached message instead of info found in history; the incoming file sharing is saved as
                // FileRecord only in history DB (chat closed), and does not contains file transfer info for proper file sharing.
                if (cacheMsg.getDate().equals(mergeDate)) {
                    history.set((insertIdx - 1), cacheMsg);
                    cacheIdx--;
                }

                // update new insertIdx and merged date for next comparison
                if (insertIdx > 0) {
                    insertIdx--;
                    if (insertIdx > 0)
                        mergeDate = history.get(insertIdx - 1).getDate();
                }
                // Just merge all the remaining cache messages if already at top of history
                else {
                    while (cacheIdx >= 0) {
                        history.add(0, cacheMsg);
                        if (cacheIdx > 0)
                            cacheMsg = cache.get(--cacheIdx);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Update the file transfer status in the msgCache; must do this else file transfer will be reactivate
     * on resume chat. Also important if historyLog is disabled.
     *
     * @param msgUuid ChatMessage uuid
     * @param status File transfer status
     * @param fileName the downloaded fileName
     * @param recordType File record type see ChatMessage MESSAGE_FILE_
     */
    public void updateCacheFTRecord(String msgUuid, int status, String fileName, int encType, int recordType)
    {
        int cacheIdx = msgCache.size() - 1;
        while (cacheIdx >= 0) {
            ChatMessageImpl cacheMsg = (ChatMessageImpl) msgCache.get(cacheIdx);
            // 20220709: cacheMsg.getMessageUID() can be null
            if (msgUuid.equals(cacheMsg.getMessageUID())) {
                cacheMsg.updateFTStatus(mDescriptor, msgUuid, status, fileName, encType, recordType, cacheMsg.getMessageDir());
                break;
            }
            cacheIdx--;
        }
    }

    /**
     * Method to remove cached message, or to update the cached message receiptStatus of the given msgUuid
     *
     * @param msgUuid ChatMessage uuid
     * @param receiptStatus message receipt status to update; null is to delete message
     */
    public void updateCacheMessage(String msgUuid, Integer receiptStatus)
    {
        int cacheIdx = msgCache.size() - 1;
        while (cacheIdx >= 0) {
            ChatMessageImpl cacheMsg = (ChatMessageImpl) msgCache.get(cacheIdx);
            if (msgUuid.equals(cacheMsg.getMessageUID())) {
                if (receiptStatus == null)
                    msgCache.remove(cacheIdx);
                else
                    cacheMsg.setReceiptStatus(receiptStatus);
                break;
            }
            cacheIdx--;
        }
    }

    /**
     * Implements the <code>Chat.isChatFocused</code> method. Returns TRUE if this chat is
     * the currently selected and if the chat window, where it's contained is active.
     * NPE: mChatSession == null from field
     *
     * @return true if this chat has the focus and false otherwise.
     */
    @Override
    public boolean isChatFocused()
    {
        return (mChatSession != null)
                && mChatSession.getChatId().equals(ChatSessionManager.getCurrentChatId());
    }

    /**
     * Returns the message written by user in the chat write area.
     *
     * @return the message written by user in the chat write area
     */
    @Override
    public String getMessage()
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Bring this chat to front if <code>b</code> is true, hide it otherwise.
     *
     * @param isVisible tells if the chat will be made visible or not.
     */
    @Override
    public void setChatVisible(boolean isVisible)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Sets the given message as a message in the chat write area.
     *
     * @param message the text that would be set to the chat write area
     */
    @Override
    public void setMessage(String message)
    {
        throw new RuntimeException("Not supported yet");
        //??? chatController.msgEdit.setText(message);
    }

    /**
     * Sends the message and blocked message caching for this message; otherwise the single send message
     * will appear twice in the chat fragment i.e. inserted and cached e.g. from share link
     *
     * @param message the text string to be sent
     * @param encType The encType of the message to be sent: RemoteOnly | 1=text/html or 0=text/plain.
     */
    public void sendMessage(String message, int encType)
    {
        cacheBlocked = true;

        int encryption = IMessage.ENCRYPTION_NONE;
        if (isOmemoChat())
            encryption = IMessage.ENCRYPTION_OMEMO;
        else if (isOTRChat())
            encryption = IMessage.ENCRYPTION_OTR;

        try {
            mCurrentChatTransport.sendInstantMessage(message, encryption | encType);
        } catch (Exception ex) {
            aTalkApp.showToastMessage(ex.getMessage());
        }
    }

    /**
     * Add a message to this <code>Chat</code>. Mainly use for System messages for internal generated messages
     *
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type of the message
     * @param encType the content encode type i.e plain or html
     * @param content the message text
     */
    @Override
    public void addMessage(String contactName, Date date, int messageType, int encType, String content)
    {
        addMessage(new ChatMessageImpl(contactName, contactName, date, messageType, encType, content, null, ChatMessage.DIR_IN));
    }

    /**
     * Add a message to this <code>Chat</code>.
     *
     * @param contactName the name of the contact sending the message
     * @param displayName the display name of the contact
     * @param date the time at which the message is sent or received
     * @param chatMsgType the type of the message. See ChatMessage
     * @param message the IMessage.
     */
    public void addMessage(String contactName, String displayName, Date date, int chatMsgType,
            IMessage message, String correctedMessageUID)
    {
        addMessage(new ChatMessageImpl(contactName, displayName, date, chatMsgType, message, correctedMessageUID, ChatMessage.DIR_IN));
    }

    /**
     * Adds a chat message to this <code>Chat</code> panel.
     *
     * @param chatMessage the ChatMessage.
     */
    public void addMessage(ChatMessageImpl chatMessage)
    {
        // Must always cache the chatMsg as chatFragment has not registered to handle incoming
        // message on first onAttach or when it is not in focus.
        cacheNextMsg(chatMessage);
        messageSpeak(chatMessage, 2 * ttsDelay);  // for chatRoom

        for (ChatSessionListener l : msgListeners) {
            l.messageAdded(chatMessage);
        }
    }

    /**
     * Caches next message when chat is not in focus and it is not being blocked via sendMessage().
     * Otherwise duplicated messages when share link
     *
     * @param newMsg the next message to cache.
     */
    private void cacheNextMsg(ChatMessageImpl newMsg)
    {
        // Timber.d("Cache blocked is %s for: %s", cacheBlocked, newMsg.getMessage());
        if (!cacheBlocked) {
            synchronized (cacheLock) {
                msgCache.add(newMsg);
            }
        }
        cacheBlocked = false;
    }

    public boolean isChatTtsEnable()
    {
        return isChatTtsEnable;
    }

    public void updateChatTtsOption()
    {
        isChatTtsEnable = ConfigurationUtils.isTtsEnable();
        if (isChatTtsEnable) {
            // Object mDescriptor = mChatSession.getDescriptor();
            if (mDescriptor instanceof MetaContact) {
                isChatTtsEnable = ((MetaContact) mDescriptor).getDefaultContact().isTtsEnable();
            }
            else {
                isChatTtsEnable = ((ChatRoomWrapper) mDescriptor).isTtsEnable();
            }
        }
        // refresh the tts delay time
        ttsDelay = ConfigurationUtils.getTtsDelay();
    }

    private void messageSpeak(ChatMessage msg, int delay)
    {
        Timber.d("Chat TTS message speak: %s = %s", isChatTtsEnable, msg.getMessage());
        if (!isChatTtsEnable)
            return;

        // ChatRoomMessageReceivedEvent from conference room
        if (ChatMessage.MESSAGE_IN == msg.getMessageType()
                || ChatMessage.MESSAGE_MUC_IN == msg.getMessageType()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Timber.w("TTS speak wait exception: %s", e.getMessage());
            }
            ttsSpeak(msg);
        }
    }

    /**
     * call TTS to speak the text given in chatMessage if it is not HttpDownloadLink
     *
     * @param chatMessage ChatMessage for TTS
     */
    public void ttsSpeak(ChatMessage chatMessage)
    {
        String textBody = chatMessage.getMessage();
        if (!TextUtils.isEmpty(textBody) && !FileBackend.isHttpFileDnLink(textBody)) {
            Intent spkIntent = new Intent(aTalkApp.getInstance(), TTSService.class);
            spkIntent.putExtra(TTSService.EXTRA_MESSAGE, textBody);
            spkIntent.putExtra(TTSService.EXTRA_QMODE, false);
            aTalkApp.getInstance().startService(spkIntent);
        }
    }

    /**
     * Send an outgoing file message to chatFragment for it to start the file send process
     * The recipient can be contact or chatRoom
     *
     * @param filePath as message content of the file to be sent
     * @param messageType indicate which File transfer message is for
     */
    public void addFTSendRequest(String filePath, int messageType)
    {
        String sendTo;
        Date date = Calendar.getInstance().getTime();

        // Create the new msg Uuid for record saved in dB
        String msgUuid = String.valueOf(System.currentTimeMillis()) + hashCode();

        Object sender = mCurrentChatTransport.getDescriptor();
        if (sender instanceof Contact) {
            sendTo = ((Contact) sender).getAddress();
        }
        else {
            sendTo = ((ChatRoom) sender).getName();
        }

        // Do not use addMessage to avoid TTS activation for outgoing file message
        ChatMessageImpl chatMsg = new ChatMessageImpl(sendTo, sendTo, date, messageType, IMessage.ENCODE_PLAIN, filePath, msgUuid, ChatMessage.DIR_OUT);
        cacheNextMsg(chatMsg);
        for (ChatSessionListener l : msgListeners) {
            l.messageAdded(chatMsg);
        }
    }

    /**
     * ChatMessage for IncomingFileTransferRequest
     *
     * Adds the given <code>IncomingFileTransferRequest</code> to the conversation panel in order to
     * notify the user of an incoming file transfer request.
     *
     * @param opSet the file transfer operation set
     * @param request the request to display in the conversation panel
     * @param date the date on which the request has been received
     * @see FileTransferActivator#fileTransferRequestReceived(FileTransferRequestEvent)
     */
    public void addFTReceiveRequest(OperationSetFileTransfer opSet, IncomingFileTransferRequest request, Date date)
    {
        Contact sender = request.getSender();
        String senderName = sender.getAddress();
        String msgContent = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, date.toString(), senderName);

        int msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
        int encType = IMessage.ENCODE_PLAIN;
        ChatMessageImpl chatMsg = new ChatMessageImpl(senderName, date, msgType, encType,
                msgContent, request.getID(), ChatMessage.DIR_IN, opSet, request, null);

        // Do not use addMessage to avoid TTS activation for incoming file message
        cacheNextMsg(chatMsg);
        for (ChatSessionListener l : msgListeners) {
            l.messageAdded(chatMsg);
        }
    }

    /**
     * Adds a new ChatLinkClickedListener. The callback is called for every link whose scheme is
     * <code>jitsi</code>. It is the callback's responsibility to filter the action based on the URI.
     *
     * Example:<br>
     * <code>jitsi://classname/action?query</code><br>
     * Use the name of the registering class as the host, the action to execute as the path and
     * any parameters as the query.
     *
     * @param chatLinkClickedListener callback that is notified when a link was clicked.
     */
    @Override
    public void addChatLinkClickedListener(ChatLinkClickedListener chatLinkClickedListener)
    {
        ChatSessionManager.addChatLinkListener(chatLinkClickedListener);
    }

    @Override
    public void messageReceived(MessageReceivedEvent messageReceivedEvent)
    {
        // cmeng: only handle messageReceivedEvent belongs to this.metaContact
        if ((mMetaContact != null) && mMetaContact.containsContact(messageReceivedEvent.getSourceContact())) {
            // Must cache chatMsg as chatFragment has not registered to handle incoming
            // message on first onAttach or not in focus

            ChatMessageImpl chatMessage = ChatMessageImpl.getMsgForEvent(messageReceivedEvent);
            cacheNextMsg(chatMessage);
            for (MessageListener l : msgListeners) {
                l.messageReceived(messageReceivedEvent);
            }
            messageSpeak(chatMessage, ttsDelay);
        }
    }

    @Override
    public void messageDelivered(MessageDeliveredEvent messageDeliveredEvent)
    {
        /*
         * (metaContact == null) for ConferenceChatTransport. Check just in case the listener is not properly
         * removed when the chat is closed. Only handle messageReceivedEvent belongs to this.metaContact
         */
        if ((mMetaContact != null) && mMetaContact.containsContact(messageDeliveredEvent.getDestinationContact())) {

            // return if delivered message does not required local display in chatWindow nor cached
            if (messageDeliveredEvent.getSourceMessage().isRemoteOnly())
                return;

            cacheNextMsg(ChatMessageImpl.getMsgForEvent(messageDeliveredEvent));
            for (MessageListener l : msgListeners) {
                l.messageDelivered(messageDeliveredEvent);
            }
        }
    }

    @Override
    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt)
    {
        for (MessageListener l : msgListeners) {
            l.messageDeliveryFailed(evt);
        }

        // Insert error message
        Timber.d("%s", evt.getReason());

        // Just show the pass in error message if false
        boolean mergeMessage = true;
        String errorMsg;
        IMessage srcMessage = (IMessage) evt.getSource();

        // contactJid cannot be nick name, otherwise message will not be displayed
        String contactJid = evt.getDestinationContact().getAddress();

        switch (evt.getErrorCode()) {
            case MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED:
                errorMsg = aTalkApp.getResString(
                        R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED, contactJid);
                break;
            case MessageDeliveryFailedEvent.NETWORK_FAILURE:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED);
                break;
            case MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
                break;
            case MessageDeliveryFailedEvent.INTERNAL_ERROR:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR);
                break;
            case MessageDeliveryFailedEvent.OMEMO_SEND_ERROR:
                errorMsg = evt.getReason();
                mergeMessage = false;
                break;
            default:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_ERROR);
        }

        String reason = evt.getReason();
        if (!TextUtils.isEmpty(reason) && mergeMessage) {
            errorMsg += " " + aTalkApp.getResString(R.string.service_gui_ERROR_WAS, reason);
        }
        addMessage(contactJid, new Date(), ChatMessage.MESSAGE_OUT, srcMessage.getMimeType(), srcMessage.getContent());
        addMessage(contactJid, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg);
    }

    /**
     * Extends <code>MessageListener</code> interface in order to provide notifications about injected
     * messages without the need of event objects.
     *
     * @author Pawel Domas
     */
    interface ChatSessionListener extends MessageListener
    {
        void messageAdded(ChatMessage msg);

    }

    /**
     * Updates the status of the given chat transport in the send via selector box and notifies
     * the user for the status change.
     *
     * @param chatTransport the <code>chatTransport</code> to update
     */
    public void updateChatTransportStatus(final ChatTransport chatTransport)
    {
        if (isChatFocused()) {
            final AppCompatActivity activity = aTalkApp.getCurrentActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    PresenceStatus presenceStatus = chatTransport.getStatus();
                    ActionBarUtil.setSubtitle(activity, presenceStatus.getStatusName());
                    ActionBarUtil.setStatus(activity, presenceStatus.getStatusIcon());
                });
            }
        }

        String contactName = ((MetaContactChatTransport) chatTransport).getContact().getAddress();
        if (ConfigurationUtils.isShowStatusChangedInChat()) {
            // Show a status message to the user.
            // addMessage(contactName, chatTransport.getName(), new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
            //        aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.getStatus().getStatusName()),
            //        IMessage.ENCRYPTION_NONE, null, null);
            addMessage(contactName, new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.getStatus().getStatusName()));
        }
    }

    /**
     * Renames all occurrences of the given <code>chatContact</code> in this chat panel.
     *
     * @param chatContact the contact to rename
     * @param name the new name
     */
    public void setContactName(ChatContact<?> chatContact, final String name)
    {
        if (isChatFocused()) {
            final AppCompatActivity activity = aTalkApp.getCurrentActivity();
            activity.runOnUiThread(() -> {
                if (mChatSession instanceof MetaContactChatSession) {
                    ActionBarUtil.setTitle(activity, name);
                }
            });
        }
    }

    /**
     * Sets the given <code>subject</code> to this chat.
     *
     * @param subject the subject to set
     */
    public void setChatSubject(final String subject, String oldSubject)
    {
        if ((subject != null) && !subject.equals(chatSubject)) {
            chatSubject = subject;

            if (isChatFocused()) {
                final AppCompatActivity activity = aTalkApp.getCurrentActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        // cmeng: check instanceof just in case user change chat session
                        if (mChatSession instanceof ConferenceChatSession) {
                            ActionBarUtil.setSubtitle(activity, subject);
                        }
                    });
                }
            }
            // Do not display change subject message if this is the original subject
            if (!TextUtils.isEmpty(oldSubject))
                this.addMessage(mChatSession.getChatEntity(), new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                        aTalkApp.getResString(R.string.service_gui_CHATROOM_SUBJECT_CHANGED, oldSubject, subject));
        }
    }

    /**
     * Updates the contact status - call from conference only.
     *
     * @param chatContact the chat contact of the conference to update
     * @param statusMessage the status message to show
     */
    public void updateChatContactStatus(final ChatContact<?> chatContact, final String statusMessage)
    {
        if (StringUtils.isNotEmpty(statusMessage)) {
            String contactName = ((ChatRoomMemberJabberImpl) chatContact.getDescriptor()).getContactAddress();
            addMessage(contactName, new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN, statusMessage);
        }
    }

    /**
     * Returns the first chat transport for the current chat session that supports group chat.
     *
     * @return the first chat transport for the current chat session that supports group chat.
     */
    public ChatTransport findInviteChatTransport()
    {
        ProtocolProviderService protocolProvider = mCurrentChatTransport.getProtocolProvider();

        // We choose between OpSets for multi user chat...
        if (protocolProvider.getOperationSet(OperationSetMultiUserChat.class) != null
                || protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class) != null) {
            return mCurrentChatTransport;
        }

        else {
            Iterator<ChatTransport> chatTransportsIter = mChatSession.getChatTransports();
            while (chatTransportsIter.hasNext()) {
                ChatTransport chatTransport = chatTransportsIter.next();

                Object groupChatOpSet = chatTransport.getProtocolProvider().getOperationSet
                        (OperationSetMultiUserChat.class);
                if (groupChatOpSet != null)
                    return chatTransport;
            }
        }
        return null;
    }

    /**
     * Invites the given <code>chatContacts</code> to this chat.
     *
     * @param inviteChatTransport the chat transport to use to send the invite
     * @param chatContacts the contacts to invite
     * @param reason the reason of the invitation
     */
    public void inviteContacts(ChatTransport inviteChatTransport, Collection<String> chatContacts, String reason)
    {
        ProtocolProviderService pps = inviteChatTransport.getProtocolProvider();

        if (mChatSession instanceof MetaContactChatSession) {
            ConferenceChatManager conferenceChatManager = AndroidGUIActivator.getUIService().getConferenceChatManager();

            // the chat session is set regarding to which OpSet is used for MUC
            if (pps.getOperationSet(OperationSetMultiUserChat.class) != null) {
                ChatRoomWrapper chatRoomWrapper
                        = MUCActivator.getMUCService().createPrivateChatRoom(pps, chatContacts, reason, false);
                if (chatRoomWrapper != null) {
                    // conferenceChatSession = new ConferenceChatSession(this, chatRoomWrapper);
                    Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                    aTalkApp.getGlobalContext().startActivity(chatIntent);
                }
                else {
                    Timber.e("Failed to create chatroom");
                }
            }
            else if (pps.getOperationSet(OperationSetAdHocMultiUserChat.class) != null) {
                AdHocChatRoomWrapper chatRoomWrapper
                        = conferenceChatManager.createAdHocChatRoom(pps, chatContacts, reason);
                // conferenceChatSession = new AdHocConferenceChatSession(this, chatRoomWrapper);
                Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                aTalkApp.getGlobalContext().startActivity(chatIntent);
            }
            // if (conferenceChatSession != null) {
            //   this.setChatSession(conferenceChatSession);
            // }
        }
        // We're already in a conference chat.
        else {
            for (String contactAddress : chatContacts) {
                try {
                    mCurrentChatTransport.inviteChatContact(JidCreate.entityBareFrom(contactAddress), reason);
                } catch (XmppStringprepException e) {
                    Timber.w("Group chat invitees Jid create error: %s, %s", contactAddress, e.getMessage());
                }
            }
        }
    }
}
