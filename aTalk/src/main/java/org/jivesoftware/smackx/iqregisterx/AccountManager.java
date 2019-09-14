/**
 *
 * Copyright 2003-2007 Jive Software.
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

package org.jivesoftware.smackx.iqregisterx;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.parts.Localpart;

import java.util.*;

/**
 * Allows creation and management of accounts on an XMPP server.
 * It support both the field parameters or DataForm submission for account creation.
 *
 * @author Matt Tucker
 * @author Eng Chong Meng
 */
public final class AccountManager extends Manager {

    private static final Map<XMPPConnection, AccountManager> INSTANCES = new WeakHashMap<>();

    /**
     * Returns the AccountManager instance associated with a given XMPPConnection.
     *
     * @param connection the connection used to look for the proper ServiceDiscoveryManager.
     * @return the AccountManager associated with a given XMPPConnection.
     */
    public static synchronized AccountManager getInstance(XMPPConnection connection) {
        AccountManager accountManager = INSTANCES.get(connection);
        if (accountManager == null) {
            accountManager = new AccountManager(connection);
            INSTANCES.put(connection, accountManager);
        }
        return accountManager;
    }

    private static boolean allowSensitiveOperationOverInsecureConnectionDefault = false;

    /**
     * The default value used by new account managers for <code>allowSensitiveOperationOverInsecureConnection</code>.
     *
     * @param allow
     * @see #sensitiveOperationOverInsecureConnection(boolean)
     * @since 4.1
     */
    public static void sensitiveOperationOverInsecureConnectionDefault(boolean allow) {
        AccountManager.allowSensitiveOperationOverInsecureConnectionDefault = allow;
    }

    private boolean allowSensitiveOperationOverInsecureConnection = allowSensitiveOperationOverInsecureConnectionDefault;

    /**
     * Set to <code>true</code> to allow sensitive operation over insecure connection.
     * <p>
     * Set to true to allow sensitive operations like account creation or password changes over an insecure (e.g.
     * unencrypted) connections.
     * </p>
     *
     * @param allow
     * @since 4.1
     */
    public void sensitiveOperationOverInsecureConnection(boolean allow) {
        this.allowSensitiveOperationOverInsecureConnection = allow;
    }

    private Registration info = null;

    /**
     * Flag that indicates whether the server supports In-Band Registration.
     * In-Band Registration may be advertised as a stream feature. If no stream feature
     * was advertised from the server then try sending an IQ stanza to discover if In-Band
     * Registration is available.
     */
    private boolean accountCreationSupported = false;

    /**
     * Creates a new AccountManager instance.
     *
     * @param connection a connection to an XMPP server.
     */
    private AccountManager(XMPPConnection connection) {
        super(connection);
    }

    /**
     * Sets whether the server supports In-Band Registration. In-Band Registration may be
     * advertised as a stream feature. If no stream feature was advertised from the server
     * then try sending an IQ stanza to discover if In-Band Registration is available.
     *
     * @param accountCreationSupported true if the server supports In-Band Registration.
     */
    // TODO: Remove this method and the accountCreationSupported boolean.
    void setSupportsAccountCreation(boolean accountCreationSupported) {
        this.accountCreationSupported = accountCreationSupported;
    }

