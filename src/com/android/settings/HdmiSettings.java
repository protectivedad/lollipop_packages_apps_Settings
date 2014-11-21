package com.android.settings;

import android.util.Log;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import com.android.settings.widget.SwitchBar;

import android.os.SystemProperties;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;
//import static android.provider.Settings.System.HDMI_LCD_TIMEOUT;
import android.content.ContentResolver;
import android.os.Handler;
import android.database.ContentObserver;

public class HdmiSettings extends SettingsPreferenceFragment
		implements OnPreferenceChangeListener,SwitchBar.OnSwitchChangeListener {
	/** Called when the activity is first created. */
	private static final String TAG = "HdmiControllerActivity";
	private static final String KEY_HDMI_RESOLUTION = "hdmi_resolution";
	private static final String KEY_HDMI_LCD = "hdmi_lcd_timeout";
	private static final String KEY_HDMI_SCALE="hdmi_screen_zoom";
	// for identify the HdmiFile state
	private boolean IsHdmiConnect = false;
	// for identify the Hdmi connection state
	private boolean IsHdmiPlug = false;
	private boolean IsHdmiDisplayOn = false;

	private ListPreference mHdmiResolution;
	private ListPreference mHdmiLcd;
	private Preference mHdmiScale;

	private File HdmiFile = null;
	private File HdmiState = null;
	private File HdmiDisplayEnable = null;
	private File HdmiDisplayMode = null;
	private File HdmiDisplayConnect = null;
	private File HdmiDisplayModes=null;
	private Context context;
	private static final int DEF_HDMI_LCD_TIMEOUT_VALUE = 10;

	private SharedPreferences sharedPreferences;
	private SharedPreferences.Editor editor;
	private SwitchBar mSwitchBar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		context = getActivity();
		sharedPreferences = getActivity().getSharedPreferences("HdmiSettings",
				Context.MODE_PRIVATE);
		String enable = sharedPreferences.getString("enable", "1");
		editor = sharedPreferences.edit();
		if (!isDualMode()) {
			addPreferencesFromResource(R.xml.hdmi_settings);
		} else {
			addPreferencesFromResource(R.xml.hdmi_settings_timeout);
			mHdmiLcd = (ListPreference) findPreference(KEY_HDMI_LCD);
			mHdmiLcd.setOnPreferenceChangeListener(this);
			ContentResolver resolver = context.getContentResolver();
			long lcdTimeout = -1;
			if ((lcdTimeout = Settings.System.getLong(resolver,
					Settings.System.HDMI_LCD_TIMEOUT,
					DEF_HDMI_LCD_TIMEOUT_VALUE)) > 0) {
				lcdTimeout /= 10;
			}
			mHdmiLcd.setValue(String.valueOf(lcdTimeout));
			mHdmiLcd.setEnabled(enable.equals("1"));
		}
		
		
		HdmiDisplayEnable = new File("/sys/class/display/HDMI/enable");
		HdmiDisplayMode = new File("/sys/class/display/HDMI/mode");
		HdmiDisplayConnect = new File("sys/class/display/HDMI/connect");
		HdmiDisplayModes=new File("sys/class/display/HDMI/modes");

		mHdmiResolution = (ListPreference) findPreference(KEY_HDMI_RESOLUTION);
		mHdmiResolution.setOnPreferenceChangeListener(this);
		//mHdmiResolution.setEntries(getModes());
		//mHdmiResolution.setEntryValues(getModes());
		String resolutionValue=sharedPreferences.getString("resolution", null);
		Log.d(TAG,"resolutionValue=  "+resolutionValue);
		//mHdmiResolution.setValue(resolutionValue+"\n");
		mHdmiResolution.setEnabled(enable.equals("1"));

		mHdmiScale=findPreference(KEY_HDMI_SCALE);
		mHdmiScale.setEnabled(enable.equals("1"));
		Log.d(TAG,"onCreate---------------------");
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
		Log.d(TAG,"onActivityCreated----------------------------------------");
		final SettingsActivity activity = (SettingsActivity) getActivity();
	    mSwitchBar = activity.getSwitchBar();
	    mSwitchBar.show();
	    mSwitchBar.addOnSwitchChangeListener(this);
	    mSwitchBar.setChecked(sharedPreferences.getString("enable", "1").equals("1"));
	    
	    //mHdmiResolution.setValue(mHdmiResolution.getEntryValues()[0].toString());
	    String resolutionValue=sharedPreferences.getString("resolution", null);
	    if(resolutionValue==null){
	    	return;
	    }
	    mHdmiResolution.setValue(resolutionValue);
	    /*
	    for(int i=0;i<resolutionValue.length();i++){
			Log.d(TAG,"str="+resolutionValue.charAt(i));
		}
		Log.d(TAG,"resolutionValue=  "+resolutionValue);
		mHdmiResolution.setValue(resolutionValue);
		for(int i=0;i<mHdmiResolution.getEntryValues()[0].length();i++){
			Log.d(TAG,"str="+mHdmiResolution.getEntryValues()[0].charAt(i));
		}*/
	}
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		Log.d(TAG,"onCreateView----------------------------------------");
		String resolutionValue=sharedPreferences.getString("resolution", null);
		//Log.d(TAG,"resolutionValue=  "+resolutionValue.toCharArray());
		//mHdmiResolution.setValue(resolutionValue);
		return super.onCreateView(inflater, container, savedInstanceState);
	}
	
	private ContentObserver mHdmiTimeoutSettingObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfChange) {

			ContentResolver resolver = getActivity().getContentResolver();
			final long currentTimeout = Settings.System.getLong(resolver,
					Settings.System.HDMI_LCD_TIMEOUT, -1);
			long lcdTimeout = -1;
			if ((lcdTimeout = Settings.System.getLong(resolver,
					Settings.System.HDMI_LCD_TIMEOUT,
					DEF_HDMI_LCD_TIMEOUT_VALUE)) > 0) {
				lcdTimeout /= 10;
			}
			mHdmiLcd.setValue(String.valueOf(lcdTimeout));
		}
	};

	@Override
	public void onResume() {
		// TODO Auto-generated method stub

		super.onResume();
		getContentResolver().registerContentObserver(
				Settings.System.getUriFor(Settings.System.HDMI_LCD_TIMEOUT),
				true, mHdmiTimeoutSettingObserver);
	}

	public void onPause() {
		super.onPause();
		// getContentResolver().unregisterContentObserver(mHdmiTimeoutSettingObserver);
	}

	public void onDestroy() {
		super.onDestroy();
		getContentResolver().unregisterContentObserver(
				mHdmiTimeoutSettingObserver);
	}

	private boolean isDualMode() {
		boolean isDualMode = false;
		File file = new File("/sys/class/graphics/fb0/dual_mode");
		if (file.exists()) {
			try {
				FileReader fread = new FileReader(file);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;
				while ((str = buffer.readLine()) != null) {
					if (!str.equals("0")) {
						isDualMode = true;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return isDualMode;
	}

	protected void setHdmiConfig(File file, boolean enable) {

		if (file.exists()) {
			try {
				Log.d(TAG, "setHdmiConfig");
				String strChecked = "1";
				String strUnChecked = "0";
				RandomAccessFile rdf = null;
				rdf = new RandomAccessFile(file, "rw");
				if (enable) {
					rdf.writeBytes(strChecked);
					editor.putString("enable", "1");
					mHdmiLcd.setEnabled(true);
					mHdmiResolution.setEnabled(true);
					mHdmiScale.setEnabled(true);
				} else {
					rdf.writeBytes(strUnChecked);
					editor.putString("enable", "0");
					mHdmiLcd.setEnabled(false);
					mHdmiResolution.setEnabled(false);
					mHdmiScale.setEnabled(false);
				}
				rdf.close();
				editor.commit();
			} catch (IOException re) {
				Log.e(TAG, "IO Exception");
				re.printStackTrace();
			}
		} else {
			Log.i(TAG, "The File " + file + " is not exists");
		}
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {
		// TODO Auto-generated method stub
		return true;
	}

	private void setHdmiLcdTimeout(int value) {
		if (value != -1) {
			value = (value) * 10;
		}
		try {
			Settings.System.putInt(getContentResolver(),
					Settings.System.HDMI_LCD_TIMEOUT, value);
		} catch (NumberFormatException e) {
			Log.e(TAG, "could not persist hdmi lcd timeout setting", e);
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object objValue) {
		// TODO Auto-generated method stub
		String key = preference.getKey();
		if (KEY_HDMI_RESOLUTION.equals(key)) {
			try {
				String strResolution = "hdmi_resolution";
				//int value = Integer.parseInt((String) objValue);
				editor.putString("resolution", (String)objValue);
				Log.d(TAG,"onPreferenceChange="+objValue);
				setHdmiOutputStyle(HdmiDisplayMode, (String)objValue);
			} catch (NumberFormatException e) {
				Log.e(TAG, "onPreferenceChanged hdmi_resolution setting error");
			}
		}

		if (KEY_HDMI_LCD.equals(key)) {
			try {
				String strMode = "hdmi_display";
				int value = Integer.parseInt((String) objValue);
				// editor.putInt("enable", value);
				setHdmiLcdTimeout(value);
			} catch (NumberFormatException e) {
				Log.e(TAG, "onPreferenceChanged hdmi_mode setting error");
			}
		}
		editor.commit();
		return true;
	}

	public static boolean isHdmiConnected(File file) {
		boolean isConnected = false;
		if (file.exists()) {
			try {
				FileReader fread = new FileReader(file);
				BufferedReader buffer = new BufferedReader(fread);
				String str = null;

				while ((str = buffer.readLine()) != null) {
					int length = str.length();
					if (str.equals("1")) {
						isConnected = true;
						break;
					}
				}
			} catch (IOException e) {
				Log.e(TAG, "IO Exception");
			}
		}
		return isConnected;
	}

	protected void setHdmiOutputStyle(File file, String style) {

		if (file.exists()) {
			try {
				

				// write into file
				File f = new File("/sys/class/display/HDMI/mode");
				OutputStream output = null;
				OutputStreamWriter outputWrite = null;
				PrintWriter print = null;
				StringBuffer    strbuf = new StringBuffer(style);
				
				output = new FileOutputStream(f);
				outputWrite = new OutputStreamWriter(output);
				print = new PrintWriter(outputWrite);
				Log.d(TAG, "strbuf=" + style);
				print.print(style);
			   //print.print("1280x720p-60"+"\n");
				print.flush();
				output.close();
			} catch (IOException e) {
				e.printStackTrace();

				Log.e(TAG, "IO Exception");
			}
		} else {
			Log.i(TAG, "The File " + file + " is not exists");
		}
	}

	@Override
	public void onSwitchChanged(Switch switchView, boolean isChecked) {
		// TODO Auto-generated method stub
		setHdmiConfig(HdmiDisplayEnable, isChecked);
	}

	private String[] getModes() {
		ArrayList<String> list = new ArrayList<String>();
		try {
			FileReader fread = new FileReader(HdmiDisplayModes);
			BufferedReader buffer = new BufferedReader(fread);
			String str = null;

			while ((str = buffer.readLine()) != null) {
				list.add(str + "\n");
			}
			fread.close();
			buffer.close();
		} catch (IOException e) {
			Log.e(TAG, "IO Exception");
		}
		return list.toArray(new String[list.size()]);
	}

}
