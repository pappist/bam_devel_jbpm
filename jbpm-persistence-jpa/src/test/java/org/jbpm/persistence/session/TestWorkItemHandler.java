package org.jbpm.persistence.session;

import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;

public class TestWorkItemHandler implements WorkItemHandler {

	private static TestWorkItemHandler INSTANCE = new TestWorkItemHandler();
	
	private WorkItem workItem;
	private WorkItem aborted;
	
	public static TestWorkItemHandler getInstance() {
		return INSTANCE;
	}
	
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		this.workItem = workItem;
	}

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		this.aborted = workItem;
	}
	
	public WorkItem getWorkItem() {
		WorkItem result = workItem;
		workItem = null;
		return result;
	}

	public WorkItem getAbortedWorkItem() {
		WorkItem result = aborted;
		aborted = null;
		return result;
	}

}
