package io.github.nanoforged.fml.utils;

public enum OSType {

    WINDOWS, LINUX, MAC, OTHER;

    private static final OSType CURRENT = detect();

    private static OSType detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return WINDOWS;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return MAC;
        }
        if (os.contains("linux") || os.contains("nix") || os.contains("nux")) {
            return LINUX;
        }
        return OTHER;
    }

    public static OSType current() {
        return CURRENT;
    }

    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    public static boolean isMac() {
        return CURRENT == MAC;
    }

    public static String arch() {
        return System.getProperty("os.arch", "unknown");
    }
}