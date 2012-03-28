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

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.drools.process.instance.impl.DefaultWorkItemManager;
import org.drools.runtime.process.WorkItemManager;
import org.jbpm.task.BaseTest;
import org.jbpm.task.Content;
import org.jbpm.task.MockUserInfo;
import org.jbpm.task.OrganizationalEntity;
import org.jbpm.task.Status;
import org.jbpm.task.Task;
import org.jbpm.task.service.DefaultEscalatedDeadlineHandler;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.responsehandlers.BlockingAddTaskResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingGetContentResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingGetTaskResponseHandler;
import org.jbpm.task.service.responsehandlers.BlockingSetContentResponseHandler;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

public abstract class TaskServiceDeadlinesBaseTest extends BaseTest {

	protected TaskServer server;
	protected TaskClient client;
    private Properties conf;
    private Wiser wiser;

    public void testDelayedEmailNotificationOnDeadline() throws Exception {
        Map  vars = new HashMap();     
        vars.put( "users", users );
        vars.put( "groups", groups );
        vars.put( "now", new Date() ); 
        
        DefaultEscalatedDeadlineHandler notificationHandler = new DefaultEscalatedDeadlineHandler( getConf() );
        WorkItemManager manager = new DefaultWorkItemManager( null );
        notificationHandler.setManager( manager );
        
        MockUserInfo userInfo = new MockUserInfo();
        userInfo.getEmails().put( users.get("tony"), "tony@domain.com" );
        userInfo.getEmails().put( users.get("darth"), "darth@domain.com" );
        
        userInfo.getLanguages().put(  users.get("tony"), "en-UK" );
        userInfo.getLanguages().put(  users.get("darth"), "en-UK" );
        notificationHandler.setUserInfo( userInfo );    
        
        taskService.setEscalatedDeadlineHandler( notificationHandler );
        
        String string = toString( new InputStreamReader( getClass().getResourceAsStream( "../DeadlineWithNotification.mvel" ) ) );
            
        BlockingAddTaskResponseHandler addTaskResponseHandler = new BlockingAddTaskResponseHandler();
        Task task = ( Task )  eval( new StringReader( string ), vars );
        client.addTask( task, null, addTaskResponseHandler );
        long taskId = addTaskResponseHandler.getTaskId();    
                                        
        Content content = new Content();
        content.setContent( "['subject' : 'My Subject', 'body' : 'My Body']".getBytes() );
        BlockingSetContentResponseHandler setContentResponseHandler  = new BlockingSetContentResponseHandler();
        client.setDocumentContent( taskId, content, setContentResponseHandler );
        long contentId = setContentResponseHandler.getContentId();
        BlockingGetContentResponseHandler  getResponseHandler = new BlockingGetContentResponseHandler();
        client.getContent( contentId, getResponseHandler );
        content = getResponseHandler.getContent();
        assertEquals( "['subject' : 'My Subject', 'body' : 'My Body']", new String( content.getContent() ) );
        
        // emails should not be set yet
        assertEquals(0, getWiser().getMessages().size() );             
        Thread.sleep( 100 );
        
        // nor yet
        assertEquals(0, getWiser().getMessages().size() );     
        
        long time = 0;
        while ( getWiser().getMessages().size() != 2 && time < 15000 ) {
            Thread.sleep( 500 );
            time += 500;
        }
        
        // 1 email with two recipients should now exist
        assertEquals(2, getWiser().getMessages().size() );        
        
        List<String> list = new ArrayList<String>(2);
        list.add( getWiser().getMessages().get( 0 ).getEnvelopeReceiver() );
        list.add( getWiser().getMessages().get( 1 ).getEnvelopeReceiver() );
        
        assertTrue( list.contains("tony@domain.com"));
        assertTrue( list.contains("darth@domain.com"));
        
        
        MimeMessage msg = (( WiserMessage  ) getWiser().getMessages().get( 0 )).getMimeMessage();
        assertEquals( "My Body", msg.getContent() );
        assertEquals( "My Subject", msg.getSubject() );
        assertEquals( "from@domain.com", ((InternetAddress)msg.getFrom()[0]).getAddress() );
        assertEquals( "replyTo@domain.com", ((InternetAddress)msg.getReplyTo()[0]).getAddress() );
        assertEquals( "tony@domain.com", ((InternetAddress)msg.getRecipients( RecipientType.TO )[0]).getAddress() );
        assertEquals( "darth@domain.com", ((InternetAddress)msg.getRecipients( RecipientType.TO )[1]).getAddress() );        
    }
    
