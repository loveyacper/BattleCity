package bert.young;

import java.util.Random;
import bert.young.Bluetooth.BonusMsg;
import bert.young.Bluetooth.EnemyBornMsg;
import bert.young.Bluetooth.EnemyHurtMsg;
import bert.young.Bonus.BonusType;
import bert.young.BulletManager.BulletPower;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

final class EnemyTank extends Tank {
    /** 受伤回调 */
    @Override
    void  OnHurt() { 
        super.OnHurt();
        if (ShouldSync())  {
        	SendHurtMsg();
        }
    }
    
    /** 是否需要同步消息 */
    boolean ShouldSync() {
        return (BattleCity.smGameMode == GameMode.SERVER);
    }
    
    /** 发送出生消息 */
    void SendBornMsg() {
        EnemyBornMsg   msg = Bluetooth.Instance().new EnemyBornMsg();
        
        msg.mID = mID;
        msg.mX  = GetPos().x / GameWorld.StandardUnit();
        msg.mY  = GetPos().y / GameWorld.StandardUnit();
        msg.mType  = mType.ordinal();
        Bluetooth.Instance().SendMessage(msg);
    }
    
    /** 发送死亡消息 */
    void SendHurtMsg() {
        TimerManager.Instance().AddTimer(new TimerManager.Timer(150, 1) {
            boolean _OnTimer() {
                EnemyHurtMsg   msg = Bluetooth.Instance().new EnemyHurtMsg();
                msg.mID = mID;
                msg.mHP = (byte)mHP;
                Bluetooth.Instance().SendMessage(msg);
                return false;
            }
        });
    }

    private static final String TAG = "EnemyTank";

    private int     mLastChangeLoop;
    private Paint   mPaint = new Paint();
    private TankType   mType;
    private boolean    mRed = false; // 这个值来回切换，使得红色闪烁
    private boolean    IsRed() {
        return mType == TankType.REDNORMAL ||
               mType == TankType.REDFAST   ||
               mType == TankType.REDSMART  ||
               mType == TankType.REDSTRONG;
    }
    
    private final Rect  mSrcRect = new Rect();
    private final Rect  mDstRect = new Rect();
    private int   mScore;
    private int   mChangeDirInterval;
    
    private static Random  mRand = new Random(System.currentTimeMillis());
    private static int     smID  = PlayerTank.PLAYER2 + 1;
    
    EnemyTank() {
        super();
        mFaceDir = Dir.DOWN;
        mLastChangeLoop = 0;
        mScore = 0;
        mPaint.setAntiAlias(true);
    }

    boolean Init(Tank.TankType   type) {
        mID      = smID ++;
        mFaceDir = Dir.DOWN;
        mDir     = Dir.DOWN;
        mHP      = 1;
        mSpeed   = TANK_SPEED;
        mBulletMgr.SetSpeed(Bullet.BULLET_SPEED);
        mBulletMgr.SetPower(BulletPower.NORMAL);
        mBulletMgr.SetConcurrent(1);
        mType  = type;
        mChangeDirInterval = 5 * GameWorld.FPS / 2;
        
        switch (mType) {
        case NORMAL:
        case REDNORMAL:
            mScore = 100;
            break;
            
        case FAST:
        case REDFAST:
            mSpeed = 3 * GameWorld.StandardUnit();
            mScore = 200;
            mChangeDirInterval = 5 * GameWorld.FPS / 3;
            break;
            
        case SMART:
        case REDSMART:
            mBulletMgr.SetSpeed(Bullet.FAST_SPEED);
            mScore = 300;
            break;
            
        case STRONG:
        case REDSTRONG:
            mHP = 4;
            mScore = 400;
            break;
        }

        SetState(ObjectState.BORN1);

        if (IsRed()) {
            mRed = true;
            //GameWorld.Instance().SetBonus(null);     // 之前的奖励消失            
            TimerManager.Instance().AddTimer(new TimerManager.Timer(333, -1) {
                @Override
                boolean _OnTimer() {
                    EnemyTank.this.mRed = !EnemyTank.this.mRed;
                    return   EnemyTank.this.IsValid();
                }
            });
        }

        return  true;
    }
    
