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

     // Проверка, является ли файл или устройство загрузочным
    private static void checkBootableFile(String command) {
        String filePath = command.substring(3).trim();
        File targetFile = new File(filePath);

        if (!targetFile.exists()) {
            System.out.println("Файл или устройство " + filePath + " не найдено.");
            return;
        }

        try (RandomAccessFile fileReader = new RandomAccessFile(targetFile, "r")) {
            byte[] sectorData = new byte[512];
            int bytesRead = fileReader.read(sectorData);

            if (bytesRead < 512) {
                System.out.println("Ошибка: не удалось прочитать полный загрузочный сектор.");
                return;
            }

            int bootSignature = ((sectorData[510] & 0xFF) << 8) | (sectorData[511] & 0xFF);
            if (bootSignature == 0xAA55) {
                System.out.println("Файл или устройство " + filePath + " является загрузочным (сигнатура 0xAA55).");
            } else {
                System.out.println("Файл или устройство " + filePath + " не является загрузочным.");
            }
        } catch (IOException error) {
            System.err.println("Ошибка чтения файла или устройства " + filePath + ": " + error.getMessage());
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

   // Генерация дампа памяти процесса
    private static void generateMemoryDump(int processId, String dumpFilePath) throws IOException, InterruptedException {
        String[] dumpCommand = {
            "bash", "-c",
            String.format(
                "gdb --batch -p %d -ex 'gcore %s' -ex 'detach' -ex 'quit'",
                processId, dumpFilePath
            )
        };

        Process dumpProcess = Runtime.getRuntime().exec(dumpCommand);

        try (BufferedReader dumpOutput = new BufferedReader(new InputStreamReader(dumpProcess.getInputStream()))) {
            String outputLine;
            while ((outputLine = dumpOutput.readLine()) != null) {
                System.out.println(outputLine);
            }
        }

        int exitStatus = dumpProcess.waitFor();
        if (exitStatus == 0) {
            System.out.println("Дамп памяти успешно создан по пути: " + dumpFilePath);
        } else {
            System.err.println("Не удалось создать дамп памяти. Код завершения: " + exitStatus);
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
