package top.lihuu.redis4j.jupiter;

import ch.vorburger.exec.ManagedProcessException;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import top.lihuu.redis4j.Redis;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author lihu <1449488533qq@gmail.com>
 * @since 2025/6/18
 */
public class Redis4jExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
    private static final AtomicBoolean started = new AtomicBoolean(false);

    private static ExtensionContext rootContext;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!started.get()) {
            return;
        }

        synchronized (Redis4jExtension.class) {
            // Double-check to avoid multiple initializations when running tests in parallel
            if (started.get()) {
                return;
            }
            Redis redis = startRedis();
            started.set(true);
            rootContext = context.getRoot();
            // We use the global store to share the Redis instance across tests
            ExtensionContext.Store globalStore = rootContext.getStore(ExtensionContext.Namespace.GLOBAL);
            globalStore.put(Redis4jExtension.class, this);
            globalStore.put(Redis.class, redis);
        }

    }

    private static Redis startRedis() throws ManagedProcessException {
        Redis redis = Redis.newEmbeddedRedis();
        redis.start();
        int port = redis.getPort();
        System.setProperty("redis.port", String.valueOf(port));
        return redis;
    }

    @Override
    public void close() throws Throwable {
        if (rootContext == null) {
            return;
        }

        Object object = rootContext.getStore(ExtensionContext.Namespace.GLOBAL).get(Redis.class);

        if (object instanceof Redis redis) {
            redis.stop();
        } else {
            throw new IllegalStateException("Redis4jExtension not initialized properly.");
        }

    }
}
