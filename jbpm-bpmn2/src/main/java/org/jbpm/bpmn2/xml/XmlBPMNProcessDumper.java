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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.drools.compiler.xml.XmlDumper;
import org.drools.definition.process.Connection;
import org.drools.definition.process.Node;
import org.drools.definition.process.NodeContainer;
import org.drools.definition.process.WorkflowProcess;
import org.drools.process.core.Work;
import org.drools.process.core.datatype.impl.type.ObjectDataType;
import org.drools.rule.builder.dialect.java.JavaDialect;
import org.drools.xml.Handler;
import org.drools.xml.SemanticModule;
import org.jbpm.bpmn2.core.Association;
import org.jbpm.bpmn2.core.DataStore;
import org.jbpm.bpmn2.core.Definitions;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.swimlane.Swimlane;
import org.jbpm.process.core.context.swimlane.SwimlaneContext;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.event.EventFilter;
import org.jbpm.process.core.event.EventTypeFilter;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.core.node.EventTrigger;
import org.jbpm.workflow.core.node.FaultNode;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.Join;
import org.jbpm.workflow.core.node.Split;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.Trigger;
import org.jbpm.workflow.core.node.WorkItemNode;

public class XmlBPMNProcessDumper {
	
	public static final String JAVA_LANGUAGE = "http://www.java.com/java";
	public static final String RULE_LANGUAGE = "http://www.jboss.org/drools/rule";
    public static final String XPATH_LANGUAGE = "http://www.w3.org/1999/XPath";
    
    public static final int NO_META_DATA = 0;
    public static final int META_DATA_AS_NODE_PROPERTY = 1;
    public static final int META_DATA_USING_DI = 2;
    
	public static XmlBPMNProcessDumper INSTANCE = new XmlBPMNProcessDumper();
	
    private final static String EOL = System.getProperty( "line.separator" );
    
    private SemanticModule semanticModule;
    private int metaDataType = META_DATA_USING_DI;
    
    private XmlBPMNProcessDumper() {
    	semanticModule = new BPMNSemanticModule();
    }
    
    public String dump(WorkflowProcess process) {
        return dump(process, META_DATA_USING_DI);
    }
    
    public String dump(WorkflowProcess process, boolean includeMeta) {
    	return dump(process, META_DATA_AS_NODE_PROPERTY);
    }
    
    public String dump(WorkflowProcess process, int metaDataType) {
        StringBuilder xmlDump = new StringBuilder();
        visitProcess(process, xmlDump, metaDataType);
        return xmlDump.toString();
    }
    
    public int getMetaDataType() {
		return metaDataType;
	}

	public void setMetaDataType(int metaDataType) {
		this.metaDataType = metaDataType;
	}

