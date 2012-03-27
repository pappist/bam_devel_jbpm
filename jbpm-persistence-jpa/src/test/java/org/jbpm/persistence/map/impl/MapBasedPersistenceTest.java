package org.jbpm.persistence.map.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.persistence.info.SessionInfo;
import org.drools.persistence.info.WorkItemInfo;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.persistence.map.EnvironmentBuilder;
import org.drools.persistence.map.KnowledgeSessionStorageEnvironmentBuilder;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.StatefulKnowledgeSession;
import org.jbpm.persistence.ProcessStorage;
import org.jbpm.persistence.ProcessStorageEnvironmentBuilder;
import org.jbpm.persistence.processinstance.ProcessInstanceInfo;
import org.junit.Before;

public class MapBasedPersistenceTest extends MapPersistenceTest{
    
    private SimpleProcessStorage storage;
    
    @Before
    public void createStorage(){
        storage = new SimpleProcessStorage();
    }
    
    @Override
    protected StatefulKnowledgeSession createSession(KnowledgeBase kbase) {
        
        EnvironmentBuilder envBuilder = new ProcessStorageEnvironmentBuilder( storage );
        Environment env = KnowledgeBaseFactory.newEnvironment();
        env.set( EnvironmentName.TRANSACTION_MANAGER,
                 envBuilder.getTransactionManager() );
        env.set( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER,
                 envBuilder.getPersistenceContextManager() );

        return JPAKnowledgeService.newStatefulKnowledgeSession( kbase,
                                                                null,
                                                                env );
    }
    
    @Override
    protected StatefulKnowledgeSession disposeAndReloadSession(StatefulKnowledgeSession ksession,
                                                             KnowledgeBase kbase) {
        int sessionId = ksession.getId();
        ksession.dispose();
        EnvironmentBuilder envBuilder = new ProcessStorageEnvironmentBuilder( storage );
        Environment env = KnowledgeBaseFactory.newEnvironment();
        env.set( EnvironmentName.TRANSACTION_MANAGER,
                 envBuilder.getTransactionManager() );
        env.set( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER,
                 envBuilder.getPersistenceContextManager() );
        
        return JPAKnowledgeService.loadStatefulKnowledgeSession( sessionId, kbase, null, env );
    }
    
    @Override
    protected int getProcessInstancesCount() {
        return storage.processes.size();
    }

    @Override
    protected int getKnowledgeSessionsCount() {
        return storage.ksessions.size();
    }
    
    private static class SimpleProcessStorage
        implements
        ProcessStorage {
        private Map<Integer, SessionInfo>         ksessions = new HashMap<Integer, SessionInfo>();
        private Map<Long, ProcessInstanceInfo> processes = new HashMap<Long, ProcessInstanceInfo>();
        private Map<Long, WorkItemInfo>        workItems = new HashMap<Long, WorkItemInfo>();

        public void saveOrUpdate(SessionInfo ksessionInfo) {
            ksessionInfo.update();
            ksessions.put( ksessionInfo.getId(),
                           ksessionInfo );
        }

        public SessionInfo findSessionInfo(Integer id) {
            return ksessions.get( id );
        }

        public ProcessInstanceInfo findProcessInstanceInfo(Long processInstanceId) {
            ProcessInstanceInfo processInstanceInfo = processes.get( processInstanceId );
            if(processInstanceInfo != null) {
                //FIXME need a way to clone a processInstance before saving
                processInstanceInfo.clearProcessInstance();
            }
            return processInstanceInfo;
        }

        public void saveOrUpdate(ProcessInstanceInfo processInstanceInfo) {
            processInstanceInfo.update();
            processes.put( processInstanceInfo.getId(),
                           processInstanceInfo );
        }

        public long getNextProcessInstanceId() {
            return processes.size() + 1;
        }

        public void removeProcessInstanceInfo(Long id) {
            processes.remove( id );
        }

        public List<Long> getProcessInstancesWaitingForEvent(String type) {
            List<Long> processInstancesWaitingForEvent = new ArrayList<Long>();
            for ( ProcessInstanceInfo processInstanceInfo : processes.values() ) {
                if ( processInstanceInfo.getEventTypes().contains( type ) ) processInstancesWaitingForEvent.add( processInstanceInfo.getId() );
            }
            return processInstancesWaitingForEvent;
        }

        public void saveOrUpdate(WorkItemInfo workItemInfo) {
            workItems.put( workItemInfo.getId(),
                           workItemInfo );
        }

        public Long getNextWorkItemId() {
            return new Long( workItems.size() + 1 );
        }

        public WorkItemInfo findWorkItemInfo(Long id) {
            return workItems.get( id );
        }

        public void remove(WorkItemInfo workItemInfo) {
            workItems.remove( workItemInfo.getId() );
        }

        public Integer getNextStatefulKnowledgeSessionId() {
            return  ksessions.size() + 1 ;
        }
    }
}
