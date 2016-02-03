package bert.young;

import java.io.InputStream;
import bert.young.Bluetooth.EnemyBornMsg;
import bert.young.Bluetooth.PlayerBornMsg;
import bert.young.Movable.Dir;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;


final class GameWorld {
    private static final String TAG = "GameWorld";

    /** 查找坦克  */
    Tank   FindObject(int id) {
        if (id == PlayerTank.PLAYER1 && BattleCity.smGameMode == GameMode.CLIENT)
            return mPartner;

        if (id == PlayerTank.PLAYER2 && BattleCity.smGameMode == GameMode.SERVER)
            return mPartner;

        return mEnemyMgr.FindEnemy(id);
    }
    
    /** Forwarding function */
    void  SetFrozen(boolean frozen) {
        mEnemyMgr.SetFrozen(frozen);
    }
    
    /** 活动中止时调用，这里值得考虑 */
    void  ResetPlayer() {
        mPlayer = null;
        mPartner= null;
    }

    /** Forwarding function */
    void ProtectHeadQuarters() {
        mMap.ProtectHeadQuarters();
    }
    
    
    private PlayerTank  mPlayer = null;
    private PlayerTank  mPartner = null; // 另一个双打朋友

    public  PlayerTank  GetMe() {
        return  mPlayer;
    }
    
    public  PlayerTank  GetPartner() {
        return  mPartner;
    }
    public  void  SetPartner(PlayerTank tank) {
        mPartner = null;
    }
    /** 处理PLAYER出生消息 */
    void OnBornPlayer(PlayerBornMsg msg) {
        mPartner = new PlayerTank();
        mPartner.Init(BattleCity.smGameMode == GameMode.CLIENT);
        mPartner.SetPos(msg.mX * GameWorld.StandardUnit(), 
                         msg.mY * GameWorld.StandardUnit());
        mPartner.SetFaceDir(Dir.Convert2Dir(msg.mFace));
        mPartner.mID = msg.mID;
        
        Log.d(TAG, "Create partner " + mPartner.GetPos().x + ", " + mPartner.GetPos().y);
    }
    
    /** 处理ENEMY出生消息 */
    boolean OnBornEnemy(EnemyBornMsg msg) {
        int id   = msg.mID;
        int x    = msg.mX;
        int y    = msg.mY;
        int type = msg.mType;
    
        MyResource.Assert(mEnemyMgr != null, "Why enemy mgr is null");
        Log.d(TAG, "Create enemy id = " + id);
        return mEnemyMgr.OnBornEnemy(id, x * GameWorld.StandardUnit(),
                                   y * GameWorld.StandardUnit(), type);
    }  
    
    static int   STEP = 0;
    public static int StandardUnit() {
        return  STEP / 4;
    }

    private final Rect mSrcRect = new Rect();
    private final Rect mDstRect = new Rect();
    
    /** 游戏区的大小 必须是正方形 */
    public  int GetSceneWidth() {
        return  mMap.GetSceneWidth();
    }
    
    public int GetTerrain(int gridx, int gridy, int tmask) {
        return mMap.GetTerrain(gridx, gridy, tmask);
    }
    public void ClearTerrain(int gridx, int gridy, int mask) {
        mMap.ClearTerrain(gridx, gridy, mask);
    }

    /** 图片的显示尺寸 */
    public int GetImgWidth() {
        return mMap.GetImgWidth();
    }
    
    /** 碰撞精度是图片大小的一半*/
    public int GetGridWidth() {
        return  mMap.GetGridWidth();
    }
    
    /** 唯一游戏世界 */
    private static GameWorld smInstance = new GameWorld();
    
    /** 全局访问点 */
    public  static GameWorld Instance() {
        return  smInstance;
    }

    /** 游戏状态 */
    enum GameState
    {
        NONE,
        BEGINSTAGE,
        PLAYING,
        PAUSED,
        ENDSTAGE,   // 瞬时状态，设置好数据
        ENDSTAGING, // 该状态持续两秒，画通关话语
        LOSE,

        MAXSTATE,
    };
    
    private GameState    mState = GameState.NONE;   
    private Headquarters mHead  = new Headquarters();
    public  boolean  HitHeadquarters(Movable pOther) {
        return mHead.Intersect(pOther);
    }
    public void  DestroyHeadquarters() {
        if (ObjectState.IsAlive(mHead.mState))
            mHead.SetState(ObjectState.EXPLODE1);
    }
    public boolean IsHeadquartersOk() {
        return ObjectState.NORMAL == mHead.GetState();
    }
    
