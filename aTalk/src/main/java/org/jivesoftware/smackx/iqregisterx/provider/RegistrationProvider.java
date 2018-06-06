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

package org.jivesoftware.smackx.iqregisterx.provider;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.bob.packet.BoB;
import org.jivesoftware.smackx.iqregisterx.packet.Registration;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.xmlpull.v1.XmlPullParser;

import java.util.*;

public class RegistrationProvider extends IQProvider<Registration>
{
    @Override
    public Registration parse(XmlPullParser parser, int initialDepth)
            throws Exception
    {
        Map<String, String> fields = new HashMap<>();
        DataForm dataForm = null;

        boolean isRegistered = false;
        String instruction = null;
        BoB bob = null;

        List<ExtensionElement> packetExtensions = new LinkedList<>();
        outerloop: while (true) {
            int eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    // Any element that's in the jabber:iq:register namespace,
                    // attempt to parse it if it's in the form <name>value</name>.
                    String nameSpace = parser.getNamespace();
                    switch (nameSpace) {
                        case Registration.NAMESPACE:
                            String name = parser.getName();
                            String value = "";

                            // Ignore instructions, but anything else should be added to the map.
                            if (name.equals(Registration.ELE_REGISTERED)) {
                                isRegistered = true;
                            }
                            else {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    value = parser.getText();
                                }
                                // Ignore instructions, but anything else should be added to the map.
                                if (!name.equals("instructions")) {
                                    fields.put(name, value);
                                }
                                else {
                                    instruction = value;
                                }
                            }
                            break;
                        case DataForm.NAMESPACE:
                            dataForm = (DataForm)
                                    PacketParserUtils.parseExtensionElement(DataForm.ELEMENT, DataForm.NAMESPACE, parser);
                            break;
                        case BoB.NAMESPACE:
                            bob = (BoB) PacketParserUtils.parseExtensionElement(BoB.ELEMENT, BoB.NAMESPACE, parser);
                            break;
                        // In case there are more packet extension.
                        default:
                            PacketParserUtils.addExtensionElement(packetExtensions, parser);
                            break;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
            }
        }

        Registration registration = new Registration(fields, dataForm);
        registration.setRegistrationStatus(isRegistered);
        registration.setInstructions(instruction);
        registration.setBoB(bob);
        registration.addExtensions(packetExtensions);
        return registration;
    }
}
