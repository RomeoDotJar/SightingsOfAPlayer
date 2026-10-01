package net.romeo.sightingsofaplayer.myth;

public enum Myth {
    HEROBRINE("herobrine", "Herobrine"),
    ENTITY_303("entity_303", "Entity 303", false),
    NULL("null", "Null", false);

    private final String id;
    private final String displayName;
    private boolean choosable; // Keeping this private with public get/set so that I can control what happens on property change

    Myth(String id, String displayName, boolean choosable) {
        this.id = id;
        this.displayName = displayName;
        this.choosable = choosable;
    }

    Myth(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.choosable = true;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isChoosable() {
        return choosable;
    }

    public static Myth byId(String id) {
        if (id == null) return null;
        for (Myth myth : values()) {
            if (myth.id.equalsIgnoreCase(id) || myth.name().equalsIgnoreCase(id)) {
                return myth;
            }
        }
        return null;
    }
}

