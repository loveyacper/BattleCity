package bert.young;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class MyResource {
    /** 子弹图片 */
    private static Bitmap  mBulletBmp = null;   
    /** 带透明的精灵图片 */
    private static Bitmap  mSpiritBmp  = null;
    /** GAME OVER */
    private static Bitmap mOverBmp = null;
    /** PARTNER */
    private static Bitmap mPartner = null;
    /** END stage */
    private static Bitmap mEnd     = null;
    /** 资源初始化 */
    static  boolean Init(Resources  resource) {
        if (null == resource)
            return  false;
        
        mBulletBmp  = BitmapFactory.decodeResource(resource, R.drawable.bullet);
        mSpiritBmp  = BitmapFactory.decodeResource(resource, R.drawable.spirit);
        mOverBmp    = BitmapFactory.decodeResource(resource, R.drawable.gameover);
        mPartner    = BitmapFactory.decodeResource(resource, R.drawable.partner);
        mEnd        = BitmapFactory.decodeResource(resource, R.drawable.endstage);

        if (null == mBulletBmp ||
            null == mSpiritBmp ||
            null == mOverBmp ||
            null == mPartner) {
            return false;
        }

        return  true;
    }

    static int RawTankSize() {
        return  mSpiritBmp.getHeight() / 2;
    }
    
    static final Bitmap GetBulletBmp() {
        return  mBulletBmp;
    }
    
    static final int RawBulletSize() {
        return  mBulletBmp.getHeight();
    }
    
    static final int RawExplodeSize() {
        return  mSpiritBmp.getHeight();
    }

    static final int RawBornSize() {
        return  mSpiritBmp.getHeight() / 2;
    }
    
    /** test only 不会用Android的assert，没法。。。*/
    static final int Assert(boolean condition , String str) {
        if (condition)
            return 0;

        System.out.println("BUG ASSERT : " + str);
        int a  = 5 / 0;
        return  a;
    }
    
    static final Bitmap GetSpirit() {
        return  mSpiritBmp;
    }
    
    static final Bitmap GetPartner() {
        return  mPartner;
    }
    
    static final Bitmap GetEndStage() {
        return  mEnd;
    }
    
    static final Bitmap GetOver() {
        return  mOverBmp;
    }
}
