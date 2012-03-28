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

package org.jbpm.task.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.drools.SystemEventListener;
import org.jbpm.eventmessaging.EventKeys;
import org.jbpm.task.AccessType;
import org.jbpm.task.AllowedToDelegate;
import org.jbpm.task.Attachment;
import org.jbpm.task.BooleanExpression;
import org.jbpm.task.Comment;
import org.jbpm.task.Content;
import org.jbpm.task.Deadline;
import org.jbpm.task.Deadlines;
import org.jbpm.task.Delegation;
import org.jbpm.task.EmailNotification;
import org.jbpm.task.EmailNotificationHeader;
import org.jbpm.task.Escalation;
import org.jbpm.task.Group;
import org.jbpm.task.I18NText;
import org.jbpm.task.Notification;
import org.jbpm.task.NotificationType;
import org.jbpm.task.OrganizationalEntity;
import org.jbpm.task.PeopleAssignments;
import org.jbpm.task.Reassignment;
import org.jbpm.task.Status;
import org.jbpm.task.StatusChange;
import org.jbpm.task.Task;
import org.jbpm.task.TaskData;
import org.jbpm.task.User;
import org.jbpm.task.UserInfo;
import org.jbpm.task.WorkItemNotification;
import org.jbpm.task.event.MessagingTaskEventListener;
import org.jbpm.task.event.TaskEventListener;
import org.jbpm.task.event.TaskEventSupport;
import org.jbpm.task.query.DeadlineSummary;
import org.jbpm.task.query.TaskSummary;
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;

public class TaskService {
    EntityManagerFactory emf;
    EntityManager em;

    ScheduledThreadPoolExecutor scheduler;

    private EscalatedDeadlineHandler escalatedDeadlineHandler;

    private UserInfo userInfo;

    private TaskEventSupport eventSupport;
    private EventKeys eventKeys;

    /**
     * Listener used for logging
     */
    private SystemEventListener systemEventListener;

    Map<Operation, List<OperationCommand>> operations;

    public TaskService(EntityManagerFactory emf,SystemEventListener systemEventListener) {
        this(emf, systemEventListener, null);
    }

    public TaskService(EntityManagerFactory emf, SystemEventListener systemEventListener, EscalatedDeadlineHandler escalationHandler) {
        this.emf = emf;
        this.systemEventListener = systemEventListener;
        this.em = emf.createEntityManager();
        if (escalationHandler != null) {
            this.escalatedDeadlineHandler = escalationHandler;
        }

        eventSupport = new TaskEventSupport();
        eventKeys = new EventKeys();
        eventSupport.addEventListener(new MessagingTaskEventListener(eventKeys));
        scheduler = new ScheduledThreadPoolExecutor(3);

        long now = System.currentTimeMillis();
        for (Object object : em.createNamedQuery("UnescalatedDeadlines").getResultList()) {
            DeadlineSummary summary = (DeadlineSummary) object;
            schedule(new ScheduledTaskDeadline(summary.getTaskId(),
                    summary.getDeadlineId(),
                    this),
                    summary.getDate().getTime() - now);
        }

        Map vars = new HashMap();

        // Search operations-dsl.mvel, if necessary using superclass if TaskService is subclassed
        InputStream is = null;
        for (Class c = getClass(); c != null; c = c.getSuperclass()) {
            is = c.getResourceAsStream("operations-dsl.mvel");
            if (is != null) {
                break;
            }
        }
        if (is == null) {
        	throw new RuntimeException("Unable To initialise TaskService, could not find Operations DSL");
        }
        Reader reader = new InputStreamReader(is);
        try {
            operations = (Map<Operation, List<OperationCommand>>) eval(toString(reader),  vars);
        } catch (IOException e) {
            throw new RuntimeException("Unable To initialise TaskService, could not load Operations DSL");
        }
    }

    public TaskServiceSession createSession() {
        return new TaskServiceSession(this, emf.createEntityManager());
    }

    public void schedule(ScheduledTaskDeadline deadline,
                         long delay) {
        scheduler.schedule(deadline,
                delay,
                TimeUnit.MILLISECONDS);
    }

    public Map<Operation, List<OperationCommand>> getOperations() {
        return operations;
    }

    public List<OperationCommand> getCommandsForOperation(Operation operation) {
        return operations.get(operation);
    }

    public EventKeys getEventKeys() {
        return eventKeys;
    }

    public void addEventListener(final TaskEventListener listener) {
        this.eventSupport.addEventListener(listener);
    }

    public void removeEventListener(final TaskEventListener listener) {
        this.eventSupport.removeEventListener(listener);
    }

    public List<TaskEventListener> getWorkingMemoryEventListeners() {
        return this.eventSupport.getEventListeners();
    }

    public TaskEventSupport getEventSupport() {
        return eventSupport;
    }

    public UserInfo getUserinfo() {
        return userInfo;
    }

