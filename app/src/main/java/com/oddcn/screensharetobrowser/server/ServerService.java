package com.oddcn.screensharetobrowser.server;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.oddcn.screensharetobrowser.RxBus;
import com.oddcn.screensharetobrowser.main.view.MainActivity;
import com.oddcn.screensharetobrowser.main.viewModel.MainViewModel;
import com.oddcn.screensharetobrowser.server.webServer.WebServer;
import com.oddcn.screensharetobrowser.server.wsServer.WsServer;
import com.oddcn.screensharetobrowser.server.wsServer.WsServerListener;
import com.oddcn.screensharetobrowser.utils.NetUtil;
import com.oddcn.screensharetobrowser.utils.notifier.Notifier;
import com.yanzhenjie.andserver.Server;

import java.net.InetSocketAddress;
import java.util.List;

import io.reactivex.functions.Consumer;


/**
 * Created by oddzh on 2017/11/1.
 */

public class ServerService extends Service {
    private static final String TAG = "ServerService";

    private ServerServiceListener serverServiceListener;

    public void setListener(ServerServiceListener listener) {
        serverServiceListener = listener;
    }

    public void makeForeground() {
        startForeground(
                1,
                Notifier.from(this)
                        .setTitle("屏幕分享服务")
                        .setText("本机 " + NetUtil.getWifiIp(this) + ":" + MainViewModel.port.get())
                        .setActivityClass(MainActivity.class)
                        .build()
        );
    }

    private WsServer wsServer;

    private Server webServer;

    @Override
    public void onCreate() {
        super.onCreate();
        webServer = WebServer.init(getAssets(), MainViewModel.port.get(), new Server.Listener() {
            @Override
            public void onStarted() {
                Log.d(TAG, "web server onStarted: ");
            }

            @Override
            public void onStopped() {
                Log.d(TAG, "web server onStopped: ");
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "web server onError: ");
                e.printStackTrace();
            }
        });

        WsServer.init("0.0.0.0", 8012);
        wsServer = WsServer.get();
        wsServer.setListener(new WsServerListener() {
            @Override
            public void onWsServerStatusChanged(boolean isRunning) {
                if (isRunning) {
                    makeForeground();
                }
                serverServiceListener.onServerStatusChanged(isRunning);
            }

            @Override
            public void onWsServerError(int errorType) {
                serverServiceListener.onWsServerError(errorType);
            }

            @Override
            public void onWsServerConnChanged(List<String> connList) {
                serverServiceListener.onWsServerConnChanged(connList);
            }
        });
    }


    public boolean isRunning() {
        return wsServer.isRunning();
    }

    public void startServer() {
        webServer.start();
        wsServer.runAsync();
    }

    public void stopServer() {
        webServer.stop();
        wsServer.stopWithException();
        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ServerServiceBinder();
    }

    public class ServerServiceBinder extends Binder {
        public ServerService getServerService() {
            return ServerService.this;
        }
    }

}