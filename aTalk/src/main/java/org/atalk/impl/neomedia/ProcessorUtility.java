/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import javax.media.*;

import timber.log.Timber;

/**
 * A utility class that provides utility functions when working with processors.
 *
 * @author Emil Ivov
 * @author Ken Larson
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class ProcessorUtility implements ControllerListener
{
    /**
     * The <code>Object</code> used for syncing when waiting for a processor to enter a specific state.
     */
    private final Object stateLock = new Object();

    /**
     * The indicator which determines whether the waiting of this instance on a processor for it to
     * enter a specific state has failed.
     */
    private boolean failed = false;

    /**
     * The maximum amount of time in seconds we will spend in waiting between
     * processor state changes to avoid locking threads forever.
     * Default value of 10 seconds, should be long enough.
     */
    private static final int WAIT_TIMEOUT = 10;

    /**
     * Initializes a new <code>ProcessorUtility</code> instance.
     */
    public ProcessorUtility()
    {
    }

    /**
     * Gets the <code>Object</code> to use for syncing when waiting for a processor to enter a specific state.
     *
     * @return the <code>Object</code> to use for syncing when waiting for a processor to enter a
     * specific state
     */
    private Object getStateLock()
    {
        return stateLock;
    }

    /**
     * Specifies whether the wait operation has failed or completed with success.
     *
     * @param failed <code>true</code> if waiting has failed; <code>false</code>, otherwise
     */
    private void setFailed(boolean failed)
    {
        this.failed = failed;
    }

    /**
     * This method is called when an event is generated by a {@code Controller} that this listener
     * is registered with. We use the event to notify all waiting on our lock and record success or failure.
     *
     * @param ce The event generated.
     */
    public void controllerUpdate(ControllerEvent ce)
    {
        Object stateLock = getStateLock();
        synchronized (stateLock) {
            // If there was an error during configure or
            // realize, the processor will be closed
            if (ce instanceof ControllerClosedEvent) {
                if (ce instanceof ControllerErrorEvent)
                    Timber.w("ControllerErrorEvent: %s", ((ControllerErrorEvent) ce).getMessage());
                else
                    Timber.d("ControllerClosedEvent: %s", ((ControllerClosedEvent) ce).getMessage());
                setFailed(true);
                // All controller events, send a notification to the waiting thread in waitForState method.
            }
            stateLock.notifyAll();
        }
    }

    /**
     * Waits until <code>processor</code> enters state and returns a boolean indicating success or
     * failure of the operation.
     *
     * @param processor Processor
     * @param state one of the Processor.XXXed state vars
     * @return <code>true</code> if the state has been reached; <code>false</code>, otherwise
     */
    public synchronized boolean waitForState(Processor processor, int state)
    {
        processor.addControllerListener(this);
        setFailed(false);

        // Call the required method on the processor
        if (state == Processor.Configured)
            processor.configure();
        else if (state == Processor.Realized)
            processor.realize();

        boolean interrupted = false;

        Object stateLock = getStateLock();
        synchronized (stateLock) {
            // Wait until we get an event that confirms the
            // success of the method, or a failure event.
            // See JmStateListener inner class
            while ((processor.getState() < state) && !failed) {
                try {
                    // don't wait forever, there is some other problem where we wait on an already closed
                    // processor and we never leave this wait
//                        Instant startTime = Instant.now();
//                        stateLock.wait(WAIT_TIMEOUT * 1000);
//                        if (Duration.between(startTime, Instant.now()).getSeconds() >= WAIT_TIMEOUT) {
//                            // timeout reached we consider failure
//                            setFailed(true);
//                       }
                    Long timeStart = System.currentTimeMillis();
                    stateLock.wait(WAIT_TIMEOUT * 1000);
                    if ((System.currentTimeMillis() - timeStart) > WAIT_TIMEOUT * 1000) {
                        // timeout reached we consider failure
                        setFailed(true);
                    }
                } catch (InterruptedException ie) {
                    Timber.w(ie, "Interrupted while waiting on Processor %s for state %s", processor, state);
                    /*
                     * XXX It is not really clear what we should do. It seems that an
                     * InterruptedException may be thrown and the Processor will still work fine.
                     * Consequently, we cannot fail here. Besides, if the Processor fails, it will
                     * tell us with a ControllerEvent anyway and we will get out of the loop.
                     */
                    interrupted = true;
                    // processor.removeControllerListener(this);
                    // return false;
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        processor.removeControllerListener(this);
        return !failed;
    }
}