    void Update() {
        if (!IsAlive())
            return;
        
        MyResource.Assert(Dir.NONE != mDir, "Update enemy dir NONE");

        if (GameWorld.Instance().GetLoopCnt() > mLastChangeLoop + mChangeDirInterval) {
            _RandChangeDir(16);
        }
        
        super.UpdateMove();     // MOVE可能会改变状态，所以下面还要判断
        if (!IsAlive()) 
            return;

        if (BattleCity.smGameMode != GameMode.CLIENT &&
           mRand.nextInt(100) < 9) {
            if (super.Fire(mPos.x, mPos.y, mFaceDir) &&
                ShouldSync()) {
                SendFireMsg(mPos.x, mPos.y, mFaceDir);
            }
        }
        
    }
    
    public void Paint(Canvas canvas) {
        if (!IsValid())
            return;

        final int rawTankSize = MyResource.RawTankSize();
        final GameWorld rWorld = GameWorld.Instance();
        final Rect  src = mSrcRect;
        final Rect  dst = mDstRect;
        
        if (mState.equals(ObjectState.NORMAL))  {
            if (mStateChanged) {
                Log.d(TAG, "Enemy enter alive");
                mStateChanged = false;
            }
            // 画坦克
            final int typeSeq = mType.ordinal();
            final int baseSeq = TankType.NORMAL.ordinal();
            src.left = (typeSeq - baseSeq + 4) * rawTankSize;
            src.top  = 0;
            src.right  = src.left + rawTankSize;
            src.bottom = src.top  + rawTankSize;

            if (IsRed()) {
                if (!mRed) {
                    src.left = (typeSeq - 1 - baseSeq + 4) * rawTankSize;
                    src.right  = src.left + rawTankSize;
                }
            } 
                
            // 胖坦克特殊对待 
            if (mType == TankType.STRONG || (mType == TankType.REDSTRONG && !mRed)) {
                final int offset = IsRed() ? -1 : 0;
                
                switch (mHP) {
                case 4:
                    src.left = (typeSeq - baseSeq + 4 + 2 + offset) * rawTankSize;
                    break;
                    
                case 3:
                case 2:
                    src.left = (typeSeq - baseSeq + 4 + 3 + offset) * rawTankSize;
                    break;
                        
                case 1:
                    src.left = (typeSeq - baseSeq + 4 + 0 + offset) * rawTankSize;
                    break;
                    
                default:
                    MyResource.Assert(false, "Dead enemy");
                    break;
                }
                
                src.right  = src.left + rawTankSize;
            }
    
            switch (mFaceDir)     {
            case UP:
                canvas.drawBitmap(MyResource.GetSpirit(), src, GetBodyRect(), mPaint);
                break;
                
            case DOWN:
                canvas.save();
                canvas.rotate(90 * 2, mPos.x + rWorld.GetGridWidth(), mPos.y +  rWorld.GetGridWidth());
                canvas.drawBitmap(MyResource.GetSpirit(), src, GetBodyRect(), mPaint);
                canvas.restore();
                    
                break;

            case LEFT:
                canvas.save();
                canvas.rotate(90 * 3, mPos.x + rWorld.GetGridWidth(), mPos.y +  rWorld.GetGridWidth());
                canvas.drawBitmap(MyResource.GetSpirit(), src, GetBodyRect(), mPaint);
                canvas.restore();
                break;
                
            case RIGHT:
                canvas.save();
                canvas.rotate(90 * 1, mPos.x + rWorld.GetGridWidth(), mPos.y +  rWorld.GetGridWidth());
                canvas.drawBitmap(MyResource.GetSpirit(), src, GetBodyRect(), mPaint);
                canvas.restore();
                break;

            default:
                Log.d(TAG, "Render with NONE direction");
                MyResource.Assert(false, "Render enemy with NONE direction?");
                break;
            }
        } else {
            final int pixelOffsetX = 20 * MyResource.RawTankSize();
            final int explodePixelOffsetX = 29 * MyResource.RawTankSize();
            
            dst.left = mPos.x;    
            dst.top  = mPos.y;
            switch (GetState()) {
            case BORN1:
            case BORN2:
            case BORN3:
                if (mStateChanged) {                     
                    mStateChanged = false;
                    TimerManager.Instance().AddTimer(new TimerManager.Timer(111, 1) {        
                        @Override
                        boolean _OnTimer() {
                            if (mState == ObjectState.BORN1 ||
                                mState == ObjectState.BORN2 ||
                                mState == ObjectState.BORN3)
                                EnemyTank.this.SetState(ObjectState.values()[EnemyTank.this.GetState().ordinal() + 1]);
                            
                            return false;
                        }
                    });
                }
                
                src.left = pixelOffsetX + (ObjectState.BORN4.ordinal() - GetState().ordinal()) * MyResource.RawBornSize();    
                src.top  = 0;
                src.right  = src.left + MyResource.RawBornSize();
                src.bottom = src.top  + MyResource.RawBornSize();
                
                dst.right = dst.left + GetBodySize();
                dst.bottom = dst.top + GetBodySize();

                canvas.drawBitmap(MyResource.GetSpirit(), src, dst, mPaint);
                break;
    
             case BORN4:
                 if (mStateChanged) { 
                     mStateChanged = false;    
                     TimerManager.Instance().AddTimer(new TimerManager.Timer(111, 1) {        
                         @Override        
                         boolean _OnTimer() {
                             if (mState == ObjectState.BORN4)
                                 SetState(ObjectState.NORMAL);

                             return false;        
                         }
                     });    
                 }

                src.left = pixelOffsetX + 0 * MyResource.RawBornSize();    
                src.top  = 0;
                src.right  = src.left + MyResource.RawBornSize();
                src.bottom = src.top  + MyResource.RawBornSize();

                dst.right = dst.left + GetBodySize();
                dst.bottom = dst.top + GetBodySize();

                canvas.drawBitmap(MyResource.GetSpirit(), src, dst, mPaint);
                break;

            
             case EXPLODE1:
             case EXPLODE2:
             case EXPLODE3:
             case EXPLODE4:
                if (mStateChanged) {
                    mStateChanged = false;
                    TimerManager.Instance().AddTimer(new TimerManager.Timer(120, 1) {
                        @Override
                        boolean _OnTimer() {
                            if (mState == ObjectState.EXPLODE1 ||
                                mState == ObjectState.EXPLODE2 ||
                                mState == ObjectState.EXPLODE3 ||
                                mState == ObjectState.EXPLODE4)                           
                                EnemyTank.this.SetState(ObjectState.values()[EnemyTank.this.GetState().ordinal() + 1]);
                            
                            return false;
                        }
                    });
                }
                    
                 // 爆炸图是普通图的1.5倍大小
                src.left = explodePixelOffsetX + (GetState().ordinal() - ObjectState.EXPLODE1.ordinal()) * MyResource.RawExplodeSize();
                src.top  = 0;
                src.right  = src.left + MyResource.RawExplodeSize();
                src.bottom = src.top  + MyResource.RawExplodeSize();

                dst.left -= GetBodySize() / 4;
                dst.top  -= GetBodySize() / 4;
                dst.right = dst.left + 3 * GetBodySize() / 2;
                dst.bottom = dst.top + 3 * GetBodySize() / 2;

                canvas.drawBitmap(MyResource.GetSpirit(), src, dst, mPaint);
                break;

             case EXPLODE5:
                    if (mStateChanged) {
                        mStateChanged = false;
                        TimerManager.Instance().AddTimer(new TimerManager.Timer(150, 1) {
                            @Override
                            boolean _OnTimer() {
                                if (mState != ObjectState.EXPLODE5)
                                    return false;

                                GameWorld  rWorld = GameWorld.Instance();
                                EnemyTank.this.SetState(ObjectState.INVALID);
                                
                                // 给玩家+分数
                                PlayerTank me = rWorld.GetMe();
                                if (null != me) {
                                    me.mScore += EnemyTank.this.mScore;
                                    if (me.mScore >= Bonus.SCORE_PER_LIFE) {
                                        me.mScore -= Bonus.SCORE_PER_LIFE;
                                        me.SetLife(me.GetLife() + 1);
                                        Misc.PlaySound(AudioID.AUDIO_LIFE);
                                    }
                                    
                                    InfoView.Instance().postInvalidate();
                                }
                                
                                if (EnemyTank.this.IsRed() &&
                                    BattleCity.smGameMode != GameMode.CLIENT) {
                                    final int type = rWorld.GetLoopCnt() % 6;
                                    Bonus bonus = Bonus.CreateBonus(BonusType.values()[type]);

                                    int  nTry = 0;
                                    int  x = 1, y = 1;
                                    do {
                                        mRand = new Random(System.currentTimeMillis() * (13*x + 11*y) / 7);
                                        x = mRand.nextInt(Map.GRID_CNT - 1) * rWorld.GetGridWidth();
                                        y = mRand.nextInt(Map.GRID_CNT - 1) * rWorld.GetGridWidth();
                                        bonus.SetPos(x, y);
                                    } while(!bonus.ValidPos() && nTry ++ < 4);

                                    rWorld.SetBonus(bonus);
                                    if (ShouldSync()) {
                                        BonusMsg msg = Bluetooth.Instance().new BonusMsg();
                                        msg.m_type = (byte)type;
                                        msg.m_x = x / GameWorld.StandardUnit();
                                        msg.m_y = y / GameWorld.StandardUnit();
                                        Bluetooth.Instance().SendMessage(msg);
                                    }
                                }
                                return false;
                            }
                        });
                    }

                    src.left = explodePixelOffsetX + 4 * MyResource.RawExplodeSize();
                    src.top  = 0;
                    src.right  = src.left + MyResource.RawExplodeSize();
                    src.bottom = src.top  + MyResource.RawExplodeSize();

                    dst.left -= GetBodySize() / 4;
                    dst.top  -= GetBodySize() / 4;
                    dst.right  = dst.left + 3 * GetBodySize() / 2;
                    dst.bottom = dst.top + 3 * GetBodySize() / 2;

                    canvas.drawBitmap(MyResource.GetSpirit(), src, dst, mPaint);
                    break;
    
             default:
                 MyResource.Assert(false, "Enemy render error state = " + GetState().ordinal());
                 break;
            }
        }
    }
    
