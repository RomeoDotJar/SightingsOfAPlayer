package net.romeo.sightingsofaplayer.soap_event.impl.nullmyth.curse;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Null Curse event:
 * As a Curse (active when Null is Passive), Null will rarely text in chat small phrases like "null" or "help".
 */
public class EventNullCurseChat extends SoapEvent {

    public static final String ID = "null_curse_chat";

    private static final List<String> PHRASES = List.of(
            "null",
            "help",
            "...",
            "who is there",
            "it is dark",
            "where are you"
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Curse;
    }

    @Override
    public int tensionGain() {
        return 5;
    }

    @Override
    public float selectionWeight() {
        // Runs rarely
        return 0.5f;
    }

    @Override
    public float nextDelayMultiplier() {
        return .01f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isCurseActive(Myth.NULL) && !context.players().isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        String phrase = PHRASES.get(context.random().nextInt(PHRASES.size()));
        Component message = Component.literal("<Null> " + phrase);
        context.server().getPlayerList().broadcastSystemMessage(message, false);
        return true;
    }
}
