package org.jbpm.process.audit;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.URLConnection;

import java.util.Map;
import java.util.Set;


import org.drools.event.process.ProcessCompletedEvent;
import org.drools.event.process.ProcessEventListener;
import org.drools.event.process.ProcessNodeLeftEvent;
import org.drools.event.process.ProcessNodeTriggeredEvent;
import org.drools.event.process.ProcessStartedEvent;
import org.drools.event.process.ProcessVariableChangedEvent;

import org.jboss.seam.security.Identity;

import org.drools.runtime.process.ProcessInstance;
import org.drools.runtime.process.NodeInstance;

import org.drools.definition.process.Process;
import org.drools.definition.process.Node;

public class BamEventListener implements ProcessEventListener {

	@Override
	public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
		System.out.println("Before Node triggered.   " + event.getNodeInstance().getNodeName());
	}

	@Override
	public void afterNodeLeft(ProcessNodeLeftEvent event) {
		//NodeInstance nodeIns = event.getNodeInstance();
		//Node node = nodeIns.getNode();
		//System.out.println(" = " + nodeIns.getNodeName());
		
		//Map<String, Object> nodeMeta = node.getMetaData();

		//Set<String> keys = nodeMeta.keySet();

		//for(String key : keys) {
		//    System.out.println("Key: " + key);
		//    System.out.println("Value: " + nodeMeta.get(key).toString());
		//}
	}

	@Override
	public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
		System.out.println("===================  After Node triggered. " + event.getNodeInstance().getNodeName());
		
	}

	@Override
	public void afterProcessCompleted(ProcessCompletedEvent event) {
		System.out.println("After process completed. " + event.getProcessInstance().getProcessName());
	}

	@Override
	public void afterProcessStarted(ProcessStartedEvent event) {
		ProcessInstance pinstance = event.getProcessInstance();
		Process process = pinstance.getProcess();
		
		//String userName = Identity.instance().getCredentials().getUsername();
		
		//System.out.println("====================");
		//System.out.println(userName);
		//System.out.println("====================");
		//sendProcess(process);
	}

	@Override
	public void afterVariableChanged(ProcessVariableChangedEvent event) {
		System.out.println("After variable change. " + event.getProcessInstance().getProcessName());
	}

	@Override
	public void beforeNodeLeft(ProcessNodeLeftEvent event) {
		System.out.println("Before Node left. " + event.getNodeInstance().getNodeName());
	}

	@Override
	public void beforeProcessCompleted(ProcessCompletedEvent event) {
		System.out.println("Before process completed. " + event.getProcessInstance().getProcessName());
	}

	@Override
	public void beforeProcessStarted(ProcessStartedEvent event) {
	}

	@Override
	public void beforeVariableChanged(ProcessVariableChangedEvent event) {
		System.out.println("Before variable changed. " + event.getProcessInstance().getProcessName());
	}

	public void sendProcess(Process process) {
		String pid = process.getId();
		String pname = process.getName();
		String pkgname = process.getPackageName();
		String ptype = process.getType();
		String pversion = process.getVersion();
		
		String params = "pid=" + pid + ";pname=" + pname + ";pkgname=" + pkgname + ";ptype=" + ptype + ";pversion=" + pversion;
	}

} 
