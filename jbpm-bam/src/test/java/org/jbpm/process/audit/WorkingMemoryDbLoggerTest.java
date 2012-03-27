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

package org.jbpm.process.audit;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Properties;

import org.drools.RuleBase;
import org.drools.RuleBaseFactory;
import org.drools.SessionConfiguration;
import org.drools.StatefulSession;
import org.drools.compiler.PackageBuilder;
import org.drools.impl.EnvironmentFactory;
import org.drools.rule.Package;
import org.jbpm.JbpmTestCase;
import org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler;

public class WorkingMemoryDbLoggerTest extends JbpmTestCase {

	public void testLogger1() {
        // load the process
        RuleBase ruleBase = createKnowledgeBase();
        // create a new session
        Properties properties = new Properties();
		properties.put("drools.processInstanceManagerFactory", "org.jbpm.process.instance.impl.DefaultProcessInstanceManagerFactory");
		properties.put("drools.processSignalManagerFactory", "org.jbpm.process.instance.event.DefaultSignalManagerFactory");
		SessionConfiguration config = new SessionConfiguration(properties);
        StatefulSession session = ruleBase.newStatefulSession(config, EnvironmentFactory.newEnvironment());
        new WorkingMemoryDbLogger(session);
        session.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());

        // start process instance
        long processInstanceId = session.startProcess("com.sample.ruleflow").getId();
        
        System.out.println("Checking process instances for process 'com.sample.ruleflow'");
        List<ProcessInstanceLog> processInstances =
        	ProcessInstanceDbLog.findProcessInstances("com.sample.ruleflow");
        assertEquals(1, processInstances.size());
        ProcessInstanceLog processInstance = processInstances.get(0);
        System.out.println(processInstance);
        assertNotNull(processInstance.getStart());
        assertNotNull(processInstance.getEnd());
        assertEquals(processInstanceId, processInstance.getProcessInstanceId());
        assertEquals("com.sample.ruleflow", processInstance.getProcessId());
        List<NodeInstanceLog> nodeInstances = ProcessInstanceDbLog.findNodeInstances(processInstanceId);
        assertEquals(6, nodeInstances.size());
        for (NodeInstanceLog nodeInstance: nodeInstances) {
        	System.out.println(nodeInstance);
            assertEquals(processInstanceId, processInstance.getProcessInstanceId());
            assertEquals("com.sample.ruleflow", processInstance.getProcessId());
            assertNotNull(nodeInstance.getDate());
        }
        ProcessInstanceDbLog.clear();
	}
	
	public void testLogger2() {
        // load the process
        RuleBase ruleBase = createKnowledgeBase();
        // create a new session
        Properties properties = new Properties();
		properties.put("drools.processInstanceManagerFactory", "org.jbpm.process.instance.impl.DefaultProcessInstanceManagerFactory");
		properties.put("drools.processSignalManagerFactory", "org.jbpm.process.instance.event.DefaultSignalManagerFactory");
		SessionConfiguration config = new SessionConfiguration(properties);
        StatefulSession session = ruleBase.newStatefulSession(config, EnvironmentFactory.newEnvironment());
        new WorkingMemoryDbLogger(session);
        session.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());

        // start process instance
        session.startProcess("com.sample.ruleflow");
        session.startProcess("com.sample.ruleflow");
        
        System.out.println("Checking process instances for process 'com.sample.ruleflow'");
        List<ProcessInstanceLog> processInstances =
        	ProcessInstanceDbLog.findProcessInstances("com.sample.ruleflow");
        assertEquals(2, processInstances.size());
        for (ProcessInstanceLog processInstance: processInstances) {
            System.out.print(processInstance);
            System.out.println(" -> " + processInstance.getStart() + " - " + processInstance.getEnd());
            List<NodeInstanceLog> nodeInstances = ProcessInstanceDbLog.findNodeInstances(processInstance.getProcessInstanceId());
            for (NodeInstanceLog nodeInstance: nodeInstances) {
            	System.out.print(nodeInstance);
                System.out.println(" -> " + nodeInstance.getDate());
            }
            assertEquals(6, nodeInstances.size());
        }
        ProcessInstanceDbLog.clear();
	}
	
	public void testLogger3() {
        // load the process
        RuleBase ruleBase = createKnowledgeBase();
        // create a new session
        Properties properties = new Properties();
		properties.put("drools.processInstanceManagerFactory", "org.jbpm.process.instance.impl.DefaultProcessInstanceManagerFactory");
		properties.put("drools.processSignalManagerFactory", "org.jbpm.process.instance.event.DefaultSignalManagerFactory");
		SessionConfiguration config = new SessionConfiguration(properties);
        StatefulSession session = ruleBase.newStatefulSession(config, EnvironmentFactory.newEnvironment());
        new WorkingMemoryDbLogger(session);
        session.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());

        // start process instance
        long processInstanceId = session.startProcess("com.sample.ruleflow2").getId();
        
        System.out.println("Checking process instances for process 'com.sample.ruleflow2'");
        List<ProcessInstanceLog> processInstances =
        	ProcessInstanceDbLog.findProcessInstances("com.sample.ruleflow2");
        assertEquals(1, processInstances.size());
        ProcessInstanceLog processInstance = processInstances.get(0);
        System.out.print(processInstance);
        System.out.println(" -> " + processInstance.getStart() + " - " + processInstance.getEnd());
        assertNotNull(processInstance.getStart());
        assertNotNull(processInstance.getEnd());
        assertEquals(processInstanceId, processInstance.getProcessInstanceId());
        assertEquals("com.sample.ruleflow2", processInstance.getProcessId());
        List<NodeInstanceLog> nodeInstances = ProcessInstanceDbLog.findNodeInstances(processInstanceId);
        for (NodeInstanceLog nodeInstance: nodeInstances) {
        	System.out.print(nodeInstance);
            System.out.println(" -> " + nodeInstance.getDate());
            assertEquals(processInstanceId, processInstance.getProcessInstanceId());
            assertEquals("com.sample.ruleflow2", processInstance.getProcessId());
            assertNotNull(nodeInstance.getDate());
        }
        assertEquals(14, nodeInstances.size());
        ProcessInstanceDbLog.clear();
	}
	
    private static RuleBase createKnowledgeBase() {
        // create a builder
        PackageBuilder builder = new PackageBuilder();
        // load the process
        Reader source = new InputStreamReader(
            WorkingMemoryDbLoggerTest.class.getResourceAsStream("/ruleflow.rf"));
        builder.addProcessFromXml(source);
        source = new InputStreamReader(
    		WorkingMemoryDbLoggerTest.class.getResourceAsStream("/ruleflow2.rf"));
        builder.addProcessFromXml(source);
        // create the knowledge base 
        Package pkg = builder.getPackage();
        RuleBase ruleBase = RuleBaseFactory.newRuleBase();
        ruleBase.addPackage(pkg);
        return ruleBase;
    }
}
