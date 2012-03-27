package org.jbpm.process.instance;

import org.drools.event.ProcessEventSupport;
import org.jbpm.process.instance.event.SignalManager;
import org.jbpm.process.instance.timer.TimerManager;

public interface InternalProcessRuntime extends org.drools.runtime.process.InternalProcessRuntime {
	
	ProcessInstanceManager getProcessInstanceManager();
	
	SignalManager getSignalManager();
	
	TimerManager getTimerManager();
	
	ProcessEventSupport getProcessEventSupport();

}
