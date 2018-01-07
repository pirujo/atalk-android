/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat.conference;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.MessageListener;

import org.atalk.android.gui.chat.*;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.jid.EntityBareJid;

import java.io.File;

/**
 * The conference implementation of the <tt>ChatTransport</tt> interface that provides abstraction to access to protocol providers.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public class AdHocConferenceChatTransport implements ChatTransport
{
	private final ChatSession chatSession;

	private final AdHocChatRoom adHocChatRoom;

	/**
	 * Creates an instance of <tt>ConferenceChatTransport</tt> by specifying the parent chat session and the ad-hoc chat room associated
	 * with this transport.
	 *
	 * @param chatSession
	 *        the parent chat session.
	 * @param chatRoom
	 *        the ad-hoc chat room associated with this conference transport.
	 */
	public AdHocConferenceChatTransport(ChatSession chatSession, AdHocChatRoom chatRoom)
	{
		this.chatSession = chatSession;
		this.adHocChatRoom = chatRoom;
	}

	/**
	 * Returns the contact address corresponding to this chat transport.
	 *
	 * @return The contact address corresponding to this chat transport.
	 */
	public String getName()
	{
		return adHocChatRoom.getName();
	}

	/**
	 * Returns the display name corresponding to this chat transport.
	 *
	 * @return The display name corresponding to this chat transport.
	 */
	public String getDisplayName()
	{
		return adHocChatRoom.getName();
	}

	/**
	 * Returns the resource name of this chat transport. This is for example the name of the user agent from which the contact is logged.
	 *
	 * @return The display name of this chat transport resource.
	 */
	public String getResourceName()
	{
		return null;
	}

	/**
	 * Indicates if the display name should only show the resource.
	 *
	 * @return <tt>true</tt> if the display name shows only the resource, <tt>false</tt> - otherwise
	 */
	public boolean isDisplayResourceOnly()
	{
		return false;
	}

	/**
	 * Returns the presence status of this transport.
	 *
	 * @return the presence status of this transport.
	 */
	public PresenceStatus getStatus()
	{
		return null;
	}

	/**
	 * Returns the <tt>ProtocolProviderService</tt>, corresponding to this chat transport.
	 *
	 * @return the <tt>ProtocolProviderService</tt>, corresponding to this chat transport.
	 */
	public ProtocolProviderService getProtocolProvider()
	{
		return adHocChatRoom.getParentProvider();
	}

	/**
	 * Returns <code>true</code> if this chat transport supports instant messaging, otherwise returns <code>false</code> .
	 *
	 * @return <code>true</code> if this chat transport supports instant messaging, otherwise returns <code>false</code> .
	 */
	public boolean allowsInstantMessage()
	{
		return true;
	}

	/**
	 * Returns <code>true</code> if this chat transport supports sms messaging, otherwise returns <code>false</code>.
	 *
	 * @return <code>true</code> if this chat transport supports sms messaging, otherwise returns <code>false</code>.
	 */
	public boolean allowsSmsMessage()
	{
		return false;
	}

	/**
	 * Returns <code>true</code> if this chat transport supports chat state notifications,
	 * otherwise returns <code>false</code>.
	 *
	 * @return <code>true</code> if this chat transport supports chat state notifications,
	 * otherwise returns <code>false</code>.
	 */
	public boolean allowsChatStateNotifications()
	{
		Object tnOpSet = adHocChatRoom.getParentProvider().getOperationSet(OperationSetChatStateNotifications.class);

		if (tnOpSet != null)
			return true;
		else
			return false;
	}

	/**
	 * Sends the given instant message trough this chat transport, by specifying the mime type (html or plain text).
	 *
	 * @param messageText
	 *        The message to send.
	 * @param encType
	 *        The encType of the message to send: 1=text/html or 0=text/plain.
	 *        @see ChatMessage Encryption Type
	 */
	public void sendInstantMessage(String messageText, int encType)
		throws Exception
	{
		// If this chat transport does not support instant messaging we do
		// nothing here.
		if (!allowsInstantMessage())
			return;

		Message message = adHocChatRoom.createMessage(messageText);
		adHocChatRoom.sendMessage(message);
	}

	/**
	 * Determines whether this chat transport supports the supplied content type
	 *
	 * @param encType
	 *        the encryption type we want to check
	 * @return <tt>true</tt> if the chat transport supports it and <tt>false</tt> otherwise.
	 */
	public boolean isContentTypeSupported(int encType)
	{
		// we only support plain text for chat rooms for now
		return (ChatMessage.ENCODE_PLAIN == encType);
	}

	/**
	 * Sending sms messages is not supported by this chat transport implementation.
	 */
	public void sendSmsMessage(String phoneNumber, String message)
		throws Exception
	{
	}

	/**
	 * Sending sms messages is not supported by this chat transport implementation.
	 */
	public void sendSmsMessage(String message)
		throws Exception
	{
	}

	/**
	 * Sending file in sms messages is not supported by this chat transport implementation.
	 */
	public FileTransfer sendMultimediaFile(File file)
		throws Exception
	{
		return null;
	}

	/**
	 * Not used.
	 * 
	 * @return
	 */
	public boolean askForSMSNumber()
	{
		return false;
	}

	/**
	 * Sending chat state notifications is not supported by this chat transport implementation.
	 */
	public void sendChatStateNotification(ChatState chatState)
	{
	}

	/**
	 * Sending files through a chat room is not yet supported by this chat transport implementation.
	 */
	public FileTransfer sendFile(File file)
		throws Exception
	{
		return null;
	}

	/**
	 * Invites the given contact in this chat conference.
	 *
	 * @param contactAddress
	 *        the address of the contact to invite
	 * @param reason
	 *        the reason for the invitation
	 */
	public void inviteChatContact(EntityBareJid contactAddress, String reason)
	{
		if (adHocChatRoom != null)
			adHocChatRoom.invite(contactAddress, reason);
	}

	/**
	 * Returns the parent session of this chat transport. A <tt>ChatSession</tt> could contain more than one transports.
	 *
	 * @return the parent session of this chat transport
	 */
	public ChatSession getParentChatSession()
	{
		return chatSession;
	}

	/**
	 * Adds an sms message listener to this chat transport.
	 *
	 * @param l
	 *        The message listener to add.
	 */
	public void addSmsMessageListener(MessageListener l)
	{
		// If this chat transport does not support sms messaging we do
		// nothing here.
		if (!allowsSmsMessage())
			return;

		OperationSetSmsMessaging smsOpSet = adHocChatRoom.getParentProvider().getOperationSet(OperationSetSmsMessaging.class);

		smsOpSet.addMessageListener(l);
	}

	/**
	 * Adds an instant message listener to this chat transport.
	 *
	 * @param l
	 *        The message listener to add.
	 */
	public void addInstantMessageListener(MessageListener l)
	{
		// If this chat transport does not support instant messaging we do
		// nothing here.
		if (!allowsInstantMessage())
			return;

		OperationSetBasicInstantMessaging imOpSet = adHocChatRoom.getParentProvider().getOperationSet(OperationSetBasicInstantMessaging.class);

		imOpSet.addMessageListener(l);
	}

	/**
	 * Removes the given sms message listener from this chat transport.
	 *
	 * @param l
	 *        The message listener to remove.
	 */
	public void removeSmsMessageListener(MessageListener l)
	{
		// If this chat transport does not support sms messaging we do
		// nothing here.
		if (!allowsSmsMessage())
			return;

		OperationSetSmsMessaging smsOpSet = adHocChatRoom.getParentProvider().getOperationSet(OperationSetSmsMessaging.class);

		smsOpSet.removeMessageListener(l);
	}

	/**
	 * Removes the instant message listener from this chat transport.
	 *
	 * @param l
	 *        The message listener to remove.
	 */
	public void removeInstantMessageListener(MessageListener l)
	{
		// If this chat transport does not support instant messaging we do
		// nothing here.
		if (!allowsInstantMessage())
			return;

		OperationSetBasicInstantMessaging imOpSet = adHocChatRoom.getParentProvider().getOperationSet(OperationSetBasicInstantMessaging.class);

		imOpSet.removeMessageListener(l);
	}

	public void dispose()
	{
	}

	/**
	 * Returns the descriptor of this chat transport.
	 *
	 * @return the descriptor of this chat transport
	 */
	public Object getDescriptor()
	{
		return adHocChatRoom;
	}

	public long getMaximumFileLength()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * Returns <tt>true</tt> if this chat transport supports message corrections and false otherwise.
	 *
	 * @return <tt>true</tt> if this chat transport supports message corrections and false otherwise.
	 */
	public boolean allowsMessageCorrections()
	{
		return false;
	}

	/**
	 * Sends <tt>message</tt> as a message correction through this transport, specifying the mime type (html or plain text) and the id of
	 * the message to replace.
	 *
	 * @param message
	 *        The message to send.
	 * @param encType
	 *        The encType of the message to send: 1=text/html or 0=text/plain.
	 *        @see ChatMessage Encryption Type
	 * @param correctedMessageUID
	 *        The ID of the message being corrected by this message.
	 */
	public void correctInstantMessage(String message, int encType, String correctedMessageUID)
	{
	}
}