    /**
     * Returns true if the server supports creating new accounts. Many servers require
     * that you not be currently authenticated when creating new accounts, so the safest
     * behavior is to only create new accounts before having logged in to a server.
     *
     * @return true if the server support creating new accounts.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public boolean supportsAccountCreation()
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        // TODO: Replace this body with isSupported() and possible deprecate this method.

        // Check if we already know that the server supports creating new accounts
        if (accountCreationSupported) {
            return true;
        }
        // No information is known yet (e.g. no stream feature was received from the server
        // indicating that it supports creating new accounts) so send an IQ packet as a way
        // to discover if this feature is supported
        if (info == null) {
            _getRegistrationInfo();
            accountCreationSupported = info.getType() != IQ.Type.error;
        }
        return accountCreationSupported;
    }

    /**
     * Returns an unmodifiable collection of the names of the required account attributes.
     * All attributes must be set when creating new accounts. The standard set of possible
     * attributes are as follows: <ul>
     *      <li>nick     -- Familiar name of the user.
     *      <li>name     -- Full name of the user.
     *      <li>first    -- Given name of the user.
     *      <li>last     -- Family name of the user.
     *      <li>email    -- Email address of the user.
     *      <li>city     -- Locality portion of a physical or mailing address.
     *      <li>state    -- Region portion of a physical or mailing address.
     *      <li>zip      -- Postal code portion of a physical or mailing address.
     *      <li>phone    -- Telephone number of the user.
     *      <li>address  -- Street portion of a physical or mailing address.
     *      <li>url      -- URL to web page describing the user.
     *      <li>date     -- Some date (e.g., birth date, hire date, sign-up date).
     *      <li>misc     -- other miscellaneous information to associate with the account.
     *      <li>text     -- textual information to associate with the account.
     *      <li>remove   -- empty flag to remove account.
     * </ul><p>
     *
     * Typically, servers require no attributes when creating new accounts, or just
     * the user's email address.
     *
     * @return the required account attributes.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public Set<String> getAccountAttributes() throws NoResponseException, XMPPErrorException,
            NotConnectedException, InterruptedException  {
        if (info == null) {
            _getRegistrationInfo();
        }
        Map<String, String> attributes = info.getAttributes();
        if (attributes != null) {
            return Collections.unmodifiableSet(attributes.keySet());
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Returns the value of a given account attribute or <code>null</code> if the account
     * attribute wasn't found.
     *
     * @param name the name of the account attribute to return its value.
     * @return the value of the account attribute or <code>null</code> if an account
     * attribute wasn't found for the requested name.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public String getAccountAttribute(String name) throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException  {
        if (info == null) {
            _getRegistrationInfo();
        }
        return info.getAttributes().get(name);
    }

    /**
     * Returns the instructions for creating a new account, or <code>null</code> if there
     * are no instructions. If present, instructions should be displayed to the end-user
     * that will complete the registration process.
     *
     * @return the account creation instructions, or <code>null</code> if there are none.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public String getAccountInstructions() throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException  {
        if (info == null) {
            _getRegistrationInfo();
        }
        return info.getInstructions();
    }

    /**
     * Gets account registration process and requirements from the server.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public Registration getRegistrationInfo() throws NoResponseException,
            XMPPErrorException,
            NotConnectedException, InterruptedException {
        if (info == null) {
            _getRegistrationInfo();
        }
        return info;
    }

    /**
     * Creates a new account using the specified username and password. The server may
     * require a number of extra account attributes such as an email address and phone
     * number. In that case, Smack will attempt to automatically set all required
     * attributes with blank values, which may or may not be accepted by the server.
     * Therefore, it's recommended to check the required account attributes and to let
     * the end-user populate them with real values instead.
     *
     * @param username the username.
     * @param password the password.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public void createAccount(Localpart username, String password) throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException  {
        // Create a map for all the required attributes, but give them blank values.
        Map<String, String> attributes = new HashMap<>();
        for (String attributeName : getAccountAttributes()) {
            attributes.put(attributeName, "");
        }
        createAccount(username, password, attributes);
    }

    /**
     * Creates a new account using the specified username, password and account attributes.
     * The attributes Map must contain only String name/value pairs and must also have values
     * for all required attributes.
     *
     * @param username the username.
     * @param password the password.
     * @param attributes the account attributes.
     * @throws XMPPErrorException if an error occurs creating the account.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     * @see #getAccountAttributes()
     */
    public void createAccount(Localpart username, String password, Map<String, String> attributes)
                    throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        if (!connection().isSecureConnection() && !allowSensitiveOperationOverInsecureConnection) {
            throw new IllegalStateException("Creating account over insecure connection");
        }
        if (username == null) {
            throw new IllegalArgumentException("Username must not be null");
        }
        if (StringUtils.isNullOrEmpty(password)) {
            throw new IllegalArgumentException("Password must not be null");
        }

