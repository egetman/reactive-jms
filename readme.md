![hex.pm](https://img.shields.io/hexpm/l/plug.svg)
![version](https://img.shields.io/badge/version-0.8-blue.svg)
[![build status](https://travis-ci.org/egetman/reactive-jms.svg?branch=master)](https://travis-ci.org/egetman/reactive-jms)
![code coverage](https://codecov.io/gh/egetman/reactive-jms/branch/master/graph/badge.svg)
# Reactive JMS Publisher (wrapper)

Reactive JMS publisher is a simple reactive wrapper for JMS API. 

In terms of reactive streams, it is a **Cold** Publisher. i.e. no data will be lost (via unhandled emitting). Emitting
 begins right after the client demand's some data.
 
JMS publisher or rather its [Source](/src/main/java/com/github/egetman/source/UnicastJmsQueueSource.java) is unicast by its nature. If multiple clients connect to the same **JMS queue**, each one will receive unique messages. (The 
 same logic as with common interaction with JMS queue through JMS API).

It's tested with [reactive-streams-jvm](https://github.com/reactive-streams/reactive-streams-jvm) tck,
and verified with **amq** & **wmq** brokers.


## When to use

If you have some components in your app, that use [reactive](https://github.com/reactive-streams/reactive-streams-jvm/tree/master/api/src/main/java/org/reactivestreams)
interfaces, it's a good choice to use one more for easy integration =)  

If you want to make some manual/dynamic throughput control, you can check the use cases of 
[Barrier](/src/main/java/com/github/egetman/barrier/Barrier.java) abstraction.

## How to use

Creation of JMS Publisher as simple as

```java
final ConnectionFactory factory = ...
final Function<Message, String> messageToString = message -> {
    try {
        return ((TextMessage) message).getText();
    } catch (Exception e) {
        throw new IllegalStateException(e);
    }
};
Publsiher<String> jmsPublisher = new ColdPublisher<>(new UnicastJmsQueueSource<>(factory, messageToString, "MY_COOL_QUEUE"));
```

Publisher acceps `Source<T>` instance as data & access provider.
```java
public ColdPublisher(@Nonnull Source<T> source) {
    this.source = Objects.requireNonNull(source, "Source must not be null");
}
```
You can implement your own source types, it's quite easy:
```java
public interface Source<E> {
    CloseableIterator<E> iterator(int key);
}
```
Jms source has 2 constructors, that accepts following params:
```java
public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                             @Nonnull String queue) {
    ...
}
    
public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                             @Nonnull String queue, String user, String password, boolean transacted, int acknowledgeMode) {
    ...
}
```

You can use whatever `Subscriber<T>` you want with `ColdPublisher<T>`. 
There is one build in: `BalancingSubscriber<T>`.

The main idea is you never ask the given `Subscription` for an unbounded sequence of elements (usually through `Long.MAX_VALUE`). 
Instead, you say how much elements you want to process for a concrete time interval. In a case when the application throughput rises too high, you can obtain additional control through `Barrier`.

 The simplest way to create a subscriber:
```java
Subscriber<T> subscriber = new BalancingSubscriber<T>(System.out::println);
```
Additionally, **BalancingSubscriber** has several overloaded constructors:
```java
public BalancingSubscriber(@Nonnull Consumer<T> onNext) {
    ...
}

public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nonnull Barrier barrier) {
    ...
}

public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nonnull Barrier barrier, int batchSize, int pollInterval) {
    ...
}

public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nullable Consumer<Throwable> onError,
                           @Nullable Runnable onComplete, @Nonnull Barrier barrier, int batchSize, int pollInterval) {
    ...
}
```

**Please feel free to send a pr =)**