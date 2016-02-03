package bert.young;

import java.io.InputStream;
import java.util.ArrayList;
import org.apache.http.util.EncodingUtils;
import android.graphics.Point;


final class Map {
    /** 地图宽度：13 图片 */ 
    public static final int IMAGE_NUM  = 13;
    public static final int GRID_CNT   = 2 * IMAGE_NUM;
    
    /** 地形枚举 */
    public static  final int  INVALID  = -1;
    public static  final int     NONE  = 0;
    public static  final int     SNOW  = 1;
    public static  final int     GRASS = 2;
    public static  final int     IRON  = 3;
    public static  final int     BLOCK = 4;
    public static  final int     WATER = 5;
    public static  final int     EAGLE = 6;
    
    public static  final int TERRAIN_TYPE = 0x07;

    public static  final int TERRAIN_TYPE_LEFTTOP       = 0x01 << 3;
    public static  final int TERRAIN_TYPE_RIGHTTOP      = 0x01 << 4;
    public static  final int TERRAIN_TYPE_LEFTBOTTOM    = 0x01 << 5;
    public static  final int TERRAIN_TYPE_RIGHTBOTTOM   = 0x01 << 6;
    public static  final int TERRAIN_TYPE_ALL           = (TERRAIN_TYPE_LEFTTOP     |
                                                           TERRAIN_TYPE_RIGHTTOP    |
                                                           TERRAIN_TYPE_LEFTBOTTOM  |
                                                           TERRAIN_TYPE_RIGHTBOTTOM);
    
    boolean Init(int sceneWidth) {
        mSceneWidth = sceneWidth;
        
        mGridInfo = new char[GRID_CNT][];
        for (int i = 0; i < mGridInfo.length; ++ i) {
            mGridInfo[i] = new char[GRID_CNT];
            for (int j = 0; j < mGridInfo[i].length; ++ j)
                mGridInfo[i][j] = (char)INVALID;
        }
        
        return true;
    }
    
    int  GetSceneWidth() {
        return  mSceneWidth;
    }
    int GetImgWidth() {
        return  GetSceneWidth() / IMAGE_NUM;
    }
    int GetGridWidth() {
        return  GetImgWidth() / 2;
    }
    
    void ClearGrassInfo() {
        mGrassInfo.clear();
    }

    ArrayList<Point>   GetGrassInfo() {
        return  mGrassInfo;
    }
    
    char GetGridInfo(int gridx, int gridy)  {
        return  mGridInfo[gridx][gridy];
    }
    
    /** 获取指定格子地形，可由MASK细化到具体位置 */
    int GetTerrain(int gridx, int gridy, int tmask)  {
        if (gridx < 0 || gridy < 0 || gridx >= GRID_CNT || gridy >= GRID_CNT)
            return INVALID;

        if ((mGridInfo[gridx][gridy] & tmask) != 0)
            return (mGridInfo[gridx][gridy] & TERRAIN_TYPE);
        
        return NONE;
    }

    /** 清除指定格子地形，可由MASK细化到具体位置 */
    void ClearTerrain(int gridx, int gridy, int mask) {
        if (gridx < 0 || gridy < 0 || gridx >= GRID_CNT || gridy >= GRID_CNT)
            return;
        if (mask == TERRAIN_TYPE_ALL)
            mGridInfo[gridx][gridy] = 0;
        else
            mGridInfo[gridx][gridy] &= (~mask);
    }
    
    /** 载入指定关卡地图 */
    boolean LoadMap(InputStream  inFile) {
        MapLoader  loader = new MapLoader();
        if (!loader.LoadFile(inFile))
            return  false;

        for (int iRow = 1; iRow <= IMAGE_NUM; ++ iRow) {
            for (int iCol = 1; iCol <= IMAGE_NUM; ++ iCol) {
                String value = loader.GetData(iRow, iCol);
                if (null == value)
                    return false;

                int  type  = Integer.parseInt(value);

                mGridInfo[(iRow - 1) * 2][(iCol - 1) * 2]   =  (char)((type & 0xFF) | TERRAIN_TYPE_ALL); // 左上
                if ((type & 0xFF) == GRASS) {
                    mGrassInfo.add(new Point((iRow - 1) * 2, (iCol - 1) * 2));
                }
                
                type >>= 8;
                mGridInfo[(iRow - 1) * 2][(iCol - 1) * 2 + 1] =  (char)((type & 0xFF)  | TERRAIN_TYPE_ALL); // 右上
                if ((type & 0xFF) == GRASS) {
                    mGrassInfo.add(new Point((iRow - 1) * 2, (iCol - 1) * 2 + 1));
                }
                
                type >>= 8;
                mGridInfo[(iRow - 1) * 2 + 1][(iCol - 1) * 2] =  (char)((type & 0xFF) | TERRAIN_TYPE_ALL);// 左下
                if ((type & 0xFF) == GRASS) {
                    mGrassInfo.add(new Point((iRow - 1) * 2 + 1, (iCol - 1) * 2));
                }
                
                type >>= 8;
                mGridInfo[(iRow - 1) * 2 + 1][(iCol - 1) * 2 + 1] =  (char)((type & 0xFF) | TERRAIN_TYPE_ALL);// 右下
                if ((type & 0xFF) == GRASS) {
                    mGrassInfo.add(new Point((iRow - 1) * 2 + 1, (iCol - 1) * 2 + 1));
                }
            }
        }

        return  true;
    }

