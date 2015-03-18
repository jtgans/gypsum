package com.theonelab.navi.gypsum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * {@link BroadcastReceiver} to handle boot-time events.
 *
 * Effectively all this does is start {@link DisplayActivity} at boot time.
 */
public class OnBootReceiver extends BroadcastReceiver {
  private static final String TAG = "OnBootReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "onReceive() called -- display activity.");
    Intent startDisplayActivity = new Intent(context, DisplayActivity.class);
    startDisplayActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(startDisplayActivity);
  }
}
