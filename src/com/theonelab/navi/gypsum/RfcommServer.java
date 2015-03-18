package com.theonelab.navi.gypsum;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.util.UUID;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple RFCOMM server that handles listening for incoming RFCOMM connections
 * and notifying the owner of this instance when a socket is available for
 * communication.
 *
 * Provides a really drop-dead simple TCP client when running on an Emulator as
 * well. Look at {@link RfcommServer#TCP_PORT} for the port number.
 */
public class RfcommServer implements Runnable {
  private static final String TAG = "RfcommServer";

  private static final int TCP_PORT = 8888;
  
  /**
   * Callback listener interface for notifying the caller when the server socket
   * has been connected to.
   */
  public interface Listener {
    /**
     * Called when a new client has connected to the given RFCOMM server
     * socket.
     */
    public void onSocketConnected(BluetoothSocket socket);

    /**
     * Called when a new client has connected to the given TCP/IP server socket.
     * Only used on emulators where no Bluetooth adapter is readily available.
     */
    public void onTcpSocketConnected(Socket socket);
  }

  private final Listener listener;
  private final Context context;

  public RfcommServer(Context context, Listener listener) {
    this.listener = listener;
    this.context = context.getApplicationContext();
  }

  public void run() {
    Log.i(TAG, "RfcommListener started.");

    if (AndroidHelper.isRunningOnEmulator(context)) {
      listenOnTcp();
    } else {
      listenOnRfcomm();
    }
  }

  public void listenOnTcp() {
    ServerSocket serverSocket = null;

    try {
      serverSocket = new ServerSocket(TCP_PORT);

      while (!Thread.interrupted()) {
        Log.i(TAG, "Waiting for next client.");
        Socket socket = serverSocket.accept();

        Log.i(TAG, "Client connected.");
        listener.onTcpSocketConnected(socket);
      }
    } catch (IOException e) {
      Log.e(TAG, "Caught IOException during TCP operation.");
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          Log.e(TAG, "Caught an IOException while closing server socket.");
        }
      }
    }
  }

  public void listenOnRfcomm() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

    if (adapter == null) {
      Log.e(TAG, "Bluetooth adapter is null -- assuming Bluetooth is off and shutting down!");
      return;
    }

    BluetoothServerSocket serverSocket = null;

    try {
      serverSocket = adapter.listenUsingRfcommWithServiceRecord(
          GypsumProtocol.BT_SERVICE_NAME,
          GypsumProtocol.BT_SERVICE_UUID);

      while (!Thread.interrupted()) {
        Log.i(TAG, "Waiting for next client.");
        BluetoothSocket socket = serverSocket.accept();

        Log.i(TAG, "Client connected.");
        listener.onSocketConnected(socket);
      }
    } catch (IOException e) {
      Log.e(TAG, "Caught IOException during Bluetooth operation.");
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          Log.e(TAG, "Caught an IOException while closing server socket.");
        }
      }
    }
  }
}