    public void setUserinfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public EntityManager getEntityManager() {
        return em;
    }

    public void setEscalatedDeadlineHandler(EscalatedDeadlineHandler escalatedDeadlineHandler) {
        this.escalatedDeadlineHandler = escalatedDeadlineHandler;
    }

    public void executeEscalatedDeadline(long taskId,
                                         long deadlineId) {
        EntityManager localEm = emf.createEntityManager();
        Task task = localEm.find(Task.class,
                taskId);
        Deadline deadline = localEm.find(Deadline.class,
                deadlineId);

        if (escalatedDeadlineHandler == null) {
            escalatedDeadlineHandler = new DefaultEscalatedDeadlineHandler();
        }

        escalatedDeadlineHandler.executeEscalatedDeadline(task,
                deadline,
                localEm,
                this);
        localEm.close();
    }

    public static String toString(Reader reader) throws IOException {
        int charValue  ;
        StringBuffer sb = new StringBuffer(1024);
        while ((charValue = reader.read()) != -1) {
            sb.append((char) charValue);
        }
        return sb.toString();
    }

    private static Map<String, Class> inputs = new HashMap<String, Class>();

    public static Map<String, Class> getInputs() {
        synchronized (inputs) {
            if (inputs.isEmpty()) {
                // org.drools.task
                inputs.put("AccessType", AccessType.class);
                inputs.put("AllowedToDelegate", AllowedToDelegate.class);
                inputs.put("Attachment", Attachment.class);
                inputs.put("BooleanExpression", BooleanExpression.class);
                inputs.put("Comment", Comment.class);
                inputs.put("Content", Content.class);
                inputs.put("Deadline", Deadline.class);
                inputs.put("Deadlines", Deadlines.class);
                inputs.put("Delegation", Delegation.class);
                inputs.put("EmailNotification", EmailNotification.class);
                inputs.put("EmailNotificationHeader", EmailNotificationHeader.class);
                inputs.put("Escalation", Escalation.class);
                inputs.put("Group", Group.class);
                inputs.put("I18NText", I18NText.class);
                inputs.put("Notification", Notification.class);
                inputs.put("NotificationType", NotificationType.class);
                inputs.put("OrganizationalEntity", OrganizationalEntity.class);
                inputs.put("PeopleAssignments", PeopleAssignments.class);
                inputs.put("Reassignment", Reassignment.class);
                inputs.put("Status", Status.class);
                inputs.put("StatusChange", StatusChange.class);
                inputs.put("Task", Task.class);
                inputs.put("TaskData", TaskData.class);
                inputs.put("User", User.class);
                inputs.put("UserInfo", UserInfo.class);
                inputs.put("WorkItemNotification", WorkItemNotification.class);

                // org.drools.task.service
                inputs.put("Allowed", Allowed.class);
                inputs.put("Command", Command.class);
                inputs.put("CommandName", CommandName.class);
                inputs.put("ContentData", ContentData.class);
                inputs.put("Operation", Operation.class);
                inputs.put("Operation.Claim", Operation.class);
                inputs.put("OperationCommand", OperationCommand.class);

                // org.drools.task.query
                inputs.put("DeadlineSummary", DeadlineSummary.class);
                inputs.put("TaskSummary", TaskSummary.class);
            }
            return inputs;
        }
    }

    public Object eval(String str,
                       Map vars) {
    	ParserConfiguration pconf = new ParserConfiguration();
    	pconf.addPackageImport("org.jbpm.task");
    	pconf.addPackageImport("org.jbpm.task.service");
    	pconf.addPackageImport("org.jbpm.task.query");
    	pconf.addPackageImport("java.util");
    	for(String entry : getInputs().keySet()){
    		pconf.addImport(entry, getInputs().get(entry));
        }
    	ParserContext context = new ParserContext(pconf);
        Serializable s = MVEL.compileExpression(str.trim(), context);
        return MVEL.executeExpression(s, vars);
    }

    public static class ScheduledTaskDeadline
            implements
            Callable {
        private long taskId;
        private long deadlineId;
        private TaskService service;

        public ScheduledTaskDeadline(long taskId,
                                     long deadlineId,
                                     TaskService service) {
            this.taskId = taskId;
            this.deadlineId = deadlineId;
            this.service = service;
        }

        public long getTaskId() {
            return taskId;
        }

        public long getDeadlineId() {
            return deadlineId;
        }

        public Object call() throws Exception {
            try {
                service.executeEscalatedDeadline(taskId,
                        deadlineId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (deadlineId ^ (deadlineId >>> 32));
            result = prime * result + (int) (taskId ^ (taskId >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof ScheduledTaskDeadline)) return false;
            ScheduledTaskDeadline other = (ScheduledTaskDeadline) obj;
            if (deadlineId != other.deadlineId) return false;
            if (taskId != other.taskId) return false;
            return true;
        }

    }
}
