# Reactive JMS Publisher (wrapper)

Reactive jms publisher is a simple reactive wrapper of jms api. 

In terms of reactive streams it is a **Cold** Publsiher. i.e. no data will be loss (via unhandled emitting). Emitting
 begins right after the clien demand's some data.
 
Jms publisher, or rather its [Source](/src/main/java/com/github/egetman/source/UnicastJmsQueueSource.java) is unicast
 by it's nature. If multiple clients connects to the same **jms queue**, each one will receive uniq messages. (The 
 same logic as with common interaction with jms queue through jms api).

It's tested with [reactive-streams-jvm](https://github.com/reactive-streams/reactive-streams-jvm),
and verified with **amq** & **wmq** brokers.


## When to use

If you have some components in your app, that use [reactive](https://github.com/reactive-streams/reactive-streams-jvm/tree/master/api/src/main/java/org/reactivestreams)
interfaces, it's good choice to use one more for easy integration =)  

If you want to make some manual/dynamic throughput control, you can check the usecases of 
[Barrier](/src/main/java/com/github/egetman/barrier/Barrier.java) abstraction.

## How to use

Creation of Jms Publisher as simple as

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
Jms source has 2 constructors, tha accepts following params:
```java
public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                             @Nonnull String queue) {
    ...
}
    
public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                             @Nonnull String queue, String user, String password, boolean transacted) {
    ...
}
```

You can use whatever `Subscriber<T>` you want with `ColdPublisher<T>`. 
There is one build in: `BalancingSubscriber<T>`.

The main idea is you never asks the given `Subscription` for unbounded sequence of element (usialy through `Long
.MAX_VALUE`). Instead you say how much elements you want to process for concrete time interval. In case when the 
application throughput rises too high, you can obtain additional control through `Barrier`.

 The simpliest way to create subscriber:
```java
Subscriber<T> subscriber = new BalancingSubscriber<T>(System.out::println);
```
Additionaly, **BalancingSubscriber** has several overloaded constructors:
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

#### Feel free to clone & pr =)