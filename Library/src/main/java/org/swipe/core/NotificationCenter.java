package org.swipe.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by pete on 11/11/16.
 */

public class NotificationCenter {

    //static reference for singleton
    private static NotificationCenter _instance;

    private HashMap<String, ArrayList<Runnable>> registredObjects;

    //default c'tor for singleton
    private NotificationCenter(){
        registredObjects = new HashMap<String, ArrayList<Runnable>>();
    }

    //returning the reference
    public static synchronized NotificationCenter defaultCenter(){
        if(_instance == null)
            _instance = new NotificationCenter();
        return _instance;
    }

    public synchronized void addFunctionForNotification(String notificationName, Runnable r){
        ArrayList<Runnable> list = registredObjects.get(notificationName);
        if(list == null) {
            list = new ArrayList<Runnable>();
            registredObjects.put(notificationName, list);
        }
        list.add(r);
    }

    public synchronized void removeFunctionForNotification(String notificationName, Runnable r){
        ArrayList<Runnable> list = registredObjects.get(notificationName);
        if(list != null) {
            list.remove(r);
        }
    }

    public synchronized void postNotification(String notificationName){
        ArrayList<Runnable> list = registredObjects.get(notificationName);
        if(list != null) {
            for(Runnable r: list)
                r.run();
        }
    }

}