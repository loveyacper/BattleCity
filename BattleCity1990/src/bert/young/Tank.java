package bert.young;


import bert.young.Bluetooth.FireMsg;
import bert.young.Bluetooth.MoveMsg;
import android.graphics.Canvas;

/*
敌人坦克的参数：血量，子弹能力，子弹数量，移动速度，是否为红坦克
图标序号
*/

abstract class Tank extends Movable  {
    
    void SendMoveMsg(Dir  dir) {
        MoveMsg   msg = Bluetooth.Instance().new MoveMsg();
        
        msg.mID  = mID;
        msg.mX   = mPos.x / GameWorld.StandardUnit();
        msg.mY   = mPos.y / GameWorld.StandardUnit();
        msg.mDir = (byte)dir.ordinal();
        Bluetooth.Instance().SendMessage(msg);
    }
    
    void SendFireMsg(int x, int y, Dir dir) {
        FireMsg  msg = Bluetooth.Instance().new FireMsg();
        
        msg.mID  = mID;
        msg.mX   = x / GameWorld.StandardUnit();
        msg.mY   = y / GameWorld.StandardUnit();
        msg.mDir = (byte)dir.ordinal();
        Bluetooth.Instance().SendMessage(msg);
    }
    
    private static final String TAG = "Tank";
    
    /** 坦克身体宽度 正方形 是GRID的两倍 */
    int GetBodySize() {
        return 2 * GameWorld.Instance().GetGridWidth();
    }
    
    enum  TankType {
        NORMAL,
        REDNORMAL,
        
        FAST,
        REDFAST,
        
        SMART,
        REDSMART,
        
        STRONG,
        REDSTRONG,

        MAX,
    }
    
    @Override
    void  OnHurt() { 
        MyResource.Assert(mHP > 0, "Dead tank on hurt, mHP = " + mHP);

        -- mHP;
            
        switch (mHP) {
        case 0:
            Misc.PlaySound(AudioID.AUDIO_BLAST);
            SetState(ObjectState.EXPLODE1);
            break;

            // 对于大坦克，一次打不死
        case 1:
        case 2:
        case 3:
            Misc.PlaySound(AudioID.AUDIO_HIT);
            break;
            
        default:
            break;
            
        }
    }
    
    final boolean  Fire(int fireX, int fireY, Dir dir) {
        if (!mBulletMgr.CanFire()) {
            return   false;
        }

        final Bullet bullet = mBulletMgr.GetBullet();
        if (bullet == null) {
            return  false;
        }
        
        bullet.Init(this);
        bullet.SetSpeed(mBulletMgr.GetSpeed());
        bullet.SetDir(dir);
        
        final int bulletSize = bullet.GetBodySize();
        
        switch (dir) {
        case UP:
            bullet.SetPos(fireX + (GetBodySize() - bulletSize) / 2, fireY - bulletSize);
            break;

        case DOWN:
            bullet.SetPos(fireX + (GetBodySize() - bulletSize) / 2, fireY + GetBodySize());
            break;

        case LEFT:
            bullet.SetPos(fireX - bulletSize, fireY + (GetBodySize() - bulletSize) / 2);
            break;

        case RIGHT:
            bullet.SetPos(fireX + GetBodySize(), fireY + (GetBodySize() - bulletSize) / 2);
            break;

        default:
            MyResource.Assert(false, "Must have a face direction!");    
        }

        if (!bullet.CheckTerrain(bullet.GetPos()))   // 子弹当前位置要判断，因为它可能直接出生在了砖块上
            bullet.OnHurt();
        return  true;
    }

    final boolean  HitTestBullets(Movable pObj) {
        if (pObj == this) {
            return  false;
        }
        
        return  mBulletMgr.HitTest(pObj);
    }
    
    /** 更新我发出的子弹 */
    final void  UpdateBullets() {
        mBulletMgr.Update();    
    }
   
    /** 画我发出的子弹 */
    final void  RenderBullets(Canvas canvas) {
        mBulletMgr.Render(canvas);
    }

