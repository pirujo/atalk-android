/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.imgstreaming;

import javax.media.*;
import javax.media.control.*;

import org.atalk.impl.neomedia.control.ImgStreamingControl;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferCaptureDevice;
import java.awt.Component;

/**
 * Implements <code>CaptureDevice</code> and <code>DataSource</code> for the purposes of image and desktop
 * streaming.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class DataSource extends AbstractVideoPullBufferCaptureDevice
{
	/**
	 * The <code>ImgStreamingControl</code> implementation which allows controlling this
	 * <code>DataSource</code> through the "standard" FMJ/JMF <code>javax.media.Control</code> means.
	 */
	private final ImgStreamingControl imgStreamingControl = new ImgStreamingControl()
	{
		public Component getControlComponent()
		{
			/*
			 * This javax.media.Control implementation provides only programmatic control and not UI
			 * control.
			 */
			return null;
		}

		public void setOrigin(int streamIndex, int displayIndex, int x, int y)
		{
			DataSource.this.setOrigin(streamIndex, displayIndex, x, y);
		}
	};

	/**
	 * Initializes a new <code>DataSource</code> instance.
	 */
	public DataSource()
	{
	}

	/**
	 * Initializes a new <code>DataSource</code> instance from a specific <code>MediaLocator</code>.
	 *
	 * @param locator
	 *        the <code>MediaLocator</code> to initialize the new instance from
	 */
	public DataSource(MediaLocator locator)
	{
		super(locator);
	}

	/**
	 * Creates a new <code>PullBufferStream</code> which is to be at a specific zero-based index in the
	 * list of streams of this <code>PullBufferDataSource</code>. The <code>Format</code>-related
	 * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param streamIndex
	 *        the zero-based index of the <code>PullBufferStream</code> in the list of streams of this
	 *        <code>PullBufferDataSource</code>
	 * @param formatControl
	 *        the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 *        information of the new instance
	 * @return a new <code>PullBufferStream</code> which is to be at the specified <code>streamIndex</code>
	 *         in the list of streams of this <code>PullBufferDataSource</code> and which has its
	 *         <code>Format</code>-related information abstracted by the specified
	 *         <code>formatControl</code>
	 * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
	 */
	@Override
	protected ImageStream createStream(int streamIndex, FormatControl formatControl)
	{
		/*
		 * full desktop: remainder => index part of desktop: remainder => index,x,y
		 */
		String remainder = getLocator().getRemainder();
		String split[] = remainder.split(",");
		int dispayIndex;
		int x;
		int y;

		if ((split != null) && (split.length > 1)) {
			dispayIndex = Integer.parseInt(split[0]);
			x = Integer.parseInt(split[1]);
			y = Integer.parseInt(split[2]);
		}
		else {
			dispayIndex = Integer.parseInt(remainder);
			x = 0;
			y = 0;
		}

		ImageStream stream = new ImageStream(this, formatControl);

		stream.setDisplayIndex(dispayIndex);
		stream.setOrigin(x, y);

		return stream;
	}

	/**
	 * Gets the control of the specified type available for this instance.
	 *
	 * @param controlType
	 *        the type of the control available for this instance to be retrieved
	 * @return an <code>Object</code> which represents the control of the specified type available for
	 *         this instance if such a control is indeed available; otherwise, <code>null</code>
	 */
	@Override
	public Object getControl(String controlType)
	{
		/*
		 * TODO As a matter of fact, we have to override getControls() and not getControl(String).
		 * However, overriding getControls() is much more complex and the ImgStreamingControl is too
		 * obscure. Besides, the ImgStreamingControl implementation of this DataSource does not
		 * provide UI control so it makes no sense for the caller to try to get it through
		 * getControls().
		 */
		if (ImgStreamingControl.class.getName().equals(controlType))
			return imgStreamingControl;
		else
			return super.getControl(controlType);
	}

	/**
	 * Set the display index and the origin of the <code>ImageStream</code> associated with a specific
	 * index in this <code>DataSource</code>.
	 *
	 * @param streamIndex
	 *        the index in this <code>DataSource</code> of the <code>ImageStream</code> to set the display
	 *        index and the origin of
	 * @param displayIndex
	 *        the display index to set on the specified <code>ImageStream</code>
	 * @param x
	 *        the x coordinate of the origin to set on the specified <code>ImageStream</code>
	 * @param y
	 *        the y coordinate of the origin to set on the specified <code>ImageStream</code>
	 */
	public void setOrigin(int streamIndex, int displayIndex, int x, int y)
	{
		synchronized (getStreamSyncRoot()) {
			Object[] streams = streams();

			if ((streams != null) && (streamIndex < streams.length)) {
				ImageStream stream = (ImageStream) streams[streamIndex];
				if (stream != null) {
					stream.setDisplayIndex(displayIndex);
					stream.setOrigin(x, y);
				}
			}
		}
	}
}
