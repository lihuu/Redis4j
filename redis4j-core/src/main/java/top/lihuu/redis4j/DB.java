package top.lihuu.redis4j;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static top.lihuu.redis4j.RedisConfiguration.Executable.Server;


public class DB {

    private static final Logger logger = LoggerFactory.getLogger(DB.class);

    protected final RedisConfiguration configuration;

    private File baseDir;
    private File libDir;
    private File dataDir;
    private File tmpDir;
    private ManagedProcess redisProcess;

    protected int dbStartMaxWaitInMS = 30000;

    protected DB(RedisConfiguration config) {
        configuration = config;
    }

    /**
     * Getter for the field <code>configuration</code>.
     */
    public RedisConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * This factory method is the mechanism for constructing a new embedded database for use. This
     * method automatically installs the database and prepares it for use.
     *
     * @param config Configuration of the embedded instance
     * @return a new DB instance
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public static DB newEmbeddedRedis(RedisConfiguration config) throws ManagedProcessException {
        DB db = new DB(config);
        db.prepareDirectories();
        db.unpackEmbeddedDb();
        return db;
    }

    /**
     * This factory method is the mechanism for constructing a new embedded database for use. This
     * method automatically installs the database and prepares it for use with default
     * configuration, allowing only for specifying port.
     *
     * @param port the port to start the embedded database on
     * @return a new DB instance
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public static DB newEmbeddedRedis(int port) throws ManagedProcessException {
        RedisConfigurationBuilder config = new RedisConfigurationBuilder();
        config.setPort(port);
        return newEmbeddedRedis(config.build());
    }

    /**
     * Starts up the redis, using the data directory and port specified in the configuration.
     *
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public synchronized void start() throws ManagedProcessException {
        logger.info("Starting up the database...");
        boolean ready = false;
        try {
            redisProcess = startPreparation();
            ready =
                    redisProcess.startAndWaitForConsoleMessageMaxMs(
                            getReadyForConnectionsTag(), dbStartMaxWaitInMS);
        } catch (Exception e) {
            logger.error("failed to start mysqld", e);
            throw new ManagedProcessException("An error occurred while starting the database", e);
        }
        if (!ready) {
            if (redisProcess != null && redisProcess.isAlive()) {
                redisProcess.destroy();
            }
            throw new ManagedProcessException(
                    "Database does not seem to have started up correctly? Magic string not seen in "
                            + dbStartMaxWaitInMS
                            + "ms: "
                            + getReadyForConnectionsTag()
                            + redisProcess.getLastConsoleLines());
        }
        logger.info("Database startup complete.");
    }

    protected String getReadyForConnectionsTag() {
        return ": ready for connections.";
    }

    synchronized ManagedProcess startPreparation() throws IOException {
        ManagedProcessBuilder builder = new ManagedProcessBuilder(configuration.getExecutable(Server));
        builder.getEnvironment().put(configuration.getOSLibraryEnvironmentVarName(), libDir.getAbsolutePath());

        if (!hasArgument("--daemonize")) {
            builder.addArgument("--daemonize yes");
        }

        if (!hasArgument("--appendonly")) {
            builder.addArgument("--appendonly no");
        }

        if (!hasArgument("--protected-mode")) {
            builder.addArgument("--protected-mode no");
        }

        if (!hasArgument("--logfile")) {
            builder.addArgument("--logfile", "stdout");
        }

        builder.addFileArgument("--dir", baseDir).setWorkingDirectory(baseDir);

        addPortAndMaybeSocketArguments(builder);
        for (String arg : configuration.getArgs()) {
            builder.addArgument(arg);
        }

        cleanupOnExit();
        // because cleanupOnExit() just installed our (class DB) own
        // Shutdown hook, we don't need the one from ManagedProcess:
        builder.setDestroyOnShutdown(false);
        logger.info("redis executable: " + builder.getExecutable());
        return builder.build();
    }

    protected boolean hasArgument(final String argumentName) {
        for (String argument : configuration.getArgs()) {
            if (argument.startsWith(argumentName)) {
                return true;
            }
        }
        return false;
    }

    protected void addPortAndMaybeSocketArguments(ManagedProcessBuilder builder)
            throws IOException {
        builder.addArgument("--port=" + configuration.getPort());
        if (!configuration.isWindows()) {
            builder.addFileArgument("--socket", getAbsoluteSocketFile());
        }
    }

    protected void addSocketOrPortArgument(ManagedProcessBuilder builder) throws IOException {
        if (!configuration.isWindows()) {
            builder.addFileArgument("--socket", getAbsoluteSocketFile());
        } else {
            builder.addArgument("--port=" + configuration.getPort());
        }
    }

    /**
     * Config Socket as absolute path. By default this is the case because DBConfigurationBuilder
     * creates the socket in /tmp, but if a user uses setSocket() he may give a relative location,
     * so we double check.
     *
     * @return config.getSocket() as File getAbsolutePath()
     */
    protected File getAbsoluteSocketFile() {
        String socket = configuration.getSocket();
        File socketFile = new File(socket);
        return socketFile.getAbsoluteFile();
    }

    /**
     * Stops the database.
     *
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public synchronized void stop() throws ManagedProcessException {
        if (redisProcess != null && redisProcess.isAlive()) {
            logger.debug("Stopping the database...");
            redisProcess.destroy();
            logger.info("Database stopped.");
        } else {
            logger.debug("Database was already stopped.");
        }
    }

    /**
     * Based on the current OS, unpacks the appropriate version of MariaDB to the file system based
     * on the configuration.
     */
    protected void unpackEmbeddedDb() {
        if (configuration.getBinariesClassPathLocation() == null) {
            logger.info(
                    "Not unpacking any embedded database (as BinariesClassPathLocation configuration is null)");
            return;
        }

        try {
            Util.extractFromClasspathToFile(configuration.getBinariesClassPathLocation(), baseDir);
            if (!configuration.isWindows()) {
                // On Windows, the executables are already executable, so no need to force them
                Util.forceExecutable(configuration.getExecutable(Server));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error unpacking embedded DB", e);
        }
    }

    /**
     * If the data directory specified in the configuration is a temporary directory, this deletes
     * any previous version. It also makes sure that the directory exists.
     *
     * @throws ManagedProcessException if something fatal went wrong
     */
    protected void prepareDirectories() throws ManagedProcessException {
        baseDir = Util.getDirectory(configuration.getBaseDir());
        libDir = Util.getDirectory(configuration.getLibDir());
        tmpDir = Util.getDirectory(configuration.getTmpDir());
        try {
            File dataDirPath = configuration.getDataDir();
            if (Util.isTemporaryDirectory(dataDirPath)) {
                FileUtils.deleteDirectory(dataDirPath);
            }
            dataDir = Util.getDirectory(dataDirPath);
        } catch (Exception e) {
            throw new ManagedProcessException(
                    "An error occurred while preparing the data directory", e);
        }
    }

    /**
     * Adds a shutdown hook to ensure that when the JVM exits, the database is stopped, and any
     * temporary data directories are cleaned up.
     */
    protected void cleanupOnExit() {
        String threadName = "Shutdown Hook Deletion Thread for Temporary DB " + dataDir.toString();
        final DB db = this;
        Runtime.getRuntime()
                .addShutdownHook(
                        new DBShutdownHook(
                                threadName,
                                db,
                                () -> redisProcess,
                                () -> baseDir,
                                () -> dataDir,
                                () -> tmpDir,
                                configuration));
    }

}
