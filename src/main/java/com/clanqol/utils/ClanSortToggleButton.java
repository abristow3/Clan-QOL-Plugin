package com.clanqol.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

import java.util.function.BooleanSupplier;

@Slf4j
public class ClanSortToggleButton
{
	// carried over from Sight's sprite ids. havent independently confirmed
	// which one actually looks like an up vs down triangle, swap these two
	// if the icon looks backwards in game
	private static final int SPRITE_ASCENDING = 1051;
	private static final int SPRITE_DESCENDING = 1050;

	private static final int BUTTON_WIDTH = 8;
	private static final int BUTTON_HEIGHT = 8;

	private final Client client;
	private final ClientThread clientThread;
	private final int x;
	private final int y;
	private final String sortAscendingTooltip;
	private final String sortDescendingTooltip;
	private final BooleanSupplier isAscending;
	private final Runnable onAscendingClick;
	private final Runnable onDescendingClick;

	private Widget button;
	private Widget parentAtCreation;

	// isAscending should return true when this column's ascending mode is the
	// sorter's current mode. onAscendingClick/onDescendingClick should call
	// the sorter's matching setX methods
	public ClanSortToggleButton(Client client, ClientThread clientThread, int x, int y,
								String sortAscendingTooltip, String sortDescendingTooltip,
								BooleanSupplier isAscending, Runnable onAscendingClick, Runnable onDescendingClick)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.x = x;
		this.y = y;
		this.sortAscendingTooltip = sortAscendingTooltip;
		this.sortDescendingTooltip = sortDescendingTooltip;
		this.isAscending = isAscending;
		this.onAscendingClick = onAscendingClick;
		this.onDescendingClick = onDescendingClick;
	}

	// call once from startUp. panel probably isnt open yet so schedule it
	public void startUp()
	{
		clientThread.invokeLater(this::createButton);
	}

	// call from onGameTick amd recreates the button if the panel got closed and reopened
	public void onGameTick()
	{
		Widget currentParent = client.getWidget(InterfaceID.ClansSidepanel.UNIVERSE);

		if (currentParent == null)
		{
			// panel not open, drop stale refs so we rebuild cleanly once it
			// reopens
			button = null;
			parentAtCreation = null;
			return;
		}

		if (button == null || currentParent != parentAtCreation)
		{
			createButton();
			return;
		}

		syncButton();
	}

	// call from shutDown
	public void reset()
	{
		hideButton();
		parentAtCreation = null;
	}

	private void createButton()
	{
		Widget parent = client.getWidget(InterfaceID.ClansSidepanel.UNIVERSE);
		if (parent == null)
		{
			button = null;
			parentAtCreation = null;
			return;
		}

		hideButton();

		button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setOriginalWidth(BUTTON_WIDTH);
		button.setOriginalHeight(BUTTON_HEIGHT);
		button.setOriginalX(x);
		button.setOriginalY(y);
		button.setOnOpListener((JavaScriptCallback) ev -> clientThread.invokeLater(this::handleClick));
		button.setHasListener(true);
		button.revalidate();

		parentAtCreation = parent;

		syncButton();
	}

	private void handleClick()
	{
		if (isAscending.getAsBoolean())
		{
			onDescendingClick.run();
		}
		else
		{
			onAscendingClick.run();
		}

		syncButton();
	}

	private void syncButton()
	{
		if (button == null)
		{
			return;
		}

		boolean ascendingActive = isAscending.getAsBoolean();
		button.setSpriteId(ascendingActive ? SPRITE_ASCENDING : SPRITE_DESCENDING);
		button.setAction(0, ascendingActive ? sortDescendingTooltip : sortAscendingTooltip);
	}

	private void hideButton()
	{
		if (button == null)
		{
			return;
		}

		button.setHidden(true);
		button = null;
	}
}