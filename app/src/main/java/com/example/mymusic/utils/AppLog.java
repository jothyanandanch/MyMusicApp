package com.example.mymusic.utils;

import android.util.Log;

public class AppLog {
    public static final String PERMISSION = "MyMusicLog:Permission";
    public static final String REPO = "MyMusicLog:Repo";
    public static final String PLAYER = "MyMusicLog:Player";

    //For Debugging (For Understanding Workflow)
    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    //For Errors(Shown in Red Color)
    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    //For Info
    public static void i(String tag,String message){
        Log.i(tag,message);
    }
}
