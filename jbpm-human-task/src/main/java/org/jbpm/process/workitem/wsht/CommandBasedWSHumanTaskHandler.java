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

package org.jbpm.process.workitem.wsht;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.SystemEventListenerFactory;
import org.drools.runtime.KnowledgeRuntime;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;
import org.jbpm.eventmessaging.EventResponseHandler;
import org.jbpm.eventmessaging.Payload;
import org.jbpm.task.AccessType;
import org.jbpm.task.Content;
import org.jbpm.task.Group;
import org.jbpm.task.I18NText;
import org.jbpm.task.OrganizationalEntity;
import org.jbpm.task.PeopleAssignments;
import org.jbpm.task.Status;
import org.jbpm.task.SubTasksStrategy;
import org.jbpm.task.SubTasksStrategyFactory;
import org.jbpm.task.Task;
import org.jbpm.task.TaskData;
import org.jbpm.task.User;
import org.jbpm.task.event.TaskCompletedEvent;
import org.jbpm.task.event.TaskEvent;
import org.jbpm.task.event.TaskEventKey;
import org.jbpm.task.event.TaskFailedEvent;
import org.jbpm.task.event.TaskSkippedEvent;
import org.jbpm.task.service.ContentData;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskClientHandler.GetContentResponseHandler;
import org.jbpm.task.service.TaskClientHandler.GetTaskResponseHandler;
import org.jbpm.task.service.mina.MinaTaskClientConnector;
import org.jbpm.task.service.mina.MinaTaskClientHandler;
import org.jbpm.task.service.responsehandlers.AbstractBaseResponseHandler;

public class CommandBasedWSHumanTaskHandler implements WorkItemHandler {

	private String ipAddress = "127.0.0.1";
	private int port = 9123;
	private TaskClient client;
	private KnowledgeRuntime session;
	
	public CommandBasedWSHumanTaskHandler(KnowledgeRuntime session) {
		this.session = session;
	}

	public void setConnection(String ipAddress, int port) {
		this.ipAddress = ipAddress;
		this.port = port;
	}
	
	public void setClient(TaskClient client) {
		this.client = client;
	}
	
	public void connect() {
		if (client == null) {
			client = new TaskClient(new MinaTaskClientConnector("org.drools.process.workitem.wsht.WSHumanTaskHandler", 
																new MinaTaskClientHandler(SystemEventListenerFactory.getSystemEventListener())));
			boolean connected = client.connect(ipAddress, port);
			
			if (!connected) {
				throw new IllegalArgumentException("Could not connect task client");
			}
		}
		TaskEventKey key = new TaskEventKey(TaskCompletedEvent.class, -1);           
		TaskCompletedHandler eventResponseHandler = new TaskCompletedHandler();
		client.registerForEvent(key, false, eventResponseHandler);
		key = new TaskEventKey(TaskFailedEvent.class, -1);           
		client.registerForEvent(key, false, eventResponseHandler);
		key = new TaskEventKey(TaskSkippedEvent.class, -1);           
		client.registerForEvent(key, false, eventResponseHandler);
	}

	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		connect();
		Task task = new Task();
		String taskName = (String) workItem.getParameter("TaskName");
		if (taskName != null) {
			List<I18NText> names = new ArrayList<I18NText>();
			names.add(new I18NText("en-UK", taskName));
			task.setNames(names);
		}
		String comment = (String) workItem.getParameter("Comment");
		if (comment != null) {
			List<I18NText> descriptions = new ArrayList<I18NText>();
			descriptions.add(new I18NText("en-UK", comment));
			task.setDescriptions(descriptions);
			List<I18NText> subjects = new ArrayList<I18NText>();
			subjects.add(new I18NText("en-UK", comment));
			task.setSubjects(subjects);
		}
		String priorityString = (String) workItem.getParameter("Priority");
		int priority = 0;
		if (priorityString != null) {
			try {
				priority = new Integer(priorityString);
			} catch (NumberFormatException e) {
				// do nothing
			}
		}
		task.setPriority(priority);
		
		TaskData taskData = new TaskData();
		taskData.setWorkItemId(workItem.getId());
		taskData.setProcessInstanceId(workItem.getProcessInstanceId());
		taskData.setSkipable(!"false".equals(workItem.getParameter("Skippable")));
        //Sub Task Data
        Long parentId = (Long) workItem.getParameter("ParentId");
        if(parentId != null){
            taskData.setParentId(parentId);
        }

        String subTaskStrategiesCommaSeparated = (String) workItem.getParameter("SubTaskStrategies");
        if(subTaskStrategiesCommaSeparated!= null && !subTaskStrategiesCommaSeparated.equals("")){
            String[] subTaskStrategies =  subTaskStrategiesCommaSeparated.split(",");
            List<SubTasksStrategy> strategies = new ArrayList<SubTasksStrategy>();
            for(String subTaskStrategyString : subTaskStrategies){
                SubTasksStrategy subTaskStrategy = SubTasksStrategyFactory.newStrategy(subTaskStrategyString);
                strategies.add(subTaskStrategy);
            }
            task.setSubTaskStrategies(strategies);
        }

