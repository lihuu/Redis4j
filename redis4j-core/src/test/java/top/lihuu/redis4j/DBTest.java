package top.lihuu.redis4j;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author lihuu
 * @since 2025-06-17 19:44:02
 */
public class DBTest {

    /**
     * @see DB#newEmbeddedRedis(int)
     */
    @Test
    public void should_start_redis_server_successfully() {
        try (DB db = DB.newEmbeddedRedis()) {
            db.start();
            String result = db.runCommand("SET HELLO world");
            assertEquals("OK\n", result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void should_run_redis_command_successfully() {
        try (DB db = DB.newEmbeddedRedis()) {
            db.start();
            db.runCommand("SET HELLO world");
            assertEquals("world\n", db.runCommand("GET HELLO"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}