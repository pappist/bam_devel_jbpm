/**
 * Copyright 2005 JBoss Inc
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

package org.jbpm.workflow.instance.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.common.InternalKnowledgeRuntime;
import org.drools.definition.process.Connection;
import org.drools.definition.process.Node;
import org.drools.runtime.process.NodeInstance;
import org.drools.runtime.process.NodeInstanceContainer;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.exclusive.ExclusiveGroup;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.exclusive.ExclusiveGroupInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.node.CompositeNodeInstance;

/**
 * Default implementation of a RuleFlow node instance.
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public abstract class NodeInstanceImpl implements org.jbpm.workflow.instance.NodeInstance, Serializable {

	private static final long serialVersionUID = 510l;
	
	private long id;
    private long nodeId;
    private WorkflowProcessInstance processInstance;
    private org.jbpm.workflow.instance.NodeInstanceContainer nodeInstanceContainer;
    private Map<String, Object> metaData = new HashMap<String, Object>();

    public void setId(final long id) {
        this.id = id;
    }

    public long getId() {
        return this.id;
    }

    public void setNodeId(final long nodeId) {
        this.nodeId = nodeId;
    }

    public long getNodeId() {
        return this.nodeId;
    }
    
    public String getNodeName() {
    	Node node = getNode();
    	return node == null ? "" : node.getName();
    }

    public void setProcessInstance(final WorkflowProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public WorkflowProcessInstance getProcessInstance() {
        return this.processInstance;
    }

    public NodeInstanceContainer getNodeInstanceContainer() {
        return this.nodeInstanceContainer;
    }
    
    public void setNodeInstanceContainer(NodeInstanceContainer nodeInstanceContainer) {
        this.nodeInstanceContainer = (org.jbpm.workflow.instance.NodeInstanceContainer) nodeInstanceContainer;
        if (nodeInstanceContainer != null) {
            this.nodeInstanceContainer.addNodeInstance(this);
        }
    }

    public Node getNode() {
        return ((org.jbpm.workflow.core.NodeContainer)
    		this.nodeInstanceContainer.getNodeContainer()).internalGetNode( this.nodeId );
    }
    
    public boolean isInversionOfControl() {
        return false;
    }
    
    public void cancel() {
        nodeInstanceContainer.removeNodeInstance(this);
    }
    
    public final void trigger(NodeInstance from, String type) {
    	boolean hidden = false;
    	if (getNode().getMetaData().get("hidden") != null) {
    		hidden = true;
    	}
    	InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
    	if (!hidden) {
    		((InternalProcessRuntime) kruntime.getProcessRuntime())
    			.getProcessEventSupport().fireBeforeNodeTriggered(this, kruntime);
    	}
        internalTrigger(from, type);
        if (!hidden) {
        	((InternalProcessRuntime) kruntime.getProcessRuntime())
        		.getProcessEventSupport().fireAfterNodeTriggered(this, kruntime);
        }
    }
    
    public abstract void internalTrigger(NodeInstance from, String type);
    
    protected void triggerCompleted(String type, boolean remove) {
        if (remove) {
            ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
            	.removeNodeInstance(this);
        }
        Node node = getNode();
        List<Connection> connections = null;
        if (node != null) {
        	connections = node.getOutgoingConnections(type);
        }
        if (connections == null || connections.isEmpty()) {
        	((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
        		.nodeInstanceCompleted(this, type);
        } else {
	        for (Connection connection: connections) {
	        	// stop if this process instance has been aborted / completed
	        	if (getProcessInstance().getState() != ProcessInstance.STATE_ACTIVE) {
	        		return;
	        	}
	    		triggerConnection(connection);
	        }
        }
    }
    
    protected void triggerConnection(Connection connection) {
    	boolean hidden = false;
    	if (getNode().getMetaData().get("hidden") != null) {
    		hidden = true;
    	}
    	InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
    	if (!hidden) {
    		((InternalProcessRuntime) kruntime.getProcessRuntime())
    			.getProcessEventSupport().fireBeforeNodeLeft(this, kruntime);
    	}
    	// check for exclusive group first
    	NodeInstanceContainer parent = getNodeInstanceContainer();
    	if (parent instanceof ContextInstanceContainer) {
    		List<ContextInstance> contextInstances = ((ContextInstanceContainer) parent).getContextInstances(ExclusiveGroup.EXCLUSIVE_GROUP);
    		if (contextInstances != null) {
    			for (ContextInstance contextInstance: new ArrayList<ContextInstance>(contextInstances)) {
    				ExclusiveGroupInstance groupInstance = (ExclusiveGroupInstance) contextInstance;
    				if (groupInstance.containsNodeInstance(this)) {
    					for (NodeInstance nodeInstance: groupInstance.getNodeInstances()) {
    						if (nodeInstance != this) {
    							((org.jbpm.workflow.instance.NodeInstance) nodeInstance).cancel();
    						}
    					}
    					((ContextInstanceContainer) parent).removeContextInstance(ExclusiveGroup.EXCLUSIVE_GROUP, contextInstance);
    				}
    				
    			}
    		}
    	}
    	// trigger next node
        ((org.jbpm.workflow.instance.NodeInstance) ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
        	.getNodeInstance(connection.getTo())).trigger(this, connection.getToType());
        if (!hidden) {
        	((InternalProcessRuntime) kruntime.getProcessRuntime())
        		.getProcessEventSupport().fireAfterNodeLeft(this, kruntime);
        }
    }
    
    public Context resolveContext(String contextId, Object param) {
        return ((NodeImpl) getNode()).resolveContext(contextId, param);
    }
    
    public ContextInstance resolveContextInstance(String contextId, Object param) {
        Context context = resolveContext(contextId, param);
        if (context == null) {
            return null;
        }
        ContextInstanceContainer contextInstanceContainer
        	= getContextInstanceContainer(context.getContextContainer());
        if (contextInstanceContainer == null) {
        	throw new IllegalArgumentException(
    			"Could not find context instance container for context");
        }
        return contextInstanceContainer.getContextInstance(context);
    }
    
    private ContextInstanceContainer getContextInstanceContainer(ContextContainer contextContainer) {
    	ContextInstanceContainer contextInstanceContainer = null; 
		if (this instanceof ContextInstanceContainer) {
        	contextInstanceContainer = (ContextInstanceContainer) this;
        } else {
        	contextInstanceContainer = getEnclosingContextInstanceContainer(this);
        }
        while (contextInstanceContainer != null) {
    		if (contextInstanceContainer.getContextContainer() == contextContainer) {
    			return contextInstanceContainer;
    		}
    		contextInstanceContainer = getEnclosingContextInstanceContainer(
				(NodeInstance) contextInstanceContainer);
    	}
        return null;
    }
    
    private ContextInstanceContainer getEnclosingContextInstanceContainer(NodeInstance nodeInstance) {
    	NodeInstanceContainer nodeInstanceContainer = nodeInstance.getNodeInstanceContainer();
    	while (true) {
    		if (nodeInstanceContainer instanceof ContextInstanceContainer) {
    			return (ContextInstanceContainer) nodeInstanceContainer;
    		}
    		if (nodeInstanceContainer instanceof NodeInstance) {
    			nodeInstanceContainer = ((NodeInstance) nodeInstanceContainer).getNodeInstanceContainer();
    		} else {
    			return null;
    		}
    	}
    }
    
    public Object getVariable(String variableName) {
    	VariableScopeInstance variableScope = (VariableScopeInstance)
    		resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
    	if (variableScope == null) {
    		variableScope = (VariableScopeInstance) ((ProcessInstance) 
    			getProcessInstance()).getContextInstance(VariableScope.VARIABLE_SCOPE);
    	}
    	return variableScope.getVariable(variableName);
    }
    
    public void setVariable(String variableName, Object value) {
    	VariableScopeInstance variableScope = (VariableScopeInstance)
    		resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
    	if (variableScope == null) {
    		variableScope = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
    		if (variableScope.getVariableScope().findVariable(variableName) == null) {
    			variableScope = null;
    		}
    	}
    	if (variableScope == null) {
    		System.err.println("Could not find variable " + variableName);
    		System.err.println("Using process-level scope");
    		variableScope = (VariableScopeInstance) ((ProcessInstance) 
    			getProcessInstance()).getContextInstance(VariableScope.VARIABLE_SCOPE);
    	}
    	variableScope.setVariable(variableName, value);
    }

    public String getUniqueId() {
    	String result = "" + getId();
    	NodeInstanceContainer parent = getNodeInstanceContainer();
    	while (parent instanceof CompositeNodeInstance) {
    		CompositeNodeInstance nodeInstance = (CompositeNodeInstance) parent;
    		result = nodeInstance.getId() + ":" + result;
    		parent = nodeInstance.getNodeInstanceContainer();
    	}
    	return result;
    }
    
	public Map<String, Object> getMetaData() {
		return this.metaData;
	}

    public void setMetaData(String name, Object data) {
        this.metaData.put(name, data);
    }
    
}
