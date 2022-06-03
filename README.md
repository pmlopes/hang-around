# hang-around

## Async Await but for Vert.x

One of the main pain points of asynchronous programming is that code flow can be hard to read. With the upcoming Java 19
we will see Virtual Threads being added to the JVM. Virtual Threads, unlike regular Threads are not OS Threads and give
us a way to create large amounts of them without worrying that we will exhaust the OS resource.

This single source file project gives you a rought idea of what you can do to make your asynchronous code more readable.

The idea isn't new. In fact if you have programmed in JavaScript this will resemble very similar:

Let's see an example of an HTTP server that will increment a value in REDIS on each call:

```java
public class Demo extends AbstractVerticle {

  @Override
  public void start(Promise<Void> start) {
    // wrap this block as a Virtual Thread so we can await (block) at any time
    // without interfere with the event loop
    asyncRun(() -> {
      Redis redis = Redis.createClient(
        vertx,
        new RedisOptions().setMaxPoolSize(32).setMaxPoolWaiting(128));

      System.out.println("Will count calls to: counter");
      // look blocking but not the event loop!
      await(redis.send(cmd(SET).arg("counter").arg(0)));

      vertx
        .createHttpServer()
        // handler's are non blocking by nature, so like in JavaScript we just mark then as
        // "async()" this will start a new virtual thread and ensure we can block at any time
        .requestHandler(async(req -> {
          try {
            // we block by waiting on the redis response
            var set = await(redis.send(cmd(INCR).arg(key)));
            // and return the result, no more chains of flatMap(), compose(), onXYZ()...
            req.response().end(set.toString());
          } catch (RuntimeException e) {
            // exceptions are now also handled as simple try-catch
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
```

### But is it fast?

On my `Intel(R) Core(TM) i7-8650U CPU @ 1.90GHz` I can observe that if I start redis as a test container (which isn't
the best idea for performance testing anyway), This application performs as:

```
~/Projects/tmp $ wrk -t2 -c100 -d10s http://127.0.0.1:8000
Running 10s test @ http://127.0.0.1:8000
  2 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.62ms   12.54ms 212.71ms   97.60%
    Req/Sec    17.23k     5.85k   23.93k    78.79%
  339750 requests in 10.01s, 14.15MB read
Requests/sec:  33932.29
Transfer/sec:      1.41MB
~/Projects/tmp $ wrk -t2 -c100 -d10s http://127.0.0.1:8000
Running 10s test @ http://127.0.0.1:8000
  2 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.48ms    0.98ms  24.25ms   94.57%
    Req/Sec    20.64k     1.90k   23.65k    94.50%
  410919 requests in 10.00s, 17.24MB read
Requests/sec:  41071.70
Transfer/sec:      1.72MB
~/Projects/tmp $ 
```

After a warm-up cycle of 10s, it can deliver `~41071 req/s`. Not bad, right?
