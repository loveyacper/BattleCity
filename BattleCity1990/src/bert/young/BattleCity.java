package bert.young;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;

enum GameMode {
    SINGLE,
    SERVER,
    CLIENT,
}

public class BattleCity extends Activity {
    private static final String  TAG = "BattleCity";
    static final String  KEY_MUSIC   = "music";
    static final String  KEY_VIBRATE = "vibrate";
    static final String  KEY_LIFE    = "life";
    static final String  KEY_STAGE   = "stage";

    private static final int   DLG_GAME_MODE_ID = 0x1;
    private static final int   DLG_SERVER_ID    = 0x2;
    private static final int   DLG_CONNECTING_ID= 0x3;
    private static final int   DLG_DEVICE_LIST  = 0x4;
    
    public static final int   MSG_ACCEPTED     = 0x1;
    public static final int   MSG_CONNECTED    = 0x2;
    public static final int   MSG_DISCONNECT   = 0x3;
    public static final int   MSG_NODEVICE     = 0x4;
    
    public  static GameMode smGameMode  = GameMode.SINGLE;
    private Bluetooth  mBluetooth;
    
    /** 处理蓝牙消息 */
    Handler  mHandler = new Handler() {
        public void handleMessage(Message msg) {
            BluetoothSocket  sock = (BluetoothSocket)msg.obj;
            switch (msg.what) {
            case MSG_ACCEPTED:
                removeDialog(DLG_SERVER_ID);
                if (null != sock) {
                    Toast.makeText(getApplicationContext(),
                                   "有新连接，开始双人游戏",
                                   Toast.LENGTH_SHORT)
                                   .show();
                    BattleCity.smGameMode = GameMode.SERVER;
                    mBluetooth.CreateNetThread(sock);
                    _LaunchGame();
                }
                else {
                    BattleCity.smGameMode = GameMode.SINGLE;
                    Toast.makeText(getApplicationContext(),
                                   "没有发现任何客户端连接",
                                   Toast.LENGTH_SHORT)
                                   .show();
                }
                break;
                
            case MSG_CONNECTED:
                removeDialog(DLG_CONNECTING_ID);
                if (null != sock) {
                    Toast.makeText(getApplicationContext(),
                                   "连接成功，开始双人游戏",
                                   Toast.LENGTH_SHORT)
                                   .show();
                    BattleCity.smGameMode = GameMode.CLIENT;
                    mBluetooth.CreateNetThread(sock);
                    _LaunchGame();
                }
                else {
                    Toast.makeText(getApplicationContext(),
                                   "无法连接服务器",
                                   Toast.LENGTH_SHORT)
                                   .show();
                    BattleCity.smGameMode = GameMode.SINGLE;
                }
                break;
                
            case MSG_DISCONNECT:
                Toast.makeText(getApplicationContext(),
                               "蓝牙断开，退出双打模式",
                               Toast.LENGTH_SHORT)
                               .show();
                BattleCity.smGameMode = GameMode.SINGLE;
                GameWorld.Instance().SetPartner(null);
                break;

            case MSG_NODEVICE:
                Toast.makeText(getApplicationContext(),
                               "没有发现蓝牙设备，只能进行单人游戏",
                               Toast.LENGTH_SHORT)
                               .show();
                break;
                
            default:
                super.handleMessage(msg);
                break;
            }
        }
    };
    
    private Set<BluetoothDevice>   mDeviceSet = new HashSet<BluetoothDevice>();    
    AlertDialog.Builder  mDeviceBuilder;
    Dialog               mDeviceDlg;
    
