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
  Thread Stats   Avg    Stdev   Max   +/- Stdev
  Latency   4.60ms  9.17ms 173.29ms   97.07%
  Req/Sec  14.77k   4.75k   18.31k  80.30%
  291108 requests in 10.00s, 12.11MB read
Requests/sec:  29098.22
Transfer/sec:    1.21MB
~/Projects/tmp $ wrk -t2 -c100 -d10s http://127.0.0.1:8000
Running 10s test @ http://127.0.0.1:8000
  2 threads and 100 connections
  Thread Stats   Avg    Stdev   Max   +/- Stdev
  Latency   2.97ms  1.69ms  52.52ms   97.31%
  Req/Sec  17.19k   1.84k   18.99k  93.00%
  342196 requests in 10.01s, 14.36MB read
Requests/sec:  34191.13
Transfer/sec:    1.43MB
~/Projects/tmp $ 
```

After a warm-up cycle of 10s, it can deliver `~34191 req/s`. Not bad, right?
