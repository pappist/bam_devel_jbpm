package org.jbpm.compiler.xml.processes;

import org.drools.xml.ExtensibleXmlParser;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.node.EndNode;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class EndNodeHandler extends AbstractNodeHandler {

    protected Node createNode() {
        return new EndNode();
    }

    public void handleNode(final Node node, final Element element, final String uri,
            final String localName, final ExtensibleXmlParser parser)
            throws SAXException {
        super.handleNode(node, element, uri, localName, parser);
        EndNode endNode = (EndNode) node;
        String terminate = element.getAttribute("terminate");
        if (terminate != null && "false".equals(terminate) ) {
            endNode.setTerminate(false);
        }
    }

    public Class generateNodeFor() {
        return EndNode.class;
    }

	public void writeNode(Node node, StringBuilder xmlDump, boolean includeMeta) {
		EndNode endNode = (EndNode) node;
		writeNode("end", endNode, xmlDump, includeMeta);
		boolean terminate = endNode.isTerminate();
        if (!terminate) {
            xmlDump.append("terminate=\"false\" ");
        }
        if (includeMeta && containsMetaData(endNode)) {
        	xmlDump.append(">" + EOL);
        	writeMetaData(endNode, xmlDump);
        	endNode("end", xmlDump);
        } else {
            endNode(xmlDump);
        }
	}

}
