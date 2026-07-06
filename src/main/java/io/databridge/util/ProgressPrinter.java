package io.databridge.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Imprime progresso da migração no terminal.
 * Usa ANSI escape codes para cor — funciona em Linux/macOS/Windows Terminal.
 */
public class ProgressPrinter {

    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String CYAN   = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String DIM    = "\u001B[2m";

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String routineName;

    public ProgressPrinter(String routineName) {
        this.routineName = routineName;
    }

    public void print(int page, int totalSoFar) {
        String time = LocalTime.now().format(TIME_FMT);
        System.out.printf(
            "%s[%s]%s %s%-25s%s page=%-4d total=%s%d%s%n",
            DIM, time, RESET,
            CYAN, routineName, RESET,
            page,
            GREEN, totalSoFar, RESET
        );
    }

    public static void printHeader(String message) {
        System.out.println(YELLOW + "==> " + message + RESET);
    }

    public static void printSuccess(String message) {
        System.out.println(GREEN + "[OK] " + message + RESET);
    }

    public static void printError(String message) {
        System.out.println("\u001B[31m[FAIL] " + message + RESET);
    }
}