    private  Bonus mBonus = null;
    public   void  SetBonus(Bonus  bonus) {
        mBonus = bonus;
    }
    
    /** 游戏当前循环次数 */
    private int  mLoopCnt = 0;
    public  int GetLoopCnt() {
        return   mLoopCnt;
    }
    
    /** 逻辑帧率 */
    public static final int FPS = 18;
    
    /** 游戏开始时间 */
    private long mStartTime;    // 应该在游戏开始时设置
    
    /** 游戏中断的时间 */
    //private long m_pauseTime = 0;
    
    /** 当前关卡数 */
    private int mStage      = 1;
    private static final int MAX_STAGE = 35;

    /** 画笔 */
    private Paint mPaint = new Paint();
    /** 边界画笔 */
    private Paint mBorderPaint = new Paint();
    
    /** 切换状态 */
    public void SetState(GameState newState) {
        mState = newState;
    }
    
    boolean IsPlaying() {
        return mState == GameState.PLAYING ||
               mState == GameState.PAUSED;
    }
 
    private Map  mMap;
    /** 初始化 */
    public boolean Init() {
        GameView  view = GameView.Instance();
        if (view.getWidth() != view.getHeight()) {
            Log.e(TAG, "You must assure view is square, width == height");
            return false;
        }

        mMap  = new Map();
        mMap.Init(view.getWidth());

        mStage = Misc.GetDefaultStage();
        if (mStage < 1)    mStage = 1;
        else if (mStage > MAX_STAGE)    mStage = MAX_STAGE;
        
        if (BattleCity.smGameMode != GameMode.SINGLE)
            mStage = 1;
        
        // !!!物体移动的单位,也是坦克身体嵌入的上限(不可达)
        STEP =  GetGridWidth() / 2;
        Log.d(TAG, "m_imgWidth = " + GetImgWidth());
        Log.d(TAG, "Grid size = " + GetGridWidth());
        Log.d(TAG, "STEP  = " + STEP);
        MyResource.Assert(STEP % 4 == 0, "Step must be 4s, wrong step = " + STEP);
        
        SetState(GameState.NONE);

        mPaint.setAntiAlias(true);
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setAlpha(200);
        mBorderPaint.setStrokeWidth(4);
        mBorderPaint.setColor(Color.DKGRAY);

        // 资源初始化
        if (!MyResource.Init(view.getResources())) {
            Log.e(TAG, "Can not init resources");
            return false;
        }

        return true;
    }

    EnemyManager   mEnemyMgr;
    
    /** 游戏结束 */
    void   GameOver() {
        Misc.PlaySound(AudioID.AUDIO_OVER);
        SetState(GameState.LOSE);
        
        TimerManager.Instance().AddTimer(new TimerManager.Timer(2500, 1){
            @Override
            boolean _OnTimer() {   // 回到主界面
                GameActivity.Instance().finish();
                return  false;
            }
        });
    }
    
    /** 每一关要受到对方的READY包才能开始*/
    public  void  StartPlay() {
        mLoopCnt   = 0;
        mStartTime = System.currentTimeMillis();
        mBonus = null;
        GameView.Instance().GetThread().ClearCmd();
        Misc.PlaySound(AudioID.AUDIO_OPENING);
        InputStream inFile = GameView.Instance().getResources().openRawResource(R.raw.stage01 + mStage - 1); 
        if (!mMap.LoadMap(inFile)) {
            Log.d(TAG, "Failed loadmap");
            MyResource.Assert(false, "Load map failed, Stage = " + mStage);  
        }
    
        mEnemyMgr = new EnemyManager();
        mEnemyMgr.Init(mStage); 
        SetFrozen(false);
        SetState(GameState.PLAYING);
        Log.d(TAG, "On Timer, start playing");
    }
    
