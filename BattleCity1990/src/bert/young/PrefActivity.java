package bert.young;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class PrefActivity extends PreferenceActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }
     
    /** Get the current value of the music option */      
    public static boolean getMusic(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("music", true);
    }
    
    /** Get the current value of the vibrate option */      
    public static boolean getVibrate(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("vibrate", true);
    }
    
    /** Get the current value of the music option */      
    public static int getLife(Context context) {
        String lifeNum = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("life", "3");

        return Integer.parseInt(lifeNum);
    }
    
    /** Get the selected stage */      
    public static int getStage(Context context) {
        String    stageNo = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("selectstage", "1");

        int stage = 1;
        try {
            stage = Integer.parseInt(stageNo);
        }
        catch(Exception e) {
            stage = 1;
        }
        
        return stage;
    }
}

