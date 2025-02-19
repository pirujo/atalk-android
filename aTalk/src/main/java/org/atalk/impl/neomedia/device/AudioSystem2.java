/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AudioSystem2 extends AudioSystem
{
    /**
     * /**
     * The number of times that {@link #willOpenStream()} has been invoked without an intervening
     * {@link #didOpenStream()} i.e. the number of API clients who are currently executing a
     * <code>Pa_OpenStream</code>-like function and which are thus inhibiting
     * <code>updateAvailableDeviceList()</code>.
     */
    private int openStream = 0;

    /**
     * The <code>Object</code> which synchronizes that access to {@link #openStream} and
     * {@link #updateAvailableDeviceList}.
     */
    private final Object openStreamSyncRoot = new Object();

    /**
     * The number of times that {@link #willUpdateAvailableDeviceList()} has been invoked without an
     * intervening {@link #didUpdateAvailableDeviceList()} i.e. the number of API clients who are
     * currently executing <code>updateAvailableDeviceList()</code> and who are thus inhibiting
     * <code>openStream</code>.
     */
    private int updateAvailableDeviceList = 0;

    /**
     * The list of <code>UpdateAvailableDeviceListListener</code>s which are to be notified before and
     * after this <code>AudioSystem</code>'s method <code>updateAvailableDeviceList()</code> is invoked.
     */
    private final List<WeakReference<UpdateAvailableDeviceListListener>> updateAvailableDeviceListListeners = new LinkedList<>();

    /**
     * The <code>Object</code> which ensures that this <code>AudioSystem</code>'s function to update the
     * list of available devices will not be invoked concurrently. The condition should hold true on
     * the native side but, anyway, it should not hurt (much) to enforce it on the Java side as
     * well.
     */
    private final Object updateAvailableDeviceListSyncRoot = new Object();

    protected AudioSystem2(String locatorProtocol, int features)
            throws Exception
    {
        super(locatorProtocol, features);
    }

    /**
     * Adds a listener which is to be notified before and after this <code>AudioSystem</code>'s method
     * <code>updateAvailableDeviceList()</code> is invoked.
     * <p>
     * <b>Note</b>: The <code>AudioSystem2</code> class keeps a <code>WeakReference</code> to the specified
     * <code>listener</code> in order to avoid memory leaks.
     * </p>
     *
     * @param listener the <code>UpdateAvailableDeviceListListener</code> to be notified before and after this
     * <code>AudioSystem</code>'s method <code>updateAvailableDeviceList()</code> is invoked
     */
    public void addUpdateAvailableDeviceListListener(UpdateAvailableDeviceListListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (updateAvailableDeviceListListeners) {
            Iterator<WeakReference<UpdateAvailableDeviceListListener>> i = updateAvailableDeviceListListeners
                    .iterator();
            boolean add = true;

            while (i.hasNext()) {
                UpdateAvailableDeviceListListener l = i.next().get();

                if (l == null)
                    i.remove();
                else if (l.equals(listener))
                    add = false;
            }
            if (add) {
                updateAvailableDeviceListListeners.add(new WeakReference<>(listener));
            }
        }
    }

    /**
     * Sorts a specific list of <code>CaptureDeviceInfo2</code>s so that the ones representing USB
     * devices appear at the beginning/top of the specified list.
     *
     * @param devices the list of <code>CaptureDeviceInfo2</code>s to be sorted so that the ones representing
     * USB devices appear at the beginning/top of the list
     */
    protected static void bubbleUpUsbDevices(List<CaptureDeviceInfo2> devices)
    {
        if (!devices.isEmpty()) {
            List<CaptureDeviceInfo2> nonUsbDevices = new ArrayList<>(
                    devices.size());

            for (Iterator<CaptureDeviceInfo2> i = devices.iterator(); i.hasNext(); ) {
                CaptureDeviceInfo2 d = i.next();

                if (!d.isSameTransportType("USB")) {
                    nonUsbDevices.add(d);
                    i.remove();
                }
            }
            if (!nonUsbDevices.isEmpty()) {
                devices.addAll(nonUsbDevices);
            }
        }
    }

    /**
     * Notifies this <code>AudioSystem</code> that an API client finished executing a
     * <code>Pa_OpenStream</code>-like function.
     */
    public void didOpenStream()
    {
        synchronized (openStreamSyncRoot) {
            openStream--;
            if (openStream < 0)
                openStream = 0;

            openStreamSyncRoot.notifyAll();
        }
    }

    /**
     * Notifies this <code>AudioSystem</code> that a it has finished executing
     * <code>updateAvailableDeviceList()</code>.
     */
    private void didUpdateAvailableDeviceList()
    {
        synchronized (openStreamSyncRoot) {
            updateAvailableDeviceList--;
            if (updateAvailableDeviceList < 0)
                updateAvailableDeviceList = 0;

            openStreamSyncRoot.notifyAll();
        }

        fireUpdateAvailableDeviceListEvent(false);
    }

    /**
     * Notifies the registered <code>UpdateAvailableDeviceListListener</code>s that this
     * <code>AudioSystem</code>'s method <code>updateAvailableDeviceList()</code> will be or was invoked.
     *
     * @param will <code>true</code> if this <code>AudioSystem</code>'s method
     * <code>updateAvailableDeviceList()</code> will be invoked or <code>false</code> if it was invoked
     */
    private void fireUpdateAvailableDeviceListEvent(boolean will)
    {
        List<WeakReference<UpdateAvailableDeviceListListener>> ls;
        synchronized (updateAvailableDeviceListListeners) {
            ls = new ArrayList<>(updateAvailableDeviceListListeners);
        }

        for (WeakReference<UpdateAvailableDeviceListListener> wr : ls) {
            UpdateAvailableDeviceListListener l = wr.get();

            if (l != null) {
                try {
                    if (will)
                        l.willUpdateAvailableDeviceList();
                    else
                        l.didUpdateAvailableDeviceList();
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    else {
                        Timber.e("UpdateAvailableDeviceListListener %s failed. %s", (will ? "will" : "did"), t.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Attempts to reorder specific lists of capture and playback/notify <code>CaptureDeviceInfo2</code>
     * s so that devices from the same hardware appear at the same indices in the respective lists.
     * The judgment with respect to the belonging to the same hardware is based on the names of the
     * specified <code>CaptureDeviceInfo2</code>s. The implementation is provided as a fallback to stand
     * in for scenarios in which more accurate relevant information is not available.
     *
     * @param captureDevices
     * @param playbackDevices
     */
    protected static void matchDevicesByName(List<CaptureDeviceInfo2> captureDevices,
            List<CaptureDeviceInfo2> playbackDevices)
    {
        Iterator<CaptureDeviceInfo2> captureIter = captureDevices.iterator();
        Pattern pattern = Pattern.compile(
                "array|headphones|microphone|speakers|\\p{Space}|\\(|\\)", Pattern.CASE_INSENSITIVE);
        LinkedList<CaptureDeviceInfo2> captureDevicesWithPlayback = new LinkedList<>();
        LinkedList<CaptureDeviceInfo2> playbackDevicesWithCapture = new LinkedList<>();
        int count = 0;

        while (captureIter.hasNext()) {
            CaptureDeviceInfo2 captureDevice = captureIter.next();
            String captureName = captureDevice.getName();

            if (captureName != null) {
                captureName = pattern.matcher(captureName).replaceAll("");
                if (captureName.length() != 0) {
                    Iterator<CaptureDeviceInfo2> playbackIter = playbackDevices.iterator();
                    CaptureDeviceInfo2 matchingPlaybackDevice = null;

                    while (playbackIter.hasNext()) {
                        CaptureDeviceInfo2 playbackDevice = playbackIter.next();
                        String playbackName = playbackDevice.getName();

                        if (playbackName != null) {
                            playbackName = pattern.matcher(playbackName).replaceAll("");
                            if (captureName.equals(playbackName)) {
                                playbackIter.remove();
                                matchingPlaybackDevice = playbackDevice;
                                break;
                            }
                        }
                    }
                    if (matchingPlaybackDevice != null) {
                        captureIter.remove();
                        captureDevicesWithPlayback.add(captureDevice);
                        playbackDevicesWithCapture.add(matchingPlaybackDevice);
                        count++;
                    }
                }
            }
        }

        for (int i = count - 1; i >= 0; i--) {
            captureDevices.add(0, captureDevicesWithPlayback.get(i));
            playbackDevices.add(0, playbackDevicesWithCapture.get(i));
        }
    }

    /**
     * Reinitializes this <code>AudioSystem</code> in order to bring it up to date with possible changes
     * in the list of available devices. Invokes <code>updateAvailableDeviceList()</code> to update the
     * devices on the native side and then {@link #initialize()} to reflect any changes on the Java
     * side. Invoked by the native side of this <code>AudioSystem</code> when it detects that the list
     * of available devices has changed.
     *
     * @throws Exception if there was an error during the invocation of <code>updateAvailableDeviceList()</code>
     * and <code>DeviceSystem.initialize()</code>
     */
    protected void reinitialize()
            throws Exception
    {
        synchronized (updateAvailableDeviceListSyncRoot) {
            willUpdateAvailableDeviceList();
            try {
                updateAvailableDeviceList();
            } finally {
                didUpdateAvailableDeviceList();
            }
        }

        /*
         * XXX We will likely minimize the risk of crashes on the native side even further by
         * invoking initialize() with updateAvailableDeviceList() locked. Unfortunately, that will
         * likely increase the risks of deadlocks on the Java side.
         */
        invokeDeviceSystemInitialize(this);
    }

    public void removeUpdateAvailableDeviceListListener(UpdateAvailableDeviceListListener listener)
    {
        if (listener == null)
            return;

        synchronized (updateAvailableDeviceListListeners) {
            Iterator<WeakReference<UpdateAvailableDeviceListListener>> i = updateAvailableDeviceListListeners
                    .iterator();

            while (i.hasNext()) {
                UpdateAvailableDeviceListListener l = i.next().get();

                if ((l == null) || l.equals(listener))
                    i.remove();
            }
        }
    }

    protected abstract void updateAvailableDeviceList();

    /**
     * Waits for all API clients to finish executing a <code>Pa_OpenStream</code> -like function.
     */
    private void waitForOpenStream()
    {
        boolean interrupted = false;

        while (openStream > 0) {
            try {
                openStreamSyncRoot.wait();
            } catch (InterruptedException ie) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * Waits for all API clients to finish executing <code>updateAvailableDeviceList()</code>.
     */
    private void waitForUpdateAvailableDeviceList()
    {
        boolean interrupted = false;

        while (updateAvailableDeviceList > 0) {
            try {
                openStreamSyncRoot.wait();
            } catch (InterruptedException ie) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * Notifies this <code>AudioSystem</code> that an API client will start executing a
     * <code>Pa_OpenStream</code>-like function.
     */
    public void willOpenStream()
    {
        synchronized (openStreamSyncRoot) {
            waitForUpdateAvailableDeviceList();

            openStream++;
            openStreamSyncRoot.notifyAll();
        }
    }

    /**
     * Notifies this <code>AudioSystem</code> that it will start executing <code>updateAvailableDeviceList()</code>.
     */
    private void willUpdateAvailableDeviceList()
    {
        synchronized (openStreamSyncRoot) {
            waitForOpenStream();

            updateAvailableDeviceList++;
            openStreamSyncRoot.notifyAll();
        }

        fireUpdateAvailableDeviceListEvent(true);
    }
}
