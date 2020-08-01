# mCounters for java
Counters for midi- and microservices.

## Motivation and goals
TBD

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


## License

The code is available under the terms of the [MIT License](http://opensource.org/licenses/MIT).
