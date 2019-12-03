package de.kai_morich.simple_usb_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.rtugeek.android.colorseekbar.ColorSeekBar;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;

import static com.rtugeek.android.colorseekbar.ColorSeekBar.mAlphaBarPosition;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.mBarWidth;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.mCachedColors;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.mColorBarPosition;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.mMaxPosition;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.pick_color_return;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.realLeft;
import static com.rtugeek.android.colorseekbar.ColorSeekBar.realRight;

/*
* By. GTO. 2019.11.27
* */
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private BarChart barChart;

    private ColorSeekBar colorSeekBar;

    private enum Connected { False, Pending, True }

    public static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    private int deviceId, portNum, baudRate;
    private String newline = "\r\n";

    private TextView receiveText;
    private TextView textview;

    //TODO : mgc Data format global val
    //TODO ===============================================
    public String mgc_header;

    public String mgc_X;
    public String mgc_Y;
    public String mgc_Z;

    public String mgc_touch;
    public String mgc_gesture;

    public String mgc_cic_s;
    public String mgc_cic_w;
    public String mgc_cic_n;
    public String mgc_cic_e;
    public String mgc_cic_c;

    public String mgc_sd_s;
    public String mgc_sd_w;
    public String mgc_sd_n;
    public String mgc_sd_e;
    public String mgc_sd_c;

    public String mgc_tail;

    public float x_percent_formula;
    public float y_percent_formula;
    public float z_percent_formula;

    public float color_percent_formula;

    public float color_full;
    public float color_final;
    public int x_num;

    String color_1 = "#fd4381";

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;
    private BroadcastReceiver broadcastReceiver;

    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);

        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
    }


    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        if(initialStart && service !=null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        textview = view.findViewById(R.id.text_view);
        barChart = view.findViewById(R.id.bargraph);

        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        colorSeekBar = view.findViewById(R.id.color_seek_bar);

        colorSeekBar.setOnColorChangeListener(new ColorSeekBar.OnColorChangeListener() {

            @Override
            public void onColorChangeListener(int colorBarPosition, int alphaBarPosition, int color) {
                x_num = (int) x_percent_formula;
                //textview.setTextColor(cached_color[x_num]);
                textview.setTextColor(color);

                System.out.println("x_num and cached color :" + x_num +  " " + cached_color[x_num]);
                System.out.println("cached color: " + mCachedColors);
                System.out.println("Bar width: " + mBarWidth);
                System.out.println("Bar left and right: " + realLeft + " , " + realRight);

            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id ==R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        UsbSerialPort usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            socket = new SerialSocket();
            service.connect(this, "Connected");
            socket.connect(getContext(), service, usbConnection, usbSerialPort, baudRate);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        socket = null;
    }


    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            byte[] data = (str + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for(final byte b: data)
            sb.append(String.format("%02x", b&0xff));  // byte to String data

        //receiveText.append(new String(data)); // ascii values
        /*
        receiveText.append(new String(sb));     // Hex values
        receiveText.append("\n");
        */

        mgc_header = sb.substring(0, 4);
        mgc_X = sb.substring(4, 8);
        mgc_Y = sb.substring(8, 12);
        mgc_Z = sb.substring(12, 16);
        mgc_touch = sb.substring(16, 18);
        mgc_gesture = sb.substring(18, 20);

        mgc_cic_s = sb.substring(20, 28);
        mgc_cic_w = sb.substring(28, 36);
        mgc_cic_n = sb.substring(36, 44);
        mgc_cic_e = sb.substring(44, 52);
        mgc_cic_c = sb.substring(52, 60);

        mgc_sd_s = sb.substring(60, 68);
        mgc_sd_w = sb.substring(68, 76);
        mgc_sd_n = sb.substring(76, 84);
        mgc_sd_e = sb.substring(84, 92);
        mgc_sd_c = sb.substring(92, 100);


        /*
        mgc_gesture_int = Integer.parseInt( mgc_gesture, 16 );

        receiveText.append(mgc_X + ", " + mgc_Y + ", " + mgc_Z + ", " +  "  " + mgc_touch + ", " + mgc_gesture + ", " +
                "  " + mgc_cic_s + ", " + mgc_cic_w + ", " + mgc_cic_n + ", " + mgc_cic_e + ", " + mgc_cic_c +
                "  " + mgc_sd_s + ", " + mgc_sd_w + ", " + mgc_sd_n + ", " + mgc_sd_e + ", " + mgc_sd_c);


        receiveText.append("\n");

        switch (mgc_gesture_int) {
            case 68:
                Toast toast0 = Toast.makeText(service.getApplicationContext(),"========== Down ==========", Toast.LENGTH_SHORT);
                toast0.show();
                receiveText.setBackgroundColor(Color.parseColor("#A814E7"));    // violet

                break;

            case 76:
                Toast toast1 = Toast.makeText(service.getApplicationContext(),"========== Left ==========", Toast.LENGTH_SHORT);
                toast1.show();
                receiveText.setBackgroundColor(Color.parseColor("#FF1E9D"));    // pink

                break;

            case 82:
                Toast toast2 = Toast.makeText(service.getApplicationContext(),"========== Right ==========", Toast.LENGTH_SHORT);
                toast2.show();
                receiveText.setBackgroundColor(Color.parseColor("#FFFA82"));    // yellow

                break;

            case 85:
                Toast toast3 = Toast.makeText(service.getApplicationContext(),"========== Up ==========", Toast.LENGTH_SHORT);
                toast3.show();
                receiveText.setBackgroundColor(Color.parseColor("#C1FF6B"));    // green

                break;

        }*/

        /*
        Toast toast = Toast.makeText(service.getApplicationContext(),gesture_hex, Toast.LENGTH_SHORT);
        toast.show();
        */

        // ===============================================================================================
        String [] mgc_X_0 = new String[2];
        mgc_X_0[0] = mgc_X.substring(0, 2);     //mgc_X [0]
        mgc_X_0[1] = mgc_X.substring(2, 4);     //mgc_X [1]
        swap(mgc_X_0);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_Y_0 = new String[2];
        mgc_Y_0[0] = mgc_Y.substring(0, 2);     //mgc_Y [0]
        mgc_Y_0[1] = mgc_Y.substring(2, 4);     //mgc_Y [1]
        swap(mgc_Y_0);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_Z_0 = new String[2];
        mgc_Z_0[0] = mgc_Z.substring(0, 2);     //mgc_Z [0]
        mgc_Z_0[1] = mgc_Z.substring(2, 4);     //mgc_Z [1]
        swap(mgc_Z_0);
        // ===============================================================================================
        String mgc_X_swap = mgc_X_0[0] + mgc_X_0[1];
        String mgc_Y_swap = mgc_Y_0[0] + mgc_Y_0[1];
        String mgc_Z_swap = mgc_Z_0[0] + mgc_Z_0[1];
        float mgc_X_int = Integer.parseInt(mgc_X_swap, 16);
        x_percent_formula = ((mgc_X_int/65534)*100);
        float mgc_Y_int = Integer.parseInt(mgc_Y_swap, 16);
        y_percent_formula = ((mgc_Y_int/65534)*100);
        float mgc_Z_int = Integer.parseInt(mgc_Z_swap, 16);
        z_percent_formula = ((mgc_Z_int/65534)*100);

        // ===============================================================================================
        String [] mgc_cic_s_2 = new String[2];
        mgc_cic_s_2[0] = mgc_cic_s.substring(0, 2);     //mgc_cic_s [0]
        mgc_cic_s_2[1] = mgc_cic_s.substring(2, 4);     //mgc_cic_s [1]

        String [] mgc_cic_s_1 = new String[2];
        mgc_cic_s_1[0] = mgc_cic_s.substring(4, 6);     //mgc_cic_s [2]
        mgc_cic_s_1[1] = mgc_cic_s.substring(6, 8);     //mgc_cic_s [3]

        swap(mgc_cic_s_2);
        swap(mgc_cic_s_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_cic_w_2 = new String[2];
        mgc_cic_w_2[0] = mgc_cic_w.substring(0, 2);     //mgc_cic_w [0]
        mgc_cic_w_2[1] = mgc_cic_w.substring(2, 4);     //mgc_cic_w [1]

        String [] mgc_cic_w_1 = new String[2];
        mgc_cic_w_1[0] = mgc_cic_w.substring(4, 6);     //mgc_cic_w [2]
        mgc_cic_w_1[1] = mgc_cic_w.substring(6, 8);     //mgc_cic_w [3]

        swap(mgc_cic_w_2);
        swap(mgc_cic_w_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_cic_n_2 = new String[2];
        mgc_cic_n_2[0] = mgc_cic_n.substring(0, 2);     //mgc_cic_n [0]
        mgc_cic_n_2[1] = mgc_cic_n.substring(2, 4);     //mgc_cic_n [1]

        String [] mgc_cic_n_1 = new String[2];
        mgc_cic_n_1[0] = mgc_cic_n.substring(4, 6);     //mgc_cic_n [2]
        mgc_cic_n_1[1] = mgc_cic_n.substring(6, 8);     //mgc_cic_n [3]

        swap(mgc_cic_n_2);
        swap(mgc_cic_n_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_cic_e_2 = new String[2];
        mgc_cic_e_2[0] = mgc_cic_e.substring(0, 2);     //mgc_cic_e [0]
        mgc_cic_e_2[1] = mgc_cic_e.substring(2, 4);     //mgc_cic_e [1]

        String [] mgc_cic_e_1 = new String[2];
        mgc_cic_e_1[0] = mgc_cic_e.substring(4, 6);     //mgc_cic_e [2]
        mgc_cic_e_1[1] = mgc_cic_e.substring(6, 8);     //mgc_cic_e [3]

        swap(mgc_cic_e_2);
        swap(mgc_cic_e_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_cic_c_2 = new String[2];
        mgc_cic_c_2[0] = mgc_cic_c.substring(0, 2);     //mgc_cic_c [0]
        mgc_cic_c_2[1] = mgc_cic_c.substring(2, 4);     //mgc_cic_c [1]

        String [] mgc_cic_c_1 = new String[2];
        mgc_cic_c_1[0] = mgc_cic_c.substring(4, 6);     //mgc_cic_c [2]
        mgc_cic_c_1[1] = mgc_cic_c.substring(6, 8);     //mgc_cic_c [3]

        swap(mgc_cic_c_2);
        swap(mgc_cic_c_1);
        // ===============================================================================================
        String mgc_cic_s_swap = mgc_cic_s_1[0]+mgc_cic_s_1[1]+mgc_cic_s_2[0]+mgc_cic_s_2[1];
        String mgc_cic_w_swap = mgc_cic_w_1[0]+mgc_cic_w_1[1]+mgc_cic_w_2[0]+mgc_cic_w_2[1];
        String mgc_cic_n_swap = mgc_cic_n_1[0]+mgc_cic_n_1[1]+mgc_cic_n_2[0]+mgc_cic_n_2[1];
        String mgc_cic_e_swap = mgc_cic_e_1[0]+mgc_cic_e_1[1]+mgc_cic_e_2[0]+mgc_cic_e_2[1];
        String mgc_cic_c_swap = mgc_cic_c_1[0]+mgc_cic_c_1[1]+mgc_cic_c_2[0]+mgc_cic_c_2[1];

        Long cic_s = Long.parseLong(mgc_cic_s_swap, 16);
        Long cic_w = Long.parseLong(mgc_cic_w_swap, 16);
        Long cic_n = Long.parseLong(mgc_cic_n_swap, 16);
        Long cic_e = Long.parseLong(mgc_cic_e_swap, 16);
        Long cic_c = Long.parseLong(mgc_cic_c_swap, 16);

        float cic_s_float = Float.intBitsToFloat(cic_s.intValue());
        float cic_w_float = Float.intBitsToFloat(cic_w.intValue());
        float cic_n_float = Float.intBitsToFloat(cic_n.intValue());
        float cic_e_float = Float.intBitsToFloat(cic_e.intValue());
        float cic_c_float = Float.intBitsToFloat(cic_c.intValue());


        // ===============================================================================================
        String [] mgc_sd_s_2 = new String[2];
        mgc_sd_s_2[0] = mgc_sd_s.substring(0, 2);     //mgc_sd_s [0]
        mgc_sd_s_2[1] = mgc_sd_s.substring(2, 4);     //mgc_sd_s [1]

        String [] mgc_sd_s_1 = new String[2];
        mgc_sd_s_1[0] = mgc_sd_s.substring(4, 6);     //mgc_sd_s [2]
        mgc_sd_s_1[1] = mgc_sd_s.substring(6, 8);     //mgc_sd_s [3]

        swap(mgc_sd_s_2);
        swap(mgc_sd_s_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_sd_w_2 = new String[2];
        mgc_sd_w_2[0] = mgc_sd_w.substring(0, 2);     //mgc_sd_w [0]
        mgc_sd_w_2[1] = mgc_sd_w.substring(2, 4);     //mgc_sd_w [1]

        String [] mgc_sd_w_1 = new String[2];
        mgc_sd_w_1[0] = mgc_sd_w.substring(4, 6);     //mgc_sd_w [2]
        mgc_sd_w_1[1] = mgc_sd_w.substring(6, 8);     //mgc_sd_w [3]

        swap(mgc_sd_w_2);
        swap(mgc_sd_w_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_sd_n_2 = new String[2];
        mgc_sd_n_2[0] = mgc_sd_n.substring(0, 2);     //mgc_sd_n [0]
        mgc_sd_n_2[1] = mgc_sd_n.substring(2, 4);     //mgc_sd_n [1]

        String [] mgc_sd_n_1 = new String[2];
        mgc_sd_n_1[0] = mgc_sd_n.substring(4, 6);     //mgc_sd_n [2]
        mgc_sd_n_1[1] = mgc_sd_n.substring(6, 8);     //mgc_sd_n [3]

        swap(mgc_sd_n_2);
        swap(mgc_sd_n_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_sd_e_2 = new String[2];
        mgc_sd_e_2[0] = mgc_sd_e.substring(0, 2);     //mgc_sd_e [0]
        mgc_sd_e_2[1] = mgc_sd_e.substring(2, 4);     //mgc_sd_e [1]

        String [] mgc_sd_e_1 = new String[2];
        mgc_sd_e_1[0] = mgc_sd_e.substring(4, 6);     //mgc_sd_e [2]
        mgc_sd_e_1[1] = mgc_sd_e.substring(6, 8);     //mgc_sd_e [3]

        swap(mgc_sd_e_2);
        swap(mgc_sd_e_1);
        // ===============================================================================================
        // ===============================================================================================
        String [] mgc_sd_c_2 = new String[2];
        mgc_sd_c_2[0] = mgc_sd_c.substring(0, 2);     //mgc_sd_c [0]
        mgc_sd_c_2[1] = mgc_sd_c.substring(2, 4);     //mgc_sd_c [1]

        String [] mgc_sd_c_1 = new String[2];
        mgc_sd_c_1[0] = mgc_sd_c.substring(4, 6);     //mgc_sd_c [2]
        mgc_sd_c_1[1] = mgc_sd_c.substring(6, 8);     //mgc_sd_c [3]

        swap(mgc_sd_c_2);
        swap(mgc_sd_c_1);
        // ===============================================================================================
        String mgc_sd_s_swap = mgc_sd_s_1[0]+mgc_sd_s_1[1]+mgc_sd_s_2[0]+mgc_sd_s_2[1];
        String mgc_sd_w_swap = mgc_sd_w_1[0]+mgc_sd_w_1[1]+mgc_sd_w_2[0]+mgc_sd_w_2[1];
        String mgc_sd_n_swap = mgc_sd_n_1[0]+mgc_sd_n_1[1]+mgc_sd_n_2[0]+mgc_sd_n_2[1];
        String mgc_sd_e_swap = mgc_sd_e_1[0]+mgc_sd_e_1[1]+mgc_sd_e_2[0]+mgc_sd_e_2[1];
        String mgc_sd_c_swap = mgc_sd_c_1[0]+mgc_sd_c_1[1]+mgc_sd_c_2[0]+mgc_sd_c_2[1];

        Long sd_s = Long.parseLong(mgc_sd_s_swap, 16);
        Long sd_w = Long.parseLong(mgc_sd_w_swap, 16);
        Long sd_n = Long.parseLong(mgc_sd_n_swap, 16);
        Long sd_e = Long.parseLong(mgc_sd_e_swap, 16);
        Long sd_c = Long.parseLong(mgc_sd_c_swap, 16);

        float sd_s_float = Float.intBitsToFloat(sd_s.intValue());
        float sd_w_float = Float.intBitsToFloat(sd_w.intValue());
        float sd_n_float = Float.intBitsToFloat(sd_n.intValue());
        float sd_e_float = Float.intBitsToFloat(sd_e.intValue());
        float sd_c_float = Float.intBitsToFloat(sd_c.intValue());

        receiveText.append("XYZ int :"+ Math.round((x_percent_formula*100/100.0)) + " " + Math.round((y_percent_formula*100/100.0))+
                " " + Math.round((z_percent_formula*100/100.0)));
        receiveText.append("\n");

        receiveText.append("CIC float :"+ (Math.round(cic_s_float*100)/100.0) + " " + (Math.round(cic_w_float*100)/100.0) + " "
                + (Math.round(cic_n_float*100)/100.0) + " " + (Math.round(cic_e_float*100)/100.0) + " " + (Math.round(cic_c_float*100)/100.0));
        receiveText.append("\n");

        receiveText.append("SD float :"+ (Math.round(sd_s_float*100)/100.0) + " " + (Math.round(sd_w_float*100)/100.0) + " "
                + (Math.round(sd_n_float*100)/100.0) + " " + (Math.round(sd_e_float*100)/100.0) + " " + (Math.round(sd_w_float*100)/100.0));
        receiveText.append("\n");

        ArrayList<BarEntry> barEntries = new ArrayList<>();
        barEntries.add(new BarEntry(sd_s_float, 0));
        barEntries.add(new BarEntry(sd_w_float, 1));
        barEntries.add(new BarEntry(sd_n_float, 2));
        barEntries.add(new BarEntry(sd_e_float, 3));
        barEntries.add(new BarEntry(sd_c_float, 4));
        BarDataSet barDataSet = new BarDataSet(barEntries, "values");

        ArrayList<String> theDates = new ArrayList<>();

        theDates.add("South");
        theDates.add("West");
        theDates.add("North");
        theDates.add("East");
        theDates.add("Center");

        BarData theData = new BarData(theDates, barDataSet);
        barChart.setData(theData);

        barChart.setTouchEnabled(true);
        barChart.setDragEnabled(true);
        barChart.setScaleEnabled(true);

    }

    public static void swap(String [] a){
        String temp;
        temp = a[0];
        a[0] = a[1];
        a[1] = temp;
    }

    private void status(String str) {
        /*
        char[] chars = str.toCharArray();
        StringBuffer hex_data = new StringBuffer();

        for (int i = 0; i < chars.length; i++)
        {
            hex_data.append(Integer.toHexString((int) chars[i]));
        }
        */
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    ///////////////////////////////////////////////////////////////////////
    //static parameters
    ///////////////////////////////////////////////////////////////////////
    public static final String GEST_REQUEST_MESSAGE              = "06";
    public static final String GEST_SYSTEM_STATUS                = "15";
    public static final String GEST_FW_VERSION_INFO              = "83";
    public static final String GEST_SENSOR_DATA_OUTPUT           = "91";
    public static final String GEST_SET_RUNTIME_PARAMETER        = "A2";

    public static int [] cached_color = { 65536, -62976, -60416, -57600, -55040, -52480, -49920, -47104, -44544, -41984,
            -39424, -36608, -34048, -228096, -553216, -878336, -1203200, -1528320, -1853440, -2178560,
            -2503680, -2894336, -3219200, -3544320, -3869440, -4194560, -5177599, -6226174, -7209213, -8192252,
            -9175292, -10223867, -11206906, -12189945, -13238520, -14221559, -15204598, -16187637, -16711918, -16711903,
            -16711889, -16711875, -16711860, -16711846, -16711831, -16711817, -16711803, -16711788, -16711774, -16711759,
            -16711745, -16715580, -16719671, -16723506, -16727341, -16731175, -16735266, -16739101, -16742936, -16747027,
            -16750862, -16754697, -16758532, -16433665, -15779585, -15059969, -14405889, -13751809, -13097729, -12378113,
            -11724033, -11070209, -10416129, -9696513, -9042433, -8388353, -7732998, -7077643, -6422288, -5701397,
            -5046043, -4390688, -3735333, -3079978, -2424623, -1703732, -1048377, -393022, -196419, -393030,
            -655178, -851789, -1113936, -1310548, -1572695, -1769307, -2031454, -2228066, -2490213, -2686825,
            -2948972 };

    //-65536	-62976	-60416	-57600	-55040	-52480	-49920	-47104	-44544	-41984	-39424	-36608	-34048	-228096	-553216	-878336	-1203200	-1528320	-1853440	-2178560	-2503680	-2894336	-3219200	-3544320	-3869440	-4194560	-5177599	-6226174	-7209213	-8192252	-9175292	-10223867	-11206906	-12189945	-13238520	-14221559	-15204598	-16187637	-16711918	-16711903	-16711889	-16711875	-16711860	-16711846	-16711831	-16711817	-16711803	-16711788	-16711774	-16711759	-16711745	-16715580	-16719671	-16723506	-16727341	-16731175	-16735266	-16739101	-16742936	-16747027	-16750862	-16754697	-16758532	-16433665	-15779585	-15059969	-14405889	-13751809	-13097729	-12378113	-11724033	-11070209	-10416129	-9696513	-9042433	-8388353	-7732998	-7077643	-6422288	-5701397	-5046043	-4390688	-3735333	-3079978	-2424623	-1703732	-1048377	-393022	-196419	-393030	-655178	-851789	-1113936	-1310548	-1572695	-1769307	-2031454	-2228066	-2490213	-2686825	-2948972

    ///////////////////////////////////////////////////////////////////////
    //static parameters
    ///////////////////////////////////////////////////////////////////////

}
