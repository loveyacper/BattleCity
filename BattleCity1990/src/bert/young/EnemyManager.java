package bert.young;

import java.util.Random;
import android.graphics.Canvas;
import android.util.Log;
import bert.young.Tank.TankType;

final class EnemyManager {
    /** 处理网络消息 */
    boolean OnBornEnemy(int id, int x, int y, int type) {
        EnemyTank   enemy = _GetEnemy();
        if (enemy == null)  {
            Log.e(Bullet.TAG, "Error OnBornEnemy type " + type + ", mID " + id);
            return false;
        }

        Log.d(Bullet.TAG, "OnBornEnemy type " + type + ", mID " + id);
        TankType  enumType = TankType.values()[type];

        enemy.Init(enumType);
        enemy.SetPos(x, y);
        enemy.mID = id;
        MyResource.Assert(id > 0, "ID must be greater than zero");
        
        InfoView.Instance().postInvalidate();
        ++ mCurEnemyIdx;
        return true;
    }
    
    EnemyManager() {
    }
    
    /** 根据id查找敌人 */
    EnemyTank FindEnemy(int id) {
        for (EnemyTank enemy : mEnemyTanks) {
            if (null != enemy && enemy.mID == id)
                return enemy;
        }
        
        return null;
    }

    // 初始化敌人类型
    void Init(int dummyStage) {
        m_enemyBornTimer = 0;
        mCurEnemyIdx  = 0;
        mBornPos        = BornPos.LEFT;
        
        if (BattleCity.smGameMode == GameMode.SINGLE)
            mEnemyTanks = new EnemyTank[4];
        else
            mEnemyTanks = new EnemyTank[6]; 
        
        for (int i = 0; i < mEnemyTanks.length; ++ i) {
            mEnemyTanks[i] = new EnemyTank();
        }
    
        // 每关3个红坦克，起始序号0，第一个在1-3中，第二个在8-11，第三个在15-18
        Random  rand = new Random(System.currentTimeMillis());
        
        final int firstRed =  1 + rand.nextInt(3);
        final int secRed   =  8 + rand.nextInt(4);
        final int thirdRed = 15 + rand.nextInt(4);
        
        for (int i = 0; i < mEnemyTypes.length; ++ i) {
            switch (rand.nextInt(Tank.TankType.MAX.ordinal()) / 2) {
            case 0:
                mEnemyTypes[i] = Tank.TankType.NORMAL;
                break;

            case 1:
                mEnemyTypes[i] = Tank.TankType.FAST;
                break;
                
            case 2:
                mEnemyTypes[i] = Tank.TankType.SMART;
                break;

            case 3:
                mEnemyTypes[i] = Tank.TankType.STRONG;
                break;
                
            default:
                MyResource.Assert(false, "ERROR ENEMY TYPE");
                break;
            }
        }
        
        mEnemyTypes[firstRed] = TankType.values()[mEnemyTypes[firstRed].ordinal() + 1];
        mEnemyTypes[secRed]   = TankType.values()[mEnemyTypes[secRed].ordinal()   + 1];
        mEnemyTypes[thirdRed] = TankType.values()[mEnemyTypes[thirdRed].ordinal() + 1];
    }

    /** 获取剩余敌人数目*/
    int  GetRemainEnemy() {
        return   MAX_ENEMY - mCurEnemyIdx;
    }
    
    /** 获取地图上敌人数目*/
    int  GetCurrentEnemyNum() {
        int num = 0;
        for (EnemyTank enemy : mEnemyTanks) {
            if (null != enemy && enemy.IsValid())
                ++ num;
        }
        
        return num;
    }
    
    /** 是否还有敌人*/
    boolean  NoEnemy() {
        return  mCurEnemyIdx >= MAX_ENEMY && 0 == GetCurrentEnemyNum();
    }
    
    /** 是否可以出生*/
    boolean CanBornEnemy(int currentLoop) {
        if (BattleCity.smGameMode == GameMode.CLIENT)
            return false;
        
        if (mCurEnemyIdx >= MAX_ENEMY)
            return false;
        
        if (0 == GetCurrentEnemyNum())
            return true;
        
        if (GetCurrentEnemyNum() < mEnemyTanks.length &&
            currentLoop >= m_enemyBornTimer + BORN_INTERVAL) {
            return  true;
        }
        
        return false;    
    }
    
