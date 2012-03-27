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

package org.jbpm.workflow.instance.node;

import java.util.Collection;

import org.drools.runtime.process.NodeInstance;
import org.drools.runtime.process.WorkflowProcessInstance;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.workflow.core.node.FaultNode;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;

/**
 * Runtime counterpart of a fault node.
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class FaultNodeInstance extends NodeInstanceImpl {

    private static final long serialVersionUID = 510l;
    
    protected FaultNode getFaultNode() {
        return (FaultNode) getNode();
    }
    
    public void internalTrigger(final NodeInstance from, String type) {
        if (!org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                "A FaultNode only accepts default incoming connections!");
        }
        String faultName = getFaultName();
        ExceptionScopeInstance exceptionScopeInstance = getExceptionScopeInstance(faultName);
        NodeInstanceContainer nodeInstanceContainer =  (NodeInstanceContainer) getNodeInstanceContainer();
        nodeInstanceContainer.removeNodeInstance(this);
        if (getFaultNode().isTerminateParent()) {
            if (nodeInstanceContainer instanceof CompositeNodeInstance) {
                ((CompositeNodeInstance) nodeInstanceContainer).cancel();
            } else if (nodeInstanceContainer instanceof WorkflowProcessInstance) {
                Collection<NodeInstance> nodeInstances = ((WorkflowProcessInstance) nodeInstanceContainer).getNodeInstances();
                for (NodeInstance nodeInstance: nodeInstances) {
                    ((org.jbpm.workflow.instance.NodeInstance) nodeInstance).cancel();
                }
            }
        }
        if (exceptionScopeInstance != null) {
        	handleException(faultName, exceptionScopeInstance);
        } else {
        	((ProcessInstance) getProcessInstance()).setState(ProcessInstance.STATE_ABORTED);
        }
    }
    
    protected ExceptionScopeInstance getExceptionScopeInstance(String faultName) {
    	return (ExceptionScopeInstance)
    		resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, faultName);
    }
    
    protected String getFaultName() {
    	return getFaultNode().getFaultName();
    }
    
    protected Object getFaultData() {
    	Object value = null;
    	String faultVariable = getFaultNode().getFaultVariable();
    	if (faultVariable != null) {
    		VariableScopeInstance variableScopeInstance = (VariableScopeInstance)
            	resolveContextInstance(VariableScope.VARIABLE_SCOPE, faultVariable);
            if (variableScopeInstance != null) {
                value = variableScopeInstance.getVariable(faultVariable);
            } else {
                System.err.println("Could not find variable scope for variable " + faultVariable);
                System.err.println("when trying to execute fault node " + getFaultNode().getName());
                System.err.println("Continuing without setting value.");
            }
    	}
    	return value;
    }
    
    protected void handleException(String faultName, ExceptionScopeInstance exceptionScopeInstance) {
        exceptionScopeInstance.handleException(faultName, getFaultData());
    }

}
