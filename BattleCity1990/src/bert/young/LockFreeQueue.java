package bert.young;

/** Android的Looper和Handler不满足需求  */
final class LockFreeQueue {
    private Object[]  mObjects;

    private volatile int  mReadPos;
    private volatile int  mWritePos;
    
    public LockFreeQueue(int size) {
        int queueSize = 1;
        while (queueSize < size) {
            queueSize <<= 1;
        }
        mObjects = new Object[queueSize];
        mReadPos = mWritePos = 0;
    }
    
    boolean IsEmpty() {
        return mReadPos == mWritePos;
    }
    
    boolean IsFull() {
        return ((mWritePos + 1) & (mObjects.length - 1)) == mReadPos;
    }
    
    void   Clear() {
        mReadPos = mWritePos;
    }
    
    boolean PushObject(Object obj) {
        if (null == obj)
            return true;
        
        if (IsFull())
            return false;
        
        mObjects[mWritePos] = obj;
        mWritePos = (mWritePos + 1) & (mObjects.length - 1);
        return  true;
    }
    
    Object  GetObject() {
    	if (IsEmpty())
    	    return null;
    	
    	return mObjects[mReadPos];
    }
    
    void    PopObject() {
        if (IsEmpty())
            return;

        mReadPos  = (mReadPos + 1) & (mObjects.length - 1);
    }
}
