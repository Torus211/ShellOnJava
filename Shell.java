import java.io.*;
import java.util.*;
import sun.misc.Signal;

public class Shell {
    private static final List<String> history = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        try {
            // Обрабатываем сигнал SIGHUP
            Signal.handle(new Signal("HUP"), signal -> {
                System.out.println("Configuration reloaded");
            });
        } catch (Exception e) {
            System.err.println("Сигнал SIGHUP не поддерживается на этой платформе.");
        }

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
            } else if (input.startsWith("\\install")) {
                handleBootableCheck(input);
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

    // Проверка, является ли диск установочным
    private static void handleBootableCheck(String input) {
        String diskPath = input.substring(9).trim();
        File disk = new File(diskPath);

        if (!disk.exists() || !disk.isDirectory()) {
            System.out.println("Указанный путь не найден или это не директория: " + diskPath);
            return;
        }

        // Проверяем на наличие загрузочных файлов
        List<String> bootFiles = Arrays.asList("bootmgr", "grldr", "syslinux.cfg");
        boolean isBootable = false;

        for (String bootFile : bootFiles) {
            File bootCheck = new File(disk, bootFile);
            if (bootCheck.exists()) {
                isBootable = true;
                System.out.println("Найден загрузочный файл: " + bootCheck.getAbsolutePath());
            }
        }

        // Проверяем директории для Linux/EFI загрузчиков
        List<String> bootDirs = Arrays.asList("boot", "efi", "syslinux");
        for (String bootDir : bootDirs) {
            File bootCheck = new File(disk, bootDir);
            if (bootCheck.exists() && bootCheck.isDirectory()) {
                isBootable = true;
                System.out.println("Найдена загрузочная директория: " + bootCheck.getAbsolutePath());
            }
        }

        if (isBootable) {
            System.out.println("Диск " + diskPath + " является установочным.");
        } else {
            System.out.println("Диск " + diskPath + " не является установочным.");
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

        int processId;
        try {
            processId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.err.println("Неверный формат PID: " + parts[1]);
            return;
        }

        File memFile = new File("/proc/" + processId + "/mem");
        if (!memFile.exists()) {
            System.out.println("Процесс с PID " + processId + " не найден или не доступен.");
            return;
        }

        File outputDump = new File("memory_dump_" + processId + ".bin");
        try (FileInputStream fis = new FileInputStream(memFile);
             FileOutputStream fos = new FileOutputStream(outputDump)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            System.out.println("Дамп памяти сохранён в файл: " + outputDump.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при создании дампа памяти: " + e.getMessage());
        }
    }

    // Выполнение внешней команды
    private static void executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Ошибка выполнения команды: " + e.getMessage());
        }
    }
}
