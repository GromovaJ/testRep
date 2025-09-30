package com.example.dungeon.model;

import java.util.*;

public class Room {
    private String name;
    private final String description;
    private final Map<String, Room> neighbors = new HashMap<>();
    private final Map<String, Door> doors = new HashMap<>(); // Добавляем двери
    private final List<Item> items = new ArrayList<>();
    private Monster monster;

    public Room(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Room> getNeighbors() {
        return neighbors;
    }

    public Map<String, Door> getDoors() {
        return doors;
    }

    public List<Item> getItems() {
        return items;
    }

    public Monster getMonster() {
        return monster;
    }

    public void setMonster(Monster m) {
        this.monster = m;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(name + ": " + description);
        if (!items.isEmpty()) {
            sb.append("\nПредметы: ").append(String.join(", ", items.stream().map(Item::getName).toList()));
        }
        if (monster != null) {
            sb.append("\nВ комнате монстр: ").append(monster.getName()).append(" (ур. ").append(monster.getLevel()).append(")");
        }
        if (!neighbors.isEmpty()) {
            sb.append("\nВыходы: ").append(String.join(", ", neighbors.keySet()));
        }
        if (!doors.isEmpty()) {
            sb.append("\nДвери: ").append(String.join(", ", doors.keySet()));
        }
        return sb.toString();
    }
    // Метод для добавления запертой двери
    public void addLockedDoor(String direction, Room targetRoom, String requiredKey) {
        Door door = new Door(targetRoom, direction, true, requiredKey);
        doors.put(direction, door);
    }

    // Метод для открытия двери
    public boolean unlockDoor(String direction, Item key) {
        Door door = doors.get(direction);
        if (door != null && door.isLocked() && door.canOpenWith(key)) {
            door.setLocked(false);
            // После открытия добавляем комнату в обычные соседи
            neighbors.put(direction, door.getTargetRoom());
            return true;
        }
        return false;
    }
}
