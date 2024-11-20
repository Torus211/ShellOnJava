#!/bin/bash

# Обновление списка пакетов
echo "Обновляем список пакетов..."
sudo apt update

# Установка OpenJDK
echo "Устанавливаем OpenJDK..."
sudo apt install -y openjdk-11-jdk

# Проверка установки Java
echo "Проверка установленной версии Java..."
java -version

# Клонирование репозитория
echo "Клонируем репозиторий..."
git clone https://github.com/Torus211/ShellOnJava

# Переход в директорию проекта
cd ShellOnJava

# Компиляция Shell.java
echo "Компилируем Shell.java..."
javac Shell.java

# Уведомление о завершении
echo "Shell.java успешно скомпилирован. Теперь вы можете запустить программу командой: java Shell"
