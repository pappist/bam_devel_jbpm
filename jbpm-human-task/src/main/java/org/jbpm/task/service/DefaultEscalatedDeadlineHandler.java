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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import javax.persistence.EntityManager;

import org.drools.process.instance.impl.WorkItemImpl;
import org.drools.runtime.process.WorkItemManager;
import org.drools.util.ChainedProperties;
import org.drools.util.ClassLoaderUtil;
import org.jbpm.process.workitem.email.EmailWorkItemHandler;
import org.jbpm.task.Content;
import org.jbpm.task.Deadline;
import org.jbpm.task.EmailNotification;
import org.jbpm.task.EmailNotificationHeader;
import org.jbpm.task.Escalation;
import org.jbpm.task.Group;
import org.jbpm.task.Notification;
import org.jbpm.task.NotificationType;
import org.jbpm.task.OrganizationalEntity;
import org.jbpm.task.Reassignment;
import org.jbpm.task.Status;
import org.jbpm.task.Task;
import org.jbpm.task.TaskData;
import org.jbpm.task.User;
import org.jbpm.task.UserInfo;
import org.mvel2.MVEL;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.templates.TemplateRuntime;

public class DefaultEscalatedDeadlineHandler
    implements
    EscalatedDeadlineHandler {

    private UserInfo     userInfo;

    private String       from;

    private String       replyTo;

    EmailWorkItemHandler handler;

    WorkItemManager      manager;
    
    public DefaultEscalatedDeadlineHandler(Properties properties) {
        handler = new EmailWorkItemHandler();
        
        String host = properties.getProperty( "mail.smtp.host", "localhost" );
        String port = properties.getProperty( "mail.smtp.port", "25" );     
        
        from = properties.getProperty( "from", null );
        replyTo = properties.getProperty( "replyTo", null );
        
        handler.setConnection( host, port, null, null );
    }
    
    public DefaultEscalatedDeadlineHandler() {
        handler = new EmailWorkItemHandler();
        
        ChainedProperties conf = new ChainedProperties("drools.email.conf",  ClassLoaderUtil.getClassLoader( null, getClass(), false ) );
        String host = conf.getProperty( "host", null );
        String port = conf.getProperty( "port", "25" );
        
        from = conf.getProperty( "from", null );
        replyTo = conf.getProperty( "replyTo", null );
        
        handler.setConnection( host, port, null, null );
 
    }
    
    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public WorkItemManager getManager() {
        return manager;
    }

    public void setManager(WorkItemManager manager) {
        this.manager = manager;
    }

    public void executeEscalatedDeadline(Task task,
                                         Deadline deadline,
                                         EntityManager em,
                                         TaskService service) {
        if ( deadline == null || deadline.getEscalations() == null ) {
            return;
        }
        
        for ( Escalation escalation : deadline.getEscalations() ) {
            // we won't impl constraints for now
            //escalation.getConstraints()
            String language = "en-UK";
            for ( Notification notification : escalation.getNotifications() ) {
                if ( notification.getNotificationType() == NotificationType.Email) {
                    executeEmailNotification( (EmailNotification) notification, task, em );
                }        
            }


            if ( !escalation.getReassignments().isEmpty()) {
                // get first and ignore the rest.
                Reassignment reassignment = escalation.getReassignments().get( 0 );
                em.getTransaction().begin();
                task.getTaskData().setStatus( Status.Ready );
                List potentialOwners = new ArrayList( reassignment.getPotentialOwners() );
                task.getPeopleAssignments().setPotentialOwners( potentialOwners );
                em.getTransaction().commit();
            }            
        }
        em.getTransaction().begin();
        deadline.setEscalated( true );
        em.getTransaction().commit();
    }

    public void executeEmailNotification(EmailNotification notification,
                                         Task task,
                                         EntityManager em) {
        Map<String, EmailNotificationHeader> headers = notification.getEmailHeaders();

        // group users into languages
        Map<String, List<User>> users = new HashMap<String, List<User>>();
        for ( OrganizationalEntity entity : notification.getBusinessAdministrators() ) {
            if ( entity instanceof Group ) {
                buildMapByLanguage( users,
                                    (Group) entity );
            } else {
                buildMapByLanguage( users,
                                    (User) entity );
            }
        }

        for ( OrganizationalEntity entity : notification.getRecipients() ) {
            if ( entity instanceof Group ) {
                buildMapByLanguage( users,
                                    (Group) entity );
            } else {
                buildMapByLanguage( users,
                                    (User) entity );
            }
        }

        TaskData taskData = task.getTaskData();
        Map<String, Object> doc = null;
        if ( taskData != null ) {
            Content content = em.find( Content.class,
                                       taskData.getDocumentContentId() );
            if ( content != null ) {
                ExpressionCompiler compiler = new ExpressionCompiler( new String( content.getContent() ) );
                doc = (Map<String, Object>) MVEL.executeExpression( compiler.compile() );
            } else {
                doc = Collections.emptyMap();
            }
        }

        for ( Iterator<Entry<String, List<User>>> it = users.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, List<User>> entry = it.next();
            EmailNotificationHeader header = headers.get( entry.getKey()  );

            Map<String, Object> email = new HashMap<String, Object>();
            StringBuilder to = new StringBuilder();
            boolean first = true;
            for ( User user : entry.getValue() ) {
                if ( !first ) {
                    to.append( ';' );
                }
                String emailAddress = userInfo.getEmailForEntity( user );
                to.append( emailAddress );
                first = false;
            }
            email.put( "To",
                       to.toString() );

            if ( header.getFrom() != null && header.getFrom().trim().length() > 0 ) {
                email.put( "From",
                           header.getFrom() );
            } else {
                email.put( "From",
                           from );
            }

            if ( header.getReplyTo() != null && header.getReplyTo().trim().length() > 0 ) {
                email.put( "Reply-To",
                           header.getReplyTo() );
            } else {
                email.put( "Reply-To",
                           replyTo );
            }

            Map<String, Object> vars = new HashMap<String, Object>();
            vars.put( "doc",
                      doc );
            String subject = (String) TemplateRuntime.eval( header.getSubject(),
                                                            vars );
            String body = (String) TemplateRuntime.eval( header.getBody(),
                                                         vars );

            email.put( "Subject",
                       subject );
            email.put( "Body",
                       body );

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameters( email );

            handler.executeWorkItem( workItem,
                                     manager );

        }
    }

    private void buildMapByLanguage(Map<String, List<User>> map,
                                    Group group) {
        for ( Iterator<OrganizationalEntity> it = userInfo.getMembersForGroup( group ); it.hasNext(); ) {
            OrganizationalEntity entity = it.next();
            if ( entity instanceof Group ) {
                buildMapByLanguage( map,
                                    (Group) entity );
            } else {
                buildMapByLanguage( map,
                                    (User) entity );
            }
        }
    }

    private void buildMapByLanguage(Map<String, List<User>> map,
                                    User user) {
        String language = userInfo.getLanguageForEntity( user );
        List<User> list = map.get( language );
        if ( list == null ) {
            list = new ArrayList<User>();
            map.put( language,
                     list );
        }
        list.add( user );
    }

}
