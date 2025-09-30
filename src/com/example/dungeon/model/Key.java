package com.example.dungeon.model;

public class Key extends Item {
    public Key(String name) {
        super(name);
    }

    @Override
    public void apply(GameState ctx) {
        System.out.println("Ключ " + getName() + " звенит. Используйте команду 'use [ключ] [направление]' для открытия двери.");
    }

}