	protected void visitProcess(WorkflowProcess process, StringBuilder xmlDump, int metaDataType) {
        String targetNamespace = (String) process.getMetaData().get("TargetNamespace");
        if (targetNamespace == null) {
        	targetNamespace = "http://www.jboss.org/drools";
        }
    	xmlDump.append(
    		"<?xml version=\"1.0\" encoding=\"UTF-8\"?> " + EOL +
            "<definitions id=\"Definition\"" + EOL +
            "             targetNamespace=\"" + targetNamespace + "\"" + EOL +
            "             typeLanguage=\"http://www.java.com/javaTypes\"" + EOL +
            "             expressionLanguage=\"http://www.mvel.org/2.0\"" + EOL +
            "             xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"" + EOL +
            "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + EOL +
            "             xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\"" + EOL +
            "             xmlns:g=\"http://www.jboss.org/drools/flow/gpd\"" + EOL +
            (metaDataType == META_DATA_USING_DI ? 
                "             xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"" + EOL +
            	"             xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"" + EOL +
        		"             xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"" + EOL : "") +
            "             xmlns:tns=\"http://www.jboss.org/drools\">" + EOL + EOL);

    	// item definitions
    	VariableScope variableScope = (VariableScope)
    		((org.jbpm.process.core.Process) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
    	visitVariableScope(variableScope, "_", xmlDump);
    	visitSubVariableScopes(process.getNodes(), xmlDump);
        
	    visitInterfaces(process.getNodes(), xmlDump);
	    
	    visitEscalations(process.getNodes(), xmlDump, new ArrayList<String>());
	    visitErrors(process.getNodes(), xmlDump, new ArrayList<String>());
	       
	    //data stores
    	Definitions def = (Definitions) process.getMetaData().get("Definitions");
    	if (def != null && def.getDataStores() != null) {
    		for (DataStore dataStore : def.getDataStores()) {
    			visitDataStore(dataStore, xmlDump);
    		}
    	}
    	
	    // the process itself
		xmlDump.append("  <process processType=\"Private\" isExecutable=\"true\" ");
        if (process.getId() != null) {
            xmlDump.append("id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(process.getId()) + "\" ");
        }
        if (process.getName() != null) {
            xmlDump.append("name=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(process.getName()) + "\" ");
        }
        String packageName = process.getPackageName();
        if (packageName != null && !"org.drools.bpmn2".equals(packageName)) {
            xmlDump.append("tns:packageName=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(packageName) + "\" ");
        }
        if (((org.jbpm.workflow.core.WorkflowProcess) process).isDynamic()) {
        	xmlDump.append("tns:adHoc=\"true\" ");
        }
        String version = process.getVersion();
        if (version != null && !"".equals(version)) {
            xmlDump.append("tns:version=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(version) + "\" ");
        }
        // TODO: package, version
        xmlDump.append(">" + EOL + EOL);
        visitHeader(process, xmlDump, metaDataType);
        visitNodes(process, xmlDump, metaDataType);
        visitConnections(process.getNodes(), xmlDump, metaDataType);
        if (def != null && def.getAssociations() != null) {
        	for (Association association : def.getAssociations()) {
        		visitAssociation(association, xmlDump);
        	}
        }
        xmlDump.append("  </process>" + EOL + EOL);
        if (metaDataType == META_DATA_USING_DI) {
        	xmlDump.append(
    			"  <bpmndi:BPMNDiagram>" + EOL +
    			"    <bpmndi:BPMNPlane bpmnElement=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(process.getId()) + "\" >" + EOL);
        	visitNodesDi(process.getNodes(), xmlDump);
        	visitConnectionsDi(process.getNodes(), xmlDump);
        	xmlDump.append(
    			"    </bpmndi:BPMNPlane>" + EOL +
        		"  </bpmndi:BPMNDiagram>" + EOL + EOL);
        }
        xmlDump.append("</definitions>");
    }
    
    private void visitDataStore(DataStore dataStore, StringBuilder xmlDump) {
    	String itemSubjectRef = dataStore.getItemSubjectRef();
    	String itemDefId = itemSubjectRef.substring(itemSubjectRef.indexOf(':') + 1);
    	xmlDump.append("  <itemDefinition id=\"" + itemDefId + "\" ");
    	if (dataStore.getType() != null && !"java.lang.Object".equals(dataStore.getType().getStringType())) {
    		xmlDump.append("structureRef=\"" + XmlDumper.replaceIllegalChars(dataStore.getType().getStringType()) + "\" ");
    	}
    	xmlDump.append("/>" + EOL);
    	
    	xmlDump.append("  <dataStore name=\"" + XmlDumper.replaceIllegalChars(dataStore.getName()) + "\"");
    	xmlDump.append(" id=\"" + XmlDumper.replaceIllegalChars(dataStore.getId()) + "\"");
    	xmlDump.append(" itemSubjectRef=\"" + XmlDumper.replaceIllegalChars(dataStore.getItemSubjectRef()) + "\"");
    	xmlDump.append("/>" + EOL);
	}
    
    private void visitAssociation(Association association, StringBuilder xmlDump) {
    	xmlDump.append("    <association id=\"" + association.getId() + "\" ");
    	xmlDump.append(" sourceRef=\"" + association.getSourceRef() + "\" ");
    	xmlDump.append(" targetRef=\"" + association.getTargetRef() + "\" ");
    	xmlDump.append("/>" + EOL);
	}

    private void visitVariableScope(VariableScope variableScope, String prefix, StringBuilder xmlDump) {
        if (variableScope != null && !variableScope.getVariables().isEmpty()) {
            for (Variable variable: variableScope.getVariables()) {
                xmlDump.append(
                    "  <itemDefinition id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(prefix + variable.getName()) + "Item\" ");
                if (variable.getType() != null && !"java.lang.Object".equals(variable.getType().getStringType())) {
                    xmlDump.append("structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(variable.getType().getStringType()) + "\" ");
                }
                xmlDump.append("/>" + EOL);
            }
            xmlDump.append(EOL);
        }
    }
    
    private void visitSubVariableScopes(Node[] nodes, StringBuilder xmlDump) {
        for (Node node: nodes) {
            if (node instanceof ContextContainer) {
                VariableScope variableScope = (VariableScope) 
                    ((ContextContainer) node).getDefaultContext(VariableScope.VARIABLE_SCOPE);
                if (variableScope != null) {
                    visitVariableScope(variableScope, XmlBPMNProcessDumper.getUniqueNodeId(node) + "-", xmlDump);
                }
            }
            if (node instanceof NodeContainer) {
                visitSubVariableScopes(((NodeContainer) node).getNodes(), xmlDump);
            }
        }
    }
    
    private void visitLanes(WorkflowProcess process, StringBuilder xmlDump) {
        // lanes
        Collection<Swimlane> swimlanes = ((SwimlaneContext)
            ((org.jbpm.workflow.core.WorkflowProcess) process)
                .getDefaultContext(SwimlaneContext.SWIMLANE_SCOPE)).getSwimlanes();
        if (!swimlanes.isEmpty()) {
            xmlDump.append("    <laneSet>" + EOL);
            for (Swimlane swimlane: swimlanes) {
                xmlDump.append("      <lane name=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(swimlane.getName()) + "\" >" + EOL);
                visitLane(process, swimlane.getName(), xmlDump);
                xmlDump.append("      </lane>" + EOL);
            }
            xmlDump.append("    </laneSet>" + EOL);
        }
    }
    
    private void visitLane(NodeContainer container, String lane, StringBuilder xmlDump) {
        for (Node node: container.getNodes()) {
            if (node instanceof HumanTaskNode) {
                String swimlane = ((HumanTaskNode) node).getSwimlane();
                if (lane.equals(swimlane)) {
                    xmlDump.append("        <flowNodeRef>" + XmlBPMNProcessDumper.getUniqueNodeId(node) + "</flowNodeRef>" + EOL);
                }
            } else {
                String swimlane = (String) node.getMetaData().get("Lane");
                if (lane.equals(swimlane)) {
                    xmlDump.append("        <flowNodeRef>" + XmlBPMNProcessDumper.getUniqueNodeId(node) + "</flowNodeRef>" + EOL);
                }
            }
            if (node instanceof NodeContainer) {
                visitLane((NodeContainer) node, lane, xmlDump);
            }
        }
    }
    
    protected void visitHeader(WorkflowProcess process, StringBuilder xmlDump, int metaDataType) {
    	List<String> imports = ((org.jbpm.process.core.Process) process).getImports();
    	Map<String, String> globals = ((org.jbpm.process.core.Process) process).getGlobals();
    	if ((imports != null && !imports.isEmpty()) || (globals != null && globals.size() > 0)) {
    		xmlDump.append("    <extensionElements>" + EOL);
    		if (imports != null) {
	    		for (String s: imports) {
	    			xmlDump.append("     <tns:import name=\"" + s + "\" />" + EOL);
	    		}
    		}
    		if (globals != null) {
	    		for (Map.Entry<String, String> global: globals.entrySet()) {
	    			xmlDump.append("     <tns:global identifier=\"" + global.getKey() + "\" type=\"" + global.getValue() + "\" />" + EOL);
	    		}
    		}
    		xmlDump.append("    </extensionElements>" + EOL);
    	}
    	// TODO: function imports
    	// TODO: exception handlers
        VariableScope variableScope = (VariableScope)
        	((org.jbpm.process.core.Process) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        if (variableScope != null) {
            visitVariables(variableScope.getVariables(), xmlDump);
        }
        visitLanes(process, xmlDump);
    }
    
    public static void visitVariables(List<Variable> variables, StringBuilder xmlDump) {
    	if (!variables.isEmpty()) {
            xmlDump.append("    <!-- process variables -->" + EOL);
            for (Variable variable: variables) {
                if (variable.getMetaData("DataObject") == null) {
                    xmlDump.append("    <property id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(variable.getName()) + "\" ");
                    if (variable.getType() != null) {
                    	xmlDump.append("itemSubjectRef=\"_" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(variable.getName()) + "Item\"" );
                    }
                    // TODO: value?
                    xmlDump.append("/>" + EOL);
                }
            }
            for (Variable variable: variables) {
                if (variable.getMetaData("DataObject") != null) {
                    xmlDump.append("    <dataObject id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(variable.getName()) + "\" ");
                    if (variable.getType() != null) {
                        xmlDump.append("itemSubjectRef=\"_" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(variable.getName()) + "Item\"" );
                    }
                    // TODO: value?
                    xmlDump.append("/>" + EOL);
                }
            }
            xmlDump.append(EOL);
    	}
    }
    
    protected void visitInterfaces(Node[] nodes, StringBuilder xmlDump) {
        for (Node node: nodes) {
            if (node instanceof WorkItemNode) {
                Work work = ((WorkItemNode) node).getWork();
                if (work != null) {
                    if ("Service Task".equals(work.getName())) {
                        String interfaceName = (String) work.getParameter("Interface");
                        if (interfaceName == null) {
                            interfaceName = "";
                        }
                        String operationName = (String) work.getParameter("Operation");
                        if (operationName == null) {
                            operationName = "";
                        }
                        String parameterType = (String) work.getParameter("ParameterType");
                        if (parameterType == null) {
                            parameterType = "";
                        }
                        xmlDump.append(
                            "  <itemDefinition id=\"" + getUniqueNodeId(node) + "_InMessageType\" " + 
                            	("".equals(parameterType) || "java.lang.Object".equals(parameterType) ?
                        			"" : "structureRef=\"" + parameterType + "\" ")
                        			+ "/>" + EOL +
                            "  <message id=\"" + getUniqueNodeId(node) + "_InMessage\" itemRef=\"" + getUniqueNodeId(node) + "_InMessageType\" />" + EOL +
                            "  <interface id=\"" + getUniqueNodeId(node) + "_ServiceInterface\" name=\"" + interfaceName + "\">" + EOL +
                            "    <operation id=\"" + getUniqueNodeId(node) + "_ServiceOperation\" name=\"" + operationName + "\">" + EOL + 
                            "      <inMessageRef>" + getUniqueNodeId(node) + "_InMessage</inMessageRef>" + EOL +
                            "    </operation>" + EOL +
                            "  </interface>" + EOL + EOL);
                    } else if ("Send Task".equals(work.getName())) {
                        String messageType = (String) work.getParameter("MessageType");
                        if (messageType == null) {
                            messageType = "";
                        }
                        xmlDump.append(
                            "  <itemDefinition id=\"" + getUniqueNodeId(node) + "_MessageType\" " +
                            	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
                        			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ")
                        			+ "/>" + EOL +
                            "  <message id=\"" + getUniqueNodeId(node) + "_Message\" itemRef=\"" + getUniqueNodeId(node) + "_MessageType\" />" + EOL + EOL);
                    } else if ("Receive Task".equals(work.getName())) {
                    	String messageId = (String) work.getParameter("MessageId");
                        String messageType = (String) work.getParameter("MessageType");
                        if (messageType == null) {
                            messageType = "";
                        }
                        xmlDump.append(
                            "  <itemDefinition id=\"" + getUniqueNodeId(node) + "_MessageType\" " + 
                            	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
                        			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ")
                        			+ "/>" + EOL +
                            "  <message id=\"" + messageId + "\" itemRef=\"" + getUniqueNodeId(node) + "_MessageType\" />" + EOL + EOL);
                    }
                }
            } else if (node instanceof EndNode) {
                String messageType = (String) node.getMetaData().get("MessageType");
                if (messageType != null) {
                    xmlDump.append(
                        "  <itemDefinition id=\"" + getUniqueNodeId(node) + "_MessageType\" " + 
                        	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
                    			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ")
                    			+ "/>" + EOL +
                        "  <message id=\"" + getUniqueNodeId(node) + "_Message\" itemRef=\"" + getUniqueNodeId(node) + "_MessageType\" />" + EOL + EOL);
                }
            } else if (node instanceof ActionNode) {
                String messageType = (String) node.getMetaData().get("MessageType");
                if (messageType != null) {
                    xmlDump.append(
                        "  <itemDefinition id=\"" + getUniqueNodeId(node) + "_MessageType\" " + 
                        	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
                    			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ") +
                    			"/>" + EOL +
                        "  <message id=\"" + getUniqueNodeId(node) + "_Message\" itemRef=\"" + getUniqueNodeId(node) + "_MessageType\" />" + EOL + EOL);
                }
            } else if (node instanceof EventNode) {
            	if (node.getMetaData().get("AttachedTo") == null) {
                	List<EventFilter> filters = ((EventNode) node).getEventFilters();
                	if (filters.size() > 0) {
    	                String messageRef = ((EventTypeFilter) filters.get(0)).getType();
		                if (messageRef.startsWith("Message-")) {
			                messageRef = messageRef.substring(8);
			                String messageType = (String) node.getMetaData().get("MessageType");
			                xmlDump.append(
			                    "  <itemDefinition id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageRef) + "Type\" " + 
			                    	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
	                        			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ") + 
	                        			"/>" + EOL +
			                    "  <message id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageRef) + "\" itemRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageRef) + "Type\" />" + EOL + EOL);
		                }
                	}
            	}
            } else if (node instanceof StartNode) {
                StartNode startNode = (StartNode) node;
                if (startNode.getTriggers() != null && !startNode.getTriggers().isEmpty()) {
                    Trigger trigger = startNode.getTriggers().get(0);
                    if (trigger instanceof EventTrigger) {
                        String eventType = ((EventTypeFilter) ((EventTrigger) trigger).getEventFilters().get(0)).getType();
                        if (eventType.startsWith("Message-")) {
                            eventType = eventType.substring(8);
                            String messageType = (String) node.getMetaData().get("MessageType");
                            xmlDump.append(
                                "  <itemDefinition id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(eventType) + "Type\" " + 
                                	("".equals(messageType) || "java.lang.Object".equals(messageType) ?
                            			"" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(messageType) + "\" ") +
                            			"/>" + EOL +
                                "  <message id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(eventType) + "\" itemRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(eventType) + "Type\" />" + EOL + EOL);
                        }
                    }
                }
            } else if (node instanceof ForEachNode) {
            	ForEachNode forEachNode = (ForEachNode) node;
            	String type = null;
            	if (forEachNode.getVariableType() instanceof ObjectDataType) {
            		type = ((ObjectDataType) forEachNode.getVariableType()).getClassName(); 
            	}
                xmlDump.append(
                    "  <itemDefinition id=\"" + XmlBPMNProcessDumper.getUniqueNodeId(forEachNode) + "_multiInstanceItemType\" " + 
                    	(type == null || "java.lang.Object".equals(type) ? "" : "structureRef=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" ") + "/>" + EOL + EOL);
            }
            if (node instanceof CompositeNode) {
            	visitInterfaces(((CompositeNode) node).getNodes(), xmlDump);
            }
        }
    }
    
    protected void visitEscalations(Node[] nodes, StringBuilder xmlDump, List<String> escalations) {
        for (Node node: nodes) {
            if (node instanceof FaultNode) {
            	FaultNode faultNode = (FaultNode) node;
            	if (!faultNode.isTerminateParent()) {
            		String escalationCode = faultNode.getFaultName();
            		if (!escalations.contains(escalationCode)) {
            			escalations.add(escalationCode);
	                    xmlDump.append(
	                        "  <escalation id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(escalationCode) + "\" escalationCode=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(escalationCode) + "\" />" + EOL);
            		}
                }
            } else if (node instanceof ActionNode) {
            	ActionNode actionNode = (ActionNode) node;
            	DroolsConsequenceAction action = (DroolsConsequenceAction) actionNode.getAction();
        		if (action != null) {
        		    String s = action.getConsequence();
	            	if (s.startsWith("org.drools.process.instance.context.exception.ExceptionScopeInstance scopeInstance = (org.drools.process.instance.context.exception.ExceptionScopeInstance) ((org.drools.workflow.instance.NodeInstance) kcontext.getNodeInstance()).resolveContextInstance(org.drools.process.core.context.exception.ExceptionScope.EXCEPTION_SCOPE, \"")) {
	            		s = s.substring(327);
	                    String type = s.substring(0, s.indexOf("\""));
	            		if (!escalations.contains(type)) {
	            			escalations.add(type);
		                    xmlDump.append(
	                            "  <escalation id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" escalationCode=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" />" + EOL);
	            		}
	            	}
        		}
            } else if (node instanceof EventNode) {
            	EventNode eventNode = (EventNode) node;
            	String type = (String) eventNode.getMetaData("EscalationEvent");
            	if (type != null) {
            		if (!escalations.contains(type)) {
            			escalations.add(type);
		                xmlDump.append(
		                    "  <escalation id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" escalationCode=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" />" + EOL);
            		}
            	}
            }
            if (node instanceof CompositeNode) {
            	visitEscalations(((CompositeNode) node).getNodes(), xmlDump, escalations);
            }
        }
    }
    
    protected void visitErrors(Node[] nodes, StringBuilder xmlDump, List<String> errors) {
        for (Node node: nodes) {
            if (node instanceof FaultNode) {
            	FaultNode faultNode = (FaultNode) node;
            	if (faultNode.isTerminateParent()) {
            		String errorCode = faultNode.getFaultName();
            		if (!errors.contains(errorCode)) {
            			errors.add(errorCode);
	                    xmlDump.append(
	                        "  <error id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(errorCode) + "\" errorCode=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(errorCode) + "\" />" + EOL);
            		}
                }
            } else if (node instanceof EventNode) {
            	EventNode eventNode = (EventNode) node;
            	String type = (String) eventNode.getMetaData("ErrorEvent");
            	if (type != null) {
            		if (!errors.contains(type)) {
            			errors.add(type);
		                xmlDump.append(
		                    "  <error id=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" errorCode=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(type) + "\" />" + EOL);
            		}
            	}
            }
            if (node instanceof CompositeNode) {
            	visitErrors(((CompositeNode) node).getNodes(), xmlDump, errors);
            }
        }
    }
    
    private void visitNodes(WorkflowProcess process, StringBuilder xmlDump, int metaDataType) {
    	xmlDump.append("    <!-- nodes -->" + EOL);
        for (Node node: process.getNodes()) {
            visitNode(node, xmlDump, metaDataType);
        }
        xmlDump.append(EOL);
    }
    
    public void visitNode(Node node, StringBuilder xmlDump, int metaDataType) {
     	Handler handler = semanticModule.getHandlerByClass(node.getClass());
        if (handler != null) {
        	((AbstractNodeHandler) handler).writeNode((org.jbpm.workflow.core.Node) node, xmlDump, metaDataType);
        } else {
        	throw new IllegalArgumentException(
                "Unknown node type: " + node);
        }
    }
    
    private void visitNodesDi(Node[] nodes, StringBuilder xmlDump) {
    	for (Node node: nodes) {
            Integer x = (Integer) node.getMetaData().get("x");
            Integer y = (Integer) node.getMetaData().get("y");
            Integer width = (Integer) node.getMetaData().get("width");
            Integer height = (Integer) node.getMetaData().get("height");
    		if (x == null) {
    			x = 0;
    		}
    		if (y == null) {
    			y = 0;
    		}
    		if (width == null) {
    			width = 48;
    		}
    		if (height == null) {
    			height = 48;
    		}
    		if (node instanceof StartNode || node instanceof EndNode || node instanceof EventNode || node instanceof FaultNode) {
    			int offsetX = (int) ((width - 48) / 2);
    			width = 48;
    	        x = x + offsetX;
    	        int offsetY = (int) ((height - 48) / 2);
    	        y = y + offsetY;
    	        height = 48;
    		} else if (node instanceof Join || node instanceof Split) {
    			int offsetX = (int) ((width - 48) / 2);
    			width = 48;
    	        x = x + offsetX;
    	        int offsetY = (int) ((height - 48) / 2);
    	        y = y + offsetY;
    	        height = 48;
    		}
    		int parentOffsetX = 0;
    		int parentOffsetY = 0;
    		NodeContainer nodeContainer = node.getNodeContainer();
    		while (nodeContainer instanceof CompositeNode) {
    			CompositeNode parent = (CompositeNode) nodeContainer;
    			Integer parentX = (Integer) parent.getMetaData().get("x");
    			if (parentX != null) {
    				parentOffsetX += parentX;
    			}
    			Integer parentY = (Integer) parent.getMetaData().get("y");
    			if (parentY != null) {
    				parentOffsetY += (Integer) parent.getMetaData().get("y");
    			}
    			nodeContainer = parent.getNodeContainer();
    		}
    		x += parentOffsetX;
    		y += parentOffsetY;
			xmlDump.append(
				"      <bpmndi:BPMNShape bpmnElement=\"" + getUniqueNodeId(node) + "\" >" + EOL +
				"        <dc:Bounds x=\"" + x + "\" " + "y=\"" + y + "\" " + 
								   "width=\"" + width + "\" " + "height=\"" + height + "\" />" + EOL +
			    "      </bpmndi:BPMNShape>" + EOL);
			if (node instanceof CompositeNode) {
				visitNodesDi(((CompositeNode) node).getNodes(), xmlDump);
			}
    	}

    }
    
    private void visitConnections(Node[] nodes, StringBuilder xmlDump, int metaDataType) {
    	xmlDump.append("    <!-- connections -->" + EOL);
        List<Connection> connections = new ArrayList<Connection>();
        for (Node node: nodes) {
            for (List<Connection> connectionList: node.getIncomingConnections().values()) {
                connections.addAll(connectionList);
            }
        }
        for (Connection connection: connections) {
            visitConnection(connection, xmlDump, metaDataType);
        }
        xmlDump.append(EOL);
    }
    
    public void visitConnection(Connection connection, StringBuilder xmlDump, int metaDataType) {
        xmlDump.append("    <sequenceFlow id=\"" +
    		getUniqueNodeId(connection.getFrom()) + "-" + 
    		getUniqueNodeId(connection.getTo()) + 
    		"\" sourceRef=\"" + getUniqueNodeId(connection.getFrom()) + "\" ");
        // TODO fromType, toType
        xmlDump.append("targetRef=\"" + getUniqueNodeId(connection.getTo()) + "\" ");
        if (metaDataType == META_DATA_AS_NODE_PROPERTY) {
            String bendpoints = (String) connection.getMetaData().get("bendpoints");
            if (bendpoints != null) {
                xmlDump.append("g:bendpoints=\"" + bendpoints + "\" ");
            }
        }
        if (connection.getFrom() instanceof Split) {
        	Split split = (Split) connection.getFrom();
        	if (split.getType() == Split.TYPE_XOR || split.getType() == Split.TYPE_OR) {
        		Constraint constraint = split.getConstraint(connection);
        		if (constraint == null) {
            		xmlDump.append(">" + EOL +
    					"      <conditionExpression xsi:type=\"tFormalExpression\" />");
        		} else {
                    if (constraint.getName() != null && constraint.getName().trim().length() > 0) {
            			xmlDump.append("name=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(constraint.getName()) + "\" ");
            		}
                    if (constraint.getPriority() != 0) {
                    	xmlDump.append("tns:priority=\"" + constraint.getPriority() + "\" ");
                    }
            		xmlDump.append(">" + EOL +
    				"      <conditionExpression xsi:type=\"tFormalExpression\" ");
                    if ("code".equals(constraint.getType())) {
                        if (JavaDialect.ID.equals(constraint.getDialect())) {
                            xmlDump.append("language=\"" + JAVA_LANGUAGE + "\" ");
                        } else if ("XPath".equals(constraint.getDialect())) {
                            xmlDump.append("language=\"" + XPATH_LANGUAGE + "\" ");
                        }
                    } else {
                        xmlDump.append("language=\"" + RULE_LANGUAGE + "\" ");
                    }
                    String constraintString = constraint.getConstraint();
                    if (constraintString == null) {
                        constraintString = "";
                    }
                    xmlDump.append(">" + XmlDumper.replaceIllegalChars(constraintString) + "</conditionExpression>");
        		}
        		xmlDump.append(EOL
    		        + "    </sequenceFlow>" + EOL);
        	} else {
            	xmlDump.append("/>" + EOL);
            }
        } else {
        	xmlDump.append("/>" + EOL);
        }
    }
    
    private void visitConnectionsDi(Node[] nodes, StringBuilder xmlDump) {
        List<Connection> connections = new ArrayList<Connection>();
        for (Node node: nodes) {
            for (List<Connection> connectionList: node.getIncomingConnections().values()) {
                connections.addAll(connectionList);
            }
            if (node instanceof CompositeNode) {
            	visitConnectionsDi(((CompositeNode) node).getNodes(), xmlDump);
            }
        }
        for (Connection connection: connections) {
            String bendpoints = (String) connection.getMetaData().get("bendpoints");
            xmlDump.append(
        		"      <bpmndi:BPMNEdge bpmnElement=\"" + 
        			getUniqueNodeId(connection.getFrom()) + "-" + getUniqueNodeId(connection.getTo()) + "\" >" + EOL);
        	Integer x = (Integer) connection.getFrom().getMetaData().get("x");
        	if (x == null) {
        		x = 0;
        	}
        	Integer y = (Integer) connection.getFrom().getMetaData().get("y");
        	if (y == null) {
        		y = 0;
        	}
        	Integer width = (Integer) connection.getFrom().getMetaData().get("width");
        	if (width == null) {
        		width = 40;
        	}
        	Integer height = (Integer) connection.getFrom().getMetaData().get("height");
        	if (height == null) {
        		height = 40;
        	}
			xmlDump.append(
				"        <di:waypoint x=\"" + (x + width/2) + "\" y=\"" + (y + height/2) + "\" />" + EOL);
            if (bendpoints != null) {
            	bendpoints = bendpoints.substring(1, bendpoints.length() - 1);
            	String[] points = bendpoints.split(";");
            	for (String point: points) {
            		String[] coords = point.split(",");
            		if (coords.length == 2) {
            			xmlDump.append(
        					"        <di:waypoint x=\"" + coords[0] + "\" y=\"" + coords[1] + "\" />" + EOL);
            		}
            	}
            }
        	x = (Integer) connection.getTo().getMetaData().get("x");
        	if (x == null) {
        		x = 0;
        	}
        	y = (Integer) connection.getTo().getMetaData().get("y");
        	if (y == null) {
        		y = 0;
        	}
        	width = (Integer) connection.getTo().getMetaData().get("width");
        	if (width == null) {
        		width = 40;
        	}
        	height = (Integer) connection.getTo().getMetaData().get("height");
        	if (height == null) {
        		height = 40;
        	}
			xmlDump.append(
				"        <di:waypoint x=\"" + (x + width/2) + "\" y=\"" + (y + height/2) + "\" />" + EOL);
        	xmlDump.append(
        		"      </bpmndi:BPMNEdge>" + EOL);
        }
    }
    
    public static String getUniqueNodeId(Node node) {
    	String result = (String) node.getMetaData().get("UniqueId");
    	if (result != null) {
    		return result;
    	}
    	result = node.getId() + "";
    	NodeContainer nodeContainer = node.getNodeContainer();
    	while (nodeContainer instanceof CompositeNode) {
    		CompositeNode composite = (CompositeNode) nodeContainer;
    		result = composite.getId() + "-" + result;
    		nodeContainer = composite.getNodeContainer();
    	}
    	return "_" + result;
    }
    
    public static String replaceIllegalCharsAttribute(final String code) {
        final StringBuilder sb = new StringBuilder();
        if ( code != null ) {
            final int n = code.length();
            for ( int i = 0; i < n; i++ ) {
                final char c = code.charAt( i );
                switch ( c ) {
	                case '<' :
	                    sb.append( "&lt;" );
	                    break;
	                case '>' :
	                    sb.append( "&gt;" );
	                    break;
	                case '&' :
	                    sb.append( "&amp;" );
	                    break;
                    case '"' :
                        sb.append( "&quot;" );
                        break;
                    default :
                        sb.append( c );
                        break;
                }
            }
        } else {
            sb.append( "null" );
        }
        return sb.toString();
    }
    
}
