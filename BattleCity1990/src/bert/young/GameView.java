package bert.young;

import bert.young.Bluetooth.BonusMsg;
import bert.young.Bluetooth.EnemyBornMsg;
import bert.young.Bluetooth.ClientAckMsg;
import bert.young.Bluetooth.EnemyHurtMsg;
import bert.young.Bluetooth.FireMsg;
import bert.young.Bluetooth.MoveMsg;
import bert.young.Bluetooth.MsgBase;
import bert.young.Bluetooth.PlayerBornMsg;
import bert.young.Bonus.BonusType;
import bert.young.Movable.Dir;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String  TAG = "GameView";
    private SurfaceHolder   mHolder = null;
    private GameThread      mThread = null;
    
    private static  GameView smGameView = null;
    public static GameView Instance() {
        return  smGameView;
    }
    
    public GameThread GetThread() {
        return  mThread;
    }

    /** 游戏线程定义开始*/
    class GameThread extends Thread {      
        private SurfaceHolder   mHolder  = null;
        private boolean         mRunning = false;
        private LockFreeQueue   mUserCmd = new LockFreeQueue(2048);
        private LockFreeQueue   mBluetoothMsg = null;
        
        /** 蓝牙线程调用该函数 */
        boolean PushBluetoothMsg(Object obj) {
            return  mBluetoothMsg != null &&
                    mBluetoothMsg.PushObject(obj);
        }
        
        /** 游戏线程处理蓝牙消息 */
        void    ProcessBluetoothMsg() {
            if (mBluetoothMsg == null)     return;
            
            GameWorld rWorld = GameWorld.Instance();
            Object  cmd = null;
            while ((cmd = mBluetoothMsg.GetObject())!= null) {
                MsgBase msg = (MsgBase)cmd;
                switch (msg.mMsgType) {
                case Bluetooth.SERVER_READY:
                    Log.d(TAG, "Server ready");
                    MyResource.Assert(BattleCity.smGameMode == GameMode.CLIENT, "Must be client");
                    ClientAckMsg ack = Bluetooth.Instance().new ClientAckMsg();
                    Bluetooth.Instance().SendMessage(ack);
                    rWorld.StartPlay();
                    break;
                    
                case Bluetooth.CLIENT_ACK:
                    Log.d(TAG, "Client ready");
                    rWorld.StartPlay();
                    break;
                    
                case Bluetooth.ENEMY_BORN: 
                    EnemyBornMsg  bornmsg = (EnemyBornMsg)msg;
                    Log.d(TAG, "Born enemy at " + bornmsg.mX + ", " + bornmsg.mY);

                    if (!rWorld.OnBornEnemy(bornmsg) && rWorld.IsPlaying()) {
                        Log.e(TAG, "Born enemy failed ,id " + bornmsg.mID);
                        return;
                    }
                    break;
                    
                case Bluetooth.PLAYER_BORN:
                    PlayerBornMsg bornMsg = (PlayerBornMsg)msg;
                    Log.d(TAG, "Born player at " + bornMsg.mX + ", " + bornMsg.mY);
                    rWorld.OnBornPlayer(bornMsg);
                    break;
                    
                case Bluetooth.MOVE:
                    MoveMsg moveMsg = (MoveMsg)msg;
                    Tank    tank    = rWorld.FindObject(moveMsg.mID);

                    if (null == tank) {
                        Log.d(TAG, "Can not find tank " + moveMsg.mID);
                    }
                    else {
                        if (!tank.IsAlive()) {
                            Log.e(TAG, "Tank is die, but svr say it's moving " + moveMsg.mID);
                            tank.mHP = 1;
                            tank.SetState(ObjectState.NORMAL);
                        }
                        tank.SetPos(moveMsg.mX * GameWorld.StandardUnit(),
                                moveMsg.mY * GameWorld.StandardUnit());
                        tank.DoMove(Dir.Convert2Dir(moveMsg.mDir));
                    }
                    break;
                    
                case Bluetooth.FIRE:
                    FireMsg fireMsg = (FireMsg)msg;
                    tank = rWorld.FindObject(fireMsg.mID);
                    if (null == tank) {
                        Log.e(TAG, "Can not find owner tank " + fireMsg.mID);
                    }
                    else {
                        Log.d(TAG, "Fire tank" + fireMsg.mID + ", at " + tank.mPos.x + ", " + tank.mPos.y);
                        if (fireMsg.mDir != tank.mFaceDir.ordinal()) {
                            Log.e(TAG, "Why dir do not consist? face " + tank.mFaceDir +
                                    ", msg dir " + fireMsg.mDir);
                        }
                        if (!tank.IsAlive()) {
                            Log.e(TAG, "Tank is die, but svr say it's alive " + fireMsg.mID);
                            tank.mHP = 1;
                            tank.SetState(ObjectState.NORMAL);
                        }
                        
                        if (!tank.Fire(fireMsg.mX * GameWorld.StandardUnit(),
                                fireMsg.mY * GameWorld.StandardUnit(),
                                Dir.Convert2Dir(fireMsg.mDir))) {
                            if (rWorld.IsPlaying()) {
                                Log.e(TAG, "Fire failed!");
                                return; // leave this msg, and process it later!
                            }
                            else {
                                // 非游戏状态下，跳过此消息
                                break;
                            }
                        }
                    }
                    break;
                    
                case Bluetooth.BONUS_BORN:
                    BonusMsg bonusMsg = (BonusMsg)msg;
                    int x = bonusMsg.m_x * GameWorld.StandardUnit();
                    int y = bonusMsg.m_y * GameWorld.StandardUnit();
                    Bonus bonus = Bonus.CreateBonus(BonusType.values()[bonusMsg.m_type]);
                    bonus.SetPos(x, y);
                    rWorld.SetBonus(bonus);   
                    Log.d(TAG, "Bonus born, type " + bonusMsg.m_type);      
                    break;
                    
                case Bluetooth.ENEMY_HURT:
                    EnemyHurtMsg dieMsg = (EnemyHurtMsg)msg;
                    tank = rWorld.FindObject(dieMsg.mID);
                    Log.d(TAG, "Enemy id " + dieMsg.mID + ", hp " + dieMsg.mHP);
                    
                    if (null == tank) {
                        if (dieMsg.mHP > 0) {
                            //MyResource.Assert(false, "");
                            Log.e(TAG, "Can not find enemy tank " + dieMsg.mID);
                        }
                    }
                    else {
                        if (tank.mHP != dieMsg.mHP) {
                            Log.e(TAG, "Local hp " + tank.mHP + ", remote hp " + dieMsg.mHP);
                            if (tank.mHP > dieMsg.mHP) {
                                tank.OnHurt();
                                if (tank.mHP > 0 && dieMsg.mHP == 0)
                                    tank.SetState(ObjectState.EXPLODE1);
                            }
                            else {
                                tank.mHP = dieMsg.mHP;
                                tank.SetState(ObjectState.NORMAL);
                            }
                        }                            
                    }
                    break;

                default:
                    Log.e(TAG, "Wrong msg type " + msg.mMsgType);
                    break;
                }
                
                mBluetoothMsg.PopObject();
            }
        }
        
        boolean PushAction(Object obj) {
            return  mUserCmd.PushObject(obj);
        }
        
        void   ClearCmd() {
            mUserCmd.Clear();
           // if (mBluetoothMsg != null) 
             //   mBluetoothMsg.Clear();
        }
        
        /** 处理用户操作 */
        void ProcessAction() {
            PlayerTank  me = GameWorld.Instance().GetMe();
            if (null == me || !GameWorld.Instance().IsPlaying())  return;

            Object  cmd = null;
            while ((cmd = mUserCmd.GetObject()) != null) {
                mUserCmd.PopObject();
                UserAction  action = (UserAction)cmd;
                switch (action.mType) {
                case ACTION_FIRE:
                    if (me.IsAlive() && me.Fire(me.mPos.x, me.mPos.y, me.mFaceDir)) {
                        Misc.PlaySound(AudioID.AUDIO_FIRE);
                        if (BattleCity.smGameMode != GameMode.SINGLE) {
                            me.SendFireMsg(me.mPos.x, me.mPos.y, me.mFaceDir);
                        }
                    }
                    break;
                    
                case ACTION_MOVE:
                    me.StopSlide();
                    me.DoMove(Dir.values()[action.mValue]);
                    me.SendMoveMsg(Dir.values()[action.mValue]);
                    break;
                    
                case ACTION_UP:
                 // 做关于雪地的滑行
                    int  gridx = (int)(me.GetGrid().x);
                    int  gridy = (int)(me.GetGrid().y);
                    if (Map.SNOW == GameWorld.Instance().GetTerrain(gridx, gridy, Map.TERRAIN_TYPE_ALL) ||
                        Map.SNOW == GameWorld.Instance().GetTerrain(gridx , gridy + 1, Map.TERRAIN_TYPE_ALL) ||
                        Map.SNOW == GameWorld.Instance().GetTerrain(gridx + 1, gridy , Map.TERRAIN_TYPE_ALL) ||
                        Map.SNOW == GameWorld.Instance().GetTerrain(gridx + 1, gridy + 1, Map.TERRAIN_TYPE_ALL)) {
                        me.Slide();
                    } else {
                        me.SetDir(Dir.NONE);
                        if (BattleCity.smGameMode != GameMode.SINGLE) {
                            me.SendMoveMsg(Dir.NONE);    
                        }
                    }
                    break;
                    
                case ACTION_UP2:
                    me.SetDir(Dir.NONE);
                    if (BattleCity.smGameMode != GameMode.SINGLE) {
                        me.SendMoveMsg(Dir.NONE);
                    }
                    break;
                }     
            }
        }
        
        GameThread(SurfaceHolder holder) {
            mHolder = holder;
        }

        public void SetRunning(boolean bRunning) {
            mRunning = bRunning;
        }

        /** 游戏线程 */
        public void run()  {
            if (BattleCity.smGameMode != GameMode.SINGLE)
                mBluetoothMsg = new LockFreeQueue(2048);

            final GameWorld  gameWorld = GameWorld.Instance();
            Log.d(TAG, "Enter gamethread " + Thread.currentThread().getId());
            while (mRunning) {
                final long now = System.currentTimeMillis();
                Canvas  canvas = null;
                
                TimerManager.Instance().UpdateTimers(now);
                ProcessAction();
                ProcessBluetoothMsg();
                
                if (gameWorld.ShouldUpdate(now)) {
                    gameWorld.UpdateWorld();
                }
                else {
                    try {
                        canvas = mHolder.lockCanvas();
                        gameWorld.PaintWorld(canvas);
                    } catch(Exception exp) {
                        exp.printStackTrace();
                    } finally {
                        if (null != canvas) 
                            mHolder.unlockCanvasAndPost(canvas);
                    }

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
                }
            }

            Log.d(TAG, "Exit game thread");
        }
    }
    // 游戏线程定义结束
    
    public GameView(Context context, AttributeSet attr) {
        super(context, attr);
        smGameView = this;
        mHolder = this.getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        // 这里启动游戏线程
        GameWorld.Instance().Init();
        if (null == mThread)    mThread = new GameThread(mHolder);
        mThread.SetRunning(true);
        
        if (BattleCity.smGameMode != GameMode.SINGLE)
            Bluetooth.Instance().StartNetThread();
        
        mThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        
        if (BattleCity.smGameMode != GameMode.SINGLE) {
            Bluetooth.Instance().Cancel();
            Bluetooth.Instance().StopNetThread();
        }
        
        // 销毁线程    
        mThread.SetRunning(false);
        while (true) {
            try {
                mThread.join();
                mThread = null;
                break;
            } catch (InterruptedException e) {
                ;
            }
        }
    }
}
