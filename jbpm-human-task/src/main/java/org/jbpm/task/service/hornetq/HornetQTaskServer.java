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

import org.drools.SystemEventListenerFactory;
import org.hornetq.core.config.Configuration;
import org.jbpm.task.service.TaskService;

public class HornetQTaskServer extends BaseHornetQTaskServer implements Runnable {

	public HornetQTaskServer(TaskService service, int port) {
		super(new HornetQTaskServerHandler(service, SystemEventListenerFactory.getSystemEventListener()), port, false);
	}

//	public HornetQTaskServer(TaskService service, int port, boolean standalone) {
//		super(new HornetQTaskServerHandler(service, SystemEventListenerFactory.getSystemEventListener()), port, standalone);
//	}

	public HornetQTaskServer(TaskService service, int port, Configuration configuration) {
		super(new HornetQTaskServerHandler(service, SystemEventListenerFactory.getSystemEventListener()), port, configuration, false);
	}

//	public HornetQTaskServer(TaskService service, int port, Configuration configuration, boolean standalone) {
//		super(new HornetQTaskServerHandler(service, SystemEventListenerFactory.getSystemEventListener()), port, configuration, standalone);
//	}
}