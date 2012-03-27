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

package org.jbpm.workflow.instance.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drools.RuntimeDroolsException;
import org.drools.runtime.process.EventListener;
import org.drools.runtime.process.NodeInstance;
import org.drools.time.TimeUtils;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.timer.Timer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.timer.TimerInstance;
import org.jbpm.process.instance.timer.TimerManager;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.node.StateBasedNode;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.impl.ExtendedNodeInstanceImpl;
import org.jbpm.workflow.instance.impl.NodeInstanceResolverFactory;
import org.mvel2.MVEL;

public abstract class StateBasedNodeInstance extends ExtendedNodeInstanceImpl implements EventBasedNodeInstanceInterface, EventListener {
	
	private static final long serialVersionUID = 510l;
    private static final Pattern PARAMETER_MATCHER = Pattern.compile("#\\{(\\S+)\\}", Pattern.DOTALL);

	private List<Long> timerInstances;

	public StateBasedNode getEventBasedNode() {
        return (StateBasedNode) getNode();
    }
    
	public void internalTrigger(NodeInstance from, String type) {
		super.internalTrigger(from, type);
		// activate timers
		Map<Timer, DroolsAction> timers = getEventBasedNode().getTimers();
		if (timers != null) {
			addTimerListener();
			timerInstances = new ArrayList<Long>(timers.size());
			TimerManager timerManager = ((InternalProcessRuntime) 
				getProcessInstance().getKnowledgeRuntime().getProcessRuntime()).getTimerManager();
			for (Timer timer: timers.keySet()) {
				TimerInstance timerInstance = createTimerInstance(timer); 
				timerManager.registerTimer(timerInstance, (ProcessInstance) getProcessInstance());
				timerInstances.add(timerInstance.getId());
			}
		}
	}
	
    protected TimerInstance createTimerInstance(Timer timer) {
    	TimerInstance timerInstance = new TimerInstance();
    	timerInstance.setDelay(resolveValue(timer.getDelay()));
    	if (timer.getPeriod() == null) {
    		timerInstance.setPeriod(0);
    	} else {
    		timerInstance.setPeriod(resolveValue(timer.getPeriod()));
    	}
    	timerInstance.setTimerId(timer.getId());
    	return timerInstance;
    }
    
    private long resolveValue(String s) {
    	try {
    		return TimeUtils.parseTimeString(s);
    	} catch (RuntimeDroolsException e) {
    		// cannot parse delay, trying to interpret it
    		Map<String, String> replacements = new HashMap<String, String>();
    		Matcher matcher = PARAMETER_MATCHER.matcher(s);
            while (matcher.find()) {
            	String paramName = matcher.group(1);
            	if (replacements.get(paramName) == null) {
                	VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
                    	resolveContextInstance(VariableScope.VARIABLE_SCOPE, paramName);
                    if (variableScopeInstance != null) {
                        Object variableValue = variableScopeInstance.getVariable(paramName);
                    	String variableValueString = variableValue == null ? "" : variableValue.toString(); 
    	                replacements.put(paramName, variableValueString);
                    } else {
                    	try {
                    		Object variableValue = MVEL.eval(paramName, new NodeInstanceResolverFactory(this));
    	                	String variableValueString = variableValue == null ? "" : variableValue.toString();
    	                	replacements.put(paramName, variableValueString);
                    	} catch (Throwable t) {
    	                    System.err.println("Could not find variable scope for variable " + paramName);
    	                    System.err.println("when trying to replace variable in processId for sub process " + getNodeName());
    	                    System.err.println("Continuing without setting process id.");
                    	}
                    }
            	}
            }
            for (Map.Entry<String, String> replacement: replacements.entrySet()) {
            	s = s.replace("#{" + replacement.getKey() + "}", replacement.getValue());
            }
            return TimeUtils.parseTimeString(s);
    	}
    }

    public void signalEvent(String type, Object event) {
    	if ("timerTriggered".equals(type)) {
    		TimerInstance timerInstance = (TimerInstance) event;
            if (timerInstances.contains(timerInstance.getId())) {
                triggerTimer(timerInstance);
            }
    	}
    }
    
    private void triggerTimer(TimerInstance timerInstance) {
    	for (Map.Entry<Timer, DroolsAction> entry: getEventBasedNode().getTimers().entrySet()) {
    		if (entry.getKey().getId() == timerInstance.getTimerId()) {
    			executeAction(entry.getValue());
    			return;
    		}
    	}
    }
    
    public String[] getEventTypes() {
    	return new String[] { "timerTriggered" };
    }
    
    public void triggerCompleted() {
        triggerCompleted(org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE, true);
    }
    
    public void addEventListeners() {
    	if (timerInstances != null && timerInstances.size() > 0) {
    		addTimerListener();
    	}
    }
    
    protected void addTimerListener() {
    	((WorkflowProcessInstance) getProcessInstance()).addEventListener("timerTriggered", this, false);
    }
    
    public void removeEventListeners() {
    	((WorkflowProcessInstance) getProcessInstance()).removeEventListener("timerTriggered", this, false);
    }

	protected void triggerCompleted(String type, boolean remove) {
		cancelTimers();
		super.triggerCompleted(type, remove);
	}
	
	public List<Long> getTimerInstances() {
		return timerInstances;
	}
	
	public void internalSetTimerInstances(List<Long> timerInstances) {
		this.timerInstances = timerInstances;
	}

    public void cancel() {
        cancelTimers();
        removeEventListeners();
        super.cancel();
    }
    
	private void cancelTimers() {
		// deactivate still active timers
		if (timerInstances != null) {
			TimerManager timerManager = ((InternalProcessRuntime)
				getProcessInstance().getKnowledgeRuntime().getProcessRuntime()).getTimerManager();
			for (Long id: timerInstances) {
				timerManager.cancelTimer(id);
			}
		}
	}
	
}
