package com.theonelab.navi.gypsum;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.telephony.TelephonyManager;

public class AndroidHelper {
  private static final String TAG = "AndroidHelper";

  public static boolean isRunningOnEmulator(Context context) {
    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    String networkOperator = tm.getNetworkOperatorName();

    Log.v(TAG, "Network operator is " + networkOperator);

    if (networkOperator.equals("Android")) {
      Log.i(TAG, "Running on emulator.");
      return true;
    }

    Log.i(TAG, "Running on real device.");
    return false;
  }
}
