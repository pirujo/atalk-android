/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia;

import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.RTCPUtils;
import org.atalk.util.function.Predicate;
import org.atalk.util.ByteArrayBuffer;
// import java.util.function.Predicate; => need API-24


/**
 * @author George Politis
 */
public class AbstractRTPPacketPredicate implements Predicate<ByteArrayBuffer>
{
    /**
     * True if this predicate should test for RTCP, false for RTP.
     */
    private final boolean rtcp;

    /**
     * Ctor.
     *
     * @param rtcp true if this predicate should test for RTCP, false for RTP.
     */
    public AbstractRTPPacketPredicate(boolean rtcp)
    {
        this.rtcp = rtcp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(ByteArrayBuffer pkt)
    {
        if (pkt == null || !RawPacket.isRtpRtcp(pkt.getBuffer(), pkt.getOffset(), pkt.getLength())) {
            return false;
        }

        if (RTCPUtils.isRtcp(pkt.getBuffer(), pkt.getOffset(), pkt.getLength())) {
            return rtcp;
        }
        else {
            return !rtcp;
        }
    }
}
