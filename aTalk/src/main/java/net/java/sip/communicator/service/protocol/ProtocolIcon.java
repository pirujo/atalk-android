/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import java.util.*;

/**
 * The <code>ProtocolIcon</code> interface is meant to be implemented by protocol implementations in
 * order to export their icon. The <code>Protocolicon</code> could support different sizes: 16x16,
 * 32x32, etc. The ICON_SIZE_XXX constats are meant to be used to specify the size of the icon, in
 * order to enable other bundles to obtain the exact image they need.
 *
 * @author Yana Stamcheva
 */
public interface ProtocolIcon
{
	/**
	 * Defines a 16x16 icon size.
	 */
	public static final String ICON_SIZE_16x16 = "IconSize16x16";

	/**
	 * Defines a 32x32 icon size.
	 */
	public static final String ICON_SIZE_32x32 = "IconSize32x32";

	/**
	 * Defines a 48x48 icon size.
	 */
	public static final String ICON_SIZE_48x48 = "IconSize48x48";

	/**
	 * logo Defines a 64x64 icon size.
	 */
	public static final String ICON_SIZE_64x64 = "IconSize64x64";

	/**
	 * Returns an iterator over a set, containing different predefined icon sizes. Each icon size in
	 * the set is one of the ICON_SIZE_XXX constants. The method is meant to be implemented by a
	 * protocol implementation in order to allow other bundles to obtain information about the
	 * number of sizes in which this icon is exported.
	 *
	 * @return Iterator an iterator over a set containing different predefined icon sizes. Each icon
	 *         size in the set is one of the ICON_SIZE_XXX constants.
	 */
	public Iterator<String> getSupportedSizes();

	/**
	 * Checks if the given icon size is supported by the current protocol implementation. If the
	 * given <code>iconSize</code> is contained in the list of supported sizes - returns TRUE, otherwise
	 * - FALSE.
	 *
	 * @param iconSize
	 *        the size of the protocol icon; one of the ICON_SIZE_XXX constants
	 * @return TRUE - if the given icon size is supported by the current implementation, FALSE -
	 *         otherwise.
	 */
	public boolean isSizeSupported(String iconSize);

	/**
	 * Returns the protocol icon image in the desired size.
	 * 
	 * @param iconSize
	 *        the size of the protocol icon; one of the ICON_SIZE_XXX constants
	 * @return the protocol icon image in the desired size
	 */
	public byte[] getIcon(String iconSize);

	/**
	 * Returns a path to the icon with the given size.
	 * 
	 * @param iconSize
	 *        the size of the icon we're looking for
	 * @return the path to the icon with the given size
	 */
	public String getIconPath(String iconSize);

	/**
	 * Returns the icon that should be used when the protocol provider is in a connecting state.
	 *
	 * @return the icon that should be used when the protocol provider is in a connecting state
	 */
	public byte[] getConnectingIcon();
}
