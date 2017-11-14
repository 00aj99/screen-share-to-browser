package com.oddcn.screensharetobrowser.server.wsServer;

import android.util.Log;
import android.widget.Toast;

import com.oddcn.screensharetobrowser.RxBus;
import com.oddcn.screensharetobrowser.main.model.entity.WsServerStatusChangedEvent;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by oddzh on 2017/10/27.
 */

public class WsServer extends WebSocketServer {

    private static final String TAG = "WsServer";

    public static final int ERROR_TYPE_NORMAL = 0;
    public static final int ERROR_TYPE_PORT_IN_USE = 1;
    public static final int ERROR_TYPE_SERVER_CLOSE_FAIL = 2;

    private WsServerListener wsServerListener;

    public void setListener(WsServerListener listener) {
        wsServerListener = listener;
    }

    private int counter = 0;

    private static WsServer wsServer;

    public WsServer(InetSocketAddress address) {
        super(address);
    }

    public static void init(String host, int port) {
        wsServer = new WsServer(new InetSocketAddress(host, port));
    }

    public static WsServer get() {
        return wsServer;
    }

    public void runAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                wsServer.run();
            }
        }).start();
    }

    public void stopWithException() {
        try {
            wsServer.stop();
            isRunning = false;
            wsServerListener.onWsServerStatusChanged(isRunning);
        } catch (IOException e) {
            e.printStackTrace();
            wsServerListener.onWsServerError(ERROR_TYPE_SERVER_CLOSE_FAIL);//关闭服务失败
        } catch (InterruptedException e) {
            e.printStackTrace();
            wsServerListener.onWsServerError(ERROR_TYPE_SERVER_CLOSE_FAIL);//关闭服务失败
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        counter++;
        String connIp = conn.getRemoteSocketAddress().getAddress().toString().replace("/", "");
        connList.add(connIp);
        wsServerListener.onWsServerConnChanged(connList);
        Log.d(TAG, "onOpen: // " + connIp + " //Opened connection number  " + counter);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "onClose: ");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "onMessage: " + message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Log.d(TAG, "onMessage: buffer");
    }

    private boolean isRunning = false;

    public boolean isRunning() {
        return isRunning;
    }

    private List<String> connList = new ArrayList<>();

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.d(TAG, "onError: " + ex.getMessage());
        ex.printStackTrace();
        if (ex.getMessage().contains("Address already in use")) {
            Log.e(TAG, "onError: 端口已被占用");
            wsServerListener.onWsServerError(ERROR_TYPE_PORT_IN_USE);//服务启动失败，端口已被占用，请更换端口
            return;
        }
        wsServerListener.onWsServerError(ERROR_TYPE_NORMAL);
    }

    @Override
    public void onClosing(WebSocket conn, int code, String reason, boolean remote) {
        super.onClosing(conn, code, reason, remote);
        counter--;
        String connIp = conn.getRemoteSocketAddress().getAddress().toString().replace("/", "");
        for (String ip : connList) {
            if (ip.equals(connIp)) {
                connList.remove(ip);
            }
        }
        wsServerListener.onWsServerConnChanged(connList);
        Log.d(TAG, "onClosing: // " + connIp + " //Opened connection number  " + counter);
    }

    @Override
    public void onStart() {
        isRunning = true;
        wsServerListener.onWsServerStatusChanged(isRunning);//服务启动成功
        Log.d(TAG, "onStart: ");
    }

}
