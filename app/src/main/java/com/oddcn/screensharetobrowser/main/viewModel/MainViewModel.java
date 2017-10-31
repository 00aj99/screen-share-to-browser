package com.oddcn.screensharetobrowser.main.viewModel;

import android.content.Context;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;
import android.view.View;
import android.widget.Toast;

import com.oddcn.screensharetobrowser.Utils;

import java.util.Random;

/**
 * Created by oddzh on 2017/10/30.
 */

public class MainViewModel {

    private Context context;

    public ObservableField<String> localIpText = new ObservableField<>();
    public ObservableInt port = new ObservableInt();
    private static final int PORT_MIN = 1024;
    private static final int PORT_MAX = 49151;

    public MainViewModel(Context context) {
        this.context = context;
        port.set(8123);
    }

    public void refreshIp() {
        localIpText.set(Utils.getWifiIp(context));
    }

    public View.OnClickListener onImgRefreshIpClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshIp();
                Toast.makeText(context, "已刷新本机IP", Toast.LENGTH_SHORT).show();
            }
        };
    }

    public View.OnClickListener onTextViewRandomChangeIpClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int randomPort = new Random().nextInt(PORT_MAX - PORT_MIN) + PORT_MIN;
                port.set(randomPort);
                refreshIp();
            }
        };
    }
}