    /** 决定是否执行逻辑帧 */
    public boolean ShouldUpdate(final long  now) {
        switch (mState) {
        case NONE:
            Log.d(TAG, "State none");
            SetState(GameState.BEGINSTAGE);
            mHead.Init();

            //新的一关，应该删除所有的TIMERS
            TimerManager.Instance().KillAll();
            mMap.ClearGrassInfo();
 
            // 持续2秒 进入游戏画面
            TimerManager.Instance().AddTimer(new TimerManager.Timer(1500, 1) {
                @Override
                boolean _OnTimer() {
                    if (null == mPlayer)
                        mPlayer = new PlayerTank();
            
                    GameMode mode = BattleCity.smGameMode;
                    if (!mPlayer.IsAlive()) {
                        mPlayer.Init(mode == GameMode.CLIENT);
                    } else {
                        mPlayer.Reset(mode == GameMode.CLIENT);
                    }
                
                    if (BattleCity.smGameMode == GameMode.SINGLE) {
                        StartPlay();
                    }
                    else {
                        Log.d(TAG, "Game mode " + BattleCity.smGameMode);
                        mPlayer.SendBornMsg();
                        // 对于服务器，询问 ARE YOU READY
                        // 对于客户端，等待服务器询问，并发送 I AM READY；START_PLAY()
                        // 服务器收到I AM READY，START_PLAY
                        if (BattleCity.smGameMode == GameMode.SERVER) {
                            Bluetooth.ServerReadyMsg msg = Bluetooth.Instance().new ServerReadyMsg();
                            Bluetooth.Instance().SendMessage(msg);
                            Log.d(TAG, "send server ready");
                        }
                    }

                    return false;
                }
            });

            return false;
            
        case BEGINSTAGE:
            return false;
            
        case PLAYING:
            return (now - mStartTime) * FPS > mLoopCnt * 1000;
            
        case PAUSED:
            // 画PAUSING 
            break;
            
        case ENDSTAGE:
            // 持续2秒 进入游戏画面
            TimerManager.Instance().AddTimer(new TimerManager.Timer(1500, 1) {
                @Override
                protected boolean _OnTimer() {
                    if (++ mStage > MAX_STAGE)
                        mStage = 1;

                    GameWorld.this.SetState(GameState.NONE);
                    Log.d(TAG, "On Timer, End stage over");
                    
                    // 关闭声音播放，新的一关开始了
                    AudioPool.Stop();
                    return   false;
                }
            });
            
            SetState(GameState.ENDSTAGING);

            break;
            
        case ENDSTAGING:
            break;
            
        case LOSE:
            // 画GAMEOVER，结束游戏
            break;
            
        default:
            break;
        }
        
        return false;
    }

    public   int  GetRemainEnemy() {
        if (null == mEnemyMgr)
            return 0;

        return  mEnemyMgr.GetRemainEnemy();
    }

    /** 游戏逻辑循环 */
    public void UpdateWorld() {
        switch (mState) {
        case PLAYING:
            ++ mLoopCnt;
            //  只让4个敌人跑出来
            if (mEnemyMgr.NoEnemy()) {
                // 过关了
                mState  = GameState.ENDSTAGE;
                break;
            }
            if (mEnemyMgr.CanBornEnemy(mLoopCnt)) {
                EnemyTank   curEnemy = mEnemyMgr.BornEnemy(mLoopCnt);
                MyResource.Assert(curEnemy != null, "Can not born enemy");
                
                switch (mEnemyMgr.GetBornPos()) {
                case LEFT:
                    curEnemy.SetPos(0, 0);
                    break;

                case CENTER:
                    curEnemy.SetPos((GameView.Instance().getWidth() - curEnemy.GetBodySize()) / 2, 0);
                    break;

                case RIGHT:
                    curEnemy.SetPos(GameView.Instance().getWidth() - curEnemy.GetBodySize(), 0);
                    break;

                default:
                    MyResource.Assert(false,  "Wrong born position");
                    break;
                }

                // 刷新敌人数目信息
                InfoView.Instance().postInvalidate();
                if (BattleCity.smGameMode == GameMode.SERVER)
                    curEnemy.SendBornMsg();
            }
            
            // 玩家更新
            if (null != mPlayer) {
                mPlayer.UpdateBullets();
                mPlayer.Update();
            }
            
            if (null != mPartner) {
                mPartner.UpdateBullets();
                mPartner.Update();
            }
        
            // 敌人更新
            mEnemyMgr.Update();
            
            // BONUS更新
            if (mBonus != null) {
                mBonus.Update();
            }
            
            // 总部更新
            mHead.Update();
        }
    }
    
