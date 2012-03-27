package org.jbpm.process.builder;

import org.drools.definition.process.Node;
import org.drools.definition.process.Process;
import org.drools.lang.descr.ActionDescr;
import org.drools.lang.descr.ProcessDescr;
import org.jbpm.process.builder.dialect.ProcessDialect;
import org.jbpm.process.builder.dialect.ProcessDialectRegistry;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.ActionNode;

public class ActionNodeBuilder extends ExtendedNodeBuilder {

    public void build(Process process,
                      ProcessDescr processDescr,
                      ProcessBuildContext context,
                      Node node) {
    	super.build(process, processDescr, context, node);
        ActionNode actionNode = ( ActionNode ) node;
        DroolsConsequenceAction action = (DroolsConsequenceAction) actionNode.getAction();
        ActionDescr actionDescr = new ActionDescr();
        actionDescr.setText( action.getConsequence() );   
        ProcessDialect dialect = ProcessDialectRegistry.getDialect( action.getDialect() );            
        dialect.getActionBuilder().build( context, action, actionDescr, (NodeImpl) node );
    }

}
