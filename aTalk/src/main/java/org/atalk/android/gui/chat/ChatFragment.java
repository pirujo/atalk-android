/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.*;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.LinearLayout.LayoutParams;

import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.chat.filetransfer.*;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.HtmlImageGetter;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.crypto.CryptoFragment;
import org.atalk.crypto.listener.CryptoModeChangeListener;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import timber.log.Timber;

/**
 * The <tt>ChatFragment</tt> working in conjunction with ChatActivity, ChatPanel, ChatController
 * etc is providing the UI for all the chat messages/info receive and display.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatFragment extends OSGiFragment implements ChatSessionManager.CurrentChatListener,
        FileTransferStatusListener, CryptoModeChangeListener, ReceiptReceivedListener
{
    /**
     * The session adapter for the contained <tt>ChatPanel</tt>.
     */
    private ChatListAdapter chatListAdapter;

    /**
     * The corresponding <tt>ChatPanel</tt>.
     */
    private ChatPanel chatPanel;

    /**
     * chat MetaContact associated with the chatFragment
     */
    private MetaContact mChatMetaContact;

    /**
     * The chat list view representing the chat.
     */
    private ListView chatListView;

    /**
     * List header used to display progress bar when history is being loaded.
     */
    private View header;

    /**
     * Remembers first visible view to scroll the list after new portion of history messages is added.
     */
    private int scrollFirstVisible;

    /**
     * Remembers top position to add to the scrolling offset after new portion of history messages is added.
     */
    private int scrollTopOffset;

    /**
     * The chat state view.
     */
    private LinearLayout chatStateView;

    /**
     * The task that loads history.
     */
    private LoadHistoryTask loadHistoryTask;

    /**
     * Stores all active file transfer requests and effective transfers with the identifier of the transfer.
     */
    private final Hashtable<String, Object> activeFileTransfers = new Hashtable<>();

    /**
     * Stores all active file transfer requests and effective DisplayMessage position.
     */
    private final Hashtable<String, Integer> activeMsgTransfers = new Hashtable<>();

    /**
     * Indicates that this fragment is the currently selected primary page. A primary page can
     * have both onShow and onHide (overlay with other dialog) state. This setting is important,
     * because of PagerAdapter is being used on phone layouts.
     *
     * @see #setPrimarySelected(boolean)
     * @see #onResume()
     */
    private boolean primarySelected = false;

    // Message chatType definitions - persistent storge constants
    public final static int MSGTYPE_UNKNOWN = 0x0;
    public final static int MSGTYPE_NORMAL = 0x1;

    public final static int MSGTYPE_OMEMO = 0x02;
    public final static int MSGTYPE_OMEMO_UT = 0x42;
    public final static int MSGTYPE_OMEMO_UA = 0x82;

    public final static int MSGTYPE_OTR = 0x03;
    public final static int MSGTYPE_OTR_UA = 0x83;

    public final static int MSGTYPE_MUC_NORMAL = 0x04;

    /*
     * Current chatType that is in use.
     */
    private int mChatType = MSGTYPE_UNKNOWN;

    private View mCFView;
    private boolean clearMsgCache = false;

    /**
     * The chat controller used to handle operations like editing and sending messages associated with this fragment.
     */
    private ChatController chatController;

    /**
     * The current chat transport.
     */
    private ChatTransport currentChatTransport;

    /**
     * The current chatFragment.
     */
    private ChatFragment currentChatFragment;

    /**
     * The current cryptoFragment.
     */
    private CryptoFragment mCryptoFragment;

    // private static int COUNTDOWN_INTERVAL = 1000; // ms

    /**
     * Flag indicates that we have loaded the history for the first time.
     */
    private boolean historyLoaded = false;

    private Activity mActivity = null;
    private FragmentActivity mFragmentActivity = null;

    private boolean mSVP_Started;
    private Object mSVP = null;
    private SvpApiImpl svpApi = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        this.chatController = new ChatController(activity, this);
        mActivity = activity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mCFView = inflater.inflate(R.layout.chat_conversation, container, false);
        chatListAdapter = new ChatListAdapter();
        chatListView = mCFView.findViewById(R.id.chatListView);

        // Inflates and adds the header, hidden by default
        this.header = inflater.inflate(R.layout.progressbar, chatListView, false);
        header.setVisibility(View.GONE);
        chatListView.addHeaderView(header);

        chatStateView = mCFView.findViewById(R.id.chatStateView);
        chatListView.setAdapter(chatListAdapter);
        chatListView.setSelector(R.drawable.array_list_selector);
        initListViewListeners();

        // Chat intent handling - chatId should not be null
        String chatId = null;
        Bundle arguments = getArguments();
        if (arguments != null)
            chatId = arguments.getString(ChatSessionManager.CHAT_IDENTIFIER);
        if (chatId == null)
            throw new IllegalArgumentException();

        chatPanel = ChatSessionManager.getActiveChat(chatId);
        if (chatPanel == null) {
            Timber.e("Chat for given id: %s not exists", chatId);
            return null;
        }

        // mChatMetaContact is null for conference
        mChatMetaContact = chatPanel.getMetaContact();
        chatPanel.addMessageListener(chatListAdapter);
        currentChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
        currentChatFragment = this;

        ProtocolProviderService pps = currentChatTransport.getProtocolProvider();
        if ((mChatMetaContact != null) && pps.isRegistered()) {
            DeliveryReceiptManager deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(pps.getConnection());
            deliveryReceiptManager.addReceiptReceivedListener(this);
        }

        mFragmentActivity = getActivity();
        if (mFragmentActivity != null) {
            FragmentManager fragmentMgr = mFragmentActivity.getSupportFragmentManager();
            mCryptoFragment = (CryptoFragment) fragmentMgr.findFragmentByTag(ChatActivity.CRYPTO_FRAGMENT);
        }
        return mCFView;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initListViewListeners()
    {
        chatListView.setOnScrollListener(new AbsListView.OnScrollListener()
        {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState)
            {
                // Proceed only if there is no active file transfer in progress
                // Detects event when user scrolls to the top of the list
                View childFirst = chatListView.getChildAt(0);
                if ((activeFileTransfers.size() == 0) && (scrollState == 0) && childFirst != null) {
                    if (childFirst.getTop() == 0) {
                        // Loads some more history if there's no loading task in progress
                        if (loadHistoryTask == null) {
                            loadHistoryTask = new LoadHistoryTask(false);
                            loadHistoryTask.execute();
                        }
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
            {
                // Remembers scrolling position to restore after new history messages are loaded
                scrollFirstVisible = firstVisibleItem;
                View firstVisible = view.getChildAt(0);
                scrollTopOffset = firstVisible != null ? firstVisible.getTop() : 0;
            }
        });

        chatListView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                chatController.onTouchAction();
            }
            return false;
        });

        // Using the contextual action mode with multi-selection
        chatListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        chatListView.setMultiChoiceModeListener(mMultiChoiceListener);

        chatListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Toast.makeText(getContext(), R.string.chat_message_long_press_hint, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * Creates new parametrized instance of <tt>ChatFragment</tt>.
     *
     * @param chatId optional phone number that will be filled.
     * @return new parametrized instance of <tt>ChatFragment</tt>.
     */
    public static ChatFragment newInstance(String chatId)
    {
        ChatFragment chatFragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ChatSessionManager.CHAT_IDENTIFIER, chatId);
        chatFragment.setArguments(args);
        return chatFragment;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (svpApi == null)
            svpApi = new SvpApiImpl();

        /*
         * If this chatFragment is added to the pager adapter for the first time it is required to
         * check again, because it's marked visible when the Views are not created yet
         *
         * cmeng - seeing problem as it includes other non-focus chatFragment causing non-sync
         * between chatFragment and chatController i.e. msg sent to wrong chatFragment
         * - resolved with initChatController() passing focus state as parameter, taking
         * appropriate actions pending the focus state;
         *
         *  Perform chatTransport changes only if and only if this chatFragment is the
         *  selected primary page - notified by chatPager (Check at ChatController)
         */
        if (primarySelected) {
            initChatController(true);

            // Invoke the listener valid only of metaContactChatSession
            if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
                chatPanel.addContactStatusListener(chatListAdapter);
                chatPanel.addChatStateListener(chatListAdapter);
            }

            ChatSessionManager.addCurrentChatListener(this);
            mSVP_Started = false;
            mSVP = null;
        }
    }

    /**
     * Need to clear msgCache onPause if set, forcing chatPanel to reload messages from DB on Resume i.e.
     * a. Use FileRecord
     * b. Message delivery receipt status
     * Any MessageDisplay status update during chatSession is only reflected in DisplayMessage messages (for scrolling)
     *
     * @see ChatPanel#clearMsgCache()
     */
    @Override
    public void onPause()
    {
        // Remove the listener valid only of metaContactChatSession
        if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
            chatPanel.removeContactStatusListener(chatListAdapter);
            chatPanel.removeChatStateListener(chatListAdapter);
        }

        // Not required - implemented as static map
        // cryptoFragment.removeCryptoModeListener(this);
        ChatSessionManager.removeCurrentChatListener(this);

        /*
         * Indicates that this fragment is no longer in focus, because of this call parent
         * <tt>Activities don't have to call it in onPause().
         */
        initChatController(false);

        if (clearMsgCache) {
            chatPanel.clearMsgCache();
        }
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        chatController.onChatCloseAction();
    }

    @Override
    public void onDetach()
    {
        Timber.d("Detach chatFragment: %s", this);
        super.onDetach();
        this.chatController = null;

        if (chatPanel != null) {
            chatPanel.removeMessageListener(chatListAdapter);
        }

        chatListAdapter = null;
        if (loadHistoryTask != null) {
            loadHistoryTask.cancel(true);
            loadHistoryTask = null;
        }
    }

    /**
     * This method must be called by parent <tt>Activity</tt> or <tt>Fragment</tt> in order to
     * register the ChatController. Setting of primarySelected must solely be performed by
     * chatPagerAdapter only to ensure both the chatFragment and chatController are in sync.
     *
     * @param isSelected <tt>true</tt> if the fragment is now the primary selected page.
     * @see ChatController #initChatController()
     */
    public void setPrimarySelected(boolean isSelected)
    {
        // Timber.d("Primary page selected: %s %s", hashCode(), isSelected);
        primarySelected = isSelected;
        initChatController(isSelected);
    }

    /**
     * Checks for <tt>ChatController</tt> initialization. To init/activate the controller fragment
     * must be visible and its View must be created.
     *
     * Non-focus chatFragment causing non-sync between chatFragment and chatController i.e.
     * sending & received messages sent to wrong chatFragment - resolved with initChatController()
     * passing in focus state as parameter; taking the appropriate actions pending the focus state;
     */
    private void initChatController(boolean inFocus)
    {
        // chatController => NPE from field
        if ((chatController != null)) {
            if (!inFocus) {
                chatController.onHide();
                // Also remove global status listener
                AndroidGUIActivator.getLoginRenderer().removeGlobalStatusListener(globalStatusListener);
            }
            else if (chatListView != null) {
                Timber.d("Init controller: %s", hashCode());
                chatController.onShow();
                // Also register global status listener
                AndroidGUIActivator.getLoginRenderer().addGlobalStatusListener(globalStatusListener);

                // Init the history & ChatType background color
                chatStateView.setVisibility(View.INVISIBLE);
                initAdapter();

                // Seem mCFView changes on re-entry into chatFragment, so update the listener
                mCryptoFragment.addCryptoModeListener(currentChatTransport.getDescriptor(), this);
                // initBackgroundColor();
                changeBackground(mCFView, chatPanel.getChatType());
            }
        }
        else {
            Timber.d("Skipping null controller init...");
        }
    }

    /**
     * Initializes the chat list adapter.
     */
    private void initAdapter()
    {
        /*
         * Initial history load is delayed until the chat is displayed to the user. We previously
         * relayed on onCreate, but it will be called too early on phone layouts where
         * ChatPagerAdapter is used. It creates ChatFragment too early that is before the first
         * message is added to the history and we are unable to retrieve it without hacks.
         */
        if (!historyLoaded) {
            /*
             * chatListAdapter.isEmpty() is used as initActive flag, chatPanel.msgCache must be
             * cleared. Otherwise it will cause chatPanel.getHistory to return old data when the
             * underlying data changed or adapter has been cleared
             */
            loadHistoryTask = new LoadHistoryTask(chatListAdapter.isEmpty());
            loadHistoryTask.execute();
            historyLoaded = true;
        }
    }

    /**
     * Tasks that need to be performed for the current chatFragment when chat history are deleted.
     * - Cancel any file transfer in progress
     * - Clean up all the display messages that are in the chatList Adapter
     * - Clear all the message that has been previously stored in the msgCache
     * - Force to reload history by clearing historyLoad = false;
     * - Refresh the display
     */
    public void onClearCurrentEntityChatHistory()
    {
        cancelActiveFileTransfers();

        // check to ensure chatListAdapter has not been destroyed before proceed (NPE from field)
        if (chatListAdapter != null)
            chatListAdapter.clearMessage();

        chatPanel.clearMsgCache();
        historyLoaded = false;
        initChatController(true);
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private AbsListView.MultiChoiceModeListener mMultiChoiceListener = new AbsListView.MultiChoiceModeListener()
    {
        int cPos;
        int headerCount;
        int checkListSize;
        SparseBooleanArray checkedList;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked)
        {
            // Here you can do something when items are selected/de-selected
            checkedList = chatListView.getCheckedItemPositions();
            checkListSize = checkedList.size();

            // Position must be aligned to the number of header views included and
            // ensure the selected view is the last MESSAGE_OUT for edit action
            cPos = position - headerCount;
            boolean visible = false;
            if ((currentChatTransport instanceof MetaContactChatTransport)
                    && (chatListAdapter.getItemViewType(cPos) == ChatListAdapter.OUTGOING_MESSAGE_VIEW)) {
                visible = true;
                if (cPos != (chatListAdapter.getCount() - 1)) {
                    for (int i = cPos + 1; i < chatListAdapter.getCount(); i++) {
                        if (chatListAdapter.getItemViewType(i) == ChatFragment.ChatListAdapter.OUTGOING_MESSAGE_VIEW) {
                            visible = false;
                            break;
                        }
                    }
                }
            }
            mode.getMenu().findItem(R.id.chat_message_edit).setVisible(visible);
            mode.getMenu().findItem(R.id.select_all).setVisible(chatListAdapter.getCount() > 1);
            mode.invalidate();
            chatListView.setSelection(position);
            mode.setTitle(chatListView.getCheckedItemCount() + " selected");
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            ChatMessage chatMsg;
            switch (item.getItemId()) {
                case R.id.chat_message_edit:
                    chatMsg = chatListAdapter.getMessage(cPos);
                    if (chatController != null) {
                        chatController.editText(chatListView, chatMsg, cPos);
                    }
                    return true;

                case R.id.select_all:
                    int size = chatListAdapter.getCount();
                    if (size < 2)
                        return true;

                    for (int i = 0; i < size; i++) {
                        cPos = i + headerCount;
                        checkedList.put(cPos, true);
                        chatListView.setSelection(cPos);
                    }
                    checkListSize = size;
                    mode.invalidate();
                    mode.setTitle(size + " selected");
                    return true;

                case R.id.copy_to_clipboard:
                    // Get clicked message text and copy it to ClipBoard
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            chatMsg = chatListAdapter.getMessage(cPos);
                            if (chatMsg != null) {
                                if (i > 0)
                                    text.append("\n").append(chatMsg.getContentForClipboard());
                                else
                                    text.append(chatMsg.getContentForClipboard());
                            }
                        }
                    }
                    ClipboardManager cmgr = (ClipboardManager) mFragmentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cmgr != null)
                        cmgr.setPrimaryClip(ClipData.newPlainText(null, text));
                    // Action picked, so close the CAB
                    mode.finish();
                    return true;

                case R.id.chat_message_del:
                    List<String> msgUidDel = new ArrayList<>();
                    List<File> msgFilesDel = new ArrayList<>();
                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            int cType = chatListAdapter.getItemViewType(cPos);
                            if ((cType == ChatListAdapter.INCOMING_MESSAGE_VIEW)
                                    || (cType == ChatListAdapter.OUTGOING_MESSAGE_VIEW)
                                    || (cType == ChatListAdapter.FILE_TRANSFER_MESSAGE_VIEW)) {
                                chatMsg = chatListAdapter.getMessage(cPos);
                                if (chatMsg != null) {
                                    if (chatMsg instanceof MergedMessage) {
                                        msgUidDel.addAll(((MergedMessage) chatMsg).getMessageUIDs());
                                    }
                                    else {
                                        msgUidDel.add(chatMsg.getMessageUID());
                                        // check and add only all incoming received media files for deletion
                                        if (cType == ChatListAdapter.FILE_TRANSFER_MESSAGE_VIEW) {
                                            int chatMsgType = chatMsg.getMessageType();
                                            boolean xferIn = (ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE == chatMsgType);
                                            // should not contain any local file if it is http file download
                                            // || (ChatMessage.MESSAGE_HTTP_FILE_LINK == chatMsgType));

                                            File file;
                                            // file is in chatHistory fileRecord or in chatMsg if yet to be converted
                                            if (chatMsg.getFileRecord() != null) {
                                                xferIn = FileRecord.IN.equals(chatMsg.getFileRecord().getDirection());
                                                file = chatMsg.getFileRecord().getFile();
                                            }
                                            else if ((file = chatListAdapter.getFileName(cPos)) == null) {
                                                // always get any in/out "voice-" file to be deleted
                                                file = new File(chatMsg.getMessage());
                                            }
                                            // always add voice files and incoming media file xfer only for deletion
                                            if ((file != null) && file.exists()
                                                    && (file.getName().startsWith("voice-") || xferIn)) {
                                                msgFilesDel.add(file);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    EntityListHelper.eraseEntityChatHistory(mFragmentActivity,
                            chatPanel.getChatSession().getDescriptor(), msgUidDel, msgFilesDel);
                    // Action picked, so close the CAB
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the action mActionMode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            // Inflate the menu for the CAB
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.chat_msg_ctx_menu, menu);
            headerCount = chatListView.getHeaderViewsCount();
            return true;
        }

        // Called each time the action mActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mActionMode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            // Here you can perform updates to the CAB due to an invalidate() request
            return false; // Return false if nothing is done
        }

        // Called when the user exits the action mActionMode
        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            // ActionMode mActionMode = null;
        }
    };

    /**
     * Refresh avatar and globals status display on change.
     */
    private EventListener<PresenceStatus> globalStatusListener = new EventListener<PresenceStatus>()
    {
        @Override
        public void onChangeEvent(PresenceStatus eventObject)
        {
            if (chatListAdapter != null)
                chatListAdapter.localAvatarOrStatusChanged();
        }
    };

    /**
     * Returns the corresponding <tt>ChatPanel</tt>.
     *
     * @return the corresponding <tt>ChatPanel</tt>
     */
    public ChatPanel getChatPanel()
    {
        return chatPanel;
    }

    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    public ListView getChatListView()
    {
        return chatListView;
    }

    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    public ChatListAdapter getChatListAdapter()
    {
        return chatListAdapter;
    }

    private Contact getContact(String sender)
    {
        if (StringUtils.isNullOrEmpty(sender))
            return null;

        ProtocolProviderService provider = chatPanel.getProtocolProvider();
        OperationSetPersistentPresenceJabberImpl presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
                provider.getOperationSet(OperationSetPersistentPresence.class);
        return presenceOpSet.findContactByID(XmppStringUtils.parseBareJid(sender));
    }

    /**
     * The ChatListAdapter is a container with rows of send and received messages, file transfer
     * status information and system information.
     */
    public class ChatListAdapter extends BaseAdapter implements ChatPanel.ChatSessionListener,
            ContactPresenceStatusListener, ChatStateNotificationsListener
    {
        /**
         * The list of chat message displays. All access and modification of this list must be
         * done on the UI thread.
         */
        private final List<MessageDisplay> messages = new ArrayList<>();

        private final Hashtable<Integer, MessageViewHolder> viewHolders = new Hashtable<>();

        /**
         * The type of the incoming message view.
         */
        static final int INCOMING_MESSAGE_VIEW = 0;

        /**
         * The type of the outgoing message view.
         */
        static final int OUTGOING_MESSAGE_VIEW = 1;

        /**
         * The type of the system message view.
         */
        static final int SYSTEM_MESSAGE_VIEW = 2;

        /**
         * The type of the error message view.
         */
        static final int ERROR_MESSAGE_VIEW = 3;

        /**
         * The type for corrected message view.
         */
        static final int CORRECTED_MESSAGE_VIEW = 4;

        /**
         * The type for Receive File message view.
         */
        static final int FILE_TRANSFER_MESSAGE_VIEW = 5;

        /**
         * Maximum number of message view types support
         */
        static final int VIEW_TYPE_MAX = 6;

        /**
         * Counter used to generate row ids.
         */
        private long idGenerator = 0;

        /**
         * HTML image getter.
         */
        private final Html.ImageGetter imageGetter = new HtmlImageGetter();

        /**
         * Pass the message to the <tt>ChatListAdapter</tt> for processing; appends it at the
         * end or merge it with the last consecutive message.
         *
         * addMessageImpl method must only be processed on UI thread.
         *
         * It creates a new message view holder if this is first message or if this is a new
         * message received i.e. non-consecutive. NotifyDataChanged for update if <tt>true</tt>
         */
        private void addMessageImpl(ChatMessage newMessage)
        {
            runOnUiThread(() -> {
                if (chatListAdapter == null) {
                    Timber.w("Add message handled, when there's no adapter - possibly after onDetach()");
                    return;
                }

                // Auto enable Omemo option on receive omemo encrypted messages and view is in focus
                if (primarySelected && (Message.ENCRYPTION_OMEMO == newMessage.getEncryptionType())
                        && !chatPanel.isOmemoChat()) {
                    mCryptoFragment.setChatType(ChatFragment.MSGTYPE_OMEMO);
                }

                int msgIdx;
                int lastMsgIdx = getLastMessageIdx(newMessage);
                ChatMessage lastMsg = (lastMsgIdx != -1) ? chatListAdapter.getMessage(lastMsgIdx) : null;

                // Create a new message view holder only if message is non-consecutive
                if ((lastMsg == null) || (!lastMsg.isConsecutiveMessage(newMessage))) {
                    messages.add(new MessageDisplay(newMessage));
                    msgIdx = messages.size() - 1;
                }
                else {
                    // Merge the message and update the object in the list
                    messages.get(lastMsgIdx).update(lastMsg.mergeMessage(newMessage));
                    msgIdx = lastMsgIdx;
                }

                if (mSVP_Started) {
                    mSVP = svpApi.svpHandler(mSVP, getMessageDisplay(msgIdx));
                }

                // List must be scrolled manually, when android:transcriptMode="normal" is set
                chatListAdapter.notifyDataSetChanged();
                chatListView.setSelection(msgIdx + chatListView.getHeaderViewsCount());
            });
        }

        /**
         * Inserts given <tt>CopyOnWriteArrayList</tt> of <tt>ChatMessage</tt> at the beginning of the list.
         * synchronized to avoid java.util.ConcurrentModificationException on receive history messages
         * - seems still happen so use CopyOnWriteArrayList at ChanPanel#LoadHistory()
         *
         * List<ChatMessage> chatMessages = new CopyOnWriteArrayList<>() to avoid ConcurrentModificationException
         *
         * @param chatMessages the CopyOnWriteArrayList of <tt>ChatMessage</tt> to prepend.
         */
        private synchronized void prependMessages(List<ChatMessage> chatMessages)
        {
            List<MessageDisplay> newMsgs = new ArrayList<>();
            MessageDisplay previous = null;
            for (ChatMessage next : chatMessages) {
                if (previous == null || !previous.msg.isConsecutiveMessage(next)) {
                    previous = new MessageDisplay(next);
                    newMsgs.add(previous);
                }
                else {
                    // Merge the message and update the object in the list
                    previous.update(previous.msg.mergeMessage(next));
                }
            }
            messages.addAll(0, newMsgs);
        }

        /**
         * Finds index of the message that will handle <tt>newMessage</tt> merging process (usually just the last one).
         * If the <tt>newMessage</tt> is a correction message, then the last message of the same type will be returned.
         *
         * @param newMessage the next message to be merged into the adapter.
         * @return index of the message that will handle <tt>newMessage</tt> merging process. If
         * <tt>newMessage</tt> is a correction message, then the last message of the same type will be returned.
         */
        private int getLastMessageIdx(ChatMessage newMessage)
        {
            // If it's not a correction message then just return the last one
            if (newMessage.getCorrectedMessageUID() == null)
                return chatListAdapter.getCount() - 1;

            // Search for the same type
            int msgType = newMessage.getMessageType();
            for (int i = (getCount() - 1); i >= 0; i--) {
                ChatMessage candidate = getMessage(i);
                if (candidate.getMessageType() == msgType) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        public int getCount()
        {
            return messages.size();
        }

        private void clearMessage()
        {
            messages.clear();
        }

        /**
         * {@inheritDoc}
         */
        public Object getItem(int position)
        {
            return messages.get(position);
        }

        ChatMessage getMessage(int pos)
        {
            return ((MessageDisplay) getItem(pos)).msg;
        }

        MessageDisplay getMessageDisplay(int pos)
        {
            return (MessageDisplay) getItem(pos);
        }

        List<MessageDisplay> getMessageDisplays()
        {
            return messages;
        }

        /**
         * {@inheritDoc}
         */
        public long getItemId(int pos)
        {
            return messages.get(pos).id;
        }

        public MessageViewHolder updateMessageDeliveryStatusForId(String msgId, int receiptStatus)
        {
            if (TextUtils.isEmpty(msgId))
                return null;

            for (int index = messages.size(); index-- > 0; ) {
                MessageDisplay message = messages.get(index);
                String msgIds = message.getServerMsgId();
                if ((msgIds != null) && msgIds.contains(msgId)) {
                    // must reload cached messages from DB on resume i.e. after session exit
                    clearMsgCache = true;
                    // Update MessageDisplay to take care when view is refresh e.g. new message arrived or scroll
                    message.updateDeliveryStatus(receiptStatus);

                    MessageViewHolder viewHolder = viewHolders.get(index);
                    // Timber.w("Get MessageViewHolder %s: %s: %S", index, msgIds, viewHolder);
                    if (viewHolder != null)
                        setMessageReceiptStatus(viewHolder.msgReceiptView, receiptStatus);
                    return viewHolder;
                }
            }
            return null;
        }

        public void setXferStatus(int pos, int status)
        {
            if (pos < messages.size())
                messages.get(pos).status = status;
        }

        public int getXferStatus(int pos)
        {
            // IndexOutOfBound from field
            if (pos < messages.size())
                return messages.get(pos).status;

            // assuming CANCELED if not found
            return FileTransferStatusChangeEvent.CANCELED;
        }

        private void setFileXfer(int pos, Object mFileXfer)
        {
            messages.get(pos).fileXfer = mFileXfer;
        }

        public File getFileName(int pos)
        {
            return messages.get(pos).sFile;
        }

        public void setFileName(int pos, File file)
        {
            messages.get(pos).sFile = file;
        }

        private Object getFileXfer(int pos)
        {
            return messages.get(pos).fileXfer;
        }

        public int getViewTypeCount()
        {
            return VIEW_TYPE_MAX;
        }

        /*
         * return the view Type of the give position
         */
        public int getItemViewType(int position)
        {
            ChatMessage chatMessage = getMessage(position);
            int messageType = chatMessage.getMessageType();

            switch (messageType) {
                case ChatMessage.MESSAGE_IN:
                case ChatMessage.MESSAGE_MUC_IN:
                case ChatMessage.MESSAGE_LOCATION_IN:
                case ChatMessage.MESSAGE_STATUS:
                    return INCOMING_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_OUT:
                case ChatMessage.MESSAGE_LOCATION_OUT:
                case ChatMessage.MESSAGE_MUC_OUT:
                    String sessionCorrUID = chatPanel.getCorrectionUID();
                    String msgCorrUID = chatMessage.getUidForCorrection();
                    if (sessionCorrUID != null && sessionCorrUID.equals(msgCorrUID)) {
                        return CORRECTED_MESSAGE_VIEW;
                    }
                    else {
                        return OUTGOING_MESSAGE_VIEW;
                    }

                case ChatMessage.MESSAGE_SYSTEM:
                    return SYSTEM_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_ERROR:
                    return ERROR_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY:
                case ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE:
                case ChatMessage.MESSAGE_FILE_TRANSFER_SEND:
                case ChatMessage.MESSAGE_STICKER_SEND:
                case ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD:
                    return FILE_TRANSFER_MESSAGE_VIEW;

                default: // Default others to INCOMING_MESSAGE_VIEW
                    return INCOMING_MESSAGE_VIEW;
            }
        }

        /**
         * Hack required to capture TextView(message body) clicks, when <tt>LinkMovementMethod</tt> is set.
         */
        private final OnClickListener msgClickAdapter = new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (chatController != null && v.getTag() instanceof Integer) {
                    Integer position = (Integer) v.getTag();
                    if (!checkHttpDownloadLink(position))
                        chatController.onItemClick(chatListView, v, position, -1 /* id not used */);
                }
            }
        };

        /**
         * Method to check for Http download file link
         */
        // @Override
        private boolean checkHttpDownloadLink(int position)
        {
            // Position must be aligned to the number of header views included
            int cPos = position - chatListView.getHeaderViewsCount();
            boolean isMsgIn = (INCOMING_MESSAGE_VIEW == getItemViewType(cPos));
            ChatMessage chatMessage = chatListAdapter.getMessage(cPos);

            String body = chatMessage.getMessage();
            if (body.matches("(?s)^aesgcm:.*") && isMsgIn) {
                // Update the msgType in DB record
                // String msgUuid = chatMessage.getMessageUID();
                // MessageHistoryServiceImpl mMHS = (MessageHistoryServiceImpl) AndroidGUIActivator.getMessageHistoryService();
                // mMHS.convertToMessageType(msgUuid, ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);

                // Local cache update
                ((ChatMessageImpl) chatMessage).setMessageType(ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                this.notifyDataSetChanged();
                return true;
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            int viewType = getItemViewType(position);
            int clickedPos = position + chatListView.getHeaderViewsCount();
            MessageViewHolder messageViewHolder;
            MessageDisplay msgDisplay = getMessageDisplay(position);
            ChatMessage chatMessage = msgDisplay.getChatMessage();

            // File Transfer convertView creation
            if (viewType == FILE_TRANSFER_MESSAGE_VIEW) {
                LayoutInflater inflater = mFragmentActivity.getLayoutInflater();
                String msgUuid = chatMessage.getMessageUID();
                int msgType = chatMessage.getMessageType();
                boolean init = false;
                View viewTemp = null;

                // Reuse convertView if available and valid
                if (convertView == null) {
                    messageViewHolder = new MessageViewHolder();
                    init = true;
                }
                else {
                    messageViewHolder = (MessageViewHolder) convertView.getTag();
                    if (messageViewHolder.viewType != viewType) {
                        messageViewHolder = new MessageViewHolder();
                        init = true;
                    }
                }

                OperationSetFileTransfer opSet;
                FileRecord fileRecord;
                IncomingFileTransferRequest request;
                String fileName, sendFrom, sendTo;
                Date date;

                switch (msgType) {
                    // File transfer start
                    case ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE:
                        clearMsgCache = true;
                        opSet = chatMessage.getOpSet();
                        request = chatMessage.getFTRequest();
                        sendFrom = chatMessage.getContactName();
                        date = chatMessage.getDate();

                        FileReceiveConversation filexferR = (FileReceiveConversation) getFileXfer(position);
                        if (filexferR == null) {
                            filexferR = FileReceiveConversation.newInstance(currentChatFragment, sendFrom,
                                    opSet, request, date);
                            setFileXfer(position, filexferR);
                        }
                        viewTemp = filexferR.ReceiveFileConversionForm(inflater,
                                messageViewHolder, parent, position, init);
                        break;

                    case ChatMessage.MESSAGE_FILE_TRANSFER_SEND:
                    case ChatMessage.MESSAGE_STICKER_SEND:
                        clearMsgCache = true;
                        fileName = chatMessage.getMessage();
                        sendTo = chatMessage.getContactName();

                        FileSendConversation filexferS = (FileSendConversation) getFileXfer(position);
                        if (filexferS == null) {
                            filexferS = FileSendConversation.newInstance(currentChatFragment, sendTo, fileName,
                                    (msgType == ChatMessage.MESSAGE_STICKER_SEND));
                            setFileXfer(position, filexferS);
                        }
                        viewTemp = filexferS.SendFileConversationForm(inflater, messageViewHolder, parent, position, init);
                        break;

                    case ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY:
                        fileRecord = msgDisplay.getFileRecord();
                        clearMsgCache = (FileRecord.STATUS_ACTIVE == fileRecord.getStatus());
                        FileHistoryConversation filexferH = FileHistoryConversation.newInstance(currentChatFragment,
                                fileRecord, chatMessage);
                        viewTemp = filexferH.FileHistoryConversationForm(inflater, messageViewHolder, parent, init);
                        break;
                    case ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD:
                        // Must reload msgCache from DB on chat window refresh
                        clearMsgCache = true;
                        sendFrom = chatMessage.getContactName();
                        date = chatMessage.getDate();
                        HttpFileDownloadJabberImpl httpFileTransfer = chatMessage.getHttpFileTransfer();
                        // Timber.w("HTTP Xfer message position @ %s and File Transfer: %s", position, httpFileTransfer);
                        if (httpFileTransfer == null) {
                            String dnLink = chatMessage.getMessage();
                            if ((dnLink != null) && dnLink.matches("(?s)^aesgcm:.*|^http[s]:.*")) {
                                Contact contact = getContact(sendFrom);
                                httpFileTransfer = new HttpFileDownloadJabberImpl(contact, msgUuid, dnLink);

                                // Save a copy into the chatMessage for later retrieval
                                ((ChatMessageImpl) chatMessage).setHttpFileTransfer(httpFileTransfer);
                            }
                        }

                        FileHttpDownloadConversation fileDownloadForm = (FileHttpDownloadConversation) getFileXfer(position);
                        if (fileDownloadForm == null && httpFileTransfer != null) {
                            fileDownloadForm = FileHttpDownloadConversation.newInstance(currentChatFragment, sendFrom,
                                    httpFileTransfer, date);
                        }
                        if (fileDownloadForm != null) {
                            setFileXfer(position, fileDownloadForm);
                            viewTemp = fileDownloadForm.HttpFileDownloadConversionForm(inflater, messageViewHolder,
                                    parent, position, init);
                        }
                        break;
                }
                if (init) {
                    convertView = viewTemp;
                    if (convertView != null)
                        convertView.setTag(messageViewHolder);
                }
                messageViewHolder.viewType = viewType;
                viewHolders.put(position, messageViewHolder);
                // messageViewHolder.timeView.setText(message.getDateStr());
                return convertView;
            }

            // Normal Chat Message convertView Creation
            else {
                if (convertView == null) {
                    messageViewHolder = new MessageViewHolder();
                    convertView = inflateViewForType(viewType, messageViewHolder, parent);
                }
                else {
                    // Convert between OUTGOING and CORRECTED
                    messageViewHolder = (MessageViewHolder) convertView.getTag();
                    int vType = messageViewHolder.viewType;
                    if ((vType == CORRECTED_MESSAGE_VIEW || vType == OUTGOING_MESSAGE_VIEW)
                            && (vType != viewType)) {
                        messageViewHolder = new MessageViewHolder();
                        convertView = inflateViewForType(viewType, messageViewHolder, parent);
                    }
                }
                // Set position used for click handling from click adapter
                // int clickedPos = position + chatListView.getHeaderViewsCount();
                messageViewHolder.messageView.setTag(clickedPos);
                if (messageViewHolder.outgoingMessageHolder != null) {
                    messageViewHolder.outgoingMessageHolder.setTag(clickedPos);
                }

                if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW
                        || messageViewHolder.viewType == OUTGOING_MESSAGE_VIEW
                        || messageViewHolder.viewType == CORRECTED_MESSAGE_VIEW) {

                    String jid = chatMessage.getContactName();
                    if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                        messageViewHolder.jidView.setText(chatMessage.getContactDisplayName() + ":");
                        setEncState(messageViewHolder.encStateView, msgDisplay.getEncryption());
                    }
                    if (messageViewHolder.viewType == OUTGOING_MESSAGE_VIEW
                            || messageViewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                        setEncState(messageViewHolder.encStateView, msgDisplay.getEncryption());
                        setMessageReceiptStatus(messageViewHolder.msgReceiptView, msgDisplay.getReceiptStatus());
                    }
                    updateStatusAndAvatarView(messageViewHolder, jid);

                    if (messageViewHolder.showMapButton != null) {
                        if (msgDisplay.hasLatLng()) {
                            messageViewHolder.showMapButton.setVisibility(View.VISIBLE);
                            messageViewHolder.showMapButton.setOnClickListener(msgDisplay);
                            messageViewHolder.showMapButton.setOnLongClickListener(msgDisplay);
                        }
                        else {
                            messageViewHolder.showMapButton.setVisibility(View.GONE);
                        }
                    }
                    messageViewHolder.timeView.setText(msgDisplay.getDateStr());
                }

                // check and make link clickable if it is not an HTTP file link
                Spannable body = (Spannable) msgDisplay.getBody();

                // OTR system messages must use setMovementMethod to make the link clickable
                if (messageViewHolder.viewType == SYSTEM_MESSAGE_VIEW) {
                    messageViewHolder.messageView.setMovementMethod(LinkMovementMethod.getInstance());
                }
                else if (!TextUtils.isEmpty(body) && !body.toString().matches("(?s)^aesgcm:.*")) {
                    Linkify.addLinks(body, Linkify.ALL);
                    // Set up link movement method i.e. make all links in TextView clickable
                    messageViewHolder.messageView.setMovementMethod(LinkMovementMethod.getInstance());
                }
                messageViewHolder.messageView.setText(body);

                // Set clicks adapter for re-edit last outgoing message OR HTTP link download support
                messageViewHolder.messageView.setOnClickListener(msgClickAdapter);
                viewHolders.put(position, messageViewHolder);
                return convertView;
            }
        }

        private View inflateViewForType(int viewType, MessageViewHolder messageViewHolder, ViewGroup parent)
        {
            messageViewHolder.viewType = viewType;
            LayoutInflater inflater = mFragmentActivity.getLayoutInflater();
            View convertView;

            if (viewType == INCOMING_MESSAGE_VIEW) {
                convertView = inflater.inflate(R.layout.chat_incoming_row, parent, false);
                messageViewHolder.avatarView = convertView.findViewById(R.id.incomingAvatarIcon);
                messageViewHolder.statusView = convertView.findViewById(R.id.incomingStatusIcon);
                messageViewHolder.jidView = convertView.findViewById(R.id.incomingJidView);
                messageViewHolder.messageView = convertView.findViewById(R.id.incomingMessageView);
                messageViewHolder.encStateView = convertView.findViewById(R.id.encStateView);
                messageViewHolder.timeView = convertView.findViewById(R.id.incomingTimeView);
                messageViewHolder.chatStateView = convertView.findViewById(R.id.chatStateImageView);
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton);
            }
            else if (viewType == OUTGOING_MESSAGE_VIEW || viewType == CORRECTED_MESSAGE_VIEW) {
                if (viewType == OUTGOING_MESSAGE_VIEW) {
                    convertView = inflater.inflate(R.layout.chat_outgoing_row, parent, false);
                }
                else {
                    convertView = inflater.inflate(R.layout.chat_corrected_row, parent, false);
                }
                messageViewHolder.avatarView = convertView.findViewById(R.id.outgoingAvatarIcon);
                messageViewHolder.statusView = convertView.findViewById(R.id.outgoingStatusIcon);
                messageViewHolder.messageView = convertView.findViewById(R.id.outgoingMessageView);
                messageViewHolder.msgReceiptView = convertView.findViewById(R.id.msg_delivery_status);
                messageViewHolder.encStateView = convertView.findViewById(R.id.encStateView);
                messageViewHolder.timeView = convertView.findViewById(R.id.outgoingTimeView);
                messageViewHolder.outgoingMessageHolder = convertView.findViewById(R.id.outgoingMessageHolder);
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton);
            }
            else {
                // System or error view
                convertView = inflater.inflate((viewType == SYSTEM_MESSAGE_VIEW)
                        ? R.layout.chat_system_row : R.layout.chat_error_row, parent, false);
                messageViewHolder.messageView = convertView.findViewById(R.id.messageView);
            }
            convertView.setTag(messageViewHolder);
            return convertView;
        }

        /**
         * Updates status and avatar views on given <tt>MessageViewHolder</tt>.
         *
         * @param viewHolder the <tt>MessageViewHolder</tt> to update.
         */
        private void updateStatusAndAvatarView(MessageViewHolder viewHolder, String jabberID)
        {
            Drawable avatar = null;
            Drawable status = null;
            if (viewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                Object descriptor = chatPanel.getChatSession().getDescriptor();
                if (descriptor instanceof MetaContact) {
                    MetaContact metaContact = (MetaContact) descriptor;
                    avatar = MetaContactRenderer.getAvatarDrawable(metaContact);
                    status = MetaContactRenderer.getStatusDrawable(metaContact);
                }
                else {
                    if (jabberID != null) {
                        Contact contact = getContact(jabberID);
                        // If we have found a contact the we set also its avatar and status.
                        if (contact != null) {
                            avatar = MetaContactRenderer.getCachedAvatarFromBytes(contact.getImage());
                            PresenceStatus pStatus = contact.getPresenceStatus();
                            status = MetaContactRenderer.getCachedAvatarFromBytes(StatusUtil.getContactStatusIcon(pStatus));
                        }
                    }
                }
            }
            else if (viewHolder.viewType == OUTGOING_MESSAGE_VIEW
                    || viewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                ProtocolProviderService pps = currentChatTransport.getProtocolProvider();
                AndroidLoginRenderer loginRenderer = AndroidGUIActivator.getLoginRenderer();
                avatar = loginRenderer.getLocalAvatarDrawable(pps);
                status = loginRenderer.getLocalStatusDrawable();
            }
            else {
                // Avatar and status are present only in outgoing or incoming message views
                return;
            }
            setAvatar(viewHolder.avatarView, avatar);
            setStatus(viewHolder.statusView, status);
        }

        @Override
        public void messageDelivered(final MessageDeliveredEvent evt)
        {
            final Contact contact = evt.getDestinationContact();
            final MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
            final ChatMessageImpl msg = ChatMessageImpl.getMsgForEvent(evt);

            Timber.log(TimberLog.FINER, "MESSAGE DELIVERED to contact: %s", contact.getAddress());
            if ((metaContact != null) && metaContact.equals(chatPanel.getMetaContact())) {
                Timber.log(TimberLog.FINER, "MESSAGE DELIVERED: process message to chat for contact: %s MESSAGE: %s",
                        contact.getAddress(), msg.getMessage());
                addMessageImpl(msg);
            }
        }

        @Override
        public void messageDeliveryFailed(MessageDeliveryFailedEvent arg0)
        {
            // Do nothing, handled in ChatPanel
        }

        @Override
        public void messageReceived(final MessageReceivedEvent evt)
        {
            // ChatPanel broadcasts all received messages to all listeners. Must filter and display
            // messages only intended for this chatFragment.
            Contact protocolContact = evt.getSourceContact();
            if (mChatMetaContact.containsContact(protocolContact)) {
                final ChatMessageImpl msg = ChatMessageImpl.getMsgForEvent(evt);
                addMessageImpl(msg);
            }
            else {
                Timber.log(TimberLog.FINER, "MetaContact not found for protocol contact: %s", protocolContact);
            }
        }

        // some messages are added directly without event triggered
        @Override
        public void messageAdded(ChatMessage msg)
        {
            addMessageImpl(msg);
        }

        /**
         * Indicates a contact has changed its status.
         */
        @Override
        public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt)
        {
            Contact sourceContact = evt.getSourceContact();
            Timber.d("Contact presence status changed: %s", sourceContact.getAddress());

            if ((chatPanel.getMetaContact() != null) && chatPanel.getMetaContact().containsContact(sourceContact)) {
                mCryptoFragment.onContactPresenceStatusChanged();
                new UpdateStatusTask().execute();
            }
        }

        @Override
        public void chatStateNotificationDeliveryFailed(ChatStateNotificationEvent evt)
        {
        }

        @Override
        public void chatStateNotificationReceived(ChatStateNotificationEvent evt)
        {
            Timber.d("Typing notification received: %s", evt.getSourceContact().getAddress());
            ChatStateNotificationHandler.handleChatStateNotificationReceived(evt, ChatFragment.this);
        }

        /**
         * Updates all avatar and status on outgoing messages rows.
         */
        private void localAvatarOrStatusChanged()
        {
            runOnUiThread(() -> {
                for (int i = 0; i < chatListView.getChildCount(); i++) {
                    View row = chatListView.getChildAt(i);
                    MessageViewHolder viewHolder = (MessageViewHolder) row.getTag();
                    if (viewHolder != null)
                        updateStatusAndAvatarView(viewHolder, null);
                }
            });
        }

        /**
         * Class used to cache processed message contents. Prevents from re-processing on each
         * View display.
         */
        class MessageDisplay implements OnClickListener, View.OnLongClickListener
        {
            /**
             * Row identifier.
             */
            private final long id;

            /**
             * File Transfer Status.
             */
            private int status;

            /**
             * Message Receipt Status.
             */
            private int receiptStatus;

            /**
             * Message Encryption Type.
             */
            private int encryption;

            private String serverMsgId;

            /**
             * Incoming or outgoing File Transfer object
             */
            private Object fileXfer;

            /**
             * Save File name</tt>
             */

            private File sFile;

            /**
             * Displayed <tt>ChatMessage</tt>
             */

            private ChatMessage msg;

            /**
             * Date string cache
             */
            private String dateStr;

            /**
             * Message body cache
             */
            private Spanned body;

            /**
             * Incoming message has LatLng info
             */
            protected boolean hasLatLng = false;

            /**
             * Lat & Lng info in the incoming message
             */
            protected double latitude;
            protected double longitude;

            /**
             * Creates new instance of <tt>MessageDisplay</tt> that will be used for displaying
             * given <tt>ChatMessage</tt>.
             *
             * @param msg the <tt>ChatMessage</tt> that will be displayed by this instance.
             */
            MessageDisplay(ChatMessage msg)
            {
                this.id = idGenerator++;
                this.msg = msg;
                this.status = -1;
                this.fileXfer = null;
                this.receiptStatus = msg.getReceiptStatus();
                this.encryption = msg.getEncryptionType();
                this.serverMsgId = msg.getServerMsgId();
                checkLatLng();
            }

            /**
             * check if the incoming message contain geo "LatLng:" information. Only the first
             * LatLng will be returned if there are multiple LatLng in consecutive messages
             */
            private void checkLatLng()
            {
                String str = msg.getMessage();
                if ((str != null) && ((getMessageType() == ChatMessage.MESSAGE_IN)
                        || (getMessageType() == ChatMessage.MESSAGE_OUT))) {
                    int startIndex = str.indexOf("LatLng:");
                    if (startIndex != -1) {
                        try {
                            String mLoc = str.substring(startIndex + 7);
                            String[] location = mLoc.split(",");

                            String sLat = location[0].toLowerCase(Locale.US);
                            if (sLat.contains("s"))
                                sLat = "-" + sLat.replaceAll("[^0-9.]+", "");
                            else
                                sLat = sLat.replaceAll("[^0-9.]+", "");

                            String sLng = location[1].toLowerCase(Locale.US);
                            if (sLng.contains("w"))
                                sLng = "-" + sLng.replaceAll("[^0-9.]+", "");
                            else
                                sLng = sLng.replaceAll("[^0-9.]+", "");

                            hasLatLng = true;
                            latitude = Double.parseDouble(sLat);
                            longitude = Double.parseDouble(sLng);
                        } catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }

            /**
             * Perform google street and map view fetch when user click the show map button
             *
             * @param view view
             */
            @Override
            public void onClick(View view)
            {
                mSVP_Started = true;
                svpApi.onSVPClick(mActivity, latitude, longitude);
            }

            /**
             * Perform google street and map view playback when user longclick the show map button
             *
             * @param v View
             */
            @Override
            public boolean onLongClick(View v)
            {
                ArrayList<double[]> mLatLng = new ArrayList<>();
                int smt = getMessageType();
                List<MessageDisplay> displayMessages = getMessageDisplays();
                for (MessageDisplay dm : displayMessages) {
                    if (dm.hasLatLng && (smt == dm.getMessageType())) {
                        mLatLng.add(new double[]{dm.latitude, dm.longitude});
                    }
                }
                if (!mLatLng.isEmpty()) {
                    mSVP_Started = true;
                    svpApi.onSVPLongClick(mActivity, mLatLng);
                }
                return true;
            }

            /**
             * @return <tt>true</tt> if the message has LatLng information
             */
            private boolean hasLatLng()
            {
                return hasLatLng;
            }

            public int getReceiptStatus()
            {
                return receiptStatus;
            }

            public int getEncryption()
            {
                return encryption;
            }

            public String getServerMsgId()
            {
                return serverMsgId;
            }

            /**
             * Returns formatted date string for the <tt>ChatMessage</tt>.
             *
             * @return formatted date string for the <tt>ChatMessage</tt>.
             */
            private String getDateStr()
            {
                if (dateStr == null) {
                    Date date = msg.getDate();
                    dateStr = GuiUtils.formatDate(date) + " " + GuiUtils.formatTime(date);
                }
                return dateStr;
            }

            public int getMessageType()
            {
                return msg.getMessageType();
            }

            private ChatMessage getChatMessage()
            {
                return this.msg;
            }

            private FileRecord getFileRecord()
            {
                return msg.getFileRecord();
            }

            /**
             * Returns <tt>Spanned</tt> message body processed for HTML tags.
             *
             * @return <tt>Spanned</tt> message body.
             */
            public Spanned getBody()
            {
                if ((body == null) && !TextUtils.isEmpty(msg.getMessage())) {
                    try { // cannot assume null body = html message
                        body = Html.fromHtml(msg.getMessage(), imageGetter, null);
                        Pattern urlMatcher = Pattern.compile("\\b[A-Za-z]+://[A-Za-z0-9:./?=]+\\b");
                        Linkify.addLinks((Spannable) body, urlMatcher, null);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return body;
            }

            /**
             * Updates this display instance with new message causing display contents to be invalidated.
             * Both receiptStatus and serverMsgId of the merged message will use the new chatMessage
             *
             * @param chatMessage new message content
             */
            public void update(ChatMessage chatMessage)
            {
                dateStr = null;
                body = null;
                msg = chatMessage;

                receiptStatus = chatMessage.getReceiptStatus();
                serverMsgId = chatMessage.getServerMsgId();
            }

            public void updateDeliveryStatus(int deliveryStatus)
            {
                receiptStatus = deliveryStatus;
            }
        }
    }

    public static class MessageViewHolder
    {
        int viewType;
        View outgoingMessageHolder;
        ImageView chatStateView;
        ImageView avatarView;
        ImageView statusView;
        TextView jidView;
        TextView messageView;
        public ImageView encStateView;
        public ImageView msgReceiptView;
        public TextView timeView;

        public ImageView arrowDir = null;
        public ImageView stickerView = null;
        public ImageButton imageLabel = null;
        public ProgressBar mProgressBar = null;

        public Button showMapButton = null;
        public Button cancelButton = null;
        public Button retryButton = null;
        public Button acceptButton = null;
        public Button rejectButton = null;

        public TextView titleLabel = null;
        public TextView fileLabel = null;
        public TextView viewFileXferError = null;
        public TextView progressSpeedLabel = null;
        public TextView estimatedTimeLabel = null;
    }

    //    class IdRow2 // need to include in MessageViewHolder for stealth support
    //    {
    //        public int mId;
    //        public View mRow;
    //        public int mCountDownValue;
    //        public boolean deleteFlag;
    //        public boolean mStartCountDown;
    //        public boolean mFileIsOpened;
    //
    //        public IdRow2(int id, View row, int startValue)
    //        {
    //            mId = id;
    //            mRow = row;
    //            mCountDownValue = startValue;
    //            deleteFlag = false;
    //            mStartCountDown = false;
    //            mFileIsOpened = false;
    //        }
    //    }

    /**
     * Loads the history in an asynchronous thread and then adds the history messages to the user
     * interface.
     */
    private class LoadHistoryTask extends AsyncTask<Void, Void, List<ChatMessage>>
    {
        /**
         * Indicates that history is being loaded for the first time.
         */
        private final boolean init;
        /**
         * Remembers adapter size before new messages were added.
         */
        private int preSize;

        LoadHistoryTask(boolean init)
        {
            this.init = init;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            header.setVisibility(View.VISIBLE);
            this.preSize = chatListAdapter.getCount();
        }

        @Override
        protected List<ChatMessage> doInBackground(Void... params)
        {
            return chatPanel.getHistory(init);
        }

        @Override
        protected void onPostExecute(List<ChatMessage> result)
        {
            super.onPostExecute(result);
            chatListAdapter.prependMessages(result);

            header.setVisibility(View.GONE);
            chatListAdapter.notifyDataSetChanged();
            int loaded = chatListAdapter.getCount() - preSize;
            int scrollTo = loaded + scrollFirstVisible;
            chatListView.setSelectionFromTop(scrollTo, scrollTopOffset);
            loadHistoryTask = null;
        }
    }

    /**
     * Updates the status user interface.
     */
    private class UpdateStatusTask extends AsyncTask<Void, Void, Void>
    {
        protected Void doInBackground(Void... params)
        {
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            if (chatListView == null || chatPanel == null) {
                return;
            }

            for (int i = 0; i < chatListView.getChildCount(); i++) {
                View chatRowView = chatListView.getChildAt(i);
                MessageViewHolder viewHolder = (MessageViewHolder) chatRowView.getTag();

                if ((viewHolder != null)
                        && (viewHolder.viewType == ChatListAdapter.INCOMING_MESSAGE_VIEW)) {
                    Drawable status = MetaContactRenderer.getStatusDrawable(chatPanel.getMetaContact());
                    ImageView statusView = viewHolder.statusView;
                    setStatus(statusView, status);
                }
            }
        }
    }

    /**
     * Sets the avatar icon for the given avatar view.
     *
     * @param avatarView the avatar image view
     * @param avatarDrawable the avatar drawable to set
     */
    public void setAvatar(ImageView avatarView, Drawable avatarDrawable)
    {
        if (avatarDrawable == null) {
            // avatarDrawable = aTalkApp.getAppResources().getDrawable(R.drawable.contact_avatar);
            avatarDrawable = ContextCompat.getDrawable(aTalkApp.getGlobalContext(), R.drawable.contact_avatar);
        }
        if (avatarView != null) {
            avatarView.setImageDrawable(avatarDrawable);
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param statusView the status icon view
     * @param statusDrawable the status drawable
     */
    public void setStatus(ImageView statusView, Drawable statusDrawable)
    {
        // File Transfer messageHolder does not have a statusView for update;
        // cmeng - server shut down causing null pointer, why???
        if (statusView != null)
            statusView.setImageDrawable(statusDrawable);
    }

    @Override
    public void onReceiptReceived(Jid fromJid, Jid toJid, final String receiptId, Stanza receipt)
    {
        runOnUiThread(() -> {
            if (chatListAdapter != null) {
                chatListAdapter.updateMessageDeliveryStatusForId(receiptId, ChatMessage.MESSAGE_DELIVERY_RECEIPT);
            }
        });
    }

    /**
     * Sets the status of the given view.
     *
     * @param receiptStatusView the encryption state view
     * @param deliveryStatus the encryption
     */
    private void setMessageReceiptStatus(ImageView receiptStatusView, int deliveryStatus)
    {
        switch (deliveryStatus) {
            case ChatMessage.MESSAGE_DELIVERY_NONE:
                receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_queued);
                break;
            case ChatMessage.MESSAGE_DELIVERY_RECEIPT:
                receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_read);
                break;
            case ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT:
                receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_sent_client);
                break;
            case ChatMessage.MESSAGE_DELIVERY_SERVER_SENT:
                receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_sent_server);
                break;
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param encStateView the encryption state view
     * @param encType the encryption
     */
    private void setEncState(ImageView encStateView, int encType)
    {
        switch (encType) {
            case Message.ENCRYPTION_NONE:
                encStateView.setImageResource(R.drawable.encryption_none);
                break;
            case Message.ENCRYPTION_OMEMO:
                encStateView.setImageResource(R.drawable.encryption_omemo);
                break;
            case Message.ENCRYPTION_OTR:
                encStateView.setImageResource(R.drawable.encryption_otr);
                break;
        }
    }

    /**
     * Sets the appropriate chat state notification interface.
     *
     * @param chatState the chat state that should be represented in the view
     */
    public void setChatState(final ChatState chatState)
    {
        if (chatStateView == null) {
            return;
        }

        mActivity.runOnUiThread(() -> {
            if (chatState != null) {
                TextView chatStateTextView = chatStateView.findViewById(R.id.chatStateTextView);
                ImageView chatStateImgView = chatStateView.findViewById(R.id.chatStateImageView);

                String buddy = chatPanel.getShortDisplayName();
                switch (chatState) {
                    case composing:
                        Drawable chatStateDrawable = chatStateImgView.getDrawable();
                        if (!(chatStateDrawable instanceof AnimationDrawable)) {
                            chatStateImgView.setImageResource(R.drawable.chat_state_drawable);
                            chatStateDrawable = chatStateImgView.getDrawable();
                        }

                        if (!((AnimationDrawable) chatStateDrawable).isRunning()) {
                            AnimationDrawable animatedDrawable = (AnimationDrawable) chatStateDrawable;
                            animatedDrawable.setOneShot(false);
                            animatedDrawable.start();
                        }
                        chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_COMPOSING, buddy));
                        break;
                    case paused:
                        chatStateImgView.setImageResource(R.drawable.typing1);
                        chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_PAUSED_TYPING, buddy));
                        break;
                    case active:
                        chatStateImgView.setImageResource(R.drawable.global_ffc);
                        chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_ACTIVE, buddy));
                        break;
                    case inactive:
                        chatStateImgView.setImageResource(R.drawable.global_away);
                        chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_INACTIVE, buddy));
                        break;
                    case gone:
                        chatStateImgView.setImageResource(R.drawable.global_extended_away);
                        chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_GONE, buddy));
                        break;
                }
                chatStateImgView.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                chatStateImgView.setPadding(7, 0, 7, 7);
                chatStateView.setVisibility(View.VISIBLE);
            }
            else {
                chatStateView.setVisibility(View.INVISIBLE);
            }
        });
    }

    // ********************************************************************************************//
    // Routines supporting File Transfer

    /**
     * Cancels all active file transfers.
     */
    private void cancelActiveFileTransfers()
    {
        Enumeration<String> activeKeys = activeFileTransfers.keys();

        while (activeKeys.hasMoreElements()) {
            // catch all so if anything happens we still will close the chatFragment / chatPanel
            try {
                String key = activeKeys.nextElement();
                Object descriptor = activeFileTransfers.get(key);

                if (descriptor instanceof IncomingFileTransferRequest) {
                    ((IncomingFileTransferRequest) descriptor).rejectFile();
                }
                else if (descriptor instanceof FileTransfer) {
                    ((FileTransfer) descriptor).cancel();
                }
            } catch (Throwable t) {
                Timber.e(t, "Cannot initActive file transfer.");
            }
        }
    }

    /**
     * Adds the given file transfer <tt>id</tt> to the list of active file transfers.
     *
     * @param id the identifier of the file transfer to add
     * @param fileTransfer the descriptor of the file transfer
     */
    public void addActiveFileTransfer(String id, FileTransfer fileTransfer, int msgId)
    {
        synchronized (activeFileTransfers) {
            // Both must be removed from chatFragment activeFileTransfers components when 'Done!'.
            if (!activeFileTransfers.contains(id)) {
                activeFileTransfers.put(id, fileTransfer);

                // Add status listener for chatFragment to track when the sendFile transfer has completed.
                fileTransfer.addStatusListener(currentChatFragment);
            }
        }

        synchronized (activeMsgTransfers) {
            if (!activeMsgTransfers.contains(id))
                activeMsgTransfers.put(id, msgId);
        }
    }

    /**
     * Removes the given file transfer <tt>id</tt> from the list of active file transfers.
     *
     * @param fileTransfer the identifier of the file transfer to remove
     */
    private void removeActiveFileTransfer(FileTransfer fileTransfer)
    {
        String id = fileTransfer.getID();
        synchronized (activeFileTransfers) {
            activeFileTransfers.remove(id);

            // if (activeFileTransfers.size() == 0) ?? is one per file transfer, so must remove
            fileTransfer.removeStatusListener(currentChatFragment);
        }

        synchronized (activeMsgTransfers) {
            int msgId = activeMsgTransfers.get(id);
            chatListAdapter.setFileXfer(msgId, null);
            activeMsgTransfers.remove(id);
        }
    }

    /**
     * Handles file transfer status changed in order to remove completed file transfers from the
     * list of active transfers.
     *
     * @param event the file transfer status change event the notified us for the change
     */
    public void statusChanged(FileTransferStatusChangeEvent event)
    {
        FileTransfer fileTransfer = event.getFileTransfer();
        final int newStatus = event.getNewStatus();

        int msgPos = activeMsgTransfers.get(fileTransfer.getID());
        // if chatFragment is still active
        if (chatListAdapter != null) {
            // Send an initActive message to recipient if file transfer is initActive while in preparing
            // state. Currently protocol did not broadcast status change under this condition.
            if ((newStatus == FileTransferStatusChangeEvent.CANCELED)
                    && (chatListAdapter.getXferStatus(msgPos) == FileTransferStatusChangeEvent.PREPARING)) {
                String msg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_CANCELED);
                // chatPanel.sendMessage(msg);
                try {
                    chatPanel.getChatSession().getCurrentChatTransport().sendInstantMessage(msg,
                            Message.ENCRYPTION_NONE | Message.ENCODE_PLAIN);
                } catch (Exception e) {
                    aTalkApp.showToastMessage(e.getMessage());
                }
            }

            // Must request to refresh the msgCache once the send file activity has started. Otherwise the
            // send file request will get activated a second time.
            if (newStatus != FileTransferStatusChangeEvent.PREPARING) {
                chatPanel.setCacheRefresh(true);
            }
            if (newStatus == FileTransferStatusChangeEvent.COMPLETED
                    || newStatus == FileTransferStatusChangeEvent.CANCELED
                    || newStatus == FileTransferStatusChangeEvent.FAILED
                    || newStatus == FileTransferStatusChangeEvent.REFUSED) {
                removeActiveFileTransfer(fileTransfer);
            }
        }
    }

    /**
     * Sends the given file through the currently selected chat transport by using the given
     * fileComponent to visualize the transfer process n the chatFragment view.
     *
     * // @param mFile the file to send
     * // @param FileSendConversation he send file component to use for file transfer & visualization
     * // @param msgId he view position on chatFragment
     */

    public class SendFile extends AsyncTask<Void, Void, Exception>
    {
        private final File mFile;
        private final FileSendConversation sendFTConversion;
        private final int msgId;
        private final Object entityJid;
        private boolean chkMaxSize = true;
        private boolean mStickerMode;
        private int mEncryption = Message.ENCRYPTION_NONE;

        public SendFile(File file, FileSendConversation sFilexferCon, int mId, boolean stickerMode)
        {
            mFile = file;
            sendFTConversion = sFilexferCon;
            msgId = mId;
            mStickerMode = stickerMode;
            entityJid = currentChatTransport.getDescriptor();
        }

        @Override
        public void onPreExecute()
        {
            long maxFileLength = currentChatTransport.getMaximumFileLength();
            if (mFile.length() > maxFileLength) {
                chatPanel.addMessage(currentChatTransport.getName(), new Date(), ChatMessage.MESSAGE_ERROR,
                        Message.ENCODE_PLAIN, aTalkApp.getResString(R.string.service_gui_FILE_TOO_BIG,
                                ByteFormat.format(maxFileLength)));

                // stop background task to proceed and update status
                chkMaxSize = false;
                chatListAdapter.setXferStatus(msgId, FileTransferStatusChangeEvent.CANCELED);
                sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption);
            }
            else {
                // must reset status here as background task cannot catch up with Android redraw
                // request? causing double send requests in slow Android devices.
                chatListAdapter.setXferStatus(msgId, FileTransferStatusChangeEvent.PREPARING);
            }
        }

        @Override
        protected Exception doInBackground(Void... param)
        {
            if (!chkMaxSize)
                return null;

            // return can either be FileTransfer or URL when httpFileUpload or exception
            Object fileXfer;
            String urlLink;

            Exception result = null;
            try {
                if (mStickerMode)
                    // mStickerMode does not attempt to send image thumbnail preview
                    fileXfer = currentChatTransport.sendSticker(mFile, mChatType, sendFTConversion);
                else
                    fileXfer = currentChatTransport.sendFile(mFile, mChatType, sendFTConversion);

                if (fileXfer instanceof FileTransfer) {
                    FileTransfer fileTransfer = (FileTransfer) fileXfer;

                    // To be removed on file transfer completion
                    addActiveFileTransfer(fileTransfer.getID(), fileTransfer, msgId);

                    // Trigger FileSendConversation to add statusListener as well
                    sendFTConversion.setProtocolFileTransfer(fileTransfer);
                }
                else {
                    if (fileXfer instanceof AesgcmUrl) {
                        urlLink = ((AesgcmUrl) fileXfer).getAesgcmUrl();
                        mEncryption = Message.ENCRYPTION_OMEMO;
                    }
                    else {
                        urlLink = (fileXfer == null) ? null : fileXfer.toString();
                        mEncryption = Message.ENCRYPTION_NONE;
                    }
                    // Timber.w("HTTP link: %s: %s", mFile.getName(), urlLink);
                    if (StringUtils.isNullOrEmpty(urlLink)) {
                        sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption);
                    }
                    else {
                        sendFTConversion.setStatus(FileTransferStatusChangeEvent.COMPLETED, entityJid, mEncryption);
                        chatController.sendMessage(urlLink, Message.FLAG_REMOTE_ONLY | Message.ENCODE_PLAIN);
                    }
                }
            } catch (Exception e) {
                result = e;
                sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption);
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception ex)
        {
            if (ex != null) {
                Timber.e(ex, "Failed to send file.");
                chatPanel.addMessage(currentChatTransport.getName(), new Date(), ChatMessage.MESSAGE_ERROR,
                        Message.ENCODE_PLAIN, aTalkApp.getResString(R.string.service_gui_FILE_DELIVERY_ERROR, ex.getMessage()));
            }
        }

        @Override
        protected void onCancelled()
        {
        }
    }

    @Override
    public void onCurrentChatChanged(String chatId)
    {
        chatPanel = ChatSessionManager.getActiveChat(chatId);
    }

    /*********************************************************************************************
     * Routines supporting changing chatFragment background color based on chat state and chatType
     * ChatFragment background colour is being updated when:
     * - User launches a chatSession
     * - User scroll the chatFragment pages
     * - User changes cryptoMode for the current chatSession
     * - Change requests from OTR Listener due to OTR state changes
     */

    @Override
    public void onCryptoModeChange(int chatType)
    {
        chatPanel.setChatType(chatType);
        changeBackground(mCFView, chatType);
    }

    /**
     * Change chatFragment background in response to initial chat session launch or event
     * triggered from omemoAuthentication and OTR mode changes in cryptoChatFragment
     *
     * @param chatType Change chat fragment view background color based on chatType
     */
    private void changeBackground(final View focusView, final int chatType)
    {
        if (mChatType == chatType)
            return;

        mChatType = chatType;

        runOnUiThread(() -> {
            switch (chatType) {
                case MSGTYPE_OMEMO:
                    focusView.setBackgroundResource(R.color.chat_background_omemo);
                    break;
                case MSGTYPE_OMEMO_UA:
                case MSGTYPE_OMEMO_UT:
                    focusView.setBackgroundResource(R.color.chat_background_omemo_ua);
                    break;
                case MSGTYPE_OTR:
                    focusView.setBackgroundResource(R.color.chat_background_otr);
                    break;
                case MSGTYPE_OTR_UA:
                    focusView.setBackgroundResource(R.color.chat_background_otr_ua);
                    break;
                case MSGTYPE_NORMAL:
                    focusView.setBackgroundResource(R.color.chat_background_normal);
                    break;
                case MSGTYPE_MUC_NORMAL:
                    focusView.setBackgroundResource(R.color.chat_background_muc);
                    break;
                default:
                    focusView.setBackgroundResource(R.color.chat_background_normal);
            }
        });
    }
}
