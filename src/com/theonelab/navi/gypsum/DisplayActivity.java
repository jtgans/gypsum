package com.theonelab.navi.gypsum;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.transition.TransitionManager;
import android.transition.Fade;

import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.Map;
import java.io.IOException;
import java.net.Socket;

public class DisplayActivity extends Activity
    implements RfcommServer.Listener, CommandParser.Listener {
  private static final String TAG = "DisplayActivity";

  /**
   * Used to store and retrieve a {@link ParcelFileDescriptor} instance that
   * links the {@link BluetoothSocket} to our instance.
   */
  public static final String PFD_EXTRA =
      "com.theonelab.navi.gypsum.DisplayActivity.PFD_EXTRA";

  /**
   * Key for the {@link DisplayView} bitmap to be saved before the Activity
   * dies.
   */
  public static final String DISPLAY_PARCELABLE_KEY =
      "com.theonelab.navi.gypsum.DisplayView.FRAMEBUFFER";

  /**
   * Key for the {@link CommandParser}'s serialized parameters table which is
   * saved before the Activity dies.
   */
  public static final String PARAMS_TABLE_PARCELABLE_KEY =
      "com.theonelab.navi.gypsum.CommandParser.PARAMETERS";

  /**
   * Receiver to keep track of when the default {@link BluetoothAdapter} is
   * actually available, based upon the bluetooth state.
   */
  private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
          final int state = intent.getIntExtra(
              BluetoothAdapter.EXTRA_STATE,
              BluetoothAdapter.ERROR);

          switch (state) {
            case BluetoothAdapter.STATE_TURNING_OFF:
              Log.v(TAG, "Bluetooth turning off");
              stopRfcommServer();
              break;

            case BluetoothAdapter.STATE_ON:
              Log.v(TAG, "Bluetooth on");
              startRfcommServer();
              break;
          }
        }
      }
    };

  private static final long CONNECTED_FADE_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2L);

  private RfcommServer server;
  private Thread serverThread;

  private CommandParser parser;
  private Thread parserThread;

  private DisplayView display;
  private TextView connectedText;
  private TextView disconnectedText;
  private ViewGroup layout;

  private BluetoothSocket btSocket;
  private Socket tcpSocket;

  private Handler uiHandler;

  public DisplayActivity() {
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate");
    super.onCreate(savedInstanceState);

    uiHandler = new Handler(getMainLooper());

    // Register for broadcasts on BluetoothAdapter state change
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(btReceiver, filter);

    // Ensure we get the full screen to use
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

    // Setup our display
    setContentView(R.layout.main);
  }

  @Override
  public void onResume() {
    Log.i(TAG, "onResume");
    super.onResume();

    display = (DisplayView) findViewById(R.id.display);

    if (display == null) {
      Log.wtf(TAG, "WHY IS DISPLAY NULL?!?!?!?!?!?!?!?!?!?!");
    }

    connectedText = (TextView) findViewById(R.id.connected);
    disconnectedText = (TextView) findViewById(R.id.disconnected);
    layout = (ViewGroup) findViewById(R.id.layout);
    updateStatusText();

    // Start listening for connections
    startRfcommServer();
  }

  @Override
  public void onPause() {
    Log.i(TAG, "onPause");
    super.onPause();
    
    // User has navigated away -- we need to shutdown.
    stopRfcommServer();
    stopParser();
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy");
    super.onDestroy();

    if (btSocket != null) {
      if (btSocket.isConnected()) {
        try {
          btSocket.close();
        } catch (IOException e) {
          Log.e(TAG, "Caught IOException on btsocket close: " + e.getMessage());
        }
      }

      btSocket = null;
    }

    unregisterReceiver(btReceiver);
  }

  private void startRfcommServer() {
    server = new RfcommServer(this, this);
    serverThread = new Thread(server);
    serverThread.start();
  }

  private void stopRfcommServer() {
    if ((serverThread != null) && (serverThread.isAlive())) {
      serverThread.interrupt();
      serverThread = null;
    }
  }

  private void stopParser() {
    if ((parserThread != null) && (parserThread.isAlive())) {
      parserThread.interrupt();
      parserThread = null;
    }
  }

  private void startParser() {
    try {
      if (btSocket != null) {
        parser = new CommandParser(this, btSocket.getInputStream(), this);
      } else if (tcpSocket != null) {
        parser = new CommandParser(this, tcpSocket.getInputStream(), this);
      }
    } catch (IOException e) {
      Log.e(TAG, "Caught IOException when attempting to get input stream: " + e.getMessage());
      return;
    }

    parser.registerCommand(
        "reset",
        new Command() {
          @Override
          public void execute(Map<String, Value> params) {
            display.clear();
            parser.clearParams();
          }
        });

    display.registerWithParser(parser);

    parserThread = new Thread(parser);
    parserThread.start();
  }

  @Override
  public void onSocketConnected(BluetoothSocket newSocket) {
    // Reject multiple connections
    if ((btSocket != null) && (btSocket.isConnected())) {
      try {
        newSocket.close();
      } catch (IOException e) {
        Log.e(TAG, "Caught IOException while attempting to close incoming socket: "
              + e.getMessage());
      }

      return;
    }

    // TODO: Implement the protocol version handshake here.

    btSocket = newSocket;
    startParser();

    updateStatusText();
  }

  @Override
  public void onTcpSocketConnected(Socket newSocket) {
    // Reject multiple connections
    if ((tcpSocket != null) && (tcpSocket.isConnected())) {
      try {
        newSocket.close();
      } catch (IOException e) {
        Log.e(TAG, "Caught IOException while attempting to close incoming socket: "
              + e.getMessage());
      }

      return;
    }

    // TODO: Implement the protocol version handshake here.

    tcpSocket = newSocket;
    startParser();

    updateStatusText();
  }

  @Override
  public void onParserStopped(CommandParser parser) {
    if ((btSocket != null) && (btSocket.isConnected())) {
      try {
        btSocket.close();
      } catch (IOException e) {
        Log.e(TAG, "IOException while attempting to close socket: " + e.getMessage());
      } finally {
        btSocket = null;
      }
    }

    if ((tcpSocket != null) && (tcpSocket.isConnected())) {
      try {
        tcpSocket.close();
      } catch (IOException e) {
      } finally {
        tcpSocket = null;
      }
    }

    updateStatusText();
  }

  private Runnable hideConnectedTextTask = new Runnable() {
      @Override
      public void run() {
        TransitionManager.beginDelayedTransition(layout, new Fade());
        connectedText.setVisibility(View.GONE);
      }
    };

  private Runnable updateStatusTextTask = new Runnable() {
      @Override
      public void run() {
        if (layout != null) {
          if ((tcpSocket != null) || (btSocket != null)) {
            TransitionManager.beginDelayedTransition(layout, new Fade());
            connectedText.setVisibility(View.VISIBLE);
            uiHandler.postDelayed(hideConnectedTextTask, CONNECTED_FADE_DELAY_MILLIS);
            disconnectedText.setVisibility(View.GONE);
          } else {
            TransitionManager.beginDelayedTransition(layout, new Fade());
            connectedText.setVisibility(View.GONE);
            disconnectedText.setVisibility(View.VISIBLE);
          }
        } else {
          Log.wtf(TAG, "layout is null! Can't update text!");
        }
      }
    };

  private void updateStatusText() {
    uiHandler.removeCallbacks(hideConnectedTextTask);
    uiHandler.removeCallbacks(updateStatusTextTask);
    uiHandler.post(updateStatusTextTask);
  }
}
