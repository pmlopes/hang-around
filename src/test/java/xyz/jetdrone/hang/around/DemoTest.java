package xyz.jetdrone.hang.around;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.CountDownLatch;

import static io.vertx.redis.client.Command.INCR;
import static io.vertx.redis.client.Command.SET;
import static io.vertx.redis.client.Request.cmd;
import static xyz.jetdrone.hang.around.AsyncAwait.*;

@Ignore
public class DemoTest extends AbstractVerticle {

    @ClassRule
    public static final GenericContainer<?> container = new GenericContainer<>("redis:6")
            .withExposedPorts(6379);

    @Test
    public void testMe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Vertx.vertx().deployVerticle(new DemoTest()).onFailure(Throwable::printStackTrace);
        latch.await();
    }

    @Override
    public void start(Promise<Void> start) {
        asyncRun(() -> {
            Redis redis = Redis.createClient(vertx, new RedisOptions()
                    .setMaxPoolSize(32)
                    .setMaxPoolWaiting(128)
                    .setConnectionString("redis://" + container.getContainerIpAddress() + ":" + container.getFirstMappedPort()));

            System.out.println("Will count calls to: counter");
            await(redis.send(cmd(SET).arg("counter").arg(0)));

            vertx
                    .createHttpServer()
                    .requestHandler(async(req -> {
                        try {
                            var set = await(redis.send(cmd(INCR).arg("counter")));
                            req.response().end(set.toString());
                        } catch (RuntimeException e) {
                            req.response()
                                    .setStatusCode(500)
                                    .end(e.getMessage());
                        }
                    }))
                    .listen(8000, "0.0.0.0")
                    .<Void>mapEmpty()
                    .onComplete(start);
        });
    }
}
