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
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.hashes.element.HashElement;
import org.jivesoftware.smackx.jet.component.JetSecurityImpl;
import org.jivesoftware.smackx.jingle.component.JingleContentImpl;
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFile;
import org.jivesoftware.smackx.jingle_filetransfer.controller.IncomingFileOfferController;
import org.jxmpp.jid.BareJid;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import timber.log.Timber;

/**
 * Jabber implementation of the jingle incoming file offer
 *
 * @author Eng Chong Meng
 */
public class IncomingFileOfferJingleImpl implements IncomingFileTransferRequest {
    private final OperationSetFileTransferJabberImpl fileTransferOpSet;

    private final IncomingFileOfferController mOffer;
    private final JingleFile mJingleFile;
    private IncomingFileTransferJingleImpl mFileTransfer = null;
    private File mFile;

    private final XMPPConnection mConnection;

    private Contact mSender;
    private final String mId;

    /*
     * Transfer file encryption type based on incoming encryption detection.
     */
    protected int mEncryption;

    /**
     * Creates an <code>IncomingFileOfferJingleImpl</code> based on the given
     * <code>fileTransferRequest</code>, coming from the Jabber protocol.
     *
     * @param pps the protocol provider
     * @param fileTransferOpSet file transfer operation set
     * @param fileTransferRequest the request coming from the Jabber protocol
     */
    public IncomingFileOfferJingleImpl(ProtocolProviderServiceJabberImpl pps,
            OperationSetFileTransferJabberImpl fileTransferOpSet, IncomingFileOfferController offer) {
        this.fileTransferOpSet = fileTransferOpSet;
        mConnection = pps.getConnection();

        mOffer = offer;
        mJingleFile = mOffer.getMetadata();
        HashElement hashElement = mJingleFile.getHashElement();
        mId = (hashElement != null) ? hashElement.getHashB64()
                : String.valueOf(System.currentTimeMillis()) + hashCode();

        // Determine the incoming content encryption type.
        mEncryption = IMessage.ENCRYPTION_NONE;
        Collection<JingleContentImpl> contentImpls = mOffer.getJingleSession().getContentImpls().values();
        for (JingleContentImpl jingleContent : contentImpls) {
            if (jingleContent.getSecurity() instanceof JetSecurityImpl) {
                mEncryption = IMessage.ENCRYPTION_OMEMO;
                break;
            };
        }

        BareJid remoteJid = mOffer.getJingleSession().getRemote().asBareJid();
        OperationSetPersistentPresenceJabberImpl opSetPersPresence
                = (OperationSetPersistentPresenceJabberImpl) pps.getOperationSet(OperationSetPersistentPresence.class);
        mSender = opSetPersPresence.findContactByJid(remoteJid);
        if (mSender == null) {
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet
                    = (OperationSetMultiUserChatJabberImpl) pps.getOperationSet(OperationSetMultiUserChat.class);

            if (mucOpSet != null)
                privateContactRoom = mucOpSet.getChatRoom(remoteJid);

            if (privateContactRoom != null) {
                mSender = opSetPersPresence.createVolatileContact(remoteJid, true);
                privateContactRoom.updatePrivateContactPresenceStatus(mSender);
            }
            else {
                // just create a volatile contact for new sender
                mSender = opSetPersPresence.createVolatileContact(remoteJid);
            }
        }
    }

    /**
     * JingleSessionImpl.addJingleSessionListener(this);
     */
    @Override
    public FileTransfer onPrepare(File file) {
        mFile = file;
        mFileTransfer = new IncomingFileTransferJingleImpl(this, file);
        return mFileTransfer;
    }

    public IncomingFileOfferController getController() {
        return mOffer;
    }

    /**
     * Returns the <code>Contact</code> making this request.
     *
     * @return the <code>Contact</code> making this request
     */
    @Override
    public Contact getSender() {
        return mSender;
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    @Override
    public String getFileDescription() {
        return mJingleFile.getDescription();
    }

    public String getMimeType() {
        return mJingleFile.getMediaType();
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    @Override
    public String getFileName() {
        return mJingleFile.getName();
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    @Override
    public long getFileSize() {
        return mJingleFile.getSize();
    }

    /**
     * The file transfer unique id.
     *
     * @return the id.
     */
    @Override
    public String getID() {
        return mId;
    }

    /**
     * Return the encryption of the incoming file corresponding to this FileTransfer.
     *
     * @return the encryption of the file corresponding to this request
     */
    public int getEncryptionType() {
        return mEncryption;
    }

    /**
     * Returns the thumbnail contained in this request. Jingle file transfer does not support thumbnail.
     *
     * @return the thumbnail contained in this request
     */
    @Override
    public byte[] getThumbnail() {
        return null;
    }

    /**
     * Accepts the file and starts the transfer.
     *
     * Note: If user cancels while in protocol negotiation; the accept() will return an error:
     * XMPPError: item-not-found - cancel
     */
    @Override
    public void acceptFile() {
        try {
            FileTransferCreatedEvent event = new FileTransferCreatedEvent(mFileTransfer, new Date());
            fileTransferOpSet.fireFileTransferCreated(event);

            mOffer.accept(mConnection, mFile);
        } catch (IOException | SmackException | InterruptedException | XMPPException.XMPPErrorException e) {
            aTalkApp.showToastMessage(R.string.xFile_FILE_RECEIVE_FAILED, e.getMessage());
            Timber.e("Receiving file failed; %s", e.getMessage());
        }
    }

    /**
     * Declines the incoming file offer.
     */
    @Override
    public void declineFile()
            throws OperationFailedException {
        try {
            mOffer.cancel(mConnection);
            mFileTransfer.removeIfoListener();
        } catch (NotConnectedException | InterruptedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
            throw new OperationFailedException("Could not decline the file offer", OperationFailedException.GENERAL_ERROR, e);
        }

        fileTransferOpSet.fireFileTransferRequestRejected(
                new FileTransferRequestEvent(fileTransferOpSet, this, new Date()));
    }
}
