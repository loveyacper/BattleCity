package bert.young;

import bert.young.Bluetooth.PlayerBornMsg;
import bert.young.BulletManager.BulletPower;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

class PlayerTank extends Tank {
    private static final String TAG = "PlayerTank";
    public  static final int   PLAYER1 = 1;
    public  static final int   PLAYER2 = 2;
    
    private Paint   mPaint   = new Paint();
    private Rect    mSrcRect = new Rect();
    private Rect    mDstRect = new Rect();
    private int     mLifeCnt;
    int             mScore;
    
    final int  GetLife() {
        return  mLifeCnt;
    }

    final void SetLife(int life) {
        mLifeCnt = life;
    }
    
    /** 是否应该同步消息 */
    boolean ShouldSync() {
        final GameMode mode = BattleCity.smGameMode;
        if (mode == GameMode.SERVER && mID == PLAYER1)
            return true;
        if (mode == GameMode.CLIENT && mID == PLAYER2)
            return true;
            
        return false;
    }
    
    /** 护身定时器 */
    private TimerManager.Timer m_protectTimer = CreateProtectTimer(6000);

    private TimerManager.Timer CreateProtectTimer(int millseconds) {
        if (millseconds < 1000) {
            millseconds = 1000;
        }

        return new TimerManager.Timer(160, millseconds / 160) {
            @Override
            boolean _OnTimer() {
                if (ObjectState.PROTECT1 == PlayerTank.this.GetState()) {
                    PlayerTank.this.SetState(ObjectState.PROTECT2);
                } else if (ObjectState.PROTECT2 == PlayerTank.this.GetState()){
                    PlayerTank.this.SetState(ObjectState.PROTECT1);
                } else {
                    return  false;
                }
                
                return  true;
            }
            
            @Override
            void  _OnTimerEnd() {
                PlayerTank.this.SetState(ObjectState.NORMAL);
                Log.d(TAG, "Enter alive state");
            }
        };
    }
 
     /** 该状态将可控制 */
    final boolean   IsControllable() {
        return  IsAlive() && GameWorld.Instance().IsHeadquartersOk();
    }

    public PlayerTank() {
        super();
        mLifeCnt = Misc.GetDefaultLife();
        mSpeed   = TANK_SPEED;
        mScore   = 0;
        mID      = BattleCity.smGameMode == GameMode.SERVER ? PLAYER1 : PLAYER2;
    }
    

    void SendBornMsg() {
        PlayerBornMsg   msg = Bluetooth.Instance().new PlayerBornMsg();

        msg.mX = mPos.x / GameWorld.StandardUnit();
        msg.mY = mPos.y / GameWorld.StandardUnit();
        msg.mFace = (byte)Dir.UP.ordinal();
        Bluetooth.Instance().SendMessage(msg);
    }
    
    private  TimerManager.Timer  mSlideTimer = new TimerManager.Timer(450, 1) {
        @Override
         boolean _OnTimer() {
            PlayerTank.this.SetDir(Dir.NONE);
            return  false;
        }
    };

    /** 滑行 */
    public  void Slide() {
        if (TimerManager.Instance().IsWorking(mSlideTimer))
            return;

        mSlideTimer.SetRemainCnt(1);
        TimerManager.Instance().AddTimer(mSlideTimer);
    }
    
    public  void StopSlide() {
        TimerManager.Instance().KillTimer(mSlideTimer);
    }

    final  boolean  Init(boolean isClient) {
        Reset(isClient);
        mBulletMgr.Reset();
        -- mLifeCnt;
        
        return true;
    }
    
    /** 重生或过关都要调用 */
    final  void Reset(boolean isClient) {
        mBulletMgr.ClearBullets();
        mHP    = 1;
        mFaceDir = Dir.UP;
        mDir   = Dir.NONE;
        
        final GameWorld rWorld = GameWorld.Instance();
        
        if (isClient) {
            SetPos(rWorld.GetGridWidth() * 2 * 8, rWorld.GetGridWidth() * 2 * (Map.IMAGE_NUM - 1));
        }
        else {
            SetPos(rWorld.GetGridWidth() * 2 * 4, rWorld.GetGridWidth() * 2 * (Map.IMAGE_NUM - 1));
        }

        SetState(ObjectState.BORN1);
    }
    
    public  void God(int  millseconds) {
        if (m_protectTimer != null)
            TimerManager.Instance().KillTimer(m_protectTimer);

        SetState(ObjectState.PROTECT1);
        m_protectTimer = CreateProtectTimer(millseconds);
        TimerManager.Instance().AddTimer(m_protectTimer);
    }

    @Override
     void  OnHurt() {
        super.OnHurt();
        Misc.Vibrate(300);
    }

    void    Update() {
        if (!GameWorld.Instance().IsHeadquartersOk())
            return;

        if (IsAlive())  
            super.UpdateMove();
    }
    
