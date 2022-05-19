package xyz.jetdrone.hang.around;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;

import java.util.UUID;

import static xyz.jetdrone.hang.around.AsyncAwait.*;


@RunWith(VertxUnitRunner.class)
public class AsyncAwaitTest {

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    @ClassRule
    public static final GenericContainer<?> container = new GenericContainer<>("redis:6")
            .withExposedPorts(6379);

    @Test
    public void testAsyncAwait(TestContext should) {
        final Async test = should.async();
        Redis redis = Redis.createClient(rule.vertx(), "redis://" + container.getContainerIpAddress() + ":" + container.getFirstMappedPort());
        asyncRun(() -> {
            var set = await(redis.send(Request.cmd(Command.SET).arg("foo").arg("bar")));
            System.out.println(set);
            var get = await(redis.send(Request.cmd(Command.GET).arg("foo")));
            System.out.println(get);
            test.complete();
        });
    }

    private Future<Void> sleep(long millis) {
        Promise<Void> promise = ((VertxInternal) rule.vertx()).promise();
        rule.vertx()
                .setTimer(millis, t -> promise.complete());
        return promise.future();
    }

    @Test
    public void testAsyncAwaitOnSleep(TestContext should) {
        final Async test = should.async();
        final String value = UUID.randomUUID().toString();
        System.out.println(value);

        Redis redis = Redis.createClient(rule.vertx(), "redis://" + container.getContainerIpAddress() + ":" + container.getFirstMappedPort());
        asyncRun(() -> {
            var set = await(redis.send(Request.cmd(Command.SET).arg("foo").arg(value)));
            System.out.println(set);
            System.out.println("Before sleep");
            await(sleep(500));
            System.out.println("Before after");
            var get = await(redis.send(Request.cmd(Command.GET).arg("foo")));
            System.out.println(get);
            System.out.println("Before sleep");
            await(sleep(500));
            System.out.println("Before after");
            test.complete();
        });
    }

    @Test
    public void testAsyncAwaitOnSleep2(TestContext should) {
        final Async test = should.async();
        asyncRun(() -> {
            System.out.println("Before sleep#2");
            await(sleep(5000));
            System.out.println("After after#2");
            test.complete();
        });
    }

    @Test
    public void testBlockingSleep(TestContext should) {
        final Async test = should.async();
        asyncRun(() -> {
            System.out.println("Before sleep#2");
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("After after#2");
            test.complete();
        });
    }
}