    /** 展示蓝牙设备列表 */
    void  ShowDeviceList(String title) {
        if (null == mDeviceBuilder)
            return;
        
        CharSequence[] deviceItems = null;
        if (!mDeviceSet.isEmpty()) {
            deviceItems = new CharSequence[mDeviceSet.size()];
            int  deviceCnt = 0;
            for (BluetoothDevice device : mDeviceSet) {
                Log.i(TAG, "Device list item " + device.getName());
                deviceItems[deviceCnt ++] = device.getName();
            }
        }
        
        mDeviceBuilder.setIcon(R.drawable.battlecity);
        mDeviceBuilder.setTitle(title);
        mDeviceBuilder.setItems(deviceItems, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                Iterator<BluetoothDevice> it = mDeviceSet.iterator();
                for (int idx = 0; idx < item; ++ idx, it.next())
                    ;
                mBluetooth.StartConnect(it.next());
                mDeviceBuilder = null;
                showDialog(DLG_CONNECTING_ID);
            }
        });

        CancelDeviceList();
        mDeviceDlg = mDeviceBuilder.create();
        mDeviceDlg.show();
    }
    
    void CancelDeviceList() {
        if (null != mDeviceDlg) {
            mDeviceDlg.cancel();
            mDeviceDlg = null;
        }
    }
    
    /** 处理蓝牙设备的搜索 */
    private final BroadcastReceiver m_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
               if (null != device) {
                   if (mDeviceSet.add(device)) {
                       Log.i(TAG, "Found " + device.getName() +
                                  " : " + device.getAddress());
                       ShowDeviceList("发现" + device.getName() + " ...");
                   }
               }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                Log.d(TAG, "Blue tooth discovery  start");
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                Log.d(TAG, "Blue tooth discovery  over");
                if (!mDeviceSet.isEmpty()) {
                    ShowDeviceList("蓝牙设备列表");
                }
                else {
                    CancelDeviceList();
                    Message  msg = new Message();
                    msg.what     = BattleCity.MSG_NODEVICE;
                    BattleCity.this.mHandler.sendMessage(msg);
                }
            }
        }   
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBluetooth  = Bluetooth.Instance();
        
        // 注册回调：当找到一个蓝牙设备
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(m_receiver, filter);

        // 注册回调：当结束搜索蓝牙设备
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(m_receiver, filter);
        
        // 注册回调：当开始搜索蓝牙设备
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        this.registerReceiver(m_receiver, filter);
        
    }
    
    /** 仅调用一次 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return   result;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())    {
        case R.id.setting:
            Log.d(TAG, "option item setting selected ");
            startActivity(new Intent(this, PrefActivity.class));
            break;
            
        case R.id.about:
            Log.d(TAG, "option item about selected ");
            startActivity(new Intent(this, About.class));
            break;
        }
        return false;
    }
    
    /** 开场 */
    private  void StartAnimation() {
        View    root = findViewById(R.id.mainview);
        final int x = root.getLeft();
        final int y = root.getTop();
        int h = root.getHeight();
 
        if (0 == h)  h = 400; // 启动时h为0
        Animation ani = new TranslateAnimation(x, x, h, y);
        ani.setDuration(2000);
        ani.setRepeatCount(0);
        ani.setInterpolator(new AccelerateInterpolator(0.6f));
        ani.setFillAfter(true);
        ani.setStartOffset(100);
        root.startAnimation(ani);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "OnStart");
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "OnRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "OnResume");
        StartAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Pause battle city");
        mBluetooth.Cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroy battle city");
        mBluetooth.Cancel();
        mBluetooth.Disable();
        unregisterReceiver(m_receiver);
    }

    /** 触摸：游戏模式选择 */
    @Override
    public boolean onTouchEvent(MotionEvent  event) {
        if (event.getAction() != MotionEvent.ACTION_UP)
            return super.onTouchEvent(event);
        
        showDialog(DLG_GAME_MODE_ID);
        return  true;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog  dlg = null;
        
        switch (id) {
        case DLG_GAME_MODE_ID: 
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.battlecity);
            builder.setTitle("游戏模式");
            CharSequence[] modeItems = {"单人游戏", "创建蓝牙游戏", "接入蓝牙游戏" };

            builder.setItems(modeItems, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    switch (item) {
                    case 0: // single
                        BattleCity.smGameMode = GameMode.SINGLE;
                        _LaunchGame();
                        break;

                    case 1: // server
                        if (!mBluetooth.Init(mHandler)) {
                            Toast.makeText(getApplicationContext(),
                                           "不支持蓝牙",
                                           Toast.LENGTH_SHORT)
                                           .show();
                            return;
                        }
                        
                        if (!mBluetooth.IsDiscoverable()) {
                            Intent  enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                            enableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, Bluetooth.LISTEN_TIME);
                            startActivityForResult(enableIntent, Bluetooth.REQUEST_SERVER_OPEN_BLUETOOTH);
                            return;
                        }
                        BattleCity.this.showDialog(DLG_SERVER_ID);
                        break;

                    case 2: // client
                        if (!mBluetooth.Init(mHandler)) {
                            Toast.makeText(getApplicationContext(),
                                           "不支持蓝牙",
                                           Toast.LENGTH_SHORT)
                                           .show();
                            break;
                        }
                        
                        if (!mBluetooth.IsBluetoothEnabled()) {
                            Intent  enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableIntent, Bluetooth.REQUEST_CLIENT_OPEN_BLUETOOTH);
                            return;
                        }
                        BattleCity.this.showDialog(DLG_DEVICE_LIST);
                        break;
                
                    default:
                        break;
                    }
                }
            });
            
            dlg = builder.create();
            dlg.show();
            
            // 调整大小，免得误按
            DisplayMetrics metric = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metric);
            final int width  = metric.widthPixels;
          
            WindowManager.LayoutParams layoutParams = dlg.getWindow().getAttributes();
            layoutParams.width  = width / 2;  
            layoutParams.height = LayoutParams.WRAP_CONTENT;  
            dlg.getWindow().setAttributes(layoutParams);  
            break;
            
        case DLG_SERVER_ID: // 创建一个对话框,正在监听
            dlg = ProgressDialog.show(BattleCity.this, "服务器运行中", 
                    "正在等待客户端连接...", true, true);
            dlg.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Log.i(TAG, "Cancel server");
                    mBluetooth.Cancel();
                }
            });
            
            mBluetooth.StartListen();
            break;
            
        case DLG_DEVICE_LIST:
            mDeviceBuilder = new AlertDialog.Builder(this);
            mDeviceSet.clear();
            mDeviceSet.addAll(mBluetooth.GetBonded());
            ShowDeviceList("正在搜索蓝牙设备...");
            mBluetooth.StartDiscoverary();
            break;
            
        case DLG_CONNECTING_ID:
            dlg = ProgressDialog.show(BattleCity.this, "连接服务器", "正在连接蓝牙设备...");
            break;

        default:
            Log.e(TAG, "Invalid dlg id = " + id);
            break;
        }

        return  dlg;
    }
    
    @Override
    public void onActivityResult(int reqCode, int result, Intent  intent) {
        switch (reqCode) {
        case Bluetooth.REQUEST_SERVER_OPEN_BLUETOOTH:
            final boolean isDiscoverable = (result > 0);
            if (isDiscoverable) {
                showDialog(DLG_SERVER_ID);
            }
            else {
                Toast.makeText(getApplicationContext(),
                        "蓝牙未开启, 只能进行单人游戏", Toast.LENGTH_SHORT).show();
            }
            break;

        case Bluetooth.REQUEST_CLIENT_OPEN_BLUETOOTH:
            if (result == Activity.RESULT_OK) {
                showDialog(DLG_DEVICE_LIST);
            }
            else {
                Toast.makeText(getApplicationContext(),
                        "蓝牙未开启, 只能进行单人游戏", Toast.LENGTH_SHORT).show();
            }
            break;
        }       
    }

    /** 启动游戏 */
    private void  _LaunchGame() {
        Intent  intent = new Intent(BattleCity.this, GameActivity.class);
        intent.putExtra(KEY_MUSIC, PrefActivity.getMusic(this));
        intent.putExtra(KEY_VIBRATE, PrefActivity.getVibrate(this));
        intent.putExtra(KEY_LIFE, PrefActivity.getLife(this));
        intent.putExtra(KEY_STAGE, PrefActivity.getStage(this));
        startActivity(intent);
    }
}