    /** 生产敌人*/
    EnemyTank BornEnemy(int currentLoop) {
        EnemyTank enemy = _GetEnemy();

        enemy.Init(mEnemyTypes[mCurEnemyIdx]);
        
        ++ mCurEnemyIdx;
        m_enemyBornTimer = currentLoop;
        
        return  enemy;
    }
    
    private EnemyTank _GetEnemy() {
        EnemyTank   result = null;

        for (EnemyTank enemy : mEnemyTanks) {
            if (!enemy.IsValid())
                result = enemy;
        }
        
        if (result == null && BattleCity.smGameMode == GameMode.CLIENT) {
        	 for (EnemyTank enemy : mEnemyTanks) {
        		 Log.e(Bullet.TAG, "enemy state " + enemy.mState);
             }
        }

        return result;
    }
    
    /** 获取出生点*/
    BornPos  GetBornPos() {
        BornPos  result = mBornPos;
        switch (mBornPos) {
        case LEFT:
            mBornPos = BornPos.CENTER;
            break;

        case CENTER:
            mBornPos = BornPos.RIGHT;
            break;

        case RIGHT:
            mBornPos = BornPos.LEFT;
            break;

        default:
            MyResource.Assert(false,  "Wrong born position");
            break;
        }

        return  result;
    }

    /** 游戏逻辑更新*/
    public void Update() {
        for (EnemyTank enemy : mEnemyTanks) {
            enemy.UpdateBullets();
            if (!mEnemyFrozen)
                enemy.Update();
        }
    }
    
    /** 画敌人*/
    public void Render(Canvas  canvas) {
        for (EnemyTank enemy : mEnemyTanks) {
            if (null != enemy) {
                enemy.Paint(canvas);
                enemy.RenderBullets(canvas);
            }
        }    
    }
    
    /** 敌人碰撞测试*/
    public boolean HitTest(Movable  pThis) {
        for (EnemyTank enemy : mEnemyTanks) {
            if (enemy.HitTestBullets(pThis)) {
                // 这里不关心enemy状态。因为他死了，他的子弹仍然可以再飞一会
                return true;
            }
            
            if (enemy.IsAlive() && !enemy.IsGod()) {
                if (pThis.HitTest(enemy))
                    return true;
            }
        }
        
        return  false;
    }
    
    /** 炸毁所有活的敌人*/
    boolean  BombAll() {
        boolean  hasEnemy = false;
        for (EnemyTank enemy : mEnemyTanks) {
            if (null != enemy &&
                enemy.IsValid()) {
                hasEnemy  = true;
                enemy.mHP = 0;
                enemy.SetState(ObjectState.EXPLODE1);
                if (enemy.ShouldSync())
                    enemy.SendHurtMsg();
            }
        }
        
        return hasEnemy;
    }
    
    /** 冻结/解冻所有敌人 */
    void  SetFrozen(boolean frozen) {
        mEnemyFrozen = frozen;
        TimerManager.Instance().KillTimer(mFrozenTimer);

        if (frozen) {    // 定身14秒
            mFrozenTimer.SetRemainCnt(1);
            TimerManager.Instance().AddTimer(mFrozenTimer);
        }
    }

    private static final int BORN_INTERVAL = 3 * GameWorld.FPS / 2;
    private static final int MAX_ENEMY     = 20;
    
    private  Tank.TankType[]   mEnemyTypes = new Tank.TankType[MAX_ENEMY];
    private  EnemyTank []      mEnemyTanks = null;    
    private  int  m_enemyBornTimer; // 敌人出生有一定的时间间隔
    private  int  mCurEnemyIdx ; // 从0-19，
    
    enum BornPos {
        LEFT,
        CENTER,
        RIGHT,
    }
    private  BornPos  mBornPos;
    private  boolean  mEnemyFrozen;
    private  final TimerManager.Timer  mFrozenTimer = new TimerManager.Timer(14 * 1000, 1) {
        @Override
        boolean _OnTimer() {
            EnemyManager.this.SetFrozen(false);
            return  false;
        }
    };
}
