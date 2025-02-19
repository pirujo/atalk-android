/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a QuickTime/QTKit <code>QTSampleBuffer</code> object.
 *
 * @author Lyubomir Marinov
 */
public class QTSampleBuffer extends NSObject
{

	/**
	 * Initializes a new <code>QTSampleBuffer</code> which is to represent a specific QuickTime/QTKit
	 * <code>QTSampleBuffer</code> object.
	 *
	 * @param ptr
	 * 		the pointer to the QuickTime/QTKit <code>QTSampleBuffer</code> object to be represented by
	 * 		the new instance
	 */
	public QTSampleBuffer(long ptr)
	{
		super(ptr);
	}

	public byte[] bytesForAllSamples()
	{
		return bytesForAllSamples(getPtr());
	}

	private static native byte[] bytesForAllSamples(long ptr);

	public QTFormatDescription formatDescription()
	{
		long formatDescriptionPtr = formatDescription(getPtr());

		return (formatDescriptionPtr == 0) ? null : new QTFormatDescription(formatDescriptionPtr);
	}

	private static native long formatDescription(long ptr);
}
