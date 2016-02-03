package bert.young;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public final class Headquarters extends GameObject {
    
    /** 碰撞判断 */
    boolean Intersect(Movable pOther) {
        return Rect.intersects(GetBodyRect(), pOther.GetBodyRect());
    }
    
    /** 初始化坐标和状态 */
    boolean Init() {
        mPos.x = 12 * GameWorld.Instance().GetGridWidth();
        mPos.y = 24 * GameWorld.Instance().GetGridWidth();
        mState = ObjectState.NORMAL;
        return true; 
    }
    
    @Override
    ObjectType GetType() {
        return   ObjectType.HEAD;
    }
    
    @Override
    int GetBodySize() {
        return  2 * GameWorld.Instance().GetGridWidth();
    }
    
    @Override
    void Update() {
        switch (mState) {
        case NORMAL:
            break;
            
        case EXPLODE1:
        case EXPLODE2:
        case EXPLODE3:
        case EXPLODE4:
            if (mStateChanged) {
                mStateChanged = false;

                TimerManager.Instance().AddTimer(new TimerManager.Timer(220, 1) {
                    @Override
                    boolean _OnTimer() {
                        int  curState = mState.ordinal();
                        Headquarters.this.SetState(ObjectState.values()[curState + 1]);
                        return false;
                    }
                });
            }
            break;
            
        case EXPLODE5:
            if (mStateChanged) {
                mStateChanged = false;

                TimerManager.Instance().AddTimer(new TimerManager.Timer(280, 1) {
                    @Override
                    boolean _OnTimer() {
                        GameWorld.Instance().GameOver();
                        return false;
                    }
                });
            }
            break;

        default:
            break;
        }    
    }
    
    @Override
    void Paint(Canvas canvas) {
        final int rawImgSize    = MyResource.RawTankSize();
        final int tankImgType   = 14; // 有14列坦克图片在前面

        if (mState.equals(ObjectState.NORMAL)) {
            mSrcRect.left    = (tankImgType + Map.EAGLE) * rawImgSize;
            mSrcRect.top     = MyResource.RawTankSize();
            mSrcRect.right   = mSrcRect.left + rawImgSize;
            mSrcRect.bottom  = mSrcRect.top  + rawImgSize;

            canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, GetBodyRect(), mPaint);
        } else {
            // 被炸毁了
            mSrcRect.left    = (tankImgType + Map.EAGLE + 1) * rawImgSize;
            mSrcRect.top     = MyResource.RawTankSize();
            mSrcRect.right   = mSrcRect.left + rawImgSize;
            mSrcRect.bottom  = mSrcRect.top  + rawImgSize;

            canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, GetBodyRect(), mPaint);

            final int explodeSize = MyResource.RawExplodeSize();
            final int explodePixelOffsetX = 29 * MyResource.RawTankSize();
            final GameWorld rWorld  = GameWorld.Instance();
            
            mDstRect.left = mPos.x;
            mDstRect.top  = mPos.y;
            
            switch (mState) {
            case EXPLODE1:
            case EXPLODE2:
            case EXPLODE3:
            case EXPLODE4:                
                int  curState = mState.ordinal();    
                // 爆炸图是普通图的1.5倍大小
                mSrcRect.left    = explodePixelOffsetX + (curState - ObjectState.EXPLODE1.ordinal()) * explodeSize;
                mSrcRect.top     = 0;
                mSrcRect.right   = mSrcRect.left + explodeSize;
                mSrcRect.bottom  = mSrcRect.top  + explodeSize;

                mDstRect.left   -= rWorld.GetGridWidth() / 2;
                mDstRect.top    -= rWorld.GetGridWidth() / 2;
                mDstRect.right   = mDstRect.left + 3 * rWorld.GetGridWidth();
                mDstRect.bottom  = mDstRect.top + 3 * rWorld.GetGridWidth();

                canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
                break;

            case EXPLODE5:
                mSrcRect.left    = explodePixelOffsetX + 4 * explodeSize;
                mSrcRect.top     = 0;
                mSrcRect.right   = mSrcRect.left + explodeSize;
                mSrcRect.bottom  = mSrcRect.top  + explodeSize;

                mDstRect.left   -= rWorld.GetGridWidth();
                mDstRect.top    -= rWorld.GetGridWidth();
                mDstRect.right   = mDstRect.left + 4 * rWorld.GetGridWidth();                    
                mDstRect.bottom  = mDstRect.top + 4 * rWorld.GetGridWidth();

                canvas.drawBitmap(MyResource.GetSpirit(), mSrcRect, mDstRect, mPaint);
                break;    
            }
        }
    }
    
    private  Paint mPaint   = new Paint();
    private  Rect  mSrcRect = new Rect();
    private  Rect  mDstRect = new Rect();
}
