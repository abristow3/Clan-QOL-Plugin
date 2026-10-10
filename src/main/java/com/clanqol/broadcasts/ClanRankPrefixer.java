package com.clanqol.broadcasts;

import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

@Slf4j
public class ClanRankPrefixer {

	private static final int VERIFY_TICKS_AFTER_APPLY = 3;
	private static final String CA_ID_REGEX = "CA_ID:\\d+\\|";

	// keeps CA messages in memory to check and refresh icon for 6 hours then stops tracking
	private static final int CA_ICON_TTL_TICKS = 36000;

	private final Client client;
	private final ClientThread clientThread;
	private final ChatIconManager chatIconManager;
	private final Queue<PendingEdit> pendingEdits = new ArrayDeque<>();
	private final List<PendingCaIcon> pendingCaIcons = new ArrayList<>();

	public ClanRankPrefixer(Client client, ClientThread clientThread, ChatIconManager chatIconManager) {
		this.client = client;
		this.clientThread = clientThread;
		this.chatIconManager = chatIconManager;
	}

	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.CLAN_MESSAGE) {
			return;
		}

		String rawMessage = event.getMessage();

		ClanChannel clanChannel = client.getClanChannel();
		ClanSettings clanSettings = client.getClanSettings();
		if (clanChannel == null || clanSettings == null) {
			return;
		}

		// CA broadcasts only. same string used for detect and strip, keep in sync.
		String withoutTags = Text.removeTags(rawMessage).trim();
		boolean isCombatAchievement = withoutTags.matches("^" + CA_ID_REGEX + ".*");
		// names can have _ - nbsp instead of space, toJagexName fixes all three
		String strippedMessage = Text.toJagexName(withoutTags.replaceFirst("^" + CA_ID_REGEX, "")).trim();

		if (strippedMessage.startsWith("To talk in your clan's channel")) {
			return;
		}

		// fixing double icon adding due to timing issue with chat channels plugin join and leave messages
		if (strippedMessage.matches(".*\\bhas (joined|left)\\.?$")) {
			return;
		}

		ClanChannelMember matched = findMatchingMember(clanChannel, strippedMessage);
		if (matched == null) {
			log.debug("No clan member match for broadcast: '{}'", strippedMessage);
			return;
		}

		ClanTitle title = clanSettings.titleForRank(matched.getRank());
		if (title == null) {
			log.debug("No title found for rank {} (member {})", matched.getRank(), Text.toJagexName(matched.getName()));
			return;
		}

		int iconIndex = chatIconManager.getIconNumber(title);

		if (isCombatAchievement) {
			boolean alreadyPending = pendingCaIcons.stream().anyMatch(p -> p.cleanText.equals(strippedMessage));
			if (!alreadyPending) {
				pendingCaIcons.add(new PendingCaIcon(strippedMessage, iconIndex, CA_ICON_TTL_TICKS));
			}
			return;
		}

		String iconPrefix = iconPrefixFor(title, iconIndex);
		PendingEdit edit = new PendingEdit(event.getMessageNode(), rawMessage, iconPrefix, VERIFY_TICKS_AFTER_APPLY);
		applyEdit(edit);
		pendingEdits.add(edit);
	}

	private String iconPrefixFor(ClanTitle title, int iconIndex) {
		// -1 means no icon registered for rank, fall back to plain text bracket
		return iconIndex >= 0
				? "<img=" + iconIndex + "> "
				: "[" + title.getName() + "] ";
	}

	private ClanChannelMember findMatchingMember(ClanChannel clanChannel, String strippedMessage) {
		// longest match wins. stops short names matching inside longer ones
		ClanChannelMember matched = null;
		String matchedName = null;

		for (ClanChannelMember member : clanChannel.getMembers()) {
			String name = Text.toJagexName(member.getName());
			if (strippedMessage.startsWith(name) && (matchedName == null || name.length() > matchedName.length())) {
				matched = member;
				matchedName = name;
			}
		}

		return matched;
	}

	public void onGameTick(GameTick event) {
		Iterator<PendingCaIcon> caIt = pendingCaIcons.iterator();
		while (caIt.hasNext()) {
			PendingCaIcon icon = caIt.next();
			icon.ticksRemaining--;
			if (icon.ticksRemaining <= 0) {
				caIt.remove();
			}
		}

		if (pendingEdits.isEmpty()) {
			return;
		}

		int size = pendingEdits.size();
		for (int i = 0; i < size; i++) {
			PendingEdit edit = pendingEdits.poll();

			String current = edit.messageNode.getRuneLiteFormatMessage();
			if (current == null || !current.startsWith(edit.iconPrefix)) {
				applyEdit(edit);
			}

			edit.ticksRemaining--;
			if (edit.ticksRemaining > 0) {
				pendingEdits.add(edit);
			}
		}
	}

	// called every rendered frame (not every tick) for responsiveness only is doing finding/prepending
	public void processPendingCaIcons() {
		if (pendingCaIcons.isEmpty()) {
			return;
		}

        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		Widget[] children = scrollArea == null ? null : scrollArea.getDynamicChildren();
		if (children == null) {
			children = new Widget[0];
		}

		for (PendingCaIcon icon : pendingCaIcons) {
			Widget match = findMatchingLine(children, icon.cleanText);
			if (match == null) {
				continue;
			}

			String currentText = match.getText();
			String iconTag = "<img=" + icon.iconIndex + ">";
			if (currentText == null || !currentText.contains(iconTag)) {
				match.setText(iconTag + " " + (currentText == null ? "" : currentText));
				match.revalidate();
			}
		}
	}

	private Widget findMatchingLine(Widget[] children, String cleanText) {
		for (Widget child : children) {
			if (child == null || child.isHidden() || child.getText() == null || child.getText().isEmpty()) {
				continue;
			}

			if (Text.toJagexName(Text.removeTags(child.getText())).contains(cleanText)) {
				return child;
			}
		}

		return null;
	}

	private void applyEdit(PendingEdit edit) {
		clientThread.invokeLater(() -> {
			edit.messageNode.setRuneLiteFormatMessage(buildMessage(edit));
			client.refreshChat();
		});
	}

	// builds the message fresh at apply time so it reflects whatever formatting other plugins have added to the node
	private String buildMessage(PendingEdit edit) {
		String current = edit.messageNode.getRuneLiteFormatMessage();
		String body;
		if (current == null || current.isEmpty()) {
			body = edit.rawMessage;
		} else if (current.startsWith(edit.iconPrefix)) {
			body = current.substring(edit.iconPrefix.length());
		} else {
			body = current;
		}
		return edit.iconPrefix + body;
	}

	// an edit thats been applied once, still being watched for a few ticks
	// in case something reverts it
	private static class PendingEdit {
		final MessageNode messageNode;
		final String rawMessage;
		final String iconPrefix;
		int ticksRemaining;

		PendingEdit(MessageNode messageNode, String rawMessage, String iconPrefix, int ticksRemaining) {
			this.messageNode = messageNode;
			this.rawMessage = rawMessage;
			this.iconPrefix = iconPrefix;
			this.ticksRemaining = ticksRemaining;
		}
	}

	// a CA broadcast waiting for its icon, matched against widget text each tick
	private static class PendingCaIcon {
		final String cleanText;
		final int iconIndex;
		int ticksRemaining;

		PendingCaIcon(String cleanText, int iconIndex, int ticksRemaining) {
			this.cleanText = cleanText;
			this.iconIndex = iconIndex;
			this.ticksRemaining = ticksRemaining;
		}
	}
}