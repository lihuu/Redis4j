package top.lihuu.redis4j;

import org.apache.commons.lang3.SystemUtils;

final class OSPlatform {

    private static final OS os = SystemUtils.IS_OS_WINDOWS ? OS.WINDOWS : SystemUtils.IS_OS_MAC ? OS.MAC : OS.LINUX;

    enum OS {
        LINUX("linux"),
        MAC("macaarch64"),
        WINDOWS("winx64");
        OS(String directoryName) {
            this.directoryName = directoryName;
        }

        private final String directoryName;

    }



    public static String getDirectoryName() {
        return get().directoryName;
    }


    static OS get() {
        return os;
    }

    public static boolean isWindows() {
        return os.equals(OSPlatform.OS.WINDOWS);
    }

    public static boolean isMacOS() {
        return OSPlatform.get().equals(OSPlatform.OS.MAC);
    }

    public static boolean isLinux() {
        return OSPlatform.get().equals(OSPlatform.OS.LINUX);
    }
}
