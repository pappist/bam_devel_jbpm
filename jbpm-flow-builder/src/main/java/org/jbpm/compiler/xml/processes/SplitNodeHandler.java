package org.jbpm.compiler.xml.processes;

import java.util.Map;

import org.drools.compiler.xml.XmlDumper;
import org.drools.xml.ExtensibleXmlParser;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.ConnectionRef;
import org.jbpm.workflow.core.node.Split;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class SplitNodeHandler extends AbstractNodeHandler {

    protected Node createNode() {
        return new Split();
    }

    public void handleNode(final Node node, final Element element, final String uri,
            final String localName, final ExtensibleXmlParser parser)
            throws SAXException {
        super.handleNode(node, element, uri, localName, parser);
        Split splitNode = (Split) node;
        String type = element.getAttribute("type");
        if (type != null && type.length() != 0 ) {
            splitNode.setType(new Integer(type));
        }
    }

    @SuppressWarnings("unchecked")
	public Class generateNodeFor() {
        return Split.class;
    }

	public void writeNode(Node node, StringBuilder xmlDump, boolean includeMeta) {
		Split splitNode = (Split) node;
		writeNode("split", splitNode, xmlDump, includeMeta);
        int type = splitNode.getType();
        if (type != 0) {
            xmlDump.append("type=\"" + type + "\" ");
        }
        if (splitNode.getConstraints().isEmpty()) {
            endNode(xmlDump);
        } else {
            xmlDump.append(">" + EOL);
            if (includeMeta) {
            	writeMetaData(splitNode, xmlDump);
            }
            xmlDump.append("      <constraints>" + EOL);
            for (Map.Entry<ConnectionRef, Constraint> entry: splitNode.getConstraints().entrySet()) {
                ConnectionRef connection = entry.getKey();
                Constraint constraint = entry.getValue();
                xmlDump.append("        <constraint "
                    + "toNodeId=\"" + connection.getNodeId() + "\" "
                    + "toType=\"" + connection.getToType() + "\" ");
                String name = constraint.getName();
                if (name != null && !"".equals(name)) {
                    xmlDump.append("name=\"" + XmlDumper.replaceIllegalChars(constraint.getName()) + "\" ");
                }
                int priority = constraint.getPriority();
                if (priority != 0) {
                    xmlDump.append("priority=\"" + constraint.getPriority() + "\" ");
                }
                xmlDump.append("type=\"" + constraint.getType() + "\" ");
                String dialect = constraint.getDialect();
                if (dialect != null && !"".equals(dialect)) {
                    xmlDump.append("dialect=\"" + dialect + "\" ");
                }
                String constraintString = constraint.getConstraint();
                if (constraintString != null) {
                    xmlDump.append(">" + XmlDumper.replaceIllegalChars(constraintString) + "</constraint>" + EOL);
                } else {
                    xmlDump.append("/>" + EOL);
                }
            }
            xmlDump.append("      </constraints>" + EOL);
            endNode("split", xmlDump);
        }
	}

}
