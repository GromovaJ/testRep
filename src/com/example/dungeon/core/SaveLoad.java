package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");

    // Сохранение игры
    public static void save(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            // Сохраняем данные игрока
            Player p = s.getPlayer();
            w.write("player;" + p.getName() + ";" + p.getHp() + ";" + p.getAttack());
            w.newLine();
            // Сохраняем инвентарь
            String inv = p.getInventory().stream().map(i -> i.getClass().getSimpleName() + ":" + i.getName()).collect(Collectors.joining(","));
            w.write("inventory;" + inv);
            w.newLine();
            // Сохраняем текущую комнату
            w.write("room;" + s.getCurrent().getName());
            w.newLine();

            // Сохраняем информацию о комнатах и их связях
            saveRooms(w, s);

            System.out.println("Сохранено в " + SAVE.toAbsolutePath());
            writeScore(p.getName(), s.getScore());
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    // Сохранение информации о комнатах и их связях
    private static void saveRooms(BufferedWriter w, GameState s) throws IOException {
        // Собираем все уникальные комнаты
        Set<Room> allRooms = new HashSet<>();
        collectAllRooms(s.getCurrent(), allRooms);

        // 2. Сохраняем (сериализация) каждую комнату
        for (Room room : allRooms) {
            StringBuilder roomLine = new StringBuilder();
            roomLine.append("room_data;").append(room.getName()).append(";");

            // 2.1 Описание комнаты
            String description = room.getDescription();
            roomLine.append("description:").append(description).append(";");

            // 2.2 Предметы в комнате
            if (!room.getItems().isEmpty()) {
                String itemsStr = room.getItems().stream()
                        .map(item -> item.getClass().getSimpleName() + ":" + item.getName())
                        .collect(Collectors.joining("|"));
                roomLine.append("items:").append(itemsStr).append(";");
            }

            // 2.3. Монстры(с полной информацией)
            if (room.getMonster() != null) {
                Monster monster = room.getMonster();
                String monsterStr = monster.getName()  + "," + monster.getLevel() + "," + monster.getHp();

                // Лут монстра, если есть
                if (monster.getLoots() != null && !monster.getLoots().isEmpty()) {
                    String lootStr = monster.getLoots().stream()
                            .map(item -> item.getClass().getSimpleName() + ":" + item.getName())
                            .collect(Collectors.joining("|"));
                    monsterStr += "," + lootStr;
                }

                roomLine.append("monster:").append(monsterStr).append(";");
            }

            // 2.4 Связи с соседними комнатами
            if (!room.getNeighbors().isEmpty()) {
                String neighborsStr = room.getNeighbors().entrySet().stream()
                        .map(entry -> entry.getKey() + ">" + entry.getValue().getName())
                        .collect(Collectors.joining("|"));
                roomLine.append("neighbors:").append(neighborsStr).append(";");
            }

            // 2.5 Двери
            if (!room.getDoors().isEmpty()) {
                String doorsStr = room.getDoors().entrySet().stream()
                        .map(entry -> {
                            Door door = entry.getValue();
                            return entry.getKey() + ">" + door.getTargetRoom().getName() + ">" +
                                    door.isLocked() + ">" + door.getRequiredKey();
                        })
                        .collect(Collectors.joining("|"));
                // Формат: "направление>комната>закрыта>ключ"
                roomLine.append("doors:").append(doorsStr).append(";");
            }

            w.write(roomLine.toString());
            w.newLine();
        }
    }

    // Рекурсивный обход карты комнат
    private static void collectAllRooms(Room startRoom, Set<Room> visited) {
        if (startRoom == null || visited.contains(startRoom)) {
            return;
        }
        // Добавляем текущую комнату
        visited.add(startRoom);

        // Рекурсивно обходим всех соседей
        for (Room neighbor : startRoom.getNeighbors().values()) {
            collectAllRooms(neighbor, visited);
        }
    }

    // Загрузка игры
    public static void load(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение не найдено.");
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, String> map = new HashMap<>(); // Основные данные
            List<String> roomDataLines = new ArrayList<>();  // Данные комнат

            // Чтение и парсинг файла
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.split(";", 2); // Разделитель ";", максимум 2 части
                if (parts.length == 2) {
                    if (parts[0].equals("room_data")) {
                        roomDataLines.add(line); // Данные комнат в отдельный список
                    } else {
                        map.put(parts[0], parts[1]); // Остальное в Map
                    }
                }
            }

            // Восстанавление игрока
            Player p = s.getPlayer();
            String[] pp = map.getOrDefault("player", "player;Hero;10;3").split(";");
            p.setName(pp[0]);
            p.setHp(Integer.parseInt(pp[1]));
            p.setAttack(Integer.parseInt(pp[2]));
            p.getInventory().clear(); // Очистка старого инвентаря

            // Восстановление инвентаря игрока
            String inv = map.getOrDefault("inventory", "");
            if (!inv.isBlank()) {
                for (String tok : inv.split(",")) { // Разделитель ","
                    String[] t = tok.split(":", 2); // Разделитель ":"
                    if (t.length < 2) continue;

                    // Создание предметов
                    switch (t[0]) {
                        case "Potion" -> p.getInventory().add(new Potion(t[1], 5));
                        case "Key" -> p.getInventory().add(new Key(t[1]));
                        case "Weapon" -> p.getInventory().add(new Weapon(t[1], 3));
                        default -> {
                        }
                    }
                }
            }

            // Восстановление комнат и их связей
            Map<String, Room> loadedRooms = loadRooms(roomDataLines);
            // Установка текущей комнаты
            String currentRoomName = map.getOrDefault("room", "Начало");

            Room currentRoom = loadedRooms.get(currentRoomName);
            if (currentRoom != null) {
                setCurrentRoom(s, currentRoom); // Рефлексия для установки private поля
                System.out.println("Текущая комната: " + currentRoomName);

                // Проверяем, есть ли монстр в текущей комнате
                if (currentRoom.getMonster() != null) {
                    System.out.println("В комнате монстр: " + currentRoom.getMonster().getName());
                }
            } else {
                System.out.println("Ошибка: комната '" + currentRoomName + "' не найдена");
            }

            System.out.println("Игра загружена (с восстановлением карты и монстров).");
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        }
    }

    // Восстановление карты комнат
    private static Map<String, Room> loadRooms(List<String> roomDataLines) {
        Map<String, Room> rooms = new HashMap<>();
        Map<String, String[]> roomConnections = new HashMap<>();
        Map<String, String[]> roomDoors = new HashMap<>();

        // Первый проход: создание комнат и сбор данных
        for (String line : roomDataLines) {
            String[] parts = line.split(";");
            String roomName = parts[1];
            // Инициализируем описание значением по умолчанию
            String description = "Восстановленная комната";

            // Извлекаем описание
            for (int i = 2; i < parts.length; i++) {
                String data = parts[i];
                if (data.startsWith("description:")) {
                    // Нашли сохраненное описание - используем его
                    description = data.substring(12); // "description:".length() = 12
                    break; // выходим из цикла, так как описание нашли
                }
            }

            // Создание комнаты с извлеченным описанием
            Room room = new Room(roomName, description);
            rooms.put(roomName, room);

            // Обработка компонентов комнаты:  предметы, монстры, связи и двери
            for (int i = 2; i < parts.length; i++) {
                String data = parts[i];

                if (data.startsWith("items:")) {
                    // Восстановление предметов
                    String itemsStr = data.substring(6);
                    if (!itemsStr.isEmpty()) {
                        for (String itemToken : itemsStr.split("\\|")) {
                            String[] itemParts = itemToken.split(":", 2);
                            if (itemParts.length == 2) {
                                Item item = createItem(itemParts[0], itemParts[1]);
                                if (item != null) {
                                    room.getItems().add(item);
                                }
                            }
                        }
                    }
                } else if (data.startsWith("monster:")) {
                    // Восстановление монстров
                    String monsterData = data.substring(8);
                    String[] monsterParts = monsterData.split(",", 4);

                    if (monsterParts.length >= 3) {
                        String monsterName = monsterParts[0];
                        int level = Integer.parseInt(monsterParts[1]);
                        int hp = Integer.parseInt(monsterParts[2]);
                        // Создание монстра
                        Monster monster = new Monster(monsterName, level, hp);
                        monster.setHp(hp);

                        // Восстановление лута монстра, если есть
                        if (monsterParts.length == 4 && !monsterParts[3].isEmpty()) {
                            List<Item> loot = new ArrayList<>();
                            for (String lootToken : monsterParts[3].split("\\|")) {
                                String[] lootParts = lootToken.split(":", 2);
                                if (lootParts.length == 2) {
                                    Item lootItem = createItem(lootParts[0], lootParts[1]);
                                    if (lootItem != null) {
                                        loot.add(lootItem);
                                    }
                                }
                            }
                            monster.setLoots(loot);
                        }

                        // Устанавливаем монстра в комнату
                        room.setMonster(monster);
                        System.out.println("Восстановлен монстр: " + monsterName + " в комнате " + roomName);
                    }
                } else if (data.startsWith("neighbors:")) {
                    // Сохранение информации о связях для второго прохода
                    roomConnections.put(roomName, data.substring(10).split("\\|"));
                } else if (data.startsWith("doors:")) {
                    // Сохранение информации о дверях для второго прохода
                    roomDoors.put(roomName, data.substring(6).split("\\|"));
                }
            }
        }

        // Второй проход: установка связей между комнатами
        for (Map.Entry<String, String[]> entry : roomConnections.entrySet()) {
            Room room = rooms.get(entry.getKey());
            String[] connections = entry.getValue();

            for (String connection : connections) {
                String[] connParts = connection.split(">");
                if (connParts.length == 2) {
                    String direction = connParts[0];
                    String targetRoomName = connParts[1];
                    Room targetRoom = rooms.get(targetRoomName);
                    if (targetRoom != null) {
                        room.getNeighbors().put(direction, targetRoom);
                    }
                }
            }
        }

        // Второй проход: установка дверей
        for (Map.Entry<String, String[]> entry : roomDoors.entrySet()) {
            Room room = rooms.get(entry.getKey());
            String[] doors = entry.getValue();

            for (String doorData : doors) {
                String[] doorParts = doorData.split(">");
                if (doorParts.length == 4) {
                    String direction = doorParts[0];
                    String targetRoomName = doorParts[1];
                    boolean locked = Boolean.parseBoolean(doorParts[2]);
                    String requiredKey = doorParts[3];

                    Room targetRoom = rooms.get(targetRoomName);
                    if (targetRoom != null) {
                        // Создаем дверь
                        Door door = new Door(targetRoom, direction, locked, requiredKey);
                        room.getDoors().put(direction, door);

                        // Если дверь не заперта, добавляем связь в соседи
                        if (!locked) {
                            room.getNeighbors().put(direction, targetRoom);
                        }
                    }
                }
            }
        }

        return rooms;
    }


    private static Item createItem(String type, String name) {
        switch (type) {
            case "Potion" -> { return new Potion(name, 5); }
            case "Key" -> { return new Key(name); }
            case "Weapon" -> { return new Weapon(name, 3); }
            default -> { return null; }
        }
    }

    // Установка текущей комнаты (рефлексия)
    private static void setCurrentRoom(GameState s, Room room) {
        try {
            java.lang.reflect.Field currentRoomField = s.getClass().getDeclaredField("current");
            currentRoomField.setAccessible(true);
            currentRoomField.set(s, room);
        } catch (Exception e) {
            System.err.println("Не удалось установить текущую комнату: " + e.getMessage());
        }
    }

    // Вывод таблицы лидеров
    public static void printScores() {
        if (!Files.exists(SCORES)) { // Заголовок только для нового файла
            System.out.println("Пока нет результатов.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("Таблица лидеров (топ-10):");
            r.lines().skip(1) // Пропускаем заголовок
                    .map(l -> l.split(",")) // Разделяем CSV
                    .map(a -> new Score(a[1], Integer.parseInt(a[2]))) // Создаем объекты Score
                    .sorted(Comparator.comparingInt(Score::score).reversed()) // Сортировка по убыванию
                    .limit(10) // Топ-10
                    .forEach(s -> System.out.println(s.player() + " — " + s.score())); // CSV заголовок
        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        }
    }

    // Запись результата в таблицу лидеров
    private static void writeScore(String player, int score) {
        try {
            boolean header = !Files.exists(SCORES); // Заголовок только для нового файла
            try (BufferedWriter w = Files.newBufferedWriter(SCORES, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (header) {
                    w.write("ts,player,score"); // CSV заголовок
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    // Record для хранения результатов
    private record Score(String player, int score) {
        // Автоматически создает:
        // - конструктор Score(String player, int score)
        // - методы player(), score()
        // - equals(), hashCode(), toString()
    }
}