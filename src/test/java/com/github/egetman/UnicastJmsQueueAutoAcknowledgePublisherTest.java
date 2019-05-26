package com.github.egetman;

import javax.jms.Session;

public class UnicastJmsQueueAutoAcknowledgePublisherTest extends UnicastJmsQueueColdPublisherTest {

    public UnicastJmsQueueAutoAcknowledgePublisherTest() {
        super(Session.AUTO_ACKNOWLEDGE);
    }
}
