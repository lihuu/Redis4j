package top.lihuu.redis4j;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static top.lihuu.redis4j.RedisConfiguration.Executable.Client;
import static top.lihuu.redis4j.RedisConfiguration.Executable.Server;


public class Redis implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);

    protected final RedisConfiguration configuration;

    private File baseDir;
    private File dataDir;
    private File tmpDir;
    private ManagedProcess redisProcess;

    protected int dbStartMaxWaitInMS = 30000;

    protected Redis(RedisConfiguration config) {
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
    public static Redis newEmbeddedRedis(RedisConfiguration config) throws ManagedProcessException {
        Redis db = new Redis(config);
        db.prepareDirectories();
        db.unpackEmbeddedDb();
        return db;
    }

    public int getPort() {
        return configuration.getPort();
    }


    /**
     * This factory method is the mechanism for constructing a new embedded database with random port for use. This
     * method automatically installs the database and prepares it for use.
     *
     * @return a new DB instance
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public static Redis newEmbeddedRedis() throws ManagedProcessException {
        return newEmbeddedRedis(0);
    }

    /**
     * This factory method is the mechanism for constructing a new embedded database with a specific port for use. This
     * method automatically installs the database and prepares it for use.
     *
     * @param port the port to use for the embedded Redis instance
     * @return a new DB instance
     * @throws ch.vorburger.exec.ManagedProcessException if something fatal went wrong
     */
    public static Redis newEmbeddedRedis(int port) throws ManagedProcessException {
        RedisConfigurationBuilder config = new RedisConfigurationBuilder();
        config.setPort(port);
        return newEmbeddedRedis(config.build());
    }

    public synchronized void start() throws ManagedProcessException {
        logger.info("Starting up redis-server...");
        boolean ready = false;
        try {
            redisProcess = startPreparation();
            ready = redisProcess.startAndWaitForConsoleMessageMaxMs(
                    getReadyForConnectionsTag(), dbStartMaxWaitInMS);
        } catch (Exception e) {
            logger.error("failed to start redis-server", e);
            throw new ManagedProcessException("An error occurred while starting redis-server", e);
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

    /**
     * Returns the magic string that indicates that the database is ready to accept connections.
     * This is used to wait for the database to be ready after starting it.
     *
     * @return the magic string indicating readiness
     */
    private String getReadyForConnectionsTag() {
        return "Ready to accept connections tcp";
    }

    synchronized ManagedProcess startPreparation() throws IOException {
        ManagedProcessBuilder builder = new ManagedProcessBuilder(configuration.getExecutable(Server));

        // always use --daemonize no, we can catch output to confirm the redis-server has been started.
        builder.addArgument("--daemonize no");

        if (!hasArgument("--appendonly")) {
            builder.addArgument("--appendonly no");
        }
        // TODO if has provided aof file, then we should use it and should set appendonly yes

        if (!hasArgument("--protected-mode")) {
            builder.addArgument("--protected-mode yes");
        }

        builder.addArgument("--dir " + dataDir.getAbsolutePath());

        addPortAndMaybeSocketArguments(builder);
        for (String arg : configuration.getArgs()) {
            builder.addArgument(arg);
        }

        cleanupOnExit();
        builder.setDestroyOnShutdown(false);
        logger.info("redis executable: " + builder.getExecutable());
        return builder.build();
    }

    /**
     * Runs a command using the Redis client. This method constructs a command to be executed
     * <p>
     * like redis-cli -p <port> <command>, where <port> is the port of the Redis server
     *
     * @param command
     * @return
     * @throws ManagedProcessException
     */
    public String runCommand(String command) throws ManagedProcessException {
        ManagedProcessBuilder managedProcessBuilder = new ManagedProcessBuilder(configuration.getExecutable(Client));
        ByteArrayOutputStream stdOutput = new ByteArrayOutputStream();
        managedProcessBuilder.addStdOut(stdOutput);
        managedProcessBuilder.addArgument("-p");
        managedProcessBuilder.addArgument(configuration.getPort() + "");
        String[] commandParts = command.split(" ");
        for (String part : commandParts) {
            managedProcessBuilder.addArgument(part);
        }
        ManagedProcess process = managedProcessBuilder.build();
        process.start();
        return stdOutput.toString();
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
        builder.addArgument("--port " + configuration.getPort());
        if (!SystemUtils.IS_OS_WINDOWS) {
            builder.addFileArgument("--socket", getAbsoluteSocketFile());
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
            if (!OSPlatform.isWindows()) {
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
    private void cleanupOnExit() {
        String threadName = "Shutdown Hook Deletion Thread for Temporary DB " + dataDir.toString();
        final Redis db = this;
        Runtime.getRuntime().addShutdownHook(
                new DBShutdownHook(
                        threadName,
                        db,
                        () -> redisProcess,
                        () -> baseDir,
                        () -> dataDir,
                        () -> tmpDir,
                        configuration));
    }

    @Override
    public void close() throws ManagedProcessException {
        this.stop();
    }
}
