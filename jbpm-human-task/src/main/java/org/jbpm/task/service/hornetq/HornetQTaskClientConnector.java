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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.integration.transports.netty.NettyConnectorFactory;
import org.hornetq.integration.transports.netty.TransportConstants;
import org.jbpm.task.service.BaseHandler;
import org.jbpm.task.service.TaskClientConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HornetQTaskClientConnector implements TaskClientConnector {

	private static final Logger logger = LoggerFactory.getLogger(HornetQTaskClientConnector.class);

	protected ClientSession session;

	protected final BaseHornetQHandler handler;
	protected final String name;   
	protected AtomicInteger counter;

	private String address;
	private Integer port;

	private ClientProducer producer;
	private ClientConsumer consumer;

	public HornetQTaskClientConnector(String name, BaseHornetQHandler handler) {
		if (name == null) {
			throw new IllegalArgumentException("Name can not be null");
		}
		this.name = name;
		this.handler = handler;
		this.counter = new AtomicInteger();
	}

	public boolean connect(String address, int port) {
		this.port = port;
		this.address = address;
		return connect();
	}

	public boolean connect() {
		if (session != null && !session.isClosed()) {
			throw new IllegalStateException("Already connected. Disconnect first.");
		} 
		try {
			Map<String, Object> connectionParams = new HashMap<String, Object>();
			if (address==null) {
				address = "127.0.0.1";
			}
			if (port==null) {
				port = 5445;
			}
			connectionParams.put(TransportConstants.PORT_PROP_NAME, port);
			connectionParams.put(TransportConstants.HOST_PROP_NAME, address);

			TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(), connectionParams);
			ClientSessionFactory factory = HornetQClient.createClientSessionFactory(transportConfiguration);
			session = factory.createSession();
			producer = session.createProducer(HornetQTaskServer.SERVER_TASK_COMMANDS_QUEUE);

			createClientQueue();

			Thread responsesThread = new Thread(new Runnable() {

				public void run() {
					try {
						consumer = session.createConsumer(name);
						while (true) {
							ClientMessage serverMessage = consumer.receive();
							if (serverMessage!=null) {
								((HornetQTaskClientHandler)handler).messageReceived(session, readMessage(serverMessage), BaseHornetQTaskServer.SERVER_TASK_COMMANDS_QUEUE);
							}
						}
					}
					catch (HornetQException e) {
						if (e.getCode()!=HornetQException.OBJECT_CLOSED) {
							throw new RuntimeException("Client Exception with class " + getClass() + " using port " + port, e);
						}
						logger.info(e.getMessage());
					}
					catch (Exception e) {
						throw new RuntimeException("Client Exception with class " + getClass() + " using port " + port, e);
					}
				}
			});
			responsesThread.start();
			session.start();
			return true;
		} catch (Exception e) {
			logger.error(e.getMessage());
			return false;
		}
	}

	private Object readMessage(ClientMessage serverMessage) throws IOException, ClassNotFoundException {
		int bodySize = serverMessage.getBodySize();
		byte[] message = new byte[bodySize];
		serverMessage.getBodyBuffer().readBytes(message);
		ByteArrayInputStream bais = new ByteArrayInputStream(message);
		ObjectInputStream ois = new ObjectInputStream(bais);
		return ois.readObject();
	}
	
	private void createClientQueue() {
		try {
			session.createQueue(name, name, true);
		}
		catch (HornetQException e) {
			if (e.getCode()!=HornetQException.QUEUE_EXISTS) {
				throw new RuntimeException("Client Exception with class " + getClass() + " using port " + port, e);
			}
			logger.info(e.getMessage());
		}
	}

	public void disconnect() throws Exception {
		if (session!= null && !session.isClosed()) {
			session.close();
			producer.close();
			if (consumer!=null) {
				consumer.close();
			}
		}
	}

	public void write(Object object) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oout;
		try {
			oout = new ObjectOutputStream(baos);
			oout.writeObject(object);
			ClientMessage message = session.createMessage(true);
			message.getBodyBuffer().writeBytes(baos.toByteArray());
			message.putStringProperty("producerId", name);
			producer.send(message);
		} catch (IOException e) {
			throw new RuntimeException("Error creating message", e);
		} catch (HornetQException e) {
			throw new RuntimeException("Error writing message", e);
		}
	}

	public AtomicInteger getCounter() {
		return counter;
	}

	public BaseHandler getHandler() {
		return handler;
	}

	public String getName() {
		return name;
	}

}
