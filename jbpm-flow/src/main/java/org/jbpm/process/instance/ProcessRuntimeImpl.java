package org.jbpm.process.instance;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.RuleBase;
import org.drools.SessionConfiguration;
import org.drools.WorkingMemory;
import org.drools.common.AbstractWorkingMemory;
import org.drools.common.InternalKnowledgeRuntime;
import org.drools.common.InternalRuleBase;
import org.drools.definition.process.Process;
import org.drools.event.ProcessEventSupport;
import org.drools.event.RuleFlowGroupDeactivatedEvent;
import org.drools.event.knowledgebase.AfterProcessAddedEvent;
import org.drools.event.knowledgebase.AfterProcessRemovedEvent;
import org.drools.event.knowledgebase.DefaultKnowledgeBaseEventListener;
import org.drools.event.process.ProcessEventListener;
import org.drools.event.rule.ActivationCreatedEvent;
import org.drools.event.rule.DefaultAgendaEventListener;
import org.drools.impl.InternalKnowledgeBase;
import org.drools.rule.Rule;
import org.drools.runtime.process.EventListener;
import org.drools.runtime.process.ProcessInstance;
import org.drools.runtime.process.WorkItemManager;
import org.drools.time.AcceptsTimerJobFactoryManager;
import org.drools.time.impl.DefaultTimerJobFactoryManager;
import org.drools.time.impl.TrackableTimeJobFactoryManager;
import org.drools.util.CompositeClassLoader;
import org.jbpm.process.core.event.EventFilter;
import org.jbpm.process.core.event.EventTypeFilter;
import org.jbpm.process.instance.event.SignalManager;
import org.jbpm.process.instance.event.SignalManagerFactory;
import org.jbpm.process.instance.timer.TimerManager;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.node.EventTrigger;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.Trigger;

public class ProcessRuntimeImpl implements InternalProcessRuntime {
	
	private AbstractWorkingMemory workingMemory;
	private InternalKnowledgeRuntime kruntime;
	
	private ProcessInstanceManager processInstanceManager;
	private SignalManager signalManager;
	private TimerManager timerManager;
	private ProcessEventSupport processEventSupport;
	private DefaultKnowledgeBaseEventListener knowledgeBaseListener;

