package com.clanqol.clanpanel;

import com.google.inject.Singleton;

// shared toggle state
@Singleton
public class ClanDisplayModeState
{
    private volatile boolean showTimezones = false;

    public boolean isShowTimezones()
    {
        return showTimezones;
    }

    public void setShowTimezones(boolean showTimezones)
    {
        this.showTimezones = showTimezones;
    }
}