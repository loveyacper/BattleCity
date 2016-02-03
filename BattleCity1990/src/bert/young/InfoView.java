package bert.young;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

class InfoView extends View {
    private  Paint   mPaint;
    
    private  Rect    mSrc  = new Rect();
    private  Rect    mDst  = new Rect();
    
    private static  View  smView = null;
    public  static  View  Instance() {
        return    smView;
    }
    
    public InfoView(Context context) {   
        super(context);
        
        smView = this;
    }

    public InfoView(Context context, AttributeSet attrs) {  
        super(context, attrs);
        smView = this;
        
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
    } 

    public void onDraw(Canvas canvas) { 
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        
        if (!GameWorld.Instance().IsPlaying()) 
            return;
        
        final int  offsetX = 20;
        final int  offsetY = 14;
        final int  iconLen = (int)(GameWorld.Instance().GetSceneWidth() * 0.047f);
        
        final int lifeOffset  = 28 * MyResource.RawTankSize();
        final int lifeIconRawSize = 16;
        final int enemyOffset = 6 * MyResource.RawTankSize();
        final int enemyIconRawSize = MyResource.RawTankSize();
        
        mSrc.left    = enemyOffset;
        mSrc.top     = 0;
        mSrc.right   = mSrc.left + enemyIconRawSize ;
        mSrc.bottom  = mSrc.top  + enemyIconRawSize ;
        
        int  enemyCnt = 0;
        // 画敌人坦克数量  图标的大小和地图格子大小一致
        for (int i = 0; i < 10; ++ i) {
            for (int j = 0; j < 2; ++ j) {
                if (enemyCnt  >= GameWorld.Instance().GetRemainEnemy())
                    break;
                
                ++ enemyCnt;

                mDst.left    = offsetX + j * iconLen;
                mDst.top     = offsetY + i * iconLen;
                mDst.right   = mDst.left + iconLen;
                mDst.bottom  = mDst.top  + iconLen;
                
                canvas.drawBitmap(MyResource.GetSpirit(), mSrc, mDst, mPaint);
            }
        }

        // 画己方生命数量
        PlayerTank  me = GameWorld.Instance().GetMe();
        if (null == me)
            return;

        mSrc.left    = lifeOffset;
        mSrc.top     = 0;
        mSrc.right   = mSrc.left + lifeIconRawSize;
        mSrc.bottom  = mSrc.top  + lifeIconRawSize;
        
        int  lifeCnt = 0;
        // 画己方坦克数量  图标的大小和地图格子大小一致
        for (int i = 0; i < 3; ++ i) {
            for (int j = 0; j < 2; ++ j) {

                if (lifeCnt  >= me.GetLife())
                    break;
                
                ++ lifeCnt;

                mDst.left    = offsetX + j * iconLen;
                mDst.top     = offsetY + i * iconLen + 11 * iconLen;
                mDst.right   = mDst.left + iconLen;
                mDst.bottom  = mDst.top  + iconLen;
                
                canvas.drawBitmap(MyResource.GetSpirit(), mSrc, mDst, mPaint);
            }
        }

        // 双打暂时不做分数
        if (BattleCity.smGameMode != GameMode.SINGLE)
            return;
        
        String   str1 = "分数:";
        String   str2 = "" + me.mScore;
        
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(20);
        mPaint.setTextAlign(Paint.Align.CENTER);
        
        canvas.drawText(str1, 2 * offsetX, 14 + 15 * iconLen,
                mPaint);
        canvas.drawText(str2, 2 * offsetX, 14 + 16 * iconLen,
                mPaint);
    }
}