	public ProcessRuntimeImpl(InternalKnowledgeRuntime kruntime) {
		this.kruntime = kruntime;
        AcceptsTimerJobFactoryManager jfm = ( AcceptsTimerJobFactoryManager ) kruntime.getTimerService();
        if ( jfm.getTimerJobFactoryManager() instanceof DefaultTimerJobFactoryManager ) {
            jfm.setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );
        }		
		((AcceptsTimerJobFactoryManager)kruntime.getTimerService()).setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );		
		((CompositeClassLoader) getRootClassLoader()).addClassLoader( getClass().getClassLoader() );
		initProcessInstanceManager();
		initSignalManager();
		timerManager = new TimerManager(kruntime, kruntime.getTimerService());
        processEventSupport = new ProcessEventSupport();
        initProcessEventListeners();
        initProcessActivationListener();        
	}
	
	public ProcessRuntimeImpl(AbstractWorkingMemory workingMemory) {
		this.workingMemory = workingMemory;
		AcceptsTimerJobFactoryManager jfm = ( AcceptsTimerJobFactoryManager ) workingMemory.getTimerService();
		if ( jfm.getTimerJobFactoryManager() instanceof DefaultTimerJobFactoryManager ) {
		    jfm.setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );
		}
		
		this.kruntime = (InternalKnowledgeRuntime) workingMemory.getKnowledgeRuntime();
		((CompositeClassLoader) getRootClassLoader()).addClassLoader( getClass().getClassLoader() );
		initProcessInstanceManager();
		initSignalManager();
		timerManager = new TimerManager(kruntime, kruntime.getTimerService());
        processEventSupport = new ProcessEventSupport();
        initProcessEventListeners();
        initProcessActivationListener();
	}
	
	private void initProcessInstanceManager() {
		String processInstanceManagerClass = ((SessionConfiguration) kruntime.getSessionConfiguration()).getProcessInstanceManagerFactory();
		try {
			processInstanceManager = 
				((ProcessInstanceManagerFactory) loadClass(processInstanceManagerClass).newInstance())
			        .createProcessInstanceManager(kruntime);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void initSignalManager() {
		String signalManagerClass = ((SessionConfiguration) kruntime.getSessionConfiguration()).getSignalManagerFactory();
		try {
			signalManager = ((SignalManagerFactory) loadClass(signalManagerClass).newInstance())
		        .createSignalManager(kruntime);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Class<?> loadClass(String className) {
	    try {
            return getRootClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
	}
	
	private ClassLoader getRootClassLoader() {
		RuleBase ruleBase = ((InternalKnowledgeBase) kruntime.getKnowledgeBase()).getRuleBase();
		if (ruleBase != null) {
			return ((InternalRuleBase) ((InternalKnowledgeBase) kruntime.getKnowledgeBase()).getRuleBase()).getRootClassLoader();
		}
		CompositeClassLoader result = new CompositeClassLoader();
		result.addClassLoader(this.getClass().getClassLoader());
		return result;
	}
	
    public ProcessInstance startProcess(final String processId) {
        return startProcess(processId, null);
    }

    public ProcessInstance startProcess(String processId,
                                        Map<String, Object> parameters) {
    	ProcessInstance processInstance = createProcessInstance(processId, parameters);
        if ( processInstance != null ) {
            // start process instance
        	return startProcessInstance(processInstance.getId());
        }
        return null;
    }
    
    public ProcessInstance createProcessInstance(String processId,
                                                 Map<String, Object> parameters) {
        try {
            kruntime.startOperation();
            if ( !kruntime.getActionQueue().isEmpty() ) {
            	kruntime.executeQueuedActions();
            }
            final Process process = kruntime.getKnowledgeBase().getProcess( processId );
            if ( process == null ) {
                throw new IllegalArgumentException( "Unknown process ID: " + processId );
            }
            return startProcess( process, parameters );
        } finally {
        	kruntime.endOperation();
        }
    }
    
    public ProcessInstance startProcessInstance(long processInstanceId) {
        try {
            kruntime.startOperation();
            if ( !kruntime.getActionQueue().isEmpty() ) {
            	kruntime.executeQueuedActions();
            }
            ProcessInstance processInstance = getProcessInstance(processInstanceId);
	        getProcessEventSupport().fireBeforeProcessStarted( processInstance, kruntime );
	        ((org.jbpm.process.instance.ProcessInstance) processInstance).start();
	        getProcessEventSupport().fireAfterProcessStarted( processInstance, kruntime );
	        return processInstance;
        } finally {
        	kruntime.endOperation();
        }
    }

    private org.jbpm.process.instance.ProcessInstance startProcess(final Process process,
                                         Map<String, Object> parameters) {
        ProcessInstanceFactory conf = ProcessInstanceFactoryRegistry.INSTANCE.getProcessInstanceFactory( process );
        if ( conf == null ) {
            throw new IllegalArgumentException( "Illegal process type: " + process.getClass() );
        }
        return conf.createProcessInstance( process,
        								   kruntime,
                                           parameters );
    }

    public ProcessInstanceManager getProcessInstanceManager() {
        return processInstanceManager;
    }
    
    public TimerManager getTimerManager() {
    	return timerManager;
    }
    
    public SignalManager getSignalManager() {
    	return signalManager;
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return processInstanceManager.getProcessInstances();
    }

    public ProcessInstance getProcessInstance(long id) {
        return processInstanceManager.getProcessInstance( id );
    }

    public void removeProcessInstance(ProcessInstance processInstance) {
        processInstanceManager.removeProcessInstance( processInstance );
    }
    
    private void initProcessEventListeners() {
        for ( Process process : kruntime.getKnowledgeBase().getProcesses() ) {
            initProcessEventListener(process);
        }
        knowledgeBaseListener = new DefaultKnowledgeBaseEventListener() {
        	@Override
        	public void afterProcessAdded(AfterProcessAddedEvent event) {
        		initProcessEventListener(event.getProcess());
        	}
        	@Override
        	public void afterProcessRemoved(AfterProcessRemovedEvent event) {
        		if (event.getProcess() instanceof RuleFlowProcess) {
        			String type = (String)
    				    ((RuleFlowProcess) event.getProcess()).getMetaData().get("StartProcessEventType");
        			StartProcessEventListener listener = (StartProcessEventListener)
        				((RuleFlowProcess) event.getProcess()).getMetaData().get("StartProcessEventListener");
        			if (type != null && listener != null) {
        				signalManager.removeEventListener(type, listener);
        			}
        		}
        	}
		};
        kruntime.getKnowledgeBase().addEventListener(knowledgeBaseListener);
    }
    
    private void initProcessEventListener(Process process) {
    	if ( process instanceof RuleFlowProcess ) {
            StartNode startNode = ((RuleFlowProcess) process).getStart();
            if (startNode != null) {
                List<Trigger> triggers = startNode.getTriggers();
                if ( triggers != null ) {
                    for ( Trigger trigger : triggers ) {
                        if ( trigger instanceof EventTrigger ) {
                            final List<EventFilter> filters = ((EventTrigger) trigger).getEventFilters();
                            String type = null;
                            for ( EventFilter filter : filters ) {
                                if ( filter instanceof EventTypeFilter ) {
                                    type = ((EventTypeFilter) filter).getType();
                                }
                            }
                            StartProcessEventListener listener = new StartProcessEventListener( process.getId(),
                                                                                                filters,
                                                                                                trigger.getInMappings() );
                            signalManager.addEventListener( type,
                                                            listener );
                            ((RuleFlowProcess) process).getMetaData().put("StartProcessEventType", type);
                            ((RuleFlowProcess) process).getMetaData().put("StartProcessEventListener", listener);
                        }
                    }
                }
            }
        }
    }
    
    public ProcessEventSupport getProcessEventSupport() {
    	return processEventSupport;
    }

    public void addEventListener(final ProcessEventListener listener) {
        this.processEventSupport.addEventListener( listener );
    }

    public void removeEventListener(final ProcessEventListener listener) {
        this.processEventSupport.removeEventListener( listener );
    }

    public List<ProcessEventListener> getProcessEventListeners() {
        return processEventSupport.getEventListeners();
    }

    private class StartProcessEventListener implements EventListener {
    	
	    private String              processId;
	    private List<EventFilter>   eventFilters;
	    private Map<String, String> inMappings;
	
	    public StartProcessEventListener(String processId,
	                                     List<EventFilter> eventFilters,
	                                     Map<String, String> inMappings) {
	        this.processId = processId;
	        this.eventFilters = eventFilters;
	        this.inMappings = inMappings;
	    }
	
	    public String[] getEventTypes() {
	        return null;
	    }
	
	    public void signalEvent(String type,
	                            Object event) {
	        for ( EventFilter filter : eventFilters ) {
	            if ( !filter.acceptsEvent( type,
	                                       event ) ) {
	                return;
	            }
	        }
	        Map<String, Object> params = null;
	        if ( inMappings != null && !inMappings.isEmpty() ) {
	            params = new HashMap<String, Object>();
	            for ( Map.Entry<String, String> entry : inMappings.entrySet() ) {
	                if ( "event".equals( entry.getValue() ) ) {
	                    params.put( entry.getKey(),
	                                event );
	                } else {
	                    params.put( entry.getKey(),
	                                entry.getValue() );
	                }
	            }
	        }
	        startProcess( processId,
	                      params );
	    }
	}

    private void initProcessActivationListener() {
    	kruntime.addEventListener(new DefaultAgendaEventListener() {
			public void activationCreated(ActivationCreatedEvent event) {
                String ruleFlowGroup = ((Rule) event.getActivation().getRule()).getRuleFlowGroup();
                if ( "DROOLS_SYSTEM".equals( ruleFlowGroup ) ) {
                    // new activations of the rule associate with a state node
                    // signal process instances of that state node
                    String ruleName = event.getActivation().getRule().getName();
                    if ( ruleName.startsWith( "RuleFlowStateNode-" ) ) {
                        int index = ruleName.indexOf( "-",
                                                      18 );
                        index = ruleName.indexOf( "-",
                                                  index + 1 );
                        String eventType = ruleName.substring( 0,
                                                               index );
                        signalManager.signalEvent( eventType,
                                                   event );
                    }
                }
			}
    	});
    	if (workingMemory != null) {
	        workingMemory.addEventListener( new org.drools.event.DefaultAgendaEventListener() {
	            public void afterRuleFlowGroupDeactivated(final RuleFlowGroupDeactivatedEvent event,
	                                                      final WorkingMemory workingMemory) {
	                signalManager.signalEvent( "RuleFlowGroup_" + event.getRuleFlowGroup().getName(),
	                                           null );
	            }
	        } );
    	}
    }

	public void abortProcessInstance(long processInstanceId) {
		ProcessInstance processInstance = getProcessInstance(processInstanceId);
		if ( processInstance == null ) {
            throw new IllegalArgumentException( "Could not find process instance for id " + processInstanceId );
        }
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setState( ProcessInstance.STATE_ABORTED );
	}

	public WorkItemManager getWorkItemManager() {
		return kruntime.getWorkItemManager();
	}

	public void signalEvent(String type, Object event) {
		signalManager.signalEvent(type, event);
	}

	public void signalEvent(String type, Object event, long processInstanceId) {
		signalManager.signalEvent(processInstanceId, type, event);
	}
	
	public void setProcessEventSupport(ProcessEventSupport processEventSupport) {
		this.processEventSupport = processEventSupport;
	}
	
	public void dispose() {
        this.processEventSupport.reset();
        this.timerManager.dispose();
        kruntime.getKnowledgeBase().removeEventListener(knowledgeBaseListener);
        kruntime = null;
        workingMemory = null;
	}

	public void clearProcessInstances() {
		this.processInstanceManager.clearProcessInstances();
	}

}