    /** 绘制游戏世界 */
    public void PaintWorld(Canvas   canvas) {
        // 首先清除画布
        canvas.drawColor(Color.BLACK);

        switch (mState)  {
        case NONE:
            return;

        case BEGINSTAGE:
            canvas.drawColor(Color.GRAY);
            Paint  paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            paint.setTextSize(12 * StandardUnit());
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("STAGE " + (mStage), canvas.getHeight() / 2,
                    canvas.getWidth() / 2, paint);
            break;
            
        case PLAYING:
            //1 先画地形；但是要记住草地坐标，因为草地要画在坦克上面
            //2 画坦克和子弹；
            //3 画草地
            //4 画BONUS
            final int rawImgSize = MyResource.RawTankSize();
            final int tankImgType = 14; // 有14列坦克图片在前面
            
            // 1 画地形
            final Rect src = mSrcRect;
            final Rect dst = mDstRect;
            for (int i = 0; i < Map.GRID_CNT; ++ i) {
                for (int j = 0; j < Map.GRID_CNT; ++ j) {
                    final char info = mMap.GetGridInfo(i, j);
                    final int terrainType = (info & Map.TERRAIN_TYPE);
                    
                    if (terrainType == Map.EAGLE ||
                        terrainType == Map.NONE ||
                        terrainType == Map.GRASS)
                        continue;

                    // 这里不要画草地
                    if (Map.BLOCK != terrainType) { // 只有砖块需要将一张图细分成 4 * 4
                        // 对于其他来说，GRID就够了
                        src.left = (tankImgType + terrainType) * rawImgSize;
                        src.top = MyResource.RawTankSize();
                        src.right = src.left + rawImgSize / 2;
                        src.bottom = src.top + rawImgSize / 2;

                        dst.left = j * GetGridWidth();
                        dst.top = i * GetGridWidth();
                        dst.right = dst.left + GetGridWidth();
                        dst.bottom = dst.top + GetGridWidth();
                        
                        // 临时做一下水面闪烁,效果居然很好
                        if (Map.WATER == terrainType &&
                            (mLoopCnt / 5) % 2 == 0) {
                            src.left = (tankImgType + 0) * rawImgSize;
                            src.right = src.left + rawImgSize / 2;
                        }

                        canvas.drawBitmap(MyResource.GetSpirit(), src, dst,
                                mPaint);
                        continue;
                    }

                    // 按顺序画砖块的4个部分
                    if (0 != (info & Map.TERRAIN_TYPE_LEFTTOP)) {
                        src.left = (tankImgType + terrainType) * rawImgSize;
                        src.top = MyResource.RawTankSize();
                        src.right = src.left + rawImgSize / 4;
                        src.bottom = src.top + rawImgSize / 4;

                        dst.left = j * GetGridWidth();
                        dst.top = i * GetGridWidth();
                        dst.right = dst.left + GetGridWidth() / 2;
                        dst.bottom = dst.top + GetGridWidth() / 2;

                        canvas.drawBitmap(MyResource.GetSpirit(), src, dst,
                                mPaint);
                    }
                    if (0 != (info & Map.TERRAIN_TYPE_RIGHTTOP)) {
                        src.left = (tankImgType + terrainType) * rawImgSize
                                + rawImgSize / 4;
                        src.top = MyResource.RawTankSize();
                        src.right = src.left + rawImgSize / 4;
                        src.bottom = src.top + rawImgSize / 4;

                        dst.left = j * GetGridWidth() + GetGridWidth() / 2;
                        dst.top = i * GetGridWidth();
                        dst.right = dst.left + GetGridWidth() / 2;
                        dst.bottom = dst.top + GetGridWidth() / 2;

                        canvas.drawBitmap(MyResource.GetSpirit(), src, dst,
                                mPaint);
                    }
                    if (0 != (info & Map.TERRAIN_TYPE_LEFTBOTTOM)) {
                        src.left = (tankImgType + terrainType) * rawImgSize;
                        src.top = MyResource.RawTankSize() + rawImgSize / 4;
                        src.right = src.left + rawImgSize / 4;
                        src.bottom = src.top + rawImgSize / 4;

                        dst.left = j * GetGridWidth();
                        dst.top = i * GetGridWidth() + GetGridWidth() / 2;
                        dst.right = dst.left + GetGridWidth() / 2;
                        dst.bottom = dst.top + GetGridWidth() / 2;

                        canvas.drawBitmap(MyResource.GetSpirit(), src, dst,
                                mPaint);
                    }
                    if (0 != (info & Map.TERRAIN_TYPE_RIGHTBOTTOM)) {
                        src.left = (tankImgType + terrainType) * rawImgSize
                                + rawImgSize / 4;
                        src.top = MyResource.RawTankSize() + rawImgSize / 4;
                        src.right = src.left + rawImgSize / 4;
                        src.bottom = src.top + rawImgSize / 4;

                        dst.left = j * GetGridWidth() + GetGridWidth() / 2;
                        dst.top = i * GetGridWidth() + GetGridWidth() / 2;
                        dst.right = dst.left + GetGridWidth() / 2;
                        dst.bottom = dst.top + GetGridWidth() / 2;

                        canvas.drawBitmap(MyResource.GetSpirit(), src, dst,
                                mPaint);
                    }
                } // end for j
            } // end for i
            
            // 画总部
            mHead.Paint(canvas);
            
            // 2 画坦克以及子弹
            if (null != mPlayer) {
                mPlayer.Paint(canvas);
                mPlayer.RenderBullets(canvas);
            }
            
            if (null != mPartner) {
                mPartner.Paint(canvas);
                mPartner.RenderBullets(canvas);
            }

            // 3 画敌人
            mEnemyMgr.Render(canvas);

            // 画草地
            for (Point pos : mMap.GetGrassInfo()) {
                src.left = (tankImgType + Map.GRASS) * rawImgSize;
                src.top  = MyResource.RawTankSize();
                src.right  = src.left + rawImgSize / 2;
                src.bottom = src.top + rawImgSize / 2;
        
                dst.left = pos.y * GetGridWidth();
                dst.top  = pos.x * GetGridWidth();
                dst.right = dst.left + GetGridWidth() ;
                dst.bottom = dst.top + GetGridWidth();

                canvas.drawBitmap(MyResource.GetSpirit(), src, dst, mPaint);
            }
            
            // 画BONUS
            if (mBonus != null) {
                mBonus.Paint(canvas);
            }

            // 画边界
            canvas.drawLine(0, 0, GetSceneWidth(), 0, mBorderPaint);
            canvas.drawLine(0, 0, 0, GetSceneWidth(), mBorderPaint);
            canvas.drawLine(GetSceneWidth(), GetSceneWidth(), 0, GetSceneWidth(), mBorderPaint);
            canvas.drawLine(GetSceneWidth(), GetSceneWidth(), GetSceneWidth(), 0, mBorderPaint);
            // fall through

        case PAUSED:
            // if (paused)画PAUSING 字样
            break;
            
        case ENDSTAGING:
            // 画分数，恭喜过关
            mDstRect.left = 0;
            mDstRect.top  = 0;
            mDstRect.right = mDstRect.left + GetSceneWidth();
            mDstRect.bottom = mDstRect.top + GetSceneWidth();
            canvas.drawBitmap(MyResource.GetEndStage(), null, mDstRect, mPaint);

            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setTextSize(12 * StandardUnit());
            paint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText("STAGE " + mStage, canvas.getHeight() / 2,
                    40 * StandardUnit(), paint);
            break;
            
        case LOSE:
            // 画GAMEOVER，结束游戏
            mDstRect.left = GetSceneWidth() / 2 - MyResource.GetOver().getWidth() / 2;
            mDstRect.top  = GetSceneWidth() / 2 - MyResource.GetOver().getHeight() / 2;
            mDstRect.right = mDstRect.left +  MyResource.GetOver().getWidth() ;
            mDstRect.bottom = mDstRect.top +  MyResource.GetOver().getHeight() ;

            canvas.drawBitmap(MyResource.GetOver(), null, mDstRect, mPaint);
            
            // 画边界
            canvas.drawLine(0, 0, GetSceneWidth(), 0, mBorderPaint);
            canvas.drawLine(0, 0, 0, GetSceneWidth(), mBorderPaint);
            canvas.drawLine(GetSceneWidth(), GetSceneWidth(), 0, GetSceneWidth(), mBorderPaint);
            canvas.drawLine(GetSceneWidth(), GetSceneWidth(), GetSceneWidth(), 0, mBorderPaint);
            
            break;
            
        default:
            break;
        }
    }
    
    /** 与场景动态物件做碰撞检测 */
    boolean HitTest(Movable  pThis) {
        // 先尝试与所有敌人坦克以及子弹相撞
        if (mEnemyMgr.HitTest(pThis))
            return true;

        // 尝试与玩家相遇
        if (null != mPlayer && mPlayer.HitTestBullets(pThis))
            return true;
        
        if (null != mPartner && mPartner.HitTestBullets(pThis))
            return true;
        
        if (null != mPlayer && mPlayer.IsAlive()) {
            if (pThis.HitTest(mPlayer))
                return true;
        }
        
        if (null != mPartner && mPartner.IsAlive()) {
            if (pThis.HitTest(mPartner))
                return true;
        }

        return false;
    }
}
