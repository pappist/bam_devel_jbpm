/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.task.service.hornetq;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.integration.transports.netty.NettyAcceptorFactory;
import org.hornetq.integration.transports.netty.NettyConnectorFactory;
import org.hornetq.integration.transports.netty.TransportConstants;
import org.jbpm.task.service.TaskServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseHornetQTaskServer extends TaskServer {

	public static final String SERVER_TASK_COMMANDS_QUEUE = "tasksQueue";
	
	private static final Logger logger = LoggerFactory.getLogger(TaskServer.class);

	private HornetQServer server;
	private HornetQTaskServerHandler handler;
	private Configuration configuration;
	private boolean standalone;

	private final int port;
	volatile boolean  embeddedServerRunning;
	private boolean running;

	private ClientSession session;
	private ClientConsumer consumer;

	public BaseHornetQTaskServer(HornetQTaskServerHandler handler, int port, boolean standalone) {
		this.handler = handler;
		this.port = port;
		this.standalone = standalone;
	}

	public BaseHornetQTaskServer(HornetQTaskServerHandler handler, int port, Configuration configuration, boolean standalone) {
		this.handler = handler;
		this.port = port;
		this.configuration = configuration;
		this.standalone = standalone;
	}

	public void run() {
		try {
			start();
			while (running) {
				ClientMessage clientMessage = consumer.receive();
				if (clientMessage!=null) {
					Object object = readMessage(clientMessage);
					String clientId = clientMessage.getStringProperty("producerId");
					handler.messageReceived(session, object, clientId);
				}
			}
		}
		catch (HornetQException e) {
			switch (e.getCode()) {
			case HornetQException.OBJECT_CLOSED:
				logger.info(e.getMessage());
				break;
			default:
				logger.error(e.getMessage());
 				break;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Server Exception with class " + getClass() + " using port " + port, e);
		}
	}

	private Object readMessage(ClientMessage msgReceived) throws IOException {
		int bodySize = msgReceived.getBodySize();
		byte[] message = new byte[bodySize];
		msgReceived.getBodyBuffer().readBytes(message);
		ByteArrayInputStream bais = new ByteArrayInputStream(message);
		try {
			ObjectInputStream ois = new ObjectInputStream(bais);
			return ois.readObject();
		} catch (IOException e) {
			throw new IOException("Error reading message");
		} catch (ClassNotFoundException e) {
			throw new IOException("Error creating message");
		}
	}

	public void start() throws Exception {

		Map<String, Object> connectionParams = new HashMap<String, Object>();
		connectionParams.put(TransportConstants.PORT_PROP_NAME, port);

		if (!standalone) {
			if (configuration==null) {
				configuration = new ConfigurationImpl();
				configuration.setPersistenceEnabled(false);
				configuration.setSecurityEnabled(false);
				configuration.setClustered(false);
			}

			TransportConfiguration transpConf = new TransportConfiguration(NettyAcceptorFactory.class.getName(), connectionParams);

			HashSet<TransportConfiguration> setTransp = new HashSet<TransportConfiguration>();
			setTransp.add(transpConf);

			configuration.setAcceptorConfigurations(setTransp);

			server = HornetQServers.newHornetQServer(configuration);
			server.start();
			embeddedServerRunning = true;
		}

		TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(), connectionParams);
		ClientSessionFactory factory = HornetQClient.createClientSessionFactory(transportConfiguration);
		session = factory.createSession();
		try {
			session.createQueue(SERVER_TASK_COMMANDS_QUEUE, SERVER_TASK_COMMANDS_QUEUE, true);
		}
		catch (HornetQException e) {
			if (e.getCode()!=HornetQException.QUEUE_EXISTS) {
				logger.info(e.getMessage());
				throw new RuntimeException("Server Exception with class " + getClass() + " using port " + port, e);
			}
		}
		consumer = session.createConsumer(SERVER_TASK_COMMANDS_QUEUE);
		session.start();
		running = true;
	}

	public void stop() throws Exception {
		if (running) {
			running = false;
			closeAll();
		}
		if (embeddedServerRunning) {
			embeddedServerRunning = false;
			closeAll();
			server.stop();
		}
	}
	
	private void closeAll() throws HornetQException {
		if (!session.isClosed()) {
			session.close();
		}
		if (!consumer.isClosed()) {
			consumer.close();
		}
	}
	
	public boolean isRunning() {
		return running;
	}
}