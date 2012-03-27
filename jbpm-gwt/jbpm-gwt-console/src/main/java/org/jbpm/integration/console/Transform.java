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

package org.jbpm.integration.console;

import java.util.Date;

import org.drools.definition.process.Process;
import org.jboss.bpm.console.client.model.NodeInstanceRef;
import org.jboss.bpm.console.client.model.ProcessDefinitionRef;
import org.jboss.bpm.console.client.model.ProcessInstanceRef;
import org.jboss.bpm.console.client.model.TaskRef;
import org.jboss.bpm.console.client.model.TokenReference;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.task.I18NText;
import org.jbpm.task.Task;
import org.jbpm.task.query.TaskSummary;

public class Transform {
	
	public static ProcessDefinitionRef processDefinition(Process process) {
		long version = 0;
		try {
			version = new Long(process.getVersion());
		} catch (NumberFormatException e) {
			// Do nothing, keep version 0
		}
		ProcessDefinitionRef result = new ProcessDefinitionRef(
			process.getId(), process.getName(), version);
		result.setPackageName(process.getPackageName());
		result.setDeploymentId("N/A");
		return result;
	}
	
	public static ProcessInstanceRef processInstance(ProcessInstanceLog processInstance) {
		ProcessInstanceRef result = new ProcessInstanceRef(
			processInstance.getProcessInstanceId() + "",
			processInstance.getProcessId(),
			processInstance.getStart(),
			processInstance.getEnd(),
			false);
		TokenReference token = new TokenReference(
			processInstance.getProcessInstanceId() + "", null, "");
		result.setRootToken(token);
		return result;
	}
	
	public static NodeInstanceRef nodeInstance(NodeInstanceLog nodeInstance) {
		return new NodeInstanceRef(
				nodeInstance.getId(),
				nodeInstance.getType(),
				nodeInstance.getNodeId(),
				nodeInstance.getNodeName(),
				nodeInstance.getDate());
	}
	
	public static TaskRef task(TaskSummary task) {
		return new TaskRef(
			task.getId(),
			Long.toString(task.getProcessInstanceId()),
			"",
			task.getName(),
			task.getActualOwner() == null ? null : task.getActualOwner().getId(),
			false,
			false);
	}

	public static TaskRef task(Task task) {
		String name = "";
		for (I18NText text: task.getNames()) {
			if ("en-UK".equals(text.getLanguage())) {
				name = text.getText();
			}
		}
		return new TaskRef(
			task.getId(),
			Long.toString(task.getTaskData().getProcessInstanceId()),
			"",
			name,
			task.getTaskData().getActualOwner() == null ? null : task.getTaskData().getActualOwner().getId(),
			false,
			false);
	}

}
