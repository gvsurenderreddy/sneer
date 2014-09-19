package diegomendes.sendpics;

import java.io.*;

import sneer.android.ui.*;
import sneer.commons.exceptions.FriendlyException;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.*;
import android.provider.MediaStore.Images;
import android.view.*;
import android.widget.*;

public class ReceivePicsActivity extends SneerActivity {

	ImageView image;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_receive_pics);

		image = (ImageView) findViewById(R.id.picture_received);
		//image.setImageBitmap((Bitmap) getIntent().getExtras().get("image"));
		
		String filename = "temp.jpg";
		
		byte[] ret = null;
		try {
			ret = readFully(getClass().getResourceAsStream("selfie_002.png"));
			image.setImageBitmap((Bitmap) toBitmap(ret));
		} catch (FriendlyException e) {}
		
		
		try {
			System.out.println("=====================================");
			System.out.println("Data Directory: " + Environment.getDataDirectory());
			System.out.println("Files Dir(Absolute): " + getApplicationContext().getFilesDir().getAbsolutePath());
			System.out.println("Files Dir(Canonical): " + getApplicationContext().getFilesDir().getCanonicalPath());
			System.out.println("=====================================");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		FileOutputStream outputStream;

		try {
		  outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
//		  outputStream.write(getIntent().getExtras().getByteArray("image"));
		  outputStream.write(ret);
		  outputStream.close();
		  addImageToGallery(getApplicationContext().getFilesDir().getAbsolutePath(), getApplicationContext(), toBitmap(ret));
		} catch (Exception e) {
		  e.printStackTrace();
		}

		testFile();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.receive_pics, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void testFile(){
		String filename = getApplicationContext().getFilesDir() + "/myfile.txt";
		System.out.println("===============");
		System.out.println("filename: " + filename);
		System.out.println("===============");
		
		String string = "Hello world!";
		FileOutputStream outputStream;

		try {
//		  outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
		  outputStream = new FileOutputStream(filename);	  
		  outputStream.write(string.getBytes());
		  outputStream.close();
		  //addImageToGallery(filename, getApplicationContext());
		} catch (Exception e) {
		  e.printStackTrace();
		}
	}
	
	public static void addImageToGallery(final String filePath, final Context context, Bitmap yourBitmap) {

	    ContentValues values = new ContentValues();
	    
	    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
	    values.put(Images.Media.MIME_TYPE, "image/jpeg");
	    values.put(MediaStore.MediaColumns.DATA, filePath);

	    
	    context.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
	    
	    MediaStore.Images.Media.insertImage(context.getContentResolver(), yourBitmap, "Sendpics" , "");
	}
}