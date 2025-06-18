package top.lihuu.redis4j;

import org.apache.commons.lang3.SystemUtils;

final class OSPlatform {

    enum OS {
        LINUX,
        MAC,
        WINDOWS
    }

    private static final OS os = SystemUtils.IS_OS_WINDOWS ? OS.WINDOWS : SystemUtils.IS_OS_MAC ? OS.MAC : OS.LINUX;

    static OS get() {
        return os;
    }

    public static boolean isWindows() {
        return os.equals(OSPlatform.OS.WINDOWS);
    }

    public static boolean isMacOS() {
        return OSPlatform.get().equals(OSPlatform.OS.MAC);
    }
}
