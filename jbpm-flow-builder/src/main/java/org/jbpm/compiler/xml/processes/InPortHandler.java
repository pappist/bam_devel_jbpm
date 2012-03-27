package org.jbpm.compiler.xml.processes;

import java.util.HashSet;

import org.drools.xml.BaseAbstractHandler;
import org.drools.xml.ExtensibleXmlParser;
import org.drools.xml.Handler;
import org.jbpm.workflow.core.node.CompositeNode;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class InPortHandler extends BaseAbstractHandler
    implements
    Handler {
    public InPortHandler() {
        if ( (this.validParents == null) && (this.validPeers == null) ) {
            this.validParents = new HashSet();
            this.validParents.add( CompositeNode.class );

            this.validPeers = new HashSet();         
            this.validPeers.add( null );

            this.allowNesting = false;
        }
    }
    
    public Object start(final String uri,
                        final String localName,
                        final Attributes attrs,
                        final ExtensibleXmlParser parser) throws SAXException {
        parser.startElementBuilder( localName,
                                    attrs );
        CompositeNode compositeNode = (CompositeNode) parser.getParent();
        final String type = attrs.getValue("type");
        emptyAttributeCheck(localName, "type", type, parser);
        final String nodeId = attrs.getValue("nodeId");
        emptyAttributeCheck(localName, "nodeId", nodeId, parser);
        final String nodeInType = attrs.getValue("nodeInType");
        emptyAttributeCheck(localName, "nodeInType", nodeInType, parser);
        compositeNode.linkIncomingConnections(type, new Long(nodeId), nodeInType);
        return null;
    }    
    
    public Object end(final String uri,
                      final String localName,
                      final ExtensibleXmlParser parser) throws SAXException {
        parser.endElementBuilder();
        return null;
    }

    public Class generateNodeFor() {
        return null;
    }    

}
