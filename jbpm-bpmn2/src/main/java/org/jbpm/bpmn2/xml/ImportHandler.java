package org.jbpm.bpmn2.xml;

import java.util.ArrayList;
import java.util.HashSet;

import org.drools.definition.process.Process;
import org.drools.xml.BaseAbstractHandler;
import org.drools.xml.ExtensibleXmlParser;
import org.drools.xml.Handler;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class ImportHandler extends BaseAbstractHandler implements Handler {

	public ImportHandler() {
		if ((this.validParents == null) && (this.validPeers == null)) {
			this.validParents = new HashSet();
			this.validParents.add(Process.class);

			this.validPeers = new HashSet();
			this.validPeers.add(null);

			this.allowNesting = false;
		}
	}

	public Object start(final String uri, final String localName,
			final Attributes attrs, final ExtensibleXmlParser parser)
			throws SAXException {
		parser.startElementBuilder(localName, attrs);
		WorkflowProcessImpl process = (WorkflowProcessImpl) parser.getParent();

		final String name = attrs.getValue("name");
		emptyAttributeCheck(localName, "name", name, parser);

		java.util.List<String> list = process.getImports();
		if (list == null) {
			list = new ArrayList<String>();
			process.setImports(list);
		}
		list.add(name);

		return null;
	}

	public Object end(final String uri, final String localName,
			final ExtensibleXmlParser parser) throws SAXException {
		parser.endElementBuilder();
		return null;
	}

	public Class generateNodeFor() {
		return null;
	}

}
