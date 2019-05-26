package com.github.egetman;

import javax.jms.Session;

public class UnicastJmsQueueClientAcknowledgePublisherTest extends UnicastJmsQueueColdPublisherTest {

    public UnicastJmsQueueClientAcknowledgePublisherTest() {
        super(Session.CLIENT_ACKNOWLEDGE);
    }
}
