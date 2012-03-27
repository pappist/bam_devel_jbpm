package org.jbpm.compiler.xml.processes;

import java.util.HashSet;

import org.drools.xml.BaseAbstractHandler;
import org.drools.xml.ExtensibleXmlParser;
import org.drools.xml.Handler;
import org.jbpm.process.core.timer.Timer;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.node.StateBasedNode;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class TimerHandler extends BaseAbstractHandler implements Handler {
	
    public TimerHandler() {
        if ( (this.validParents == null) && (this.validPeers == null) ) {
            this.validParents = new HashSet<Class<?>>();
            this.validParents.add( StateBasedNode.class );

            this.validPeers = new HashSet<Class<?>>();         
            this.validPeers.add( null );

            this.allowNesting = false;
        }
    }
    
    public Object start(final String uri,
                        final String localName,
                        final Attributes attrs,
                        final ExtensibleXmlParser parser) throws SAXException {
        parser.startElementBuilder( localName, attrs );
        return null;
    }    
    
    public Object end(final String uri,
                      final String localName,
                      final ExtensibleXmlParser parser) throws SAXException {
        Element element = parser.endElementBuilder();
        StateBasedNode parent = (StateBasedNode) parser.getParent();
        String id = element.getAttribute("id");
        emptyAttributeCheck( localName, "id", id, parser );
        String delay = element.getAttribute("delay");
        String period = element.getAttribute("period");
        Timer timer = new Timer();
        timer.setId(new Long(id));
        if (delay != null && delay.length() != 0 ) {
            timer.setDelay(delay);
        }
        if (period != null && period.length() != 0 ) {
            timer.setPeriod(period);
        }
        org.w3c.dom.Node xmlNode = element.getFirstChild();
        DroolsAction action = null;
        if (xmlNode instanceof Element) {
    		Element actionXml = (Element) xmlNode;
    		action = AbstractNodeHandler.extractAction(actionXml);
        }
        parent.addTimer(timer, action);
        return null;
    }

    @SuppressWarnings("unchecked")
	public Class generateNodeFor() {
        return null;
    }    

}
