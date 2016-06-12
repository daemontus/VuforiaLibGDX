package com.github.daemontus.ar.vuforia;


import com.vuforia.State;

/**
 * Interface you neet to implement if you want to control vuforia
 *
 * Created by daemontus on 03/04/14.
 */
public interface SessionControl {

    // To be called to initialize the trackers
    boolean doInitTrackers();


    // To be called to load the trackers' data
    boolean doLoadTrackersData();


    // To be called to start tracking with the initialized trackers and their
    // loaded data
    boolean doStartTrackers();


    // To be called to stop the trackers
    boolean doStopTrackers();


    // To be called to destroy the trackers' data
    boolean doUnloadTrackersData();


    // To be called to deinitialize the trackers
    boolean doDeinitTrackers();


    // This callback is called after the Vuforia initialization is complete,
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    void onInitARDone(VuforiaException e);


    // This callback is called every cycle
    void onQCARUpdate(State state);
}
