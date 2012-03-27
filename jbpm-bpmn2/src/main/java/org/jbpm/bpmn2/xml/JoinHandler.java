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

package org.jbpm.bpmn2.xml;

import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.node.Join;
import org.xml.sax.Attributes;

public class JoinHandler extends AbstractNodeHandler {
    
    protected Node createNode(Attributes attrs) {
    	throw new IllegalArgumentException("Reading in should be handled by gateway handler");
    }
    
    @SuppressWarnings("unchecked")
	public Class generateNodeFor() {
        return Join.class;
    }

	public void writeNode(Node node, StringBuilder xmlDump, int metaDataType) {
		Join join = (Join) node;
		switch (join.getType()) {
			case Join.TYPE_AND:
				writeNode("parallelGateway", node, xmlDump, metaDataType);
				break;
			case Join.TYPE_XOR:
				writeNode("exclusiveGateway", node, xmlDump, metaDataType);
				break;
			default:
				writeNode("complexGateway", node, xmlDump, metaDataType);
		}
		xmlDump.append("gatewayDirection=\"Converging\" ");
		endNode(xmlDump);
	}

}
