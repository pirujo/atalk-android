/*
 * Copyright @ 2018 Atlassian Pty Ltd
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

package org.atalk.util.logging;

import org.json.JSONObject;

import java.util.Map;

import timber.log.Timber;

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
public class TimeSeriesLogger
{
    /**
     * The Java logger that's going to output the time series points.
     */
    private final Logger logger;

    /**
     * Create a logger for the specified class.
     *
     * @param clazz The class for which to create a logger.
     *
     * @return a suitable Logger
     * @throws NullPointerException if the class is null.
     */
    public static TimeSeriesLogger getTimeSeriesLogger(Class<?> clazz)
            throws NullPointerException
    {
        String name = "timeseries." + clazz.getName();
        Logger logger = Logger.getLogger(name);
        return new TimeSeriesLogger(logger);
    }

    /**
     * Ctor.
     */
    public TimeSeriesLogger(Logger logger)
    {
        this.logger = logger;
    }

    /**
     * Check if a message with a TRACE level would actually be logged by this logger.
     *
     * @return true if the TRACE level is currently being logged, otherwise false.
     */
    public boolean isTraceEnabled()
    {
        return logger.isTraceEnabled();
    }

    /**
     * Check if a message with a WARNING level would actually be logged by this logger.
     *
     * @return true if the WARNING level is currently being logged, otherwise false.
     */
    public boolean isWarnEnabled()
    {
        return logger.isWarnEnabled();
    }

    /**
     * Check if a message with a INFO level would actually be logged by this logger.
     *
     * @return true if the INFO level is currently being logged, otherwise false.
     */
    public boolean isInfoEnabled()
    {
        return logger.isInfoEnabled();
    }

    /**
     * Traces a {@link DiagnosticContext.TimeSeriesPoint}.
     *
     * @param point the point to trace.
     */
    public void trace(Map<String, Object> point)
    {
        if (point != null && !point.isEmpty()) {
            logger.trace(new JSONObject(point).toString());
        }
    }

    /**
     * Logs a {@link DiagnosticContext.TimeSeriesPoint} in WARNING level.
     *
     * @param point the point to log.
     */
    public void warn(Map<String, Object> point)
    {
        if (point != null && !point.isEmpty()) {
            logger.warn(new JSONObject(point).toString());
        }
    }

    /**
     * Logs a {@link DiagnosticContext.TimeSeriesPoint} in INFO level.
     *
     * @param point the point to log.
     */
    public void info(Map<String, Object> point)
    {
        if (point != null && !point.isEmpty()) {
            logger.info(new JSONObject(point).toString());
        }
    }
}