    /** 尝试移动到目标点 */
    @Override
     boolean _TryMove(final Pos target) {
        if (!super._TryMove(target)) {
            return false;
        }

        final GameWorld rWorld = GameWorld.Instance();

        int targetTerrainType1 = Map.INVALID;
        int targetTerrainType2 = Map.INVALID;

        int destGridLTy = target.x / rWorld.GetGridWidth(),
            destGridLTx = target.y / rWorld.GetGridWidth();    // LEFT TOP

        int destGridRBy = (target.x + GetBodySize() - 1) / rWorld.GetGridWidth(),
            destGridRBx = (target.y + GetBodySize() - 1) / rWorld.GetGridWidth();    // RIGHT BOTTOM

        int destGridLBy = destGridLTy;
        int destGridLBx = destGridRBx;
        int destGridRTy = destGridRBy;
        int destGridRTx = destGridLTx;

        switch (mDir)    {
        case UP:
            targetTerrainType1 = rWorld.GetTerrain(destGridLTx, destGridLTy, Map.TERRAIN_TYPE_ALL);
            targetTerrainType2 = rWorld.GetTerrain(destGridRTx, destGridRTy, Map.TERRAIN_TYPE_ALL);
            break;

        case LEFT:
            targetTerrainType1 = rWorld.GetTerrain(destGridLTx, destGridLTy, Map.TERRAIN_TYPE_ALL);
            targetTerrainType2 = rWorld.GetTerrain(destGridLBx, destGridLBy, Map.TERRAIN_TYPE_ALL);
            break;

        case DOWN:
            targetTerrainType1 = rWorld.GetTerrain(destGridLBx, destGridLBy, Map.TERRAIN_TYPE_ALL);
            targetTerrainType2 = rWorld.GetTerrain(destGridRBx, destGridRBy, Map.TERRAIN_TYPE_ALL);
            break;

        case RIGHT:
            targetTerrainType1 = rWorld.GetTerrain(destGridRBx, destGridRBy, Map.TERRAIN_TYPE_ALL);
            targetTerrainType2 = rWorld.GetTerrain(destGridRTx, destGridRTy, Map.TERRAIN_TYPE_ALL);
            break;

        default:
            assert false : "Try move with no dir!";
        }
        
        if (Map.INVALID == targetTerrainType1 || Map.IRON  == targetTerrainType1 ||
            Map.BLOCK   == targetTerrainType1 || Map.WATER == targetTerrainType1 || 
            Map.INVALID == targetTerrainType2 || Map.IRON  == targetTerrainType2 ||
            Map.BLOCK   == targetTerrainType2 || Map.WATER == targetTerrainType2)  {
            return false;
        }
        else  {
            // 通过了地形检测，这里检测动态阻挡
            final int oldx = mPos.x;
            final int oldy = mPos.y;
            SetPos(target.x, target.y);
            if (rWorld.HitTest(this) ||
                rWorld.HitHeadquarters(this))  {
                SetPos(oldx, oldy);
                return false;
            }

            return  true;
        }
    }