    /** 保护司令部 */
    void ProtectHeadQuarters() {
        SetHeadQuarters(IRON);
        mHeadTimer.SetRemainCnt(1);
        TimerManager.Instance().AddTimer(mHeadTimer);
    }
    
    void SetHeadQuarters(int terrain) {
        char  type = (char)(terrain | TERRAIN_TYPE_ALL);
        mGridInfo[23][11] = type;
        mGridInfo[24][11] = type;
        mGridInfo[25][11] = type;
        mGridInfo[23][12] = type;
        mGridInfo[23][13] = type;
        mGridInfo[23][14] = type;
        mGridInfo[24][14] = type;
        mGridInfo[25][14] = type;
    }
    
    private TimerManager.Timer   mHeadTipTimer = new TimerManager.Timer(150, 16) {
        private   int  terrain = IRON;
        @Override
        boolean _OnTimer() {
            if (terrain == BLOCK)
                terrain = IRON;
            else
                terrain = BLOCK;
            
            Map.this.SetHeadQuarters(terrain);
            
            return   true;
        }
        @Override
        void  _OnTimerEnd() {
            Map.this.SetHeadQuarters(BLOCK);
        }
    };

    private TimerManager.Timer   mHeadTimer = new TimerManager.Timer(14 * 1000, 1) {
        @Override
        boolean _OnTimer() {
            mHeadTipTimer.SetRemainCnt(16);
            TimerManager.Instance().AddTimer(mHeadTipTimer);
            
            return   false;
        }
    };

    /** 读地图文件辅助类 */
    private static class  MapLoader {
        boolean LoadFile(InputStream  inFile) {
            String   content = ""; 

            try {
                int length = inFile.available();       
                byte [] buffer = new byte[length];
                inFile.read(buffer);
                content = EncodingUtils.getString(buffer, "UTF-8");
                inFile.close();   
            } catch(Exception e) {
                e.printStackTrace();        
                return  false;
            }
            
             mRows = mCols = 0;
             mData = new ArrayList<String>();
             
             String value = "";
             char   ch = 0;   
             boolean  bFirstLine = true;
             
             int idx = -1;
             while (++ idx < content.length()) {                 
                 ch = content.charAt(idx);

                 switch (ch)   {
                    case NEWLINE:
                    case SEPARATOR:
                        if (bFirstLine) {
                            ++ mCols;
                            if (NEWLINE == ch)
                                bFirstLine = false;    
                        }
                        
                        mData.add(value);
                        value = "";
                        break;

                    case '\r':
                        break;

                    default:
                        value += ch;      
                 }    
             }

             mRows = mData.size() / mCols;

             return IMAGE_NUM == mRows && IMAGE_NUM == mCols;
        }

        String  GetData(int row, int col) {
            if (row < 1 || row > IMAGE_NUM || col < 1 || col > IMAGE_NUM)
                    return null;

            return mData.get((row-1) * IMAGE_NUM + col - 1);
        }

        private  static final int SEPARATOR = ',';
        private  static final int NEWLINE   = '\n';

        private int mRows;
        private int mCols;
        private ArrayList<String>  mData = null;
    } // END Maploader

    /** 草地数据 独立出来，因为要画在坦克上面, BONUS之下 */
    private ArrayList<Point>   mGrassInfo = new ArrayList<Point>(); // 每个点表示一个格子坐标，该格子地形是草地
    /** 地图数据 */ 
    private char  mGridInfo[][];
    /** 地图的边长 */ 
    private int  mSceneWidth;    
}