    void    Paint(Canvas canvas) {     
        if (!IsValid())
            return;
        
        final int rawTankSize = MyResource.RawTankSize();
        final GameWorld rWorld = GameWorld.Instance();

        Bitmap bmp = null;
        if ((BattleCity.smGameMode == GameMode.CLIENT && this == rWorld.GetMe()) ||
             BattleCity.smGameMode == GameMode.SERVER && this == rWorld.GetPartner())
            bmp = MyResource.GetPartner();
        else 
            bmp = MyResource.GetSpirit();

        if (ObjectState.IsAlive(mState))  {
            int  imgIdx = 0;
            if (mBulletMgr.GetPower() == BulletPower.BOOST) {
                imgIdx = 3;
            } else if (mBulletMgr.GetConcurrent() == 2) {
                imgIdx = 2;
            } else if (mBulletMgr.GetSpeed() == Bullet.FAST_SPEED) {
                imgIdx = 1;
            }
            switch (mFaceDir)     {
            case UP:
                mSrcRect.left = imgIdx * rawTankSize;
                mSrcRect.top  = 0;
                mSrcRect.right  = mSrcRect.left + rawTankSize;
                mSrcRect.bottom = mSrcRect.top  + rawTankSize;

                mDstRect.left = mPos.x;
                mDstRect.top  = mPos.y;
                mDstRect.right = mDstRect.left + GetBodySize();
                mDstRect.bottom = mDstRect.top + GetBodySize();
                
                canvas.drawBitmap(bmp, mSrcRect, mDstRect, mPaint);
                break;
                
            case DOWN:
            case LEFT:
            case RIGHT:              
                int  coef = 0;
                if (mFaceDir == Dir.DOWN) {
                    coef = 2;
                } else if (mFaceDir == Dir.LEFT) {
                    coef = 3;
                } else if (mFaceDir == Dir.RIGHT) {
                    coef = 1;
                }
                
                canvas.save();
                canvas.rotate(90 * coef, mPos.x + GetBodySize() / 2, mPos.y +  GetBodySize() / 2);
                
                mSrcRect.left = imgIdx * rawTankSize;
                mSrcRect.top  = 0;
                mSrcRect.right  = mSrcRect.left + rawTankSize;
                mSrcRect.bottom = mSrcRect.top  + rawTankSize;

                mDstRect.left = mPos.x;
                mDstRect.top  = mPos.y;
                mDstRect.right = mDstRect.left + GetBodySize();
                mDstRect.bottom = mDstRect.top + GetBodySize();

                canvas.drawBitmap(bmp, mSrcRect, mDstRect, mPaint);
                canvas.restore();
                break;

            default:
                assert false : "Render player with no direction?";
                break;
            }
        }

        final int explodeSize = MyResource.RawExplodeSize();
        final int explodePixelOffsetX = 29 * MyResource.RawTankSize();
        final int tankTypeNum = 14; // 14列坦克图片
        final int terrainTypeNum = 8; // 8列地形图片
        mDstRect.left = mPos.x;
        mDstRect.top  = mPos.y;
        
        final int pixelOffsetX = 20 * MyResource.RawTankSize();
        
        switch (GetState())  {
        case BORN1:
        case BORN2:
        case BORN3:
            if (mStateChanged) {                     
                mStateChanged = false;
                TimerManager.Instance().AddTimer(new TimerManager.Timer(120, 1) {        
                    @Override
                     boolean _OnTimer() {        
                        if (mState == ObjectState.BORN1 ||
                            mState == ObjectState.BORN2 ||
                            mState == ObjectState.BORN3)
                            PlayerTank.this.SetState(ObjectState.values()[GetState().ordinal() + 1]);
                        return false;
                    }
                });
            }
            
            mSrcRect.left = pixelOffsetX + (ObjectState.BORN4.ordinal() - GetState().ordinal()) * MyResource.RawBornSize();    
            mSrcRect.top  = 0;
            mSrcRect.right  = mSrcRect.left + MyResource.RawBornSize();
            mSrcRect.bottom = mSrcRect.top  + MyResource.RawBornSize();
            
            mDstRect.right = mDstRect.left + GetBodySize();
            mDstRect.bottom = mDstRect.top + GetBodySize();

            canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
            break;

         case BORN4:
             if (mStateChanged) { 
                 mStateChanged = false;    
                 TimerManager.Instance().AddTimer(new TimerManager.Timer(120, 1) {        
                     @Override        
                      boolean _OnTimer() {
                         if (mState == ObjectState.BORN4)
                             PlayerTank.this.God(6000);
                         return   false;
                      }
                 });    
             }

            mSrcRect.left = pixelOffsetX + 0 * MyResource.RawBornSize();    
            mSrcRect.top  = 0;
            mSrcRect.right  = mSrcRect.left + MyResource.RawBornSize();
            mSrcRect.bottom = mSrcRect.top  + MyResource.RawBornSize();

            mDstRect.right = mDstRect.left + GetBodySize();
            mDstRect.bottom = mDstRect.top + GetBodySize();

            canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
            break;

         case PROTECT1:
             mSrcRect.left = (tankTypeNum + terrainTypeNum) * rawTankSize;
             mSrcRect.top  = rawTankSize; // 保护图在第2排
             mSrcRect.right  = mSrcRect.left + rawTankSize;
             mSrcRect.bottom = mSrcRect.top  + rawTankSize;

             mDstRect.right = mDstRect.left + 2 * rWorld.GetGridWidth();
             mDstRect.bottom = mDstRect.top + 2 * rWorld.GetGridWidth();
                
             canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
             break;
                
         case PROTECT2:
             mSrcRect.left = (tankTypeNum + terrainTypeNum + 1) * rawTankSize;
             mSrcRect.top  = rawTankSize; // 保护图在第2排
             mSrcRect.right  = mSrcRect.left + rawTankSize;
             mSrcRect.bottom = mSrcRect.top  + rawTankSize;

             mDstRect.right = mDstRect.left + 2 * rWorld.GetGridWidth();
             mDstRect.bottom = mDstRect.top + 2 * rWorld.GetGridWidth();

             canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
             break;
             // 爆炸 
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
                            PlayerTank.this.SetState(ObjectState.values()[GetState().ordinal() + 1]);
                        else
                            Log.e(TAG, "Play should be explode1234, but wrong state is " + mState);
                        
                        return false;
                    }
                 });   
             }

             // 爆炸图是普通图的1.5倍大小
             mSrcRect.left = explodePixelOffsetX + (GetState().ordinal() - ObjectState.EXPLODE1.ordinal()) * explodeSize;
             mSrcRect.top  = 0;
             mSrcRect.right  = mSrcRect.left + explodeSize;
             mSrcRect.bottom = mSrcRect.top  + explodeSize;

             mDstRect.left -= GetBodySize() / 4;
             mDstRect.top  -= GetBodySize() / 4;
             mDstRect.right = mDstRect.left + 3 * GetBodySize() / 2;
             mDstRect.bottom = mDstRect.top + 3 * GetBodySize() / 2;

             canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
             break;
   
         case EXPLODE5: 
             if (mStateChanged) {
                 mStateChanged = false;
                 TimerManager.Instance().AddTimer(new TimerManager.Timer(250, 1) {
                     @Override
                     boolean _OnTimer() { 
                         if (!ShouldSync() &&
                              BattleCity.smGameMode != GameMode.SINGLE) {
                             if (mState == ObjectState.EXPLODE5)
                                 SetState(ObjectState.INVALID);
                             return false;
                         }
                         
                         // 让玩家重生
                         int  remainLife = PlayerTank.this.GetLife();
                         if (remainLife > 0) {
                             final boolean isClient = BattleCity.smGameMode == GameMode.CLIENT;
                             PlayerTank.this.Init(isClient);
                             InfoView.Instance().postInvalidate();
                             if (ShouldSync())
                                 PlayerTank.this.SendBornMsg();   
                         } else {
                             GameWorld.Instance().GameOver();
                         }
 
                         return   false;
                     }
                 });   
             }
   
             mSrcRect.left = explodePixelOffsetX + 4 * explodeSize;
             mSrcRect.top  = 0;
             mSrcRect.right  = mSrcRect.left + explodeSize;
             mSrcRect.bottom = mSrcRect.top  + explodeSize;

             mDstRect.left -= GetBodySize() / 4;
             mDstRect.top  -= GetBodySize() / 4;
             mDstRect.right  = mDstRect.left + 3 * GetBodySize() / 2;
             mDstRect.bottom = mDstRect.top + 3 * GetBodySize() / 2;

             canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
             break;
   
         default:
             MyResource.Assert(mState.equals(ObjectState.NORMAL), "Wrong player state " + mState);
             break;  
        }
    }
    
    /** 获取物件类型 */
    ObjectType GetType() {
        return ObjectType.PLAYER; 
    }
    
    /** 设置子弹速度 */
    final void BoostBullet() {
        if (mBulletMgr.GetPower() == BulletPower.BOOST) {
            return;
        }
        
        final int oldSpeed = mBulletMgr.GetSpeed();
        if (oldSpeed == Bullet.BULLET_SPEED) {
            mBulletMgr.SetSpeed(Bullet.FAST_SPEED);
        }
        else {
            if (mBulletMgr.GetConcurrent() == 1)
                mBulletMgr.SetConcurrent(2);
            else
                mBulletMgr.SetPower(BulletPower.BOOST);
        }
    }
};
