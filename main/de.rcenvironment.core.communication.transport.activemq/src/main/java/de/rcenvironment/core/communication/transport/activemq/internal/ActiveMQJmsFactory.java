/*
 * Copyright (C) 2006-2014 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */
package de.rcenvironment.core.communication.transport.activemq.internal;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

import de.rcenvironment.core.communication.channel.ServerContactPoint;
import de.rcenvironment.core.communication.model.NetworkContactPoint;
import de.rcenvironment.core.communication.transport.jms.common.JmsArtifactFactory;
import de.rcenvironment.core.communication.transport.jms.common.JmsBroker;
import de.rcenvironment.core.communication.transport.jms.common.RemoteInitiatedMessageChannelFactory;

/**
 * Factory for creating embedded ActiveMQ broker instances and related classes. Its purpose is to
 * decouple the rest of the codebase from ActiveMQ specifics.
 * 
 * @author Robert Mischke
 */
public class ActiveMQJmsFactory implements JmsArtifactFactory {

    @Override
    public JmsBroker createBroker(ServerContactPoint scp, RemoteInitiatedMessageChannelFactory remoteInitiatedConnectionFactory) {
        return new ActiveMQBroker(scp, remoteInitiatedConnectionFactory);
    }

    @Override
    public ConnectionFactory createConnectionFactory(NetworkContactPoint ncp) {
        String url = String.format("tcp://%s:%d?keepAlive=true", ncp.getHost(), ncp.getPort());
        ConnectionFactory connectionFactory = createConnectionFactory(url);
        return connectionFactory;
    }

    private ConnectionFactory createConnectionFactory(final String url) {
        return new ActiveMQConnectionFactory(url);
    }

}