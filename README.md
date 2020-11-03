# mCounters for java
Counters for midi- and microservices.

## Motivation and goals
It's a common problem to collect and expose metrics from your midi- and microservice applications. This library tryes to help you whith it.

The library is aimed to be:
 - as fast as possible. It uses memory mapped files and direct memory access to store the counters.
 - service implementation agnostic. For example, you can expose JMX metrics from your Java application and read them from a Golang sidecar application or vise versa (see https://github.com/anatolygudkov/mc4go).
 - usable to expose static information about the application as well as dynamic counters.
 - a 0-dependency project. Just copy-paste the sources into your project.

## Usage
### How to publish counters

```java
final Properties statics = new Properties();
statics.put("my.statics.property1", "value1");
statics.put("my.statics.propertyN", "valueN");

try (MCountersWriter writer =
        new MCountersWriter("mycounters.dat", statics, 500)) {

    final MCounter counter1 = writer.addCounter("my.counters.1");
    final MCounter counterM = writer.addCounter("my.counters.M", 100);

    counter1.increment();

    counterM.set(1000);

    ...

    counter1.close();
    counterM.close();
}
```
### How to read counters
```java
try (MCountersReader reader = new MCountersReader("mycounters.dat")) {

    System.out.println("PID of counters' process: " + reader.pid());

    System.out.println("Statics:");
    reader.forEachStatic((label, value) -> 
        System.out.printf("\t%s=%s%n", label, value));

    System.out.println("Counters:");
    reader.forEachCounter((id, label, value) -> 
        System.out.printf("[%d]%s=%d%n", id, label, value));

}
```
## Concurrency issues
- Counters are thread safe and one counter can be modified in different threads.
- After a counter is closed, it must be not used, since its memory slot can be occupied by a new counter and the value of that new counter will be modified unexpectedtly.
- Counters must not be accessed after the writer is closed, because such modification leads to a segmentation fault.

## License
The code is available under the terms of the [MIT License](http://opensource.org/licenses/MIT).
