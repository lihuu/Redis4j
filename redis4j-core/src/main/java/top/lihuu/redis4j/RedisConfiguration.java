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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * origin from <a href="https://github.com/MariaDB4j/MariaDB4j/blob/main/mariaDB4j-core/src/main/java/ch/vorburger/mariadb4j/DBConfiguration.java">...</a>
 * <p>
 * I just do some refactoring to make it more suitable for Redis4j.
 *
 * @author Michael Vorburger
 * @author lihuu
 */
public interface RedisConfiguration {

    /**
     * TCP Port to start DB server on.
     *
     * @return returns port value
     */
    int getPort();

    /**
     * UNIX Socket to start DB server on (ignored on Windows).
     *
     * @return returns socket value
     */
    String getSocket();

    /**
     * Where from on the classpath should the binaries be extracted to the file system.
     *
     * @return null (not empty) if nothing should be extracted.
     */
    String getBinariesClassPathLocation();

    /**
     * Base directory where DB binaries are expected to be found.
     *
     * @return returns base directory value
     */
    File getBaseDir();

    /**
     * Base directory for DB's actual data files.
     *
     * @return returns data directory value
     */
    File getDataDir();

    /**
     * Directory for DB's temporary files.
     *
     * @return returns temporary directory value
     */
    File getTmpDir();

    /**
     * Whether to delete the base and data directory on shutdown, if it is in a temporary directory.
     * NB: If you've set the base and data directories to non temporary directories, then they'll
     * never get deleted.
     *
     * @return returns value of isDeletingTemporaryBaseAndDataDirsOnShutdown
     */
    boolean isDeletingTemporaryBaseAndDataDirsOnShutdown();

    /**
     * Whether running on Windows (some start-up parameters are different).
     *
     * @return returns boolean isWindows
     */
    boolean isWindows();

    List<String> getArgs();

    String getOSLibraryEnvironmentVarName();

    /**
     * Returns an instance of ManagedProcessListener class.
     *
     * @return Process callback when DB process is killed or is completed
     */
    ManagedProcessListener getProcessListener();

    File getExecutable(Executable executable);

    enum Executable {
        Server,
        Benchmark,
        Client
    }

    class Impl implements RedisConfiguration {

        private final int port;
        private final String socket;
        private final String binariesClassPathLocation;
        private final File baseDir;
        private final File dataDir;
        private final File tmpDir;
        private final boolean isDeletingTemporaryBaseAndDataDirsOnShutdown;
        private final List<String> args;
        private final String osLibraryEnvironmentVarName;
        private final ManagedProcessListener listener;
        private final Map<Executable, Supplier<File>> executables;

        Impl(
                int port,
                String socket,
                String binariesClassPathLocation,
                File baseDir,
                File dataDir,
                File tmpDir,
                List<String> args,
                String osLibraryEnvironmentVarName,
                boolean isDeletingTemporaryBaseAndDataDirsOnShutdown,
                Map<Executable, Supplier<File>> executables,
                ManagedProcessListener listener) {
            this.port = port;
            this.socket = socket;
            this.binariesClassPathLocation = binariesClassPathLocation;
            this.baseDir = baseDir;
            this.dataDir = dataDir;
            this.tmpDir = tmpDir;
            this.isDeletingTemporaryBaseAndDataDirsOnShutdown =
                    isDeletingTemporaryBaseAndDataDirsOnShutdown;
            this.args = args;
            this.osLibraryEnvironmentVarName = osLibraryEnvironmentVarName;
            this.listener = listener;
            this.executables = executables;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public String getSocket() {
            return socket;
        }

        @Override
        public String getBinariesClassPathLocation() {
            return binariesClassPathLocation;
        }

        @Override
        public File getBaseDir() {
            return baseDir;
        }


        @Override
        public File getDataDir() {
            return dataDir;
        }

        @Override
        public File getTmpDir() {
            return tmpDir;
        }

        @Override
        public boolean isDeletingTemporaryBaseAndDataDirsOnShutdown() {
            return isDeletingTemporaryBaseAndDataDirsOnShutdown;
        }

        @Override
        public boolean isWindows() {
            return SystemUtils.IS_OS_WINDOWS;
        }

        @Override
        public List<String> getArgs() {
            return args;
        }

        @Override
        public String getOSLibraryEnvironmentVarName() {
            return osLibraryEnvironmentVarName;
        }

        @Override
        public ManagedProcessListener getProcessListener() {
            return listener;
        }

        @Override
        public File getExecutable(Executable executable) {
            return executables
                    .getOrDefault(
                            executable,
                            () -> {
                                throw new IllegalArgumentException(executable.name());
                            })
                    .get();
        }
    }
}
