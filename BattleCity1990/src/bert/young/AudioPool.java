package bert.young;


import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;


public class AudioPool {
    
    private  static SoundPool  mPool;
    
    static  void Create() {
        mPool = new SoundPool(6, AudioManager.STREAM_MUSIC, 0);
    }
    
    static  int  Load(Context  cxt,  int  resid) {
        return   mPool.load(cxt, resid, 1);
    }
    
    static  int Play(int  soundid, int vol, int repeat) {
        if (null == mPool)
            Create();
        
        return   mPool.play(soundid, vol, vol, 1, repeat, 1.0f);
    }
    
    static void Pause(int streamid) {
        if (null != mPool)
            mPool.pause(streamid);        
    }
    
    static void Resume(int streamid) {
        if (null != mPool)
            mPool.resume(streamid);        
    }

    static void Stop() {
        if (null != mPool) {
            mPool.autoPause ();
        }
    }
    
    static void Destroy() {
        if (null != mPool) {
            mPool.release();
            mPool = null;
        }
    }
}