        attributes.put("username", username.toString());
        attributes.put("password", password);
        Registration reg = new Registration(attributes);
        reg.setType(IQ.Type.set);
        reg.setTo(connection().getXMPPServiceDomain());
        createStanzaCollectorAndSend(reg).nextResultOrThrow();
    }

    /**
     * Creates a new account using DataForm submission that contains all the necessary
     * answers for all requested fields. The DataForm.Type must be "submit".
     *
     * @param formSubmit the registration DataForm to submit.
     * @throws XMPPErrorException if an error occurs creating the account.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public void createAccount(DataForm formSubmit)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        if (!connection().isSecureConnection() && !allowSensitiveOperationOverInsecureConnection) {
            throw new IllegalStateException("Creating account over insecure connection");
        }

        Registration reg = new Registration(formSubmit);
        reg.setType(IQ.Type.set);
        reg.setTo(connection().getXMPPServiceDomain());
        createStanzaCollectorAndSend(reg).nextResultOrThrow();
    }

    /**
     * Changes the password of the currently logged-in account. This operation can only
     * be performed after a successful login operation has been completed. Not all servers
     * support changing passwords; an XMPPException will be thrown when that is the case.
     *
     * @param newPassword new password.
     *
     * @throws IllegalStateException if not currently logged-in to the server.
     * @throws XMPPErrorException if an error occurs when changing the password.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public void changePassword(String newPassword) throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        if (!connection().isSecureConnection() && !allowSensitiveOperationOverInsecureConnection) {
            throw new IllegalStateException("Changing password over insecure connection.");
        }
        Map<String, String> map = new HashMap<>();
        map.put("username",  connection().getUser().getLocalpart().toString());
        map.put("password", newPassword);
        Registration reg = new Registration(map);
        reg.setType(IQ.Type.set);
        reg.setTo(connection().getXMPPServiceDomain());
        createStanzaCollectorAndSend(reg).nextResultOrThrow();
    }

    /**
     * Deletes the currently logged-in account from the server. This operation can only
     * be performed after a successful login operation has been completed. Not all servers
     * support deleting accounts; an XMPPException will be thrown when that is the case.
     *
     * @throws IllegalStateException if not currently logged-in to the server.
     * @throws XMPPErrorException if an error occurs when deleting the account.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     */
    public void deleteAccount() throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        Map<String, String> attributes = new HashMap<>();
        // To delete an account, we add a single attribute, "remove", that is blank.
        attributes.put("remove", "");
        Registration reg = new Registration(attributes);
        reg.setType(IQ.Type.set);
        reg.setTo(connection().getXMPPServiceDomain());
        createStanzaCollectorAndSend(reg).nextResultOrThrow();
    }

    public boolean isSupported()
                    throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
        XMPPConnection connection = connection();

        ExtensionElement extensionElement = connection.getFeature(Registration.Feature.ELEMENT,
                Registration.Feature.NAMESPACE);
        if (extensionElement != null) {
            return true;
        }

        // Fallback to disco#info only if this connection is authenticated, as otherwise we won't have an full JID and
        // won't be able to do IQs.
        if (connection.isAuthenticated()) {
            return ServiceDiscoveryManager.getInstanceFor(connection).serverSupportsFeature(Registration.NAMESPACE);
        }
        return false;
    }

    /**
     * Gets the account registration info from the server.
     * @throws XMPPErrorException if there was an XMPP error returned.
     * @throws NoResponseException if there was no response from the remote entity.
     * @throws NotConnectedException if the XMPP connection is not connected.
     * @throws InterruptedException if the calling thread was interrupted.
     *
     * @throws XMPPException if an error occurs.
     * @throws SmackException if there was no response from the server.
     */
    private synchronized void _getRegistrationInfo() throws NoResponseException, XMPPErrorException,
            NotConnectedException, InterruptedException {
        Registration reg = new Registration();
        reg.setTo(connection().getXMPPServiceDomain());
        info = createStanzaCollectorAndSend(reg).nextResultOrThrow();
    }

    private StanzaCollector createStanzaCollectorAndSend(IQ req) throws NotConnectedException, InterruptedException {
        return connection().createStanzaCollectorAndSend(new StanzaIdFilter(req.getStanzaId()), req);
    }
}
