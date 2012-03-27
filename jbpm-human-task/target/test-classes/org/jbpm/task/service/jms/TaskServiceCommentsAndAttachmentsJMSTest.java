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

package org.jbpm.task.service.jms;

import java.util.Properties;

import javax.naming.Context;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.drools.SystemEventListenerFactory;
import org.easymock.EasyMock;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServiceCommentsAndAttachmentsBaseTest;

public class TaskServiceCommentsAndAttachmentsJMSTest extends TaskServiceCommentsAndAttachmentsBaseTest {

	private Context context;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
		
		this.context = EasyMock.createMock(Context.class);
		EasyMock.expect(context.lookup("ConnectionFactory")).andReturn(factory).anyTimes();
		EasyMock.replay(context);
		
		Properties serverProperties = new Properties();
		serverProperties.setProperty("JMSTaskServer.connectionFactory", "ConnectionFactory");
		serverProperties.setProperty("JMSTaskServer.transacted", "true");
		serverProperties.setProperty("JMSTaskServer.acknowledgeMode", "AUTO_ACKNOWLEDGE");
		serverProperties.setProperty("JMSTaskServer.queueName", "tasksQueue");
		serverProperties.setProperty("JMSTaskServer.responseQueueName", "tasksResponseQueue");
		
		server = new JMSTaskServer(taskService, serverProperties, context);
		Thread thread = new Thread(server);
		thread.start();
		System.out.println("Waiting for the JMS Task Server to come up");
        while (!server.isRunning()) {
        	System.out.print(".");
        	Thread.sleep( 50 );
        }

        Properties clientProperties = new Properties();
		clientProperties.setProperty("JMSTaskClient.connectionFactory", "ConnectionFactory");
		clientProperties.setProperty("JMSTaskClient.transactedQueue", "true");
		clientProperties.setProperty("JMSTaskClient.acknowledgeMode", "AUTO_ACKNOWLEDGE");
		clientProperties.setProperty("JMSTaskClient.queueName", "tasksQueue");
		clientProperties.setProperty("JMSTaskClient.responseQueueName", "tasksResponseQueue");
        
		client = new TaskClient(new JMSTaskClientConnector("client 1",
								new JMSTaskClientHandler(SystemEventListenerFactory.getSystemEventListener()),
								clientProperties, context));
		client.connect();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		client.disconnect();
		server.stop();
	}

}
