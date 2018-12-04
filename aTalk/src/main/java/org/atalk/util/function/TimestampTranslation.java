/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.util.function;

// import java.util.function.Function; => need API-24

/**
 * @author George Politis
 */
public class TimestampTranslation extends AbstractFunction<Long, Long>
{
    /**
     * The delta to apply to the timestamp that is specified as an argument in the apply method.
     */
    private final long tsDelta;

    /**
     * Ctor.
     *
     * @param tsDelta The delta to apply to the timestamp that is specified as an argument in the apply method.
     */
    public TimestampTranslation(long tsDelta)
    {
        this.tsDelta = tsDelta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long apply(Long ts)
    {
        return tsDelta == 0 ? ts : (ts + tsDelta) & 0xFFFFFFFFL;
    }
}
