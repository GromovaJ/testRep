package com.example.dungeon.model;

public class Door {
    private final Room targetRoom;
    private boolean locked;
    private final String requiredKey;
    private final String direction;

    public Door(Room targetRoom, String direction, boolean locked, String requiredKey) {
        this.targetRoom = targetRoom;
        this.direction = direction;
        this.locked = locked;
        this.requiredKey = requiredKey;
    }

    public Room getTargetRoom() {
        return targetRoom;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getRequiredKey() {
        return requiredKey;
    }

    public String getDirection() {
        return direction;
    }

    public boolean canOpenWith(Item item) {
        return item instanceof Key && item.getName().equals(requiredKey);
    }
}