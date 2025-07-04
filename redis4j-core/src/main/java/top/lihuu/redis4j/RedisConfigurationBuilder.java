/*
 * #%L
 * MariaDB4j
 * %%
 * Copyright (C) 2012 - 2014 Michael Vorburger
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package top.lihuu.redis4j;

import ch.vorburger.exec.ManagedProcessListener;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static top.lihuu.redis4j.RedisConfiguration.Executable.*;

public class RedisConfigurationBuilder {

    private static final String DEFAULT_DATA_DIR = "/data";

    private String databaseVersion = null;

    private String osDirectoryName = OSPlatform.getDirectoryName();

    protected File baseDir = new File(SystemUtils.JAVA_IO_TMPDIR + "/Redis4j/base");

    protected File dataDir = new File(SystemUtils.JAVA_IO_TMPDIR + "/Redis4j" + DEFAULT_DATA_DIR);
    private File initRdbFile = null; // see initAofFile()
    protected String socket = null; // see _getSocket()
    protected int port = 0;
    protected boolean isDeletingTemporaryBaseAndDataDirsOnShutdown = true;
    protected boolean isUnpackingFromClasspath = true;
    protected List<String> args = new ArrayList<>();

    private boolean frozen = false;
    private ManagedProcessListener listener;

    protected String defaultCharacterSet = null;
    protected Map<RedisConfiguration.Executable, Supplier<File>> executables = new HashMap<>();

    public static RedisConfigurationBuilder newBuilder() {
        return new RedisConfigurationBuilder();
    }

    protected RedisConfigurationBuilder() {
    }

    protected void checkIfFrozen(String setterName) {
        if (frozen) {
            throw new IllegalStateException("cannot " + setterName + "() anymore after build()");
        }
    }

    public File getBaseDir() {
        return baseDir;
    }

    public String path() {
        return "Redis4j/" + java.util.UUID.randomUUID().toString() + "-" + port + "/";
    }

    public RedisConfigurationBuilder setBaseDir(File baseDir) {
        checkIfFrozen("setBaseDir");
        this.baseDir = baseDir;
        return this;
    }

    public File getDataDir() {
        return dataDir;
    }

    public RedisConfigurationBuilder setDataDir(File dataDir) {
        checkIfFrozen("setDataDir");
        this.dataDir = dataDir;
        return this;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the port number.
     *
     * @param port port number, or 0 to use detectFreePort()
     * @return this
     */
    public RedisConfigurationBuilder setPort(int port) {
        checkIfFrozen("setPort");
        this.port = port;
        return this;
    }

    /**
     * Set a custom process listener to listen to DB start/shutdown events.
     *
     * @param listener custom listener
     * @return this
     */
    public RedisConfigurationBuilder setProcessListener(ManagedProcessListener listener) {
        this.listener = listener;
        return this;
    }

    public ManagedProcessListener getProcessListener() {
        return listener;
    }

    public boolean isDeletingTemporaryBaseAndDataDirsOnShutdown() {
        return isDeletingTemporaryBaseAndDataDirsOnShutdown;
    }

    /**
     * Defines if the configured data and base directories should be deleted on shutdown. If you've
     * set the base and data directories to non temporary directories using {@link
     * #setBaseDir(File)} or {@link #setDataDir(File)}, then they'll also never get deleted anyway.
     *
     * @param doDelete Default value is true, set false to override
     * @return returns this
     */
    public RedisConfigurationBuilder setDeletingTemporaryBaseAndDataDirsOnShutdown(boolean doDelete) {
        checkIfFrozen("keepsDataAndBaseDir");
        isDeletingTemporaryBaseAndDataDirsOnShutdown = doDelete;
        return this;
    }

    protected int detectFreePort() {
        try {
            ServerSocket ss = new ServerSocket(0);
            port = ss.getLocalPort();
            ss.setReuseAddress(true);
            ss.close();
            return port;
        } catch (IOException e) {
            // This should never happen
            throw new RuntimeException(e);
        }
    }

    public String getSocket() {
        return socket;
    }

    public RedisConfigurationBuilder setSocket(String socket) {
        checkIfFrozen("setSocket");
        this.socket = socket;
        return this;
    }

    public RedisConfiguration build() {
        if (dataDir == null) {
            String p = SystemUtils.JAVA_IO_TMPDIR + "/" + path();
            this.baseDir = new File(p + "/base");
        }

        frozen = true;
        return new RedisConfiguration.Impl(
            _getPort(),
            _getSocket(),
            _getBinariesClassPathLocation(),
            getBaseDir(),
            _getDataDir(),
            _getArgs(),
            isSecurityDisabled(),
            buildExecutables(),
            getProcessListener(), initRdbFile);
    }

    public boolean isSecurityDisabled() {
        return false;
    }

    public RedisConfigurationBuilder addArg(String arg) {
        checkIfFrozen("addArg");
        args.add(arg);
        return this;
    }

    protected File _getDataDir() {
        if (isNull(getDataDir())
            || getDataDir().equals(new File(SystemUtils.JAVA_IO_TMPDIR, DEFAULT_DATA_DIR))) {
            return new File(
                SystemUtils.JAVA_IO_TMPDIR
                    + File.separator
                    + DEFAULT_DATA_DIR
                    + File.separator
                    + _getPort());
        }
        return getDataDir();
    }

    private boolean isNull(File file) {
        return file == null;
    }

    protected int _getPort() {
        int port = getPort();
        if (port == 0) {
            port = detectFreePort();
        }
        return port;
    }

    protected String _getSocket() {
        String socket = getSocket();
        if (socket == null) {
            String portStr = String.valueOf(getPort());
            // Use /tmp instead getBaseDir() here, else we too easily hit
            // the "mysqld ERROR The socket file path is too long (> 107)" issue
            socket = SystemUtils.JAVA_IO_TMPDIR + "/redis4j." + portStr + ".sock";
        }
        return socket;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public void setDatabaseVersion(String databaseVersion) {
        checkIfFrozen("setDatabaseVersion");
        this.databaseVersion = databaseVersion;
    }

    private String getRedisVersion() {
        String databaseVersion = getDatabaseVersion();
        if (databaseVersion == null) {
            if (!OSPlatform.isMacOS() && !OSPlatform.isWindows() && !OSPlatform.isLinux()) {
                throw new IllegalStateException(
                    "OS not directly supported, please use setDatabaseVersion() to set the name "
                        + "of the package that the binaries are in, for: "
                        + SystemUtils.OS_VERSION);
            }
            return "redis-8.0.2";
        }
        return databaseVersion;
    }

    protected String getBinariesClassPathLocation() {
        return getClass().getPackage().getName().replace(".", "/") +
            "/" + getRedisVersion() + "/" +
            getOS();
    }

    public RedisConfigurationBuilder setOS(String osDirectoryName) {
        checkIfFrozen("setOS");
        this.osDirectoryName = osDirectoryName;
        return this;
    }

    public String getOS() {
        return osDirectoryName;
    }

    protected String _getOSLibraryEnvironmentVarName() {
        return switch (OSPlatform.get()) {
            case LINUX -> "LD_LIBRARY_PATH";
            case MAC -> "DYLD_FALLBACK_LIBRARY_PATH";
            case WINDOWS -> "PATH";
        };
    }

    protected String _getBinariesClassPathLocation() {
        if (isUnpackingFromClasspath) {
            return getBinariesClassPathLocation();
        }
        return null; // see ch.vorburger.mariadb4j.DB.unpackEmbeddedDb()
    }

    public boolean isUnpackingFromClasspath() {
        return isUnpackingFromClasspath;
    }

    public RedisConfigurationBuilder setUnpackingFromClasspath(boolean isUnpackingFromClasspath) {
        checkIfFrozen("setUnpackingFromClasspath");
        this.isUnpackingFromClasspath = isUnpackingFromClasspath;
        return this;
    }

    public String getURL(String databaseName) {
        return "jdbc:mariadb://localhost:" + getPort() + "/" + databaseName;
    }

    public List<String> _getArgs() {
        return args;
    }

    public RedisConfigurationBuilder setDefaultCharacterSet(String defaultCharacterSet) {
        checkIfFrozen("setDefaultCharacterSet");
        this.defaultCharacterSet = defaultCharacterSet;
        return this;
    }

    private Map<RedisConfiguration.Executable, Supplier<File>> buildExecutables() {
        String extension = OSPlatform.isWindows() ? ".exe" : "";
        executables.putIfAbsent(Server, () -> new File(baseDir, "redis-server" + extension));
        executables.putIfAbsent(Benchmark, () -> new File(baseDir, "redis-benchmark" + extension));
        executables.putIfAbsent(Client, () -> new File(baseDir, "redis-cli" + extension));
        return executables;
    }

    public RedisConfigurationBuilder setInitRdbFile(File initRdbFile) {
        checkIfFrozen("setInitRdbFile");
        this.initRdbFile = initRdbFile;
        return this;
    }
}
