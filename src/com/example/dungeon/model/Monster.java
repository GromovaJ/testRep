package com.example.dungeon.model;

import java.util.ArrayList;
import java.util.List;

public class Monster extends Entity {
    private final int level;
    private  List<Item> loots = new ArrayList<>();

    public Monster(String name, int level, int hp) {
        super(name, hp);
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public List<Item> getLoots() {
        return loots;
    }

    public void setLoots(List<Item> loots) {
        this.loots = loots;
    }
}

