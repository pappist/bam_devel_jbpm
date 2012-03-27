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

package org.jbpm.ruleflow.instance;

import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;

public class RuleFlowProcessInstance extends WorkflowProcessInstanceImpl {

    private static final long serialVersionUID = 510l;
    
    public RuleFlowProcess getRuleFlowProcess() {
        return (RuleFlowProcess) getProcess();
    }

    public void internalStart() {
    	StartNode startNode = getRuleFlowProcess().getStart();
    	if (startNode != null) {
    		((NodeInstance) getNodeInstance(startNode)).trigger(null, null);
    	}
    }

}
