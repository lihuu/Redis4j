package top.lihuu.redis4j;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author lihuu
 * @since 2025-06-17 19:44:02
 */
public class RedisTest {

    /**
     * @see Redis#newEmbeddedRedis(int)
     */
    @Test
    public void should_start_redis_server_successfully() {
        try (Redis db = Redis.newEmbeddedRedis()) {
            db.start();
            String result = db.runCommand("SET HELLO world");
            assertEquals("OK\n", result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void should_run_redis_command_successfully() {
        try (Redis db = Redis.newEmbeddedRedis()) {
            db.start();
            db.runCommand("SET HELLO world");
            assertEquals("world\n", db.runCommand("GET HELLO"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void should_run_with_custom_rdb_file_successfully() {
        URL resource = getClass().getClassLoader().getResource("dump.rdb");
        String file = resource.getFile();
        File initRdbFile = new File(file);
        if (!initRdbFile.exists()) {
            throw new RuntimeException("initRdbFile not found: " + initRdbFile.getAbsolutePath());
        }

        RedisConfiguration redisConfiguration = RedisConfigurationBuilder
                .newBuilder()
                .setInitRdbFile(initRdbFile)
                .build();
        try (Redis db = Redis.newEmbeddedRedis(redisConfiguration)) {
            db.start();
            String result = db.runCommand("--scan");
            System.out.println("The result of keys * is: " + result);
            result = db.runCommand("CONFIG GET dir");
            System.out.println("The result of CONFIG GET dir is: " + result);
            result = db.runCommand("CONFIG GET dbfilename");
            System.out.println("The result of CONFIG GET dbfilename is: " + result);
            assertEquals("world\n", db.runCommand("GET hello"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}