    public void testDelayedReassignmentOnDeadline() throws Exception {
        Map vars = new HashMap();     
        vars.put( "users", users );
        vars.put( "groups", groups );
        vars.put( "now", new Date() ); 
        
        DefaultEscalatedDeadlineHandler notificationHandler = new DefaultEscalatedDeadlineHandler(getConf());
        WorkItemManager manager = new DefaultWorkItemManager( null );
        notificationHandler.setManager( manager );
        
        MockUserInfo userInfo = new MockUserInfo();
        userInfo.getEmails().put( users.get("tony"), "tony@domain.com" );
        userInfo.getEmails().put( users.get("luke"), "luke@domain.com" );
        userInfo.getEmails().put( users.get("bobba"), "luke@domain.com" );
        userInfo.getEmails().put( users.get("jabba"), "luke@domain.com" );
        
        userInfo.getLanguages().put(  users.get("tony"), "en-UK" );
        userInfo.getLanguages().put(  users.get("luke"), "en-UK" );
        userInfo.getLanguages().put(  users.get("bobba"), "en-UK" );
        userInfo.getLanguages().put(  users.get("jabba"), "en-UK" );
        notificationHandler.setUserInfo( userInfo );    
        
        taskService.setEscalatedDeadlineHandler( notificationHandler );
        
        String string = toString( new InputStreamReader( getClass().getResourceAsStream( "../DeadlineWithReassignment.mvel" ) ) );
            
        BlockingAddTaskResponseHandler addTaskResponseHandler = new BlockingAddTaskResponseHandler();
        Task task = ( Task )  eval( new StringReader( string ), vars );               
        client.addTask( task, null, addTaskResponseHandler );
        long taskId = addTaskResponseHandler.getTaskId();    
        
        // Shouldn't have re-assigned yet
        Thread.sleep( 1000 );
        BlockingGetTaskResponseHandler getTaskHandler = new BlockingGetTaskResponseHandler(); 
        client.getTask( taskId, getTaskHandler );
        task = getTaskHandler.getTask();
        List<OrganizationalEntity> potentialOwners = task.getPeopleAssignments().getPotentialOwners();
        List<String> ids = new ArrayList<String>(potentialOwners.size());
        for ( OrganizationalEntity entity : potentialOwners ) {
            ids.add( entity.getId() );
        }
        assertTrue( ids.contains( users.get( "tony" ).getId() ));
        assertTrue( ids.contains( users.get( "luke" ).getId() ));        
        
        // should have re-assigned by now
        long time = 0;
        while ( getWiser().getMessages().size() != 2 && time < 15000 ) {
            Thread.sleep( 500 );
            time += 500;
        }
        
        getTaskHandler = new BlockingGetTaskResponseHandler(); 
        client.getTask( taskId, getTaskHandler );
        task = getTaskHandler.getTask();
        assertEquals( Status.Ready, task.getTaskData().getStatus()  );
        potentialOwners = task.getPeopleAssignments().getPotentialOwners();
        System.out.println( potentialOwners );
        ids = new ArrayList<String>(potentialOwners.size());
        for ( OrganizationalEntity entity : potentialOwners ) {
            ids.add( entity.getId() );
        }
        assertTrue( ids.contains( users.get( "bobba" ).getId() ));
        assertTrue( ids.contains( users.get( "jabba" ).getId() ));                  
    }

//	public void setClient(TaskClient client) {
//		this.client = client;
//	}
//
//	public TaskClient getClient() {
//		return client;
//	}

	public void setConf(Properties conf) {
		this.conf = conf;
	}

	public Properties getConf() {
		return conf;
	}

	public void setWiser(Wiser wiser) {
		this.wiser = wiser;
	}

	public Wiser getWiser() {
		return wiser;
	}
}
