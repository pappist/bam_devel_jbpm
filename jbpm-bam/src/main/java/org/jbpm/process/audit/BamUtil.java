package org.jbpm.process.audit;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.io.InputStream;
import java.io.IOException;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;

public class BamUtil {

	public static String BAM_HOST = "";
	public static String BAM_PORT = "";
	public final static String ROOT_PATH = "/hu.bme.mit.pi.onlab.bam";

	public static boolean testBamConnection() {
	      
	    
	    try {
	      ClientRequest request = new ClientRequest("http://" + BAM_HOST + ":" + BAM_PORT + ROOT_PATH + "/connection/test");
	      
	      ClientResponse response = request.get();
	      
	      if(response.getStatus() == 200) return true;
	    } catch (Exception e) {
	      System.out.println("WARNING: Faild to BAM Server: Host: " + BAM_HOST + ", Port: " + BAM_PORT + "!");
	      return false;
	    }
	    
	      
	      return false;
	}

	public static Boolean putDataToBAM(String path, String JSONData) {
	    try {
		ClientRequest request = new ClientRequest("http://" + BAM_HOST + ":" + BAM_PORT + ROOT_PATH + path);
		request.accept("application/json");
		request.body("application/json", JSONData);
		
		ClientResponse response = request.put(String.class);
		
		if(response.getStatus() == 201 || response.getStatus() == 202) {
		  
		  System.out.println("INFO: Successfully added data to BAM Server: Path: " + path + " DATA: " + JSONData);
		  return true;
		}
		
	    } catch (Exception e) {
	      e.printStackTrace();
	      return false;
	    }
	    
	      
	      return false;
	}

	public static String getCurrentTime() {
	    Format formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSZZZZ");
	    return formatter.format(Calendar.getInstance().getTime());
	}


} 