    @Override
    ObjectType GetType() {
        return ObjectType.ENEMY; 
    }

    @Override
    protected boolean _TryMove(Pos target) {
        boolean bCanGo = super._TryMove(target);
        if (!bCanGo && IsAlive()) {
            // 不能走的时候，有10%的几率开火
            GameMode mode = BattleCity.smGameMode;
            if (mode != GameMode.CLIENT &&
                mRand.nextInt(128) < 13) {
                if (super.Fire(mPos.x, mPos.y, mFaceDir)) {
                    if (ShouldSync())
                        SendFireMsg(mPos.x, mPos.y, mFaceDir);
                    return false;
                }
            }
            
            // 开火失败，下面换方向
            _RandChangeDir(22);
            return false;
        }

        return true;
    }

    /** 一定概率换方向 */
    private void  _RandChangeDir(int percent) {
        if (BattleCity.smGameMode == GameMode.CLIENT)
            return;
        
        if (mRand.nextInt(100) >= percent)
            return;

        final GameWorld rWorld = GameWorld.Instance();
        mLastChangeLoop = rWorld.GetLoopCnt();
        final Dir oldDir = mDir;
        int   nTry = 0;

        do {
            mDir = Dir.Convert2Dir(mRand.nextInt(4));
        } while (nTry ++ < 3 && mDir == oldDir);
        
        MyResource.Assert(Dir.NONE != mDir, "rand change to NONE");
        
        final int oldX = mPos.x;
        final int oldY = mPos.y;
        final Pos grid = GetGrid();

        if (mDir != oldDir) {            
            switch (mDir) {
            case UP:
            case DOWN:
                if (oldDir == Dir.LEFT || oldDir == Dir.RIGHT || oldDir == Dir.NONE) {
                    final int leftBorderX = (int)grid.y * rWorld.GetGridWidth();

                    if (mPos.x < leftBorderX + rWorld.GetGridWidth() / 2) { // 尝试向左偏移
                        SetPos(leftBorderX, mPos.y);
                    }
                    else {
                        SetPos(leftBorderX + rWorld.GetGridWidth(), mPos.y);
                    }
                }
                break;

            case LEFT:
            case RIGHT:
                if (oldDir == Dir.UP || oldDir == Dir.DOWN || oldDir == Dir.NONE) {        
                    final int leftBorderY = (int)grid.x * rWorld.GetGridWidth();

                    if (mPos.y < leftBorderY + rWorld.GetGridWidth() / 2) { // 尝试向上偏移
                        SetPos(mPos.x, leftBorderY);
                    }
                    else {
                        SetPos(mPos.x, leftBorderY + rWorld.GetGridWidth());
                    }
                }
                break;

            default:
                MyResource.Assert(false, "Enemy tank NONE dir");
            }
            
            if (GameWorld.Instance().HitTest(this)) {
                if (IsAlive()) {
                    SetPos(oldX, oldY);
                    SetDir(Dir.Convert2Dir(mRand.nextInt(4)));
                }
            } else {
                mFaceDir = mDir;
            }
        }
        
        if (mDir != oldDir && ShouldSync())
            SendMoveMsg(mDir);
    }
};
