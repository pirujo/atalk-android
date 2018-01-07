package org.atalk.android.plugin.geolocation;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.akhgupta.easylocation.*;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.*;
import org.atalk.android.gui.chat.ChatActivity;

public class GeoLocation extends EasyLocationActivity
		implements View.OnClickListener, SeekBar.OnSeekBarChangeListener
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(GeoLocation.class);
	private static String SEND_CONT = "Send_Continuous";

	private TextView mLatitudeTextView;
	private TextView mLongitudeTextView;
	private TextView mLocationAddressTextView;
	private SeekBar mSeekDistanceInterval;
	private SeekBar mSeekTimeInterval;

	private boolean mSVP_Started;
	private boolean sendContinuous;
	private int gpsMinDistance = 50;    // meters
	private int gpsDistanceStep = 5;   // meters
	private int sendTimeInterval = 60; // seconds
	private int timeIntervalStep = 10; // seconds
	private Button mSendSingle;
	private Button mSendCont;
	private CheckBox mGpsTrack;

	private Location mLocation = null;
	private SplitStreetViewPanoramaAndMapActivity mSVP = null;
	private LocationRequest locationRequest;
	private EasyLocationRequest easyLocationRequest;

	private boolean demo = false;
	private float delta = 0; // for demo

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			sendContinuous = savedInstanceState.getBoolean(SEND_CONT);
		}
		else {
			sendContinuous = false;
		}

		setContentView(R.layout.geo_location);
		mLatitudeTextView = (TextView) findViewById(R.id.latitude_textview);
		mLongitudeTextView = (TextView) findViewById(R.id.longitude_textview);
		mLocationAddressTextView = (TextView) findViewById(R.id.locationAddress_textview);

		mSendSingle = (Button) findViewById(R.id.requestSingleLocationButton);
		mSendSingle.setOnClickListener(this);
		mGpsTrack = (CheckBox) findViewById(R.id.gps_track);

		mSendCont = (Button) findViewById(R.id.requestLocationUpdatesButton);
		mSendCont.setText(String.format(getString(R.string.send_cont_location_updates),
				gpsMinDistance, sendTimeInterval));
		mSendCont.setOnClickListener(this);
		mSendCont.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				demo = true;
				mSendCont.performClick();;
				return true;
			}
		});

		mSeekDistanceInterval = (SeekBar) findViewById(R.id.seekDistanceInterval);
		mSeekDistanceInterval.setMax(100);
		mSeekDistanceInterval.setProgress(gpsMinDistance / gpsDistanceStep);
		mSeekDistanceInterval.setOnSeekBarChangeListener(this);

		mSeekTimeInterval = (SeekBar) findViewById(R.id.seekTimeInterval);
		mSeekTimeInterval.setMax(100);
		int progress = (sendTimeInterval - timeIntervalStep) / timeIntervalStep;
		if (progress < 0)
			progress = 0;
		mSeekTimeInterval.setProgress(progress);
		mSeekTimeInterval.setOnSeekBarChangeListener(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putBoolean(SEND_CONT, sendContinuous);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		aTalkApp.setCurrentActivity(this);
		mGpsTrack.setChecked(false);
		mSVP_Started = false;
		mSVP = null;
		demo = false;

		if (sendContinuous) {
			// change to reflect actual state so send button acts correctly to resume
			sendContinuous = false;
			performContButtonClick(this, mSendCont);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		sendContinuous = false;
	}

	/**
	 * Must perform the button click on a separate thread to allow any Activity started in
	 * onDestroy() to complete. Then perform button click only in UiThread； otherwise exception:
	 * “Only the original thread that created a view hierarchy can touch its views.”
	 */
	private void performContButtonClick(final GeoLocation geoLoc, final Button sendButton)
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try {
					// Wait for any in progress activity to complete
					Thread.sleep(1000);
					geoLoc.runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							logger.info("Send continuous location updates @ " + sendTimeInterval);
							sendButton.performClick();
						}
					});
				}
				catch (Exception ex) {
					logger.error("Exception: ", ex);
				}
			}
		}).start();
	}

	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
			case R.id.requestSingleLocationButton:
				if (sendContinuous) {
					toggleSendButton(sendContinuous);
					stopLocationUpdates();
				}
				locationRequest = new LocationRequest()
						.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
						.setInterval(5000)
						.setFastestInterval(5000);
				easyLocationRequest = new EasyLocationRequestBuilder()
						.setLocationRequest(locationRequest)
						.setAddressRequest(true)
						.setFallBackToLastLocationTime(3000)
						.build();
				requestSingleLocationFix(easyLocationRequest);
				break;

			case R.id.requestLocationUpdatesButton:
				if (!sendContinuous) {
					toggleSendButton(sendContinuous);
					locationRequest = new LocationRequest()
							.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
							.setInterval(sendTimeInterval * 1000)
							.setFastestInterval(sendTimeInterval * 1000);
					easyLocationRequest = new EasyLocationRequestBuilder()
							.setLocationRequest(locationRequest)
							.setAddressRequest(true)
							.setFallBackToLastLocationTime(sendTimeInterval * 500)
							.build();
					requestLocationUpdates(easyLocationRequest);
				}
				else {
					toggleSendButton(sendContinuous);
					stopLocationUpdates();
				}
		}
	}

	private void toggleSendButton(boolean sentCont)
	{
		mSendSingle.setEnabled(sendContinuous);
		if (sendContinuous) {
			mSendSingle.setAlpha(1.0f);
			sendContinuous = false;
			mSendCont.setText(String.format(getString(R.string.send_cont_location_updates),
					gpsMinDistance, sendTimeInterval));
			mSendCont.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		}
		else {
			mSendSingle.setAlpha(0.3f);
			sendContinuous = true;
			mSendCont.setText(R.string.stop_location_updates);
		}
	}

	@Override
	public void onLocationPermissionGranted()
	{
		showToast("Location permission granted");
	}

	@Override
	public void onLocationPermissionDenied()
	{
		showToast("Location permission denied");
	}

	@Override
	public void onLocationReceived(Location location, String locAddress)
	{
		if (demo) {
			delta += 0.0001;
			location.setLatitude(location.getLatitude() + delta);
			location.setLongitude(location.getLongitude() - delta);
		}

		boolean needUpdate = false;
		String mLatitude = String.valueOf(location.getLatitude());
		String mLongitude = String.valueOf(location.getLongitude());

		mLatitudeTextView.setText(mLatitude);
		mLongitudeTextView.setText(mLongitude);
		mLocationAddressTextView.setText(locAddress);

		if (!sendContinuous || (mLocation == null)) {
			mLocation = location;
			needUpdate = true;
		}
		else {
			float distance = location.distanceTo(mLocation);
			if (distance >= (float) gpsMinDistance) {
				mLocation = location;
				needUpdate = true;
			}
		}

		if (needUpdate) {
			String msg = locAddress + " \nLatLng: " + mLatitude + "," + mLongitude;
			ChatActivity.sendLocation(msg);

			if (!sendContinuous) {
				showGoogleMap(location);
			}
			else if (mGpsTrack.isChecked()) {
				if (!mSVP_Started)
					showGoogleMap(location);
				else {
					if (mSVP == null) {
						Activity currentActivity = aTalkApp.getCurrentActivity();
						if (currentActivity != null) {
							if (currentActivity instanceof SplitStreetViewPanoramaAndMapActivity) {
								mSVP = (SplitStreetViewPanoramaAndMapActivity) currentActivity;
							}
						}
					}
					if (mSVP != null) {
						mSVP.onLocationChanged(location);
					}
				}
			}
		}
	}

    @Override
    public void noLocationReceived() {
        showToast("No location received");
    }

	private void showGoogleMap(Location location)
	{
		// You can now create a LatLng Object for use with maps
		mSVP_Started = true;
		LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
		Intent intent = new Intent(this, SplitStreetViewPanoramaAndMapActivity.class);
		intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, latLng);
		startActivity(intent);
	}

	@Override
	public void onLocationProviderEnabled()
	{
		showToast("Location services are now ON");
	}

	@Override
	public void onLocationProviderDisabled()
	{
		showToast("Location services are still Off");
	}

	/**
	 * Notification that the progress level has changed. Clients can use the fromUser parameter
	 * to distinguish user-initiated changes from those that occurred programmatically.
	 *
	 * @param seekBar
	 * 		The SeekBar whose progress has changed
	 * @param progress
	 * 		The current progress level. This will be in the range 0..max where max
	 * 		was set by {@link ProgressBar#setMax(int)}. (The default value for max is 100.)
	 * @param fromUser
	 * 		True if the progress change was initiated by the user.
	 */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (seekBar == mSeekDistanceInterval)
			gpsMinDistance = progress * gpsDistanceStep;
		else {
			if (progress == 0)
				sendTimeInterval = 5;
			else
				sendTimeInterval = (progress) * timeIntervalStep;
		}

		mSendCont.setText(String.format(getString(R.string.send_cont_location_updates),
				gpsMinDistance, sendTimeInterval));
		mSendCont.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		if (sendContinuous) {
			mSendCont.setText(R.string.stop_location_updates);
		}
		showToast(getString(R.string.apply_new_interval_setting));
	}

	private void showToast(String message)
	{
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
}