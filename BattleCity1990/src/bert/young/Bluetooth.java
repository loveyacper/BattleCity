package bert.young;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Set;
import bert.young.GameView.GameThread;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

final class Bluetooth {
    private static final String TAG = "Bluetooth";
    private static final UUID  BATTLECITY_UUID = UUID.fromString("37812095-2001-0707-8000-617FECBDCAEA");
    private static final String DEVICE_NAME    = "BattleCity";
    public static final int   LISTEN_TIME   = 60;
    public static final int   REQUEST_SERVER_OPEN_BLUETOOTH = 0x10;
    public static final int   REQUEST_CLIENT_OPEN_BLUETOOTH = 0x11;
    
    private BluetoothAdapter mAdapter;
    private ListenThread     mListenThread;
    private ConnectThread    mConnectThread;
    private NetThread        mNetThread;
    private Handler          mHandler;
    private static Bluetooth smBluetooth = new Bluetooth();
    
    /** 返回唯一实例 */
    public static Bluetooth Instance() {
        return smBluetooth;
    }
    /** 私有构造函数 */
    private Bluetooth() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        Disable();
    }
        
    /** 初始化蓝牙设备 参数是主线程的handler */
    boolean  Init(Handler  handler) {
        mHandler = handler;
        return  null != mAdapter;    
    }
    
    /** */
    void Disable() {
        if (mAdapter != null)   mAdapter.disable();
    }
    
    /** 开始监听 */
    void StartListen() {
        Cancel();
        mListenThread = new ListenThread();
        mListenThread.start();
    }
    
    /** 开始连接 */
    void StartConnect(BluetoothDevice device) {
        Cancel();
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }
    
    /** 创建蓝牙通信线程 */
    void CreateNetThread(BluetoothSocket sock) {
        if (null == sock)  return;
        
        if (null != mNetThread)
            mNetThread.StopNet();
        
        mNetThread = new NetThread(sock);
    }
    
    /** 启动蓝牙通信线程 */
    void StartNetThread() {
        Cancel();
        mNetThread.start();
    }
    
    /** 停止蓝牙通信线程 */
    void StopNetThread() {
        if (mNetThread != null) {
            mNetThread.StopNet();
            try {
                mNetThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mNetThread = null;
        }
    }
    
    /** 取消蓝牙动作  */
    void Cancel() {
        if (null != mListenThread) {
            mListenThread.StopListen();
        }
        
        if (null != mConnectThread) {
            mConnectThread.StopConnect();
        }
        
        if (null != mAdapter) {
            mAdapter.cancelDiscovery();
        }
    }
    
    /** 获取已配对的蓝牙设备 */
    Set<BluetoothDevice> GetBonded() {
        if (null != mAdapter) {
            return mAdapter.getBondedDevices();
        }
        return  null;
    }
    
    /** 通信线程发送消息 */
    void  SendMessage(MsgBase msg)  {
        if (null != mNetThread) {
            mNetThread.SendMessage(msg);
        }
    }
    
    /** 是否启用了蓝牙设备 */
    boolean IsBluetoothEnabled() {
        return  mAdapter.isEnabled();
    }
    /** 是否可被搜索 */
    boolean  IsDiscoverable() {
        return mAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
    }
    /** 开始搜索蓝牙设备 */
    void  StartDiscoverary() {
        if (mAdapter.isDiscovering()) {
            mAdapter.cancelDiscovery();
        }

        mAdapter.startDiscovery();
    }
    
    /** 服务器监听线程 */
    private class ListenThread extends Thread  {
        BluetoothServerSocket mListenSock = null;

        private  void StopListen() {
            synchronized (this) {
                if (mListenSock == null)  return;

                try {
                    mListenSock.close(); // 不能多次调用close()
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mListenSock = null;
            }
        }

        @Override
        public void run()  {
            Log.d(TAG, "Enter listening thread!");
            setName("AcceptThread");
            mAdapter.cancelDiscovery();
            
            try {
                mListenSock = mAdapter.listenUsingRfcommWithServiceRecord(DEVICE_NAME,
                        BATTLECITY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Can not create listen sock");
                return;
            }
            
            BluetoothSocket  acceptSock = null;
            try {
                acceptSock = mListenSock.accept(LISTEN_TIME * 1000);
            } catch (IOException e) {
                Log.e(TAG, "Exception when listen ");
                acceptSock = null;
            }

            StopListen();
            Log.d(TAG, "exit lisening thread!");            

            Message  msg = new Message();
            msg.what     = BattleCity.MSG_ACCEPTED;
            msg.obj      = acceptSock;
            mHandler.sendMessage(msg);
        }
    }
    
    /** 客户端连接线程 */
    private class ConnectThread extends Thread {
        private BluetoothSocket mSocket;
        private final BluetoothDevice mSvrDevice;

        public ConnectThread(BluetoothDevice device) {
            mSvrDevice = device;
  
            try {
                mSocket = device.createRfcommSocketToServiceRecord(BATTLECITY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
                mSocket = null;
            }
        }

        public void run() {
            Log.i(TAG, "BEGIN ConnectThread, server device = " + mSvrDevice.getName());
            setName("ConnectThread");
            mAdapter.cancelDiscovery();
            
            BluetoothSocket result = mSocket;
            try {
                mSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Connect exception");
                StopConnect();
                result = null;
            }
            
            mSocket = null;
            Message  msg = new Message();
            msg.what     = BattleCity.MSG_CONNECTED;
            msg.obj      = result;
            mHandler.sendMessage(msg);
            Log.i(TAG, "exit ConnectThread, connect " + (result == null ? "failed" : "success"));
        }

        private void StopConnect() {
            synchronized (this) {
                if (mSocket == null)  return;
                
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mSocket = null;
            }
        }
    }
    
    /** 网络消息处理线程 */
    private class NetThread extends Thread {
        private BluetoothSocket mSocket;
        private InputStream     mInput;
        private OutputStream    mOutput;
        
        NetThread(BluetoothSocket sock) {
            MyResource.Assert(sock != null, "");
            mSocket = sock;
            
            InputStream  input  = null;
            OutputStream output = null;
            try {
                input  = mSocket.getInputStream();
                output = mSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            mInput  = input;
            mOutput = output;
        }

        boolean  SendMessage(MsgBase msg)  {
            boolean  success = true;
            if (!msg.Serialize(mOutput))
                success = false;
            
            try {
				mOutput.flush();
			} catch (IOException e) {
				e.printStackTrace();
				success = false;
			}
			
			if (!success)
			    StopNet();
			
			return  success;
        }
        
        /** 投递消息给游戏线程 */
        private boolean PostPackage(ByteBuffer  pkg) {
            MsgBase msg = null;
            switch (pkg.getInt()) {
            case SERVER_READY:
                msg = new ServerReadyMsg();
                msg.mMsgType = SERVER_READY;
                Log.d(TAG, "recv svr ready");
                break;
            case CLIENT_ACK:
                msg = new ClientAckMsg();
                msg.mMsgType = CLIENT_ACK;
                Log.d(TAG, "recv client ack");
                break;
                
            case ENEMY_BORN:
                msg = new EnemyBornMsg();
                msg.mMsgType = ENEMY_BORN;
                if (!msg.Unserialize(pkg)) {
                    Log.e(TAG, "Wrong enemy born msg!");
                    return false;
                }
                break;
                
            case PLAYER_BORN:
                msg = new PlayerBornMsg();
                msg.mMsgType = PLAYER_BORN;
                if (!msg.Unserialize(pkg)) {
                    Log.e(TAG, "Wrong player born msg");
                    return false;
                }
                break;
                
            case  MOVE:
                msg = new MoveMsg();
                msg.mMsgType = MOVE;
                if (!msg.Unserialize(pkg)) {
                    Log.e(TAG, "Wrong move msg!");
                    return false;
                }
                break;
                
            case  FIRE:
                msg = new FireMsg();
                msg.mMsgType = FIRE;
                if (!msg.Unserialize(pkg)){
                    Log.e(TAG, "Wrong fire msg!");
                    return false;
                }
                break;
                
            case  BONUS_BORN:
                msg = new BonusMsg();
                msg.mMsgType = BONUS_BORN;
                if (!msg.Unserialize(pkg)) {
                    Log.e(TAG, "Wrong bonus msg!");
                    return  false;
                }
                break;
                
            case ENEMY_HURT:
                msg = new EnemyHurtMsg(); 
                msg.mMsgType = ENEMY_HURT;
                if (!msg.Unserialize(pkg)) {
                    Log.e(TAG, "Wrong enemy die msg");
                    return false;    
                }
                break;
                
            default:
                Log.e(TAG, "what the fuck msgtype " + pkg.getInt());
                return false;
            }

            GameThread thread = GameView.Instance().GetThread();
            if (thread != null)
                thread.PushBluetoothMsg(msg);
            
            return  true;
        }

        /** 通信线程运行 */
        public void run()  {
            Log.d(TAG, "Enter net thread!");

            ByteBuffer  recvBuf = ByteBuffer.allocate(1024);
            byte[]      tmpBuf  = new byte[1024];
            recvBuf.position(0);
            recvBuf.limit(0);
            
            while (true) {
                int nRead = 0;
                try { 
                    nRead = mInput.read(tmpBuf);
                } catch (IOException e1) {
                    e1.printStackTrace();
                    break;
                }
                
                if (nRead <= 0)  {
                    Log.e(TAG, "Disconnect bluetooth, read bytes = " + nRead);
                    break;
                }

                int nRecved = recvBuf.remaining();
                recvBuf.limit(recvBuf.remaining() + nRead);
                recvBuf.position(nRecved);
                recvBuf.put(tmpBuf, 0, nRead);
                recvBuf.flip();
                while (recvBuf.remaining() >= 4) {
                    recvBuf.mark();
                    int pkgLen = recvBuf.getInt();
                    if (recvBuf.remaining() >= pkgLen) {
                        if (!PostPackage(recvBuf)) {
                            StopNet();
                            break;
                        }
                    }
                    else {
                        recvBuf.reset();
                        break;
                    }
                }
                
                nRecved = recvBuf.remaining();
                if (nRecved > 0) {
                    recvBuf.compact();
                }
            }

            StopNet();
            Log.d(TAG, "exit net thread!");
        }
        
        private void StopNet() {
            synchronized (this) {
                if (mSocket == null)  return;
                
                Log.d(TAG, "Try stop net thread");
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mSocket = null;
                Message  msg = new Message();
                msg.what     = BattleCity.MSG_DISCONNECT;
                mHandler.sendMessage(msg);
            }
        }
    }
    
    /** 协议定义 */
    abstract class MsgBase {
        abstract boolean Serialize(OutputStream output);
        abstract boolean Unserialize(ByteBuffer msg);
        int      mMsgType;
    }

    static final int SERVER_READY = 1;
    static final int CLIENT_ACK   = 2;
    static final int ENEMY_BORN   = 3;
    static final int PLAYER_BORN  = 4;
    static final int MOVE         = 5;
    static final int FIRE         = 6;
    static final int BONUS_BORN   = 7;
    static final int ENEMY_HURT   = 8;
    static final int HEARBEAT     = 9;

    /** SERVER_READY: srv tell client I am ready  */
    class ServerReadyMsg extends MsgBase {
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(8);
                buf.putInt(4);
                buf.putInt(SERVER_READY);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            return true;
        }
    }

    /** CLIENT_ACK: client recv srv ready  */
    class ClientAckMsg extends MsgBase {
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(8);
                buf.putInt(4);
                buf.putInt(CLIENT_ACK);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            return true;
        }
    }
    
    /** ENEMY_BORN    */
    class EnemyBornMsg extends MsgBase {
        int   mID;
        int   mX;
        int   mY;
        int   mType;
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(24);
                buf.putInt(20);
                buf.putInt(ENEMY_BORN);
                buf.putInt(mID);
                buf.putInt(mX);
                buf.putInt(mY);
                buf.putInt(mType);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            mID    = msg.getInt();
            mX     = msg.getInt();
            mY     = msg.getInt();
            mType  = msg.getInt();
            Log.d(TAG, "Born msg, index " + mID + " , pos x =  " + mX + " pos y = " + mY);
            return true;
        }
    }
    
    /** PLAYER_BORN    */
    class PlayerBornMsg extends MsgBase {
        int   mX, mY;
        int   mID;
        byte  mFace;
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(21);
                buf.putInt(17);
                buf.putInt(PLAYER_BORN);
                buf.putInt(mX);
                buf.putInt(mY);
                buf.putInt(mID);
                buf.put(mFace);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            mX = msg.getInt();
            mY = msg.getInt();
            mID= msg.getInt();
            mFace  = msg.get();
            Log.d(TAG, "Born player msg, " + " , pos x =  " + mX + " pos y = " + mY);
            return true;
        }
    }
    
    /**  MOVE */
    class MoveMsg extends MsgBase {
        int   mID;
        int   mX;
        int   mY;
        byte  mDir;
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(21);
                buf.putInt(17);
                buf.putInt(MOVE);
                buf.putInt(mX);
                buf.putInt(mY);
                buf.putInt(mID);
                buf.put(mDir);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer  msg) {
            mX    = msg.getInt();
            mY    = msg.getInt();
            mID    = msg.getInt();
            mDir   = msg.get();
            Log.d(TAG, "Move msg, index " + mID + " , dir " + mDir);
            return true;
        }
    }
    
    /** FIRE : Bullet born */
    class FireMsg extends MsgBase {
        int     mID;
        int     mX;
        int     mY;
        byte    mDir;// 朝向；做个检验吧，这个方向应该与本地运行的方向一致
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(21);
                buf.putInt(17);
                buf.putInt(FIRE);
                buf.putInt(mID);
                buf.putInt(mX);
                buf.putInt(mY);
                buf.put(mDir);  
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            mID   = msg.getInt();
            mX    = msg.getInt();
            mY    = msg.getInt();
            mDir  = msg.get();
            Log.d(TAG, "Fire msg, index " + mID + " , dir " + mDir);
            return true;
        }
    }
    
    /** BONUS_BORN : Bonus  */
    class BonusMsg extends MsgBase {
        int   m_x;
        int   m_y; 
        byte  m_type;
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer  buf = ByteBuffer.allocate(17);
                buf.putInt(13);
                buf.putInt(BONUS_BORN);
                buf.putInt(m_x);
                buf.putInt(m_y);
                buf.put(m_type);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            m_x = msg.getInt();
            m_y = msg.getInt();
            m_type  = msg.get();
            
            return true;
        }
    }
    
    /** Enemy_DIE */
    class EnemyHurtMsg extends MsgBase {
        int   mID;
        byte  mHP;
        
        public boolean Serialize(OutputStream output) {
            try {
                ByteBuffer buf = ByteBuffer.allocate(13);
                buf.putInt(9);
                buf.putInt(ENEMY_HURT);
                buf.putInt(mID);
                buf.put(mHP);
                output.write(buf.array());
            } catch (IOException e) {
                return false;
            }
            
            return  true;
        }
        
        public boolean Unserialize(ByteBuffer msg) {
            mID = msg.getInt();
            mHP = msg.get();
            return true;
        }
    }
}
