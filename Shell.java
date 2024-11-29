import java.io.*;
import java.nio.ByteBuffer;
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
                handleDiskCheck(input);  // Замена команды \l на проверку загрузочного диска
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

    // Проверка загрузочного диска
    private static void handleDiskCheck(String input) {
        String diskPath = input.substring(3).trim();  // Получаем путь к диску
        if (isBootableDisk(diskPath)) {
            System.out.println("Диск " + diskPath + " является загрузочным.");
        } else {
            System.out.println("Диск " + diskPath + " не является загрузочным.");
        }
    }

    // Метод для проверки загрузочного диска
    public static boolean isBootableDisk(String diskPath) {
        // Открываем файл устройства
        try (RandomAccessFile diskFile = new RandomAccessFile(diskPath, "r")) {
            byte[] buffer = new byte[512]; // Размер одного сектора
            int bytesRead = diskFile.read(buffer);
            
            // Проверяем, что удалось прочитать 512 байт
            if (bytesRead != 512) {
                System.out.println("Не удалось прочитать полный сектор.");
                return false;
            }

            // Проверяем последние два байта на сигнатуру 0x55AA
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byte lastByte = byteBuffer.get(510);  // 510-й байт (0x55)
            byte secondLastByte = byteBuffer.get(511); // 511-й байт (0xAA)

            return lastByte == (byte) 0x55 && secondLastByte == (byte) 0xAA;
        } catch (IOException e) {
            System.err.println("Ошибка при чтении диска: " + e.getMessage());
            return false;
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
        createCoreDump(procId);
    }

    // Метод для создания дампа памяти с использованием gcore
    private static void createCoreDump(String pid) {
        try {
            // Запуск команды gcore для создания дампа памяти
            ProcessBuilder processBuilder = new ProcessBuilder("gcore", pid);
            processBuilder.inheritIO();  // Наследуем ввод/вывод для отображения результатов
            Process process = processBuilder.start();
            int exitCode = process.waitFor();  // Ожидаем завершения команды
            if (exitCode == 0) {
                System.out.println("Дамп памяти для процесса " + pid + " успешно создан.");
            } else {
                System.err.println("Ошибка при создании дампа памяти.");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
