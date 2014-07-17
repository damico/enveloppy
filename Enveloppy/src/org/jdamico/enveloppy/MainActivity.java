package org.jdamico.enveloppy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static long taskTime = 15000; 

	// Debugging
	private static final String TAG = "BluetoothChat";
	private static final boolean D = true;

	// Message types sent from the BluetoothChatService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Layout Views

	//private ListView mConversationView;
	private EditText mOutEditText;
	private Button mSendButton;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Array adapter for the conversation thread
	//private ArrayAdapter<String> mConversationArrayAdapter;
	// String buffer for outgoing messages
	private StringBuffer mOutStringBuffer;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BluetoothChatService mChatService = null;

	private TextView tv = null;

	private Camera mCamera;

	// View to display the camera output.
	private CamPreview mPreview;
	private int sessionShots = 1;

	//Variable to store brightness value
	private boolean wait = false;

	private Handler handler =new Handler();
	final Runnable r = new Runnable()
	{
		public void run() 
		{
			if(!wait) shot();

			handler.postDelayed(this, taskTime);
		}
	};



	public void shot(){

		if(sessionShots == 1) {

			float brightness = 0 / (float)255;
			WindowManager.LayoutParams lp = getWindow().getAttributes();
			lp.screenBrightness = brightness;
			getWindow().setAttributes(lp);
			
			RelativeLayout rl = (RelativeLayout)findViewById(R.id.rell);
			rl.setBackgroundColor(Color.BLACK);
			tv.setBackgroundColor(Color.WHITE);
			mOutEditText.setBackgroundColor(Color.WHITE);
		}

		try{
			mCamera.takePicture(null, null, mPicture);
			File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
			long freeSpace = mediaStorageDir.getFreeSpace();
			tv.setText(String.valueOf(readableFileSize(freeSpace))+ " ["+sessionShots+"]");
			sessionShots++;
		}catch(Exception e){
			e.printStackTrace();
		}

	}

	public String readableFileSize(long size) {
		if(size <= 0) return "0";
		final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
		return new DecimalFormat("#,###0.###").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	public void kill(){
		if (mChatService != null) mChatService.stop();
		releaseCameraAndPreview();
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		safeCameraOpenInView();

		tv = (TextView) findViewById(R.id.textView1);



		if(D) Log.e(TAG, "+++ ON CREATE +++");



		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		//		//handle brightness
		//		cResolver = getContentResolver();
		//
		//		//Get the current window
		//		window = getWindow();
		//
		//		try
		//		{
		//			// To handle the auto
		//			Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
		//			//Get the current system brightness
		//			brightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
		//		}catch (SettingNotFoundException e) {
		//			Log.e("Error", "Cannot access system brightness");
		//			e.printStackTrace();
		//		}

	}

	@Override
	public void onStart() {
		super.onStart();
		if(D) Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null) setupChat();
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if(D) Log.e(TAG, "+ ON RESUME + "+sessionShots+"  "+taskTime);

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't started already
			if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
				// Start the Bluetooth chat services
				mChatService.start();
			}
		}
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		//if(D) Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		//if(D) Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (mChatService != null) mChatService.stop();
		//if(D) Log.e(TAG, "--- ON DESTROY ---");
	}

	private void ensureDiscoverable() {
		if(D) Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() !=
				BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}


	// The action listener for the EditText widget, to listen for the return key
	private TextView.OnEditorActionListener mWriteListener =
			new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
			// If the action is a key-up event on the return key, send the message
			if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			if(D) Log.i(TAG, "END onEditorAction");
			return true;
		}
	};

	/**
	 * Sends a message.
	 * @param message  A string of text to send.
	 */
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
			return;
		}

		// Check that there's actually something to send
		if (message.length() > 0) {
			// Get the message bytes and tell the BluetoothChatService to write
			byte[] send = message.getBytes();
			mChatService.write(send);

			// Reset out string buffer to zero and clear the edit text field
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					tv.setText(R.string.title_connected_to);
					tv.append(mConnectedDeviceName);
					break;
				case BluetoothChatService.STATE_CONNECTING:
					tv.setText(R.string.title_connecting);
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					tv.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				String writeMessage = new String(writeBuf);
				tv.setText("Me:  " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				tv.setText(mConnectedDeviceName+":  " + readMessage);

				switch (readMessage.trim().toLowerCase()) {
				case "a":
					wait = false;
					taskTime = 10000;
					handler.postDelayed(r, taskTime);
					break;
				case "b":
					wait = true;
					break;

				case "q":
					kill();
					break;

				default:
					break;
				}

				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(), "Connected to "
						+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
						Toast.LENGTH_SHORT).show();
				break;
			}
		}
	};


	private void setupChat() {
		Log.d(TAG, "setupChat()");

		// Initialize the compose field with a listener for the return key
		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
		mOutEditText.setOnEditorActionListener(mWriteListener);

		// Initialize the send button with a listener that for click events
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Send a message using content of the edit text widget
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});

		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = new BluetoothChatService(this, mHandler);

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
	}


	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D) Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras()
						.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				mChatService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				setupChat();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	private boolean safeCameraOpenInView() {
		boolean qOpened = false;
		releaseCameraAndPreview();
		mCamera = getCameraInstance();
		//mCameraView = view;
		qOpened = (mCamera != null);

		if(qOpened == true){
			mPreview = new CamPreview(this.getBaseContext(), mCamera);
			FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
			preview.addView(mPreview);
			mPreview.startCameraPreview();
		}
		return qOpened;
	}

	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
			
			
			
			Parameters camParams = c.getParameters();
			List<Size> sizes = camParams.getSupportedPictureSizes();
			
			int maxW = 0;
			int maxH = 0;
			
			for (int i = 0; i < sizes.size(); i++) {
				if(sizes.get(i).width > maxW){
					maxW = sizes.get(i).width;
					maxH = sizes.get(i).height;
				}
			}
			
			camParams.setPictureSize(maxW, maxH);
			
			Log.d(TAG, "Pict Size: "+maxW+" x "+maxH);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return c; // returns null if camera is unavailable
	}

	private void releaseCameraAndPreview() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
		if(mPreview != null){
			mPreview.destroyDrawingCache();
			mPreview.mCamera = null;
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {


		switch (item.getItemId()) {
		case R.id.scan:
			// Launch the DeviceListActivity to see devices and do scan
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		case R.id.discoverable:
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			return true;
		case R.id.killable:
			// Ensure this device is discoverable by others
			kill();
			return true;

		}
		return false;


	}

	private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			File pictureFile = getOutputMediaFile();
			if (pictureFile == null){
				Toast.makeText(getApplicationContext(), "Image retrieval failed.", Toast.LENGTH_SHORT)
				.show();
				return;
			}

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();

				// Restart the camera preview.
				safeCameraOpenInView();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}


	};

	private File getOutputMediaFile(){

		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES), "Enveloppy");

		if (! mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
				Log.d("Camera Guide", "Required media storage does not exist");
				return null;
			}
		}

		// Create  a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator +
				"IMG_"+ timeStamp + ".jpg");

		return mediaFile;
	}


	@Override
	public void onBackPressed() {

		//releaseCameraAndPreview();
		//android.os.Process.killProcess(android.os.Process.myPid());
	}

}


