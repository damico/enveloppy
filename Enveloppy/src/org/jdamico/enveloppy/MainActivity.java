package org.jdamico.enveloppy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private Button btn = null;
	private TextView tv = null;

	private Camera mCamera;

	// View to display the camera output.
	private CamPreview mPreview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		boolean opened = safeCameraOpenInView();

        if(opened == false){
            Log.d("CameraGuide","Error, Camera failed to open");
            
        }

        btn = (Button) findViewById(R.id.button_capture);
        tv = (TextView) findViewById(R.id.textView1);
        
        btn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    	mCamera.takePicture(null, null, mPicture);
                    	File mediaStorageDir = Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES);
                    	long freeSpace = mediaStorageDir.getFreeSpace();
                    	System.out.println(freeSpace);
                    	tv.setText(String.valueOf(freeSpace));
                    }
                }
        );
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

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
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

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");

        

        return mediaFile;
    }

    
    @Override
	public void onBackPressed() {

		releaseCameraAndPreview();
		android.os.Process.killProcess(android.os.Process.myPid());
	}
}


