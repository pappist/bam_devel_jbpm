package org.jbpm.process.audit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jbpm.task.service.Operation;


public class BamEngine {

      public static void initBamConnection() {
	  File file = new File("bam-config.xml");
	  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	  try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();
			NodeList nodeLst = doc.getElementsByTagName("bam-server");
			System.out.println("Information of all Bam servers");
			
			int s = 0;
			boolean connOK = false;
			while(s < nodeLst.getLength() && !connOK) {
				
				Node fstNode = nodeLst.item(s);

				if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

					Element fstElmnt = (Element) fstNode;
					NodeList fstNmElmntLst = fstElmnt
							.getElementsByTagName("host");
					Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
					NodeList fstNm = fstNmElmnt.getChildNodes();
					System.out.println("Host Name : "
							+ ((Node) fstNm.item(0)).getNodeValue());
					BamUtil.BAM_HOST = ((Node) fstNm.item(0)).getNodeValue();

					NodeList lstNmElmntLst = fstElmnt
							.getElementsByTagName("port");
					Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
					NodeList lstNm = lstNmElmnt.getChildNodes();
					System.out.println("Port : "
							+ ((Node) lstNm.item(0)).getNodeValue());
					BamUtil.BAM_PORT = ((Node) lstNm.item(0)).getNodeValue();
				
			}
				if(BamUtil.testBamConnection()) connOK = true;
				s++;
			    
			}

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

      }

      public static Boolean sendTaskActivity(Operation op, long taskId, String userId) {
	
	  String data = "{\"operation\":" + op.ordinal() + ",\"taskid\":" + taskId + ",\"userid\":\"" + userId + "\",\"opTime\":\"" + BamUtil.getCurrentTime() + "\"}";

	  System.out.println(" =================== Send TaskActivity called: " + data);
	  return BamUtil.putDataToBAM("/workflow/add_task_activity", data);
	  
      }
      


    

}
