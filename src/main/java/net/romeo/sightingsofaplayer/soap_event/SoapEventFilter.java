package net.romeo.sightingsofaplayer.soap_event;

import java.util.HashSet;

public class SoapEventFilter {
    public final HashSet<SoapEvent.Categories> categoryWhitelist;
    public final HashSet<SoapEvent.Categories> categoryBlacklist;

    public SoapEventFilter(
            HashSet<SoapEvent.Categories> categoryWhitelist,
            HashSet<SoapEvent.Categories> categoryBlacklist
    ) {
        this.categoryBlacklist = categoryBlacklist;
        this.categoryWhitelist = categoryWhitelist;
    }

    public SoapEventFilter() {
        this.categoryBlacklist = new HashSet<>();
        this.categoryWhitelist = new HashSet<>();
    }

    public boolean fits(SoapEvent event) {
        return (categoryWhitelist.isEmpty()||categoryWhitelist.contains(event.category())) &&
                (categoryBlacklist.isEmpty()||!categoryBlacklist.contains(event.category()));
    }
}
