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

#### initialization tips: