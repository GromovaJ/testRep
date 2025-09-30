package com.example.dungeon.core;
// Пользовательское исключение
public class InvalidCommandException extends RuntimeException {
    public InvalidCommandException(String m) {
        super(m);
    }
}
