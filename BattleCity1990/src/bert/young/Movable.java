package bert.young;

abstract class Movable extends GameObject {    
    /** 这里是为了效率 避免重新分配 */
    private final  Pos      mTargetPos = new Pos();
    private final  Pos      mGrid      = new Pos();
    int   mID = -1;

    enum Dir {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        NONE;
        
        static Dir Convert2Dir(int dir) {
            switch (dir) {
            case 0:
                return Dir.UP;
            case 1:
                return Dir.DOWN;
            case 2:
                return Dir.LEFT;
            case 3:
                return Dir.RIGHT;

            default:
                return Dir.NONE;
            }
        }
    };

    /** 初始化 */
    public Movable() {  
        mDir    = Dir.NONE;     // 脸的朝向在子类中决定
        mGrid.x = -1;
        mGrid.y = -1;
        mHP     = 0;
    }

    /** 获取当前所在格子 */
    Pos   GetGrid() {
        final GameWorld rWorld = GameWorld.Instance();
        mGrid.x = mPos.y / rWorld.GetGridWidth();
        mGrid.y = mPos.x / rWorld.GetGridWidth();
        
        MyResource.Assert(mGrid.x >= 0 && mGrid.y >= 0, "GetGrid ERROR");
        return  mGrid;
    }
    
    /** 移动 */
    void   UpdateMove() {
        if (Dir.NONE == mDir) {
            return;
        }

        /* 以半个格子移动，是有原因的：
         * 包容坦克重叠，且坦克换方向时，偏移上限是半个格子尺寸
         */
        int   nStep   = mSpeed / GameWorld.STEP;
        int   nRemain = mSpeed % GameWorld.STEP;

        for (int i = 0; i < nStep; ++ i)    {
            if (!_StepMove(GameWorld.STEP))
                return;
        }

        _StepMove(nRemain);
    }
    
    /**
     * 2012.01.14  碰撞测试
     * 做个改动，由于碰撞并不能严格按照矩形相交来计算(俩坦克经常身体镶嵌而死锁)
     * 这里改为虚函数，交给子类重写
     */
    abstract boolean   HitTest(Movable pOther);

    /** 设置物体的运动方向，必要时调整朝向 */
    final void   SetDir(Dir dir) {
        SetFaceDir(dir);
        mDir = dir;
    }
    
    final void  SetFaceDir(Dir dir) {
        if (dir != Dir.NONE) 
            mFaceDir = dir;
    }
    
    /** 设置物体的速度 */
    final void  SetSpeed(int speed) {
        mSpeed = speed;
    }
    
    /** 该状态将参与碰撞检测，移动更新 */
    final boolean  IsAlive() {
        return  ObjectState.IsAlive(mState);
    }
    
    /** 该状态无敌 */
    final boolean  IsGod()   {
        return ObjectState.IsGod(mState);
    }

    /** 该状态将参与游戏逻辑 */
    final boolean  IsValid() {
         return ObjectState.IsValid(mState);
    }

    /** 设置生命值 */
    final void  SetHP(int hp) {
        mHP = hp;
    }

    /** 判断是否超过地图边界 */ 
    boolean  _TryMove(final Pos targetPos) {
        assert(GetBodySize() > 0);

        // 子弹比较特殊不能调用这个函数
        final int sceneWidth = GameWorld.Instance().GetSceneWidth();
        if (targetPos.x < 0 ||
            targetPos.y < 0 ||
            targetPos.x + GetBodySize() > sceneWidth ||
            targetPos.y + GetBodySize() > sceneWidth) {
            return false;
        }

        return true;
    }
   
    /** 受伤回调 */
    abstract void    OnHurt();
 
    /** 格子移动是个固定的方法，但它要调用模板方法 _TryMove */
    private  boolean  _StepMove(int distCanMove) {
        MyResource.Assert(distCanMove <= GameWorld.STEP, "Move more than STEP");
        
        if (distCanMove == 0)
            return false;

        if (!IsAlive())
            return false;
        
        mTargetPos.x = mPos.x;
        mTargetPos.y = mPos.y;
        switch (mDir)  {
        case UP:
            mTargetPos.y -= distCanMove;
            break;
    
        case DOWN:
            mTargetPos.y += distCanMove;
            break;
    
        case LEFT:
            mTargetPos.x -= distCanMove;    
            break;

        case RIGHT:
            mTargetPos.x += distCanMove;
            break;

        default:
            return false;
        }

        return  _TryMove(mTargetPos);
    }
    
    Dir    mDir;
    Dir    mFaceDir;
    int    mHP;
    int    mSpeed;
}