    @Override
     boolean HitTest(Movable pOther) {
        if (pOther == this)
            return  false;

        // 碰撞状态过滤
        if (!this.IsAlive() || !pOther.IsAlive())
            return  false;

        if (pOther.GetType() == ObjectType.BULLET) 
            return  pOther.HitTest(this);
         
        // 坦克相遇，要特殊处理矩形碰撞
        // 在TRYMOVE中已经保证了不会越界
        final  int  myLeft = mPos.x;
        final  int  myTop  = mPos.y;
        final  int  myRight = myLeft + GetBodySize();
        final  int  myBottom = myTop + GetBodySize();

        final  int  hisLeft = pOther.mPos.x;
        final  int  hisTop  = pOther.mPos.y;
        final  int  hisRight = hisLeft + pOther.GetBodySize();
        final  int  hisBottom = hisTop + pOther.GetBodySize();
        
        // 计算两者在X/Y轴是否相交
        final boolean interSectY = (hisTop < myBottom && hisBottom > myTop) ;
        final boolean interSectX = (hisLeft < myRight && hisRight > myLeft);
        int   offset = 0;
        final boolean  debugPlayer = (this.GetType() != pOther.GetType());
        if (!interSectX || !interSectY) 
            return false;

        switch (this.mFaceDir) {
        case LEFT:
            offset  = hisRight - myLeft;

            // offset有上限，是为了允许坦克略微重叠，但要小于偏移的精度：GRIDSIZE/2 （坦克换方向的偏移）
            // 之所以0也碰撞，是要保持坦克之间至少有一个像素的距离
            if (offset >= 0 && offset < GameWorld.STEP) {
                return  true;
            }  else  if (debugPlayer && 
                    offset < 4 * GameWorld.STEP && offset >= GameWorld.STEP) {
//                Log.d("0LEFT OLD", "x = " + m_oldPos.x + " y = " + m_oldPos.y );
//                Log.d("0LEFT", "l = " + myLeft + " t = " + myTop + " r = " + myRight + " b = " + myBottom);
//                Log.d("1LEFT", "l = " + hisLeft + " t = " + hisTop + " r = " + hisRight + " b = " + hisBottom);
            }
            
            return  false;
            
        case RIGHT:
            offset  = myRight - hisLeft;
            
            // offset有上限，是为了允许坦克略微重叠，但要小于移动的精度：STEP
            if (offset >= 0 && offset < GameWorld.STEP) {
                return  true;
            } else  if (debugPlayer && 
                    offset < 4 * GameWorld.STEP && offset >= GameWorld.STEP) {
//                Log.d("0RIGHT OLD", "x = " + m_oldPos.x + " y = " + m_oldPos.y);
//                Log.d("0RIGHT", "l = " + myLeft + " t = " + myTop + " r = " + myRight + " b = " + myBottom);
//                Log.d("1RIGHT", "l = " + hisLeft + " t = " + hisTop + " r = " + hisRight + " b = " + hisBottom);
            }
            
            return  false;
            
        case DOWN:
            offset = myBottom - hisTop;
            
            if (offset >= 0 && offset < GameWorld.STEP) {
                return  true;
            } else  if (debugPlayer && 
                    offset < 4 * GameWorld.STEP && offset >= GameWorld.STEP) {
//                Log.d("0DOWN OLD", "x = " + m_oldPos.x + " y = " + m_oldPos.y );
//                Log.d("0DOWN", "l = " + myLeft + " t = " + myTop + " r = " + myRight + " b = " + myBottom);
//                Log.d("1DOWN", "l = " + hisLeft + " t = " + hisTop + " r = " + hisRight + " b = " + hisBottom);
            }
            
            return  false;
            
        case UP:
            offset = hisBottom - myTop;
            
            if (offset >= 0 && offset < GameWorld.STEP) {
                return  true;
            } else if (debugPlayer && 
                    offset < 4 * GameWorld.STEP && offset >= GameWorld.STEP) {
//                Log.d("0UP OLD", "x = " + m_oldPos.x + " y = " + m_oldPos.y);
//                Log.d("0UP", "l = " + myLeft + " t = " + myTop + " r = " + myRight + " b = " + myBottom);
//                Log.d("1UP", "l = " + hisLeft + " t = " + hisTop + " r = " + hisRight + " b = " + hisBottom);
            }

            return  false;

        default:
            break;
        }
        
        return   true;
    }
    
    void  DoMove(Dir  type) {
        final GameWorld  rWorld = GameWorld.Instance();
        final Dir        oldDir  = mDir;
        final GameObject.Pos  myGrid = GetGrid();

        switch (type) {
        case  UP:
        case  DOWN:
            if (oldDir == Dir.LEFT || oldDir == Dir.RIGHT || oldDir == Dir.NONE) {
                int leftBorderX = myGrid.y * rWorld.GetGridWidth();

                if (GetPos().x < leftBorderX + rWorld.GetGridWidth() / 2)
                    SetPos(leftBorderX, GetPos().y);
                else
                    SetPos(leftBorderX + rWorld.GetGridWidth(), GetPos().y);
            }
            SetDir(type);
            break;
    
        case LEFT:
        case RIGHT:
            if (oldDir == Dir.UP || oldDir == Dir.DOWN || oldDir == Dir.NONE) {        
                int leftBorderY = myGrid.x * rWorld.GetGridWidth();

                if (GetPos().y < leftBorderY + rWorld.GetGridWidth() / 2)
                    SetPos(GetPos().x, leftBorderY);
                else
                    SetPos(GetPos().x, leftBorderY + rWorld.GetGridWidth());
            }
            SetDir(type);
            break;
            
        default:
            SetDir(Dir.NONE);
            break;
        }
    }

    BulletManager  mBulletMgr = new BulletManager(2);
    public static final int TANK_SPEED = 2 * GameWorld.StandardUnit();
};
