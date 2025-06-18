package top.lihuu.redis4j.jupiter;

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
            if (started.get()) {
                return;
            }
            // Initialize Redis4j or any other setup needed before all tests
            // For example, starting an embedded Redis server
            // Redis.newEmbeddedRedis().start();
            Redis redis = Redis.newEmbeddedRedis();
            redis.start();
            started.set(true);
            rootContext = context.getRoot();
            ExtensionContext.Store globalStore = rootContext.getStore(ExtensionContext.Namespace.GLOBAL);
            globalStore.put(Redis4jExtension.class, this);
            globalStore.put(Redis.class, redis);
        }

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
