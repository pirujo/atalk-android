/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.*;

import java.io.IOException;

/**
 * The <tt>ReasonProvider</tt> parses "reason" elements into {@link ReasonPacketExtension}
 * instances.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class ReasonProvider extends ExtensionElementProvider<ReasonPacketExtension>
{
	/**
	 * Parses a reason extension sub-packet and creates a {@link ReasonPacketExtension} instance. At
	 * the beginning of the method call, the xml parser will be positioned on the opening element of
	 * the packet extension. As required by the smack API, at the end of the method call, the parser
	 * will be positioned on the closing element of the packet extension.
	 *
	 * @param parser
	 *        an XML parser positioned at the opening <tt>reason</tt> element.
	 *
	 * @return a new {@link ReasonPacketExtension} instance.
	 * @throws java.lang.Exception
	 *         if an error occurs parsing the XML.
	 */

	@Override
	public ReasonPacketExtension parse(XmlPullParser parser, int initDepth)
		throws XmlPullParserException, IOException, SmackException
	{
		String text = null;
		Reason reason = null;

		boolean done = false;
		int eventType;
		String elementName;

		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();

			if (eventType == XmlPullParser.START_TAG) {
				// the reason itself.
				if (reason == null) {
					// let the parse exception fly as it would mean we have some weird element
					// first in the list.
					reason = Reason.parseString(elementName);
				}
				else if (elementName.equals(ReasonPacketExtension.TEXT_ELEMENT_NAME)) {
					try {
						text = parseText(parser);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
				else {
					// this is an element that we don't currently support.
				}
			}
			else if (eventType == XmlPullParser.END_TAG) {
				if (parser.getName().equals(ReasonPacketExtension.ELEMENT_NAME)) {
					done = true;
				}
			}
		}
		return new ReasonPacketExtension(reason, text, null);
	}

	/**
	 * Returns the content of the next {@link XmlPullParser#TEXT} element that we encounter in
	 * <tt>parser</tt>.
	 *
	 * @param parser
	 *        the parse that we'll be probing for text.
	 *
	 * @return the content of the next {@link XmlPullParser#TEXT} element we come across or
	 *         <tt>null</tt> if we encounter a closing tag first.
	 *
	 * @throws java.lang.Exception
	 *         if an error occurs parsing the XML.
	 */
	public String parseText(XmlPullParser parser)
		throws Exception
	{
		boolean done = false;

		int eventType;
		String text = null;

		while (!done) {
			eventType = parser.next();

			if (eventType == XmlPullParser.TEXT) {
				text = parser.getText();
			}
			else if (eventType == XmlPullParser.END_TAG) {
				done = true;
			}
		}
		return text;
	}
}
