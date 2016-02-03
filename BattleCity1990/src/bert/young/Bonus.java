package bert.young;

import android.graphics.Canvas;
import android.graphics.Rect;

final class Bonus extends GameObject {
    
    static final int  SCORE_PER_LIFE = 20000;
    
    private enum  BonusState {
        INVALID,
        BLINK1,
        BLINK2,
    }
    
    Bonus(BonusType  type) {
        super();
        SetState(BonusState.BLINK1);
        mType  = type;
    }
    
    boolean ValidPos() {
        final GameWorld rWorld = GameWorld.Instance();
        int gridx = (int) (mPos.y / rWorld.GetGridWidth());
        int gridy = (int) (mPos.x / rWorld.GetGridWidth());
        
        int terrain1 = rWorld.GetTerrain(gridx, gridy, Map.TERRAIN_TYPE_ALL);
        int terrain2 = rWorld.GetTerrain(gridx + 1, gridy, Map.TERRAIN_TYPE_ALL);
        int terrain3 = rWorld.GetTerrain(gridx, gridy + 1, Map.TERRAIN_TYPE_ALL);
        int terrain4 = rWorld.GetTerrain(gridx + 1, gridy + 1, Map.TERRAIN_TYPE_ALL);
        
        if (terrain1 == Map.EAGLE ||
            terrain2 == Map.EAGLE ||
            terrain3 == Map.EAGLE ||
            terrain4 == Map.EAGLE) {
            return  false;
        }
        
        boolean  cannot1 = terrain1 == Map.IRON || terrain1 == Map.WATER;
        boolean  cannot2 = terrain2 == Map.IRON || terrain2 == Map.WATER;
        boolean  cannot3 = terrain3 == Map.IRON || terrain3 == Map.WATER;
        boolean  cannot4 = terrain4 == Map.IRON || terrain4 == Map.WATER;

        return   !(cannot1 && cannot2 && cannot3 && cannot4);        
    }
    
    /** 工厂方法 */
    static  Bonus  CreateBonus(BonusType  type) {
        Misc.PlaySound(AudioID.AUDIO_BONUS);
        return  new Bonus(type);
    }

    @Override
    int GetBodySize() {
        return   2 * GameWorld.Instance().GetGridWidth();
    }

    @Override
    ObjectType GetType() {
        return ObjectType.BONUS;
    }

    @Override
    void Paint(Canvas canvas) {
        if (mState != BonusState.BLINK1) {
            return;
        }
        
        final int tankImgN = 14; // 有14列坦克图片在前面
        final int  imgSeq  = (mType.ordinal() - BonusType.IRON.ordinal());
        final int rawBonusSize = MyResource.RawTankSize();

        mSrc.left = tankImgN * MyResource.RawTankSize() + imgSeq * rawBonusSize;
        mSrc.top  = 0;
        mSrc.right  = mSrc.left + rawBonusSize;
        mSrc.bottom = mSrc.top  + rawBonusSize;

        canvas.drawBitmap(MyResource.GetSpirit(), mSrc, GetBodyRect(), null);
    }

  
    /** 设置物体的逻辑状态 */
    void SetState(BonusState newState) {
        if (mState != newState) {
            mStateChanged = true;
            mState  = newState;
        }
    }

    @Override
    void Update() {
        if (mState == BonusState.INVALID) {
            return;
        }

        switch (mState) {
        case BLINK1:
            if (mStateChanged) {
                mStateChanged = false;
                
                TimerManager.Instance().AddTimer(new TimerManager.Timer(250, 1) {
                    @Override
                    boolean _OnTimer() {
                        Bonus.this.SetState(BonusState.BLINK2);
                        return  false;
                    }
                });
            }
            break;
            
        case BLINK2:
            if (mStateChanged) {
                mStateChanged = false;
                
                TimerManager.Instance().AddTimer(new TimerManager.Timer(200, 1) {
                    @Override
                    boolean _OnTimer() {
                        Bonus.this.SetState(BonusState.BLINK1);
                        return  false;
                    }
                });
            }
            break;
        }
        
        PlayerTank  player  = GameWorld.Instance().GetMe();
        PlayerTank  partner = GameWorld.Instance().GetPartner();
        if (null == player && null == partner)
            return;

        if (player != null && player.IsAlive()) {
            if (Rect.intersects(GetBodyRect(), player.GetBodyRect())) {
                _OnEat(player);
                player.mScore += 500;
                if (player.mScore >= Bonus.SCORE_PER_LIFE) {
                    player.mScore -= Bonus.SCORE_PER_LIFE;
                    player.SetLife(player.GetLife() + 1);
                    Misc.PlaySound(AudioID.AUDIO_LIFE);
                }
                
                InfoView.Instance().postInvalidate();
                return;
            }
        }
        
        if (partner != null && partner.IsAlive()) {
            if (Rect.intersects(GetBodyRect(), partner.GetBodyRect())) {
                _OnEat(partner);
            }
        }
    }
    
    
    private void _OnEat(PlayerTank    player) {
        Misc.PlaySound(AudioID.AUDIO_LIFE);
        GameWorld.Instance().SetBonus(null);
        SetState(BonusState.INVALID);

        switch (mType) {
        case IRON:
            // 将老巢周围地形设置为铁块，并设置TIMER，14秒后开始闪烁，20秒后变成砖块
            GameWorld.Instance().ProtectHeadQuarters();
            break;
            
        case STAR:
            // 加强 player的子弹速度
            player.BoostBullet();
            break;
            
        case LIFE:
            // 增加生命
            player.SetLife(player.GetLife() + 1);
            InfoView.Instance().postInvalidate();
            break;
            
        case PROTECT:
            // 令PLAYER处于无敌状态，调用GOD即可    
            player.God(14 * 1000);
            break;
            
        case BOMB:
            if (GameWorld.Instance().mEnemyMgr.BombAll())
                Misc.PlaySound(AudioID.AUDIO_BLAST);
            break;
            
        case TIMER:
            GameWorld.Instance().SetFrozen(true);
            break;
        }
    }
    
    enum BonusType {
        IRON,           // 保护老鹰 
        STAR,           // 加强子弹
        LIFE,           // 增加生命
        PROTECT,        // 保护自己
        BOMB,           // 秒杀敌方
        TIMER,          // 凝固敌方
        NONE,           // 无
    }

    private  BonusState mState;
    private  Rect       mSrc = new Rect();
    private  BonusType  mType;
    private  boolean    mStateChanged = false;
}
