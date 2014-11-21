

package com.android.settings;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.os.RemoteException;
import android.os.IPowerManager;
import android.os.ServiceManager;
import android.preference.SeekBarPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;

import java.util.Map;
import java.io.*;

import android.os.SystemProperties;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.SystemProperties;
import android.content.ContentResolver;

import java.io.RandomAccessFile;

import static android.provider.Settings.System.HDMI_LCD_TIMEOUT;
import android.widget.Toast;

import com.android.settings.R;

public class HdmiReceiver extends BroadcastReceiver {
	private final String ACTION = "android.intent.action.HDMI_PLUG";
	private static final String TAG = "HdmiReceiver";
	private File HdmiDisplayEnable = new File("/sys/class/display/HDMI/enable");
	private File HdmiDisplayMode = new File("/sys/class/display/HDMI/mode");
	private File HdmiDisplayScale = new File("/sys/class/display/HDMI/scale");
	private Context mcontext;
	private SharedPreferences preferences;

	@Override
	public void onReceive(Context context, Intent intent) {
		mcontext = context;
		String enable = null;
		String scale = null;
		String resol = null;
		preferences = context.getSharedPreferences("HdmiSettings",
				Context.MODE_PRIVATE);
		if (intent.getAction().equals(ACTION)) {
			int state = intent.getIntExtra("state", 1);
			if (state == 1) {
				enable = preferences.getString("enable", "1");
				resol = preferences.getString("resolution", null);
				if(resol==null){
					resol=getCurrentMode();
				}
				scale = preferences.getString("scale_set", "100");
				restoreHdmiValue(HdmiDisplayEnable, enable, "enable");
				restoreHdmiValue(HdmiDisplayMode, resol, "hdmi_resolution");
				restoreHdmiValue(HdmiDisplayScale, scale, "hdmi_scale");
			}
			if (getFBDualDisplayMode() == 1) {
				if (state == 1) {
					SystemProperties.set("sys.hdmi_screen.scale",
							scale);
				} else {
					SystemProperties.set("sys.hdmi_screen.scale",
							scale);
				}
			}
			String text = context.getResources().getString(
					(state == 1) ? R.string.hdmi_connect
							: R.string.hdmi_disconnect);
			Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
			//if (getFBDualDisplayMode() != 0) {
			//	TurnonScreen();
			//}
			Log.d(TAG,
					"enable =" + String.valueOf(enable) + " scale="
							+ String.valueOf(scale) + " resol="
							+ String.valueOf(resol));
		}

	}

	private void TurnonScreen() {
		// boolean ff = SystemProperties.getBoolean("persist.sys.hdmi_screen",
		// false);
		ContentResolver resolver = mcontext.getContentResolver();
		try {
			int brightness = Settings.System.getInt(resolver,
					Settings.System.SCREEN_BRIGHTNESS, 102);
			IPowerManager power = IPowerManager.Stub.asInterface(ServiceManager
					.getService("power"));
			if (power != null) {
				power.setTemporaryScreenBrightnessSettingOverride(brightness);
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception" + e);
		}
	}

	private int getFBDualDisplayMode() {
		int mode = 0;
		File DualModeFile = new File("/sys/class/graphics/fb0/dual_mode");
		if (DualModeFile.exists()) {
			try {
				byte[] buf = new byte[10];
				int len = 0;
				RandomAccessFile rdf = new RandomAccessFile(DualModeFile, "r");
				len = rdf.read(buf);
				String modeStr = new String(buf, 0, 1);
				mode = Integer.valueOf(modeStr);
				rdf.close();
			} catch (IOException re) {
				Log.e(TAG, "IO Exception");
			} catch (NumberFormatException re) {
				Log.e(TAG, "NumberFormatException");
			}
		}
		return mode;
	}


	protected void restoreHdmiValue(File file, String value, String style) {
		if (file.exists()) {
			try {
				FileReader fread = new FileReader(file);
				BufferedReader buffer = new BufferedReader(fread);
				String substr = null;
				String str = null;
				int length = 0;
					if (style.equals("enable")) {
						Log.d(TAG, "restoreHdmiValue enable");
						RandomAccessFile rdf = null;
						rdf = new RandomAccessFile(file, "rw");
						rdf.writeBytes(value);
						rdf.close();
					}
					if (style.equals("hdmi_scale")) {
						OutputStream output = null;
						OutputStreamWriter outputWrite = null;
						PrintWriter print = null;
						try {
							output = new FileOutputStream(file);
							outputWrite = new OutputStreamWriter(output);
							print = new PrintWriter(outputWrite);
							print.print(value);
							print.flush();
							output.close();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
					}
				}
				if (style.equals("hdmi_resolution")) {
					Log.d(TAG, "restoreHdmiValue hdmi_resolution");
					OutputStream output = null;
					OutputStreamWriter outputWrite = null;
					PrintWriter print = null;

					output = new FileOutputStream(file);
					outputWrite = new OutputStreamWriter(output);
					print = new PrintWriter(outputWrite);
					print.print(value);
					print.flush();
					output.close();
				}

				buffer.close();
				fread.close();

			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		} else {
			Log.e(TAG, "File:" + file + "not exists");
		}
	}

	private String getCurrentMode(){
		String str=null;
		try {
			FileReader fread = new FileReader(HdmiDisplayMode);
			BufferedReader buffer = new BufferedReader(fread);

			while ((str = buffer.readLine()) != null) {
				str=str+"\n";
			}
		} catch (IOException e) {
			Log.e(TAG, "IO Exception");
		}
		return str;
	}
}
