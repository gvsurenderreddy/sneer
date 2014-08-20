package sneer.android.main.ui.utils;

import static sneer.android.main.SneerApp.*;
import android.app.*;
import android.content.*;

public class Puk {

	public static void sendYourPublicKey(Activity activity, String puk) {
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "My Sneer public key");
		// sneer().self().publicKey().current().toString()
		sharingIntent.putExtra(Intent.EXTRA_TEXT, buildSneerUri(puk));
		activity.startActivity(sharingIntent);
	}

	
	public static String buildSneerUri(String puk) {
		return "http://sneer.me/public-key?" + puk;
	}

}
