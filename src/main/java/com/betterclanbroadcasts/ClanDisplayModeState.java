package com.betterclanbroadcasts;

import com.google.inject.Singleton;

// shared toggle state
@Singleton
class ClanDisplayModeState
{
    private volatile boolean showTimezones = false;

    boolean isShowTimezones()
    {
        return showTimezones;
    }

    void setShowTimezones(boolean showTimezones)
    {
        this.showTimezones = showTimezones;
    }
}