        PeopleAssignments assignments = new PeopleAssignments();
		List<OrganizationalEntity> potentialOwners = new ArrayList<OrganizationalEntity>();

		String actorId = (String) workItem.getParameter("ActorId");
		if (actorId != null && actorId.trim().length() > 0) {
			String[] actorIds = actorId.split(",");
			for (String id: actorIds) {
				potentialOwners.add(new User(id.trim()));
			}
            //Set the first user as creator ID??? hmmm might be wrong
            if (potentialOwners.size() > 0){
                taskData.setCreatedBy((User)potentialOwners.get(0));
            }
        }
		
        String groupId = (String) workItem.getParameter("GroupId");
		if (groupId != null && groupId.trim().length() > 0) {
			String[] groupIds = groupId.split(",");
			for (String id: groupIds) {
				potentialOwners.add(new Group(id.trim()));
			}
		}

        assignments.setPotentialOwners(potentialOwners);
		List<OrganizationalEntity> businessAdministrators = new ArrayList<OrganizationalEntity>();
		businessAdministrators.add(new User("Administrator"));
		assignments.setBusinessAdministrators(businessAdministrators);
		task.setPeopleAssignments(assignments);
        
		task.setTaskData(taskData);

		ContentData content = null;
		Object contentObject = workItem.getParameter("Content");
		if (contentObject != null) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out;
			try {
				out = new ObjectOutputStream(bos);
				out.writeObject(contentObject);
				out.close();
				content = new ContentData();
				content.setContent(bos.toByteArray());
				content.setAccessType(AccessType.Inline);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
                // If the content is not set we will automatically copy all the input objects into 
                // the task content
                else {
                    contentObject = workItem.getParameters();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out;
			try {
				out = new ObjectOutputStream(bos);
				out.writeObject(contentObject);
				out.close();
				content = new ContentData();
				content.setContent(bos.toByteArray());
				content.setAccessType(AccessType.Inline);
                                content.setType("java.util.map");
			} catch (IOException e) {
				e.printStackTrace();
			}
                }
		client.addTask(task, content, null);
	}
	
	public void dispose() throws Exception {
		if (client != null) {
			client.disconnect();
		}
	}

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		GetTaskResponseHandler abortTaskResponseHandler = new AbortTaskResponseHandler();
    	client.getTaskByWorkItemId(workItem.getId(), abortTaskResponseHandler);
	}
    
    private class TaskCompletedHandler extends AbstractBaseResponseHandler implements EventResponseHandler {
        
        public void execute(Payload payload) {
            TaskEvent event = ( TaskEvent ) payload.get();
        	long taskId = event.getTaskId();
        	GetTaskResponseHandler getTaskResponseHandler =
        		new GetCompletedTaskResponseHandler();
        	client.getTask(taskId, getTaskResponseHandler);   
        }
        
        public boolean isRemove() {
        	return false;
        }
    }
    
    private class GetCompletedTaskResponseHandler extends AbstractBaseResponseHandler implements GetTaskResponseHandler {

		public void execute(Task task) {
			long workItemId = task.getTaskData().getWorkItemId();
			if (task.getTaskData().getStatus() == Status.Completed) {
				System.out.println("Notification of completed task " + workItemId);
				String userId = task.getTaskData().getActualOwner().getId();
				Map<String, Object> results = new HashMap<String, Object>();
				results.put("ActorId", userId);
				long contentId = task.getTaskData().getOutputContentId();
				if (contentId != -1) {
					GetContentResponseHandler getContentResponseHandler =
						new GetResultContentResponseHandler(task, results);
					client.getContent(contentId, getContentResponseHandler);
				} else {
					session.getWorkItemManager().completeWorkItem(workItemId, results);
				}
			} else {
				System.out.println("Notification of aborted task " + workItemId);
				session.getWorkItemManager().abortWorkItem(workItemId);
			}
		}
    }
    
    private class GetResultContentResponseHandler extends AbstractBaseResponseHandler implements GetContentResponseHandler {

    	private Task task;
    	private Map<String, Object> results;

    	public GetResultContentResponseHandler(Task task, Map<String, Object> results) {
    		this.task = task;
    		this.results = results;
    	}
    	
		@SuppressWarnings("unchecked")
		public void execute(Content content) {
			ByteArrayInputStream bis = new ByteArrayInputStream(content.getContent());
			ObjectInputStream in;
			try {
				in = new ObjectInputStream(bis);
				Object result = in.readObject();
				in.close();
				results.put("Result", result);
				if (result instanceof Map) {
					Map<?, ?> map = (Map) result;
					for (Map.Entry<?, ?> entry: map.entrySet()) {
						if (entry.getKey() instanceof String) {
							results.put((String) entry.getKey(), entry.getValue());
						}
					}
				}
				session.getWorkItemManager().completeWorkItem(task.getTaskData().getWorkItemId(), results);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
    }

    private class AbortTaskResponseHandler extends AbstractBaseResponseHandler implements GetTaskResponseHandler {

		public void execute(Task task) {
			if (task != null) {
				client.skip(task.getId(), "Administrator", null);
			}
		}
    }
    
}
