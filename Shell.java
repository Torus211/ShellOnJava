import java.io.*;
import java.util.*;
import sun.misc.Signal;

public class Shell {
    private static final List<String> history = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // Обрабатываем сигнал SIGHUP
        Signal.handle(new Signal("HUP"), signal -> {
            System.out.println("Configuration reloaded");
        });

        Scanner scanner = new Scanner(System.in);
        System.out.println("Добро пожаловать в Shell! Для выхода введите exit или \\q.");
        System.out.print("> ");

        while (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            // Добавляем команду в историю
            history.add(input);

            if (input.equalsIgnoreCase("exit") || input.equals("\\q")) {
                saveHistory();
                System.out.println("До свидания!");
                break;
            }

            if (input.equals("history")) {
                printHistory();
            } else if (input.startsWith("echo ")) {
                handleEcho(input);
            } else if (input.startsWith("\\e ")) {
                handleEnv(input);
            } else if (input.startsWith("\\l ")) {
                handleDiskInfo(input);
            } else if (input.startsWith("\\cron")) {
                handleCron();
            } else if (input.startsWith("\\mem ")) {
                handleMemoryDump(input);
            } else {
                executeCommand(input);
            }

            System.out.print("> ");
        }

        scanner.close();
    }

    // Сохраняем историю команд в файл
    private static void saveHistory() {
        try (FileWriter writer = new FileWriter("history.txt")) {
            for (String command : history) {
                writer.write(command + "\n");
            }
            System.out.println("История команд сохранена в файл history.txt.");
        } catch (IOException e) {
            System.err.println("Ошибка сохранения истории: " + e.getMessage());
        }
    }

    // Печать истории команд
    private static void printHistory() {
        if (history.isEmpty()) {
            System.out.println("История команд пуста.");
        } else {
            System.out.println("История команд:");
            for (int i = 0; i < history.size(); i++) {
                System.out.println((i + 1) + ": " + history.get(i));
            }
        }
    }

    // Обработка команды echo
    private static void handleEcho(String input) {
        System.out.println(input.substring(5).trim());
    }

    // Вывод значения переменной окружения
    private static void handleEnv(String input) {
        String variable = input.substring(3).trim();
        if (variable.startsWith("$")) {
            variable = variable.substring(1);
        }
        String value = System.getenv(variable);
        if (value != null) {
            System.out.println(variable + "=" + value);
        } else {
            System.out.println("Переменная окружения " + variable + " не найдена.");
        }
    }

    // Информация о разделах
    private static void handleDiskInfo(String input) {
        String device = input.substring(3).trim();
        File devFile = new File(device);
        if (devFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/partitions"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(devFile.getName())) {
                        System.out.println(line.trim());
                    }
                }
            } catch (IOException e) {
                System.err.println("Ошибка чтения информации о разделах: " + e.getMessage());
            }
        } else {
            System.out.println("Устройство " + device + " не найдено.");
        }
    }

    // Подключение VFS для задач cron
    private static void handleCron() {
        File vfsFile = new File("/tmp/vfs");
        try (FileWriter writer = new FileWriter(vfsFile)) {
            File cronDir = new File("/var/spool/cron");
            if (cronDir.exists() && cronDir.isDirectory()) {
                for (File file : Objects.requireNonNull(cronDir.listFiles())) {
                    writer.write(file.getName() + "\n");
                }
                System.out.println("VFS для задач cron создан в /tmp/vfs.");
            } else {
                System.out.println("Директория для задач cron не найдена.");
            }
        } catch (IOException e) {
            System.err.println("Ошибка создания VFS: " + e.getMessage());
        }
    }

    // Дамп памяти процесса
    private static void handleMemoryDump(String input) {
        String[] parts = input.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: \\mem <procid>");
            return;
        }
        String procId = parts[1];
        File memFile = new File("/proc/" + procId + "/map_files");
        if (memFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(memFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.err.println("Ошибка чтения памяти процесса: " + e.getMessage());
            }
        } else {
            System.out.println("Процесс с ID " + procId + " не найден.");
        }
    }

    // Выполнение внешней команды
    private static void executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Ошибка выполнения команды: " + e.getMessage());
        }
    }
}
