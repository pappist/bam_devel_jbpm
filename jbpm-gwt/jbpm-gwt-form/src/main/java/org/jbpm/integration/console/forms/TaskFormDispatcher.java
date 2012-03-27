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

package org.jbpm.integration.console.forms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.activation.DataHandler;

import org.drools.SystemEventListenerFactory;
import org.jboss.bpm.console.server.plugin.FormAuthorityRef;
import org.jbpm.task.Content;
import org.jbpm.task.I18NText;
import org.jbpm.task.Task;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.mina.MinaTaskClientConnector;
import org.jbpm.task.service.mina.MinaTaskClientHandler;
import org.jbpm.task.service.responsehandlers.BlockingGetContentResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingGetTaskResponseHandler;

/**
 * @author Kris Verlaenen
 */
public class TaskFormDispatcher extends AbstractFormDispatcher {

	private TaskClient client;

	public void connect() {
		if (client == null) {
			String ipAddress;
			int port;
			Properties properties = new Properties();
			try {
				properties.load(AbstractFormDispatcher.class.getResourceAsStream("/jbpm.console.properties"));
				ipAddress = properties.getProperty("jbpm.console.task.service.host");
				port = new Integer(properties.getProperty("jbpm.console.task.service.port"));
			} catch (IOException e) {
				throw new RuntimeException("Could not load jbpm.console.properties", e);
			}
			client = new TaskClient(new MinaTaskClientConnector("org.drools.process.workitem.wsht.WSHumanTaskHandler",
									new MinaTaskClientHandler(SystemEventListenerFactory.getSystemEventListener())));
			boolean connected = client.connect(ipAddress, port);
			if (!connected) {
				throw new IllegalArgumentException(
					"Could not connect task client");
			}
		}
	}
	
	public DataHandler provideForm(FormAuthorityRef ref) {
		connect();
		BlockingGetTaskResponseHandler getTaskResponseHandler = new BlockingGetTaskResponseHandler();
    	client.getTask(new Long(ref.getReferenceId()), getTaskResponseHandler);
		Task task = getTaskResponseHandler.getTask();
		Object input = null;
		long contentId = task.getTaskData().getDocumentContentId();
		if (contentId != -1) {
			BlockingGetContentResponseHandler getContentResponseHandler = new BlockingGetContentResponseHandler();
	    	client.getContent(contentId, getContentResponseHandler);
	    	Content content = getContentResponseHandler.getContent();
	    	ByteArrayInputStream bis = new ByteArrayInputStream(content.getContent());
			ObjectInputStream in;
			try {
				in = new ObjectInputStream(bis);
				input = in.readObject();
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		// check if a template exists
		String name = null;
		List<I18NText> names = task.getNames();
		for (I18NText text: names) {
			if ("en-UK".equals(text.getLanguage())) {
				name = text.getText();
			}
		}
		InputStream template = getTemplate(name);
		if (template == null) {
			template = TaskFormDispatcher.class.getResourceAsStream("/DefaultTask.ftl");
		}

		// merge template with process variables
		Map<String, Object> renderContext = new HashMap<String, Object>();
		renderContext.put("task", task);
		renderContext.put("content", input);
		if (input instanceof Map) {
			Map<?, ?> map = (Map) input;
			for (Map.Entry<?, ?> entry: map.entrySet()) {
				if (entry.getKey() instanceof String) {
					renderContext.put((String) entry.getKey(), entry.getValue());
				}
			}
		}
		return processTemplate(name, template, renderContext);
	}

}
