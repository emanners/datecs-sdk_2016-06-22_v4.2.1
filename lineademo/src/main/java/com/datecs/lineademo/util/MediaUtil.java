package com.datecs.lineademo.util;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Vibrator;

public class MediaUtil {

    public static void playSound(Context context, int soundID){
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(500);

        MediaPlayer mp = MediaPlayer.create(context, soundID);
        mp.setVolume(1.0f, 1.0f);
        mp.start();
    }

}
