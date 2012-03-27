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

import org.drools.runtime.process.NodeInstance;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;

/**
 * Runtime counterpart of a start node.
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class StartNodeInstance extends NodeInstanceImpl {

    private static final long serialVersionUID = 510l;

    public void internalTrigger(final NodeInstance from, String type) {
        if (type != null) {
            throw new IllegalArgumentException(
                "A StartNode does not accept incoming connections!");
        }
        if (from != null) {
            throw new IllegalArgumentException(
                "A StartNode can only be triggered by the process itself!");
        }
        triggerCompleted();
    }
    
    public StartNode getStartNode() {
        return (StartNode) getNode();
    }

    public void triggerCompleted() {
        triggerCompleted(org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE, true);
    }
    
}
