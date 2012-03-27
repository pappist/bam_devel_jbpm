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

package org.jbpm.workflow.core.impl;

import org.drools.definition.process.Node;
import org.drools.definition.process.NodeContainer;
import org.jbpm.process.core.impl.ProcessImpl;
import org.jbpm.workflow.core.WorkflowProcess;

/**
 * Default implementation of a RuleFlow process.
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class WorkflowProcessImpl extends ProcessImpl implements WorkflowProcess, org.jbpm.workflow.core.NodeContainer {

    private static final long serialVersionUID = 510l;
    
    private boolean autoComplete = false;
    private boolean dynamic = false;
    private org.jbpm.workflow.core.NodeContainer nodeContainer;
    
    public WorkflowProcessImpl() {
        nodeContainer = (org.jbpm.workflow.core.NodeContainer) createNodeContainer();
    }
    
    protected NodeContainer createNodeContainer() {
        return new NodeContainerImpl();
    }
    
    public Node[] getNodes() {
        return nodeContainer.getNodes();
    }

    public Node getNode(final long id) {
        return nodeContainer.getNode(id);
    }
    
    public Node internalGetNode(long id) {
    	return getNode(id);
    }

    public void removeNode(final Node node) {
        nodeContainer.removeNode(node);
        ((org.jbpm.workflow.core.Node) node).setNodeContainer(null);
    }

    public void addNode(final Node node) {
        nodeContainer.addNode(node);
        ((org.jbpm.workflow.core.Node) node).setNodeContainer(this);
    }
    
    public boolean isAutoComplete() {
        return autoComplete;
    }
    
    public void setAutoComplete(boolean autoComplete) {
        this.autoComplete = autoComplete;
    }

	public boolean isDynamic() {
		return dynamic;
	}

	public void setDynamic(boolean dynamic) {
		this.dynamic = dynamic;
	}

}
