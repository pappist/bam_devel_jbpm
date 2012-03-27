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

package org.jbpm.process.instance.context.exception;

import org.drools.spi.ProcessContext;
import org.jbpm.process.core.context.exception.ActionExceptionHandler;
import org.jbpm.process.core.context.exception.ExceptionHandler;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.workflow.instance.NodeInstance;

public class DefaultExceptionScopeInstance extends ExceptionScopeInstance {

	private static final long serialVersionUID = 510l;

	public void handleException(ExceptionHandler handler, String exception, Object params) {
		
		if (handler instanceof ActionExceptionHandler) {
			Action action = (Action) ((ActionExceptionHandler) handler).getAction().getMetaData("Action");
			try {
		    	ProcessInstance processInstance = getProcessInstance();
			    ProcessContext processContext = new ProcessContext(processInstance.getKnowledgeRuntime());
			    ContextInstanceContainer contextInstanceContainer = getContextInstanceContainer();
			    if (contextInstanceContainer instanceof NodeInstance) {
			    	processContext.setNodeInstance((NodeInstance) contextInstanceContainer);
			    } else {
			    	processContext.setProcessInstance(processInstance);
			    }
			    String faultVariable = handler.getFaultVariable();
			    if (faultVariable != null) {
			    	processContext.setVariable(faultVariable, params);
			    }
		        action.execute(processContext);
			} catch (Exception e) {
			    throw new RuntimeException("unable to execute Action", e);
			}
		} else {
			throw new IllegalArgumentException("Unknown exception handler " + handler);
		}
	}

}
