package com.clanqol.broadcasts;

import java.awt.Dimension;
import java.awt.Graphics2D;

import com.clanqol.ClanQolConfig;
import net.runelite.client.ui.overlay.Overlay;

public class ClanCaIconMaintainer extends Overlay
{
	private final ClanQolConfig config;
	private final ClanRankPrefixer clanRankPrefixer;

	public ClanCaIconMaintainer(ClanQolConfig config, ClanRankPrefixer clanRankPrefixer)
	{
		this.config = config;
		this.clanRankPrefixer = clanRankPrefixer;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.enabled())
		{
			clanRankPrefixer.processPendingCaIcons();
		}
		return null;
	}
}