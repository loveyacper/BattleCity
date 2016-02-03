package bert.young;

/** 来自LINUX内核的动态定时器算法 */

class TimerManager {

    // TIMER定义
    static class Timer {
        Timer  prev = null;
        Timer  next = null;
        long   nextTriger = System.currentTimeMillis();
        int    interval;
        int    count; // -1 表示永远
        
        Timer() {
            this.interval = 0;
            this.count    = 0;
        }
        
        Timer(int interval, int count) {
            this.interval = interval;
            this.count    = count;
            
            if (interval <= 0) {
                this.count = 0;
            }
            
            this.nextTriger += interval;
        }
        
        final void  SetRemainCnt(int cnt) {
            count = cnt;
            nextTriger = System.currentTimeMillis();
            nextTriger += interval;
        }

        final  boolean  OnTimer()  {
            if (count < 0 || -- count >= 0) {
                nextTriger += interval;
                return  _OnTimer();
            }
             
            return false;     
        }
        
        void     _OnTimerEnd() {
            return;
        }

        boolean  _OnTimer() {
            return false;
        }
    } // TIMER定义结束



    private TimerManager() {
        m_thisCheckTime = System.currentTimeMillis();
        
        for (int i = 0; i < m_list1.length; ++ i) {
            m_list1[i] = new Timer(0, 0);
        }
        
        for (int i = 0; i < m_list2.length; ++ i) {
            m_list2[i] = new Timer(0, 0);
        }
        
        for (int i = 0; i < m_list3.length; ++ i) {
            m_list3[i] = new Timer(0, 0);
        }
        
        for (int i = 0; i < m_list4.length; ++ i) {
            m_list4[i] = new Timer(0, 0);
        }
        
        for (int i = 0; i < m_list5.length; ++ i) {
            m_list5[i] = new Timer(0, 0);
        }
    }
    
    private static  final TimerManager mgr = new TimerManager();
    public  static TimerManager Instance() {
        return     mgr;
    }
    

    final  boolean  IsWorking(Timer  timer) {
        return timer != null && timer.prev != null;
    }
    
    
    public void UpdateTimers(long now) {
        while (m_thisCheckTime <= now) {
            int index = (int)(m_thisCheckTime & (LIST1_SIZE - 1));
            if (0 == index &&
                !_Cacsade(m_list2, _Index(0)) &&
                !_Cacsade(m_list3, _Index(1)) &&
                !_Cacsade(m_list4, _Index(2)))  {
                _Cacsade(m_list5, _Index(3));
            }

            ++ m_thisCheckTime;

            Timer   pTimer = null;
            while (null != (pTimer = m_list1[index].next)) {
                KillTimer(pTimer);
                if (pTimer.OnTimer()) {
                    AddTimer(pTimer);
                } else {
                    pTimer._OnTimerEnd();
                }
            }
        }        
    }

    public void AddTimer(Timer pTimer) {
        if (null == pTimer) {
            return;
        }
        
        KillTimer(pTimer);
        
        int  diff  =  (int)(pTimer.nextTriger - m_thisCheckTime);
        Timer  pListHead =  null;
        long trigTime  =  pTimer.nextTriger;

            
        if (diff < 0)    {
            pListHead = m_list1[(int)(m_thisCheckTime & (LIST1_SIZE - 1))];    
        }
            
        else if (diff <  LIST1_SIZE)   {
            pListHead = m_list1[(int)(trigTime & (LIST1_SIZE - 1))];   
        }
        else if (diff < 1 << (LIST1_BITS + LIST_BITS))   {
            pListHead = m_list2[(int)((trigTime >> LIST1_BITS) & (LIST_SIZE - 1))];    
        }    
        else if (diff < 1 << (LIST1_BITS + 2 * LIST_BITS))   {    
            pListHead = m_list3[(int)((trigTime >> (LIST1_BITS + LIST_BITS)) & (LIST_SIZE - 1))];
        }
        else if (diff < 1 << (LIST1_BITS + 3 * LIST_BITS))   {        
            pListHead = m_list4[(int)((trigTime >> (LIST1_BITS + 2 * LIST_BITS)) & (LIST_SIZE - 1))];
        }    
        else   {
            pListHead = m_list5[(int)((trigTime >> (LIST1_BITS + 3 * LIST_BITS)) & (LIST_SIZE - 1))];    
        }

        // push front    
        MyResource.Assert(null == pListHead.prev, "Error TimerMgr");
        pTimer.prev = pListHead;    
        pTimer.next = pListHead.next;    
        if (null != pListHead.next)  {   
            pListHead.next.prev = pTimer;
        }
    
        pListHead.next = pTimer;
    }

    public void KillTimer(Timer pTimer) {
        if (null != pTimer && null != pTimer.prev) {
            pTimer.prev.next = pTimer.next;

            if (null != pTimer.next) {
                pTimer.next.prev = pTimer.prev;
            }

            pTimer.prev = pTimer.next = null;
        }
    }
    
    public void  KillAll() {
        for (int i = 0; i < LIST1_SIZE; ++ i) {
            Timer   pTimer;
            while (null != (pTimer = m_list1[i].next)) {
                KillTimer(pTimer);
            }
        }

        for (int i = 0; i < LIST_SIZE; ++ i) {
            Timer  pTimer;
            while (null != (pTimer = m_list2[i].next)) {
                KillTimer(pTimer);
            }

            while (null != (pTimer = m_list3[i].next)) {
                KillTimer(pTimer);
            }

            while (null != (pTimer = m_list4[i].next)) {
                KillTimer(pTimer);
            }

            while (null != (pTimer = m_list5[i].next)) {
                KillTimer(pTimer);
            }
        }
    }
    
    // Return false if noop
    private boolean _Cacsade(Timer[] pList, int index) {
        if (null == pList || 
            index < 0 ||
            index > LIST_SIZE ||
            null == pList[index] ||
            null == pList[index].next) {
            return false;
        }
        
        Timer  pTimer = pList[index].next;
        while (pTimer != null) {
            Timer  tmpNext = pTimer.next;
            KillTimer(pTimer);
            AddTimer(pTimer);
            pTimer = tmpNext;
        }

        return true;
    }

    // from list2 to list5
    private int  _Index(int level) {
        long current = m_thisCheckTime;    
        current >>= (LIST1_BITS + level * LIST_BITS);
        return  (int)(current & (LIST_SIZE - 1));
    }
 
    static final int LIST1_BITS = 8;
    static final int LIST_BITS  = 6;
    static final int LIST1_SIZE = 1 << LIST1_BITS;
    static final int LIST_SIZE  = 1 << LIST_BITS;


    long  m_thisCheckTime;

    Timer[] m_list1 = new Timer[LIST1_SIZE]; // 256 ms * ACCURACY
    Timer[] m_list2 = new Timer[LIST_SIZE];  // 64 * 256ms = 16秒
    Timer[] m_list3 = new Timer[LIST_SIZE];  // 64 * 64 * 256ms = 17分钟
    Timer[] m_list4 = new Timer[LIST_SIZE];  // 64 * 64 * 64 * 256ms = 18 小时
    Timer[] m_list5 = new Timer[LIST_SIZE];  // 64 * 64 * 64 * 64 * 256ms = 49 天
}
