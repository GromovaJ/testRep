package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));

        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });

        // Добавлена команда about
        commands.put("about", (ctx, a) -> {
            String gameName = System.getProperty("game.name", "DungeonMini");
            String gameVersion = System.getProperty("game.version", "1.0");
            String javaVersion = System.getProperty("java.version");

            System.out.println("Название игры: " + gameName);
            System.out.println("Версия игры: " + gameVersion);
            System.out.println("Версия Java: " + javaVersion);
        });

        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));

        // Реализация перемещения
        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                System.out.println("Укажите направление: move [направление]");
                return;
            }
            String direction = a.getFirst().toLowerCase();
            Room current = ctx.getCurrent();

            // Проверяем обычные выходы
            if (current.getNeighbors().containsKey(direction)) {
                Room nextRoom = current.getNeighbors().get(direction);
                ctx.setCurrent(nextRoom);
                System.out.println("Вы перешли в: " + nextRoom.getName());
                System.out.println(nextRoom.describe());
            }
            // Проверяем двери
            else if (current.getDoors().containsKey(direction)) {
                Door door = current.getDoors().get(direction);
                if (door.isLocked()) {
                    System.out.println("Дверь заперта! Нужен ключ: " + door.getRequiredKey()+". Используйте команду: use [ключ] on [направление]" );
                }
            } else {
                System.out.println("Туда нельзя пойти.");
            }
        });

        // Реализация взятия предмета
        commands.put("take", (ctx, a) -> {
            // Проверка аргументов
            if (a.isEmpty()) throw new InvalidCommandException("Укажите название предмета");

            // Подготовка данных
            String itemName = String.join(" ", a);
            Room current = ctx.getCurrent();
            Player player = ctx.getPlayer();

            // Поиск предмета в комнате
            // С Optional - явно показывает, что значение может отсутствовать (защита от NullPointerException)
            Optional<Item> itemToTake = current.getItems().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
                    .findFirst();

            // Проверка найденного предмета
            if (itemToTake.isEmpty()) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в комнате");
            }

            // Перемещение предмета
            Item item = itemToTake.get();
            current.getItems().remove(item);
            player.getInventory().add(item);
            System.out.println("Взято: " + item.getName());
        });

        // Реализация инвентаря игрока
        commands.put("inventory", (ctx, a) -> {
            Player player = ctx.getPlayer();

            if (player.getInventory().isEmpty()) {
                System.out.println("Инвентарь пуст");
                return;
            }
            // Группировка по типу предмета и сортировка по названию
            player.getInventory().stream()            // Создаем поток из элементов инвентаря
                    .collect(Collectors.groupingBy(   // Группируем предметы (элементы) по типу (имени класса)
                            item -> item.getClass().getSimpleName(),
                            TreeMap::new,       //С охраняем результат в TreeMap для автоматической сортировки
                            Collectors.toList() // Собираем предметы (элементы) в ArrayList
                    )) // Результат: TreeMap<String, List<Item>>,
                      // где: Ключи - отсортированы по алфавиту (т.к. TreeMap), Значения - списки предметов каждого типа
                    .forEach((type, items) -> {
                        // Сортировка предметов по имени
                        List<Item> sortedItems = items.stream()
                                .sorted(Comparator.comparing(Item::getName)) // Сортировка по имени предмета
                                .toList();                                   // Преобразование в список

                        System.out.println("- " + type + " (" + sortedItems.size() + "): " +
                                //Преобразуем коллекцию отсортированных предметов в строку, содержащую их названия, разделённые запятыми
                                sortedItems.stream()
                                        .map(Item::getName)                          // Преобразование Item -> String (имя)
                                        .collect(Collectors.joining(", "))); // Объединение всех строк через запятую
                    });
        });

        // Реализация использования предмета
        commands.put("use", (ctx, a) -> {
            // Проверяем ввел ли пользователь какие-либо аргументы после команды use
            if (a.isEmpty()) {
                System.out.println("Укажите предмет: use [предмет] или use [предмет] on [направление]");
                return;
            }

            // Ищем ключевое слово "on" для разделения предмета и направления
            int onIndex = a.indexOf("on");
            String itemName;
            String direction = null;

            if (onIndex != -1 && onIndex < a.size() - 1) {
                // Есть направление: use [многословный предмет] on [направление]
                itemName = String.join(" ", a.subList(0, onIndex)); // берем все аргументы до "on" и объединяет их в строку с пробелами
                direction = a.get(onIndex + 1).toLowerCase(); // берем аргумент сразу после "on"?переводит в нижний регистр
            } else {
                // Нет направления: просто использование предмета
                itemName = String.join(" ", a); // берем все аргументы и объединяет их в строку с пробелами
            }

            Player player = ctx.getPlayer(); // Получаем объект игрока из контекста
            // Поиск предмета в инвентаре
            Optional<Item> item = player.getInventory().stream() // Создаем Stream из инвентаря игрока
                    .filter(i -> i.getName().equalsIgnoreCase(itemName)) //Фильтруем предметы по названию (без учета регистра)
                    .findFirst(); // Ищем первый подходящий предмет

            if (item.isEmpty()) {
                System.out.println("У вас нет предмета: " + itemName);
                return;
            }

            // Если указано направление - пытаемся открыть дверь
            if (direction != null) {
                if (item.get() instanceof Key key) { // Проверка типа предмета: является ли предмет ключом
                    if (ctx.getCurrent().unlockDoor(direction, key)) { // Попытка открыть дверь:
                        System.out.println("Щелк! Дверь в направлении " + direction + " открыта!");
                        player.getInventory().remove(key); // Удаление ключа из инвентаря
                    } else {
                        // Неудачное открытие: Сообщение об ошибке
                        System.out.println("Ключ не подходит или дверь не найдена.");
                    }
                } else {
                    // Неподходящий предмет: Сообщение, что предмет нельзя использовать для дверей
                    System.out.println("Этот предмет нельзя использовать для открытия дверей.");
                }
            } else {
                // Обычное использование предмета
                item.get().apply(ctx);
            }
        });

        // Реализуем бой с монстром
        commands.put("fight", (ctx, a) -> {
            Room current = ctx.getCurrent();
            Monster monster = current.getMonster();
            Player player = ctx.getPlayer();

            if (monster == null) {
                throw new InvalidCommandException("В этой комнате нет монстров");
            }

            System.out.println("Начинается бой с " + monster.getName() + " (ур. " + monster.getLevel() + ")");

            Scanner scanner = new Scanner(System.in);

            try {
                // Игровой цикл боя (ход игрока -> ход монстра)
                while (player.getHp() > 0 && monster.getHp() > 0) {
                    System.out.println("\nВаше HP: " + player.getHp() + ", HP монстра: " + monster.getHp());
                    // Ход игрока
                    System.out.print("Нажмите Enter для атаки или 'бежать' для отступления: ");
                    String input = scanner.nextLine();

                    if ("бежать".equalsIgnoreCase(input)) {
                        System.out.println("Вы сбежали из боя!");
                        return;
                    }

                    // Игрок атакует
                    int playerDamage = player.getAttack();
                    monster.setHp(monster.getHp() - playerDamage);
                    System.out.println("Вы бьёте " + monster.getName() + " на " + playerDamage +
                            ". HP монстра: " + Math.max(0, monster.getHp()));
                    // Проверка победы над монстром
                    if (monster.getHp() <= 0) {
                        System.out.println("Вы победили " + monster.getName() + "!");

                        // Вывод лута монстра
                        List<Item> monsterLoot = monster.getLoots();
                        if (monsterLoot != null) {
                        if (!monsterLoot.isEmpty()) {
                            System.out.println("Монстр выронил: " +
                                    // Получение лута монстра
                                    monsterLoot.stream()
                                            .map(Item::getName)
                                            .collect(Collectors.joining(", ")));

                            // Добавление лута в комнату
                            current.getItems().addAll(monsterLoot);
                        }
                    }
                        current.setMonster(null); // Удаление монстра из комнаты
                        ctx.addScore(monster.getLevel() * 10); // Начисление очков
                        return;
                    }

                    // Монстр атакует
                    int monsterDamage = monster.getLevel();
                    player.setHp(player.getHp() - monsterDamage);
                    System.out.println(monster.getName() + " отвечает на " + monsterDamage +
                            ". Ваше HP: " + Math.max(0, player.getHp()));

                    if (player.getHp() <= 0) {
                        System.out.println("Вы погибли! Игра окончена.");
                        System.exit(0);
                    }
                }
            } finally {
                // Не закрываем scanner, чтобы System.in оставался доступным
            }
        });
        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        Room treasureRoom = new Room("Сокровищница", "Золото, драгоценности и волшебные предметы");

        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        cave.getNeighbors().put("west", forest);
        treasureRoom.getNeighbors().put("west", cave);
        cave.addLockedDoor("east", treasureRoom, "Ключ от сокровищницы");

        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.getItems().add(new Potion("Приворотное зелье", 15));
        treasureRoom.getItems().add(new Weapon("Магический меч", 10));

        forest.setMonster(new Monster("Волк", 1, 8));
        treasureRoom.setMonster(new Monster("Гоблин", 7, 20));
        forest.getMonster().getLoots().add(new Key("Ключ от сокровищницы"));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        // Создает BufferedReader для чтения ввода с консоли (автоматически закрывается)
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                // Чтение ввода
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                // Парсинг команды
                List<String> parts = Arrays.asList(line.split("\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());

                //Поиск и выполнение команды
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);

                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    // Обработка ожидаемых ошибок - понятные сообщения для игрока
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    // Обработка непредвиденных ошибок - техническая информация
                    System.out.println("Непредвиденная ошибка1: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
