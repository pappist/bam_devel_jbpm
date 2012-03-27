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

package org.jbpm.workflow.instance.impl;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.workflow.instance.NodeInstance;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

public class NodeInstanceResolverFactory extends ImmutableDefaultFactory {

	private static final long serialVersionUID = 510l;
	
	private NodeInstance nodeInstance;
	
	public NodeInstanceResolverFactory(NodeInstance nodeInstance) {
		this.nodeInstance = nodeInstance;
	}

	public boolean isResolveable(String name) {
		return nodeInstance.resolveContextInstance(VariableScope.VARIABLE_SCOPE, name) != null;
	}
	
	public VariableResolver getVariableResolver(String name) {
		Object value = ((VariableScopeInstance)
			nodeInstance.resolveContextInstance(
					VariableScope.VARIABLE_SCOPE, name)).getVariable(name);
		return new SimpleValueResolver(value);
	}
	
}
