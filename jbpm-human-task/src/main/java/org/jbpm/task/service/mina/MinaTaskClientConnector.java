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

package org.jbpm.task.service.mina;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jbpm.task.service.BaseHandler;
import org.jbpm.task.service.TaskClientConnector;

public class MinaTaskClientConnector implements TaskClientConnector {

    protected IoSession session;

    protected final BaseMinaHandler handler;
    protected final String name;   
    protected AtomicInteger counter;

    protected SocketConnector connector; 
    protected SocketAddress address;

    public MinaTaskClientConnector(String name, BaseMinaHandler handler) {
        if (name == null) {
            throw new IllegalArgumentException("Name can not be null");
        }
        this.name = name;
        this.handler = handler;
        counter = new AtomicInteger();
    }

    public boolean connect(SocketConnector connector, SocketAddress address) {
        this.connector = connector;
        this.address = address;
        this.connector.setHandler( this.handler );
        return connect();
    }

	public boolean connect(String address, int port) {
		this.connector = new NioSocketConnector();
        this.address = new InetSocketAddress( address, port );
        this.connector.setHandler( this.handler );
        return connect();
	}

    public boolean connect() {
        if (session != null && session.isConnected()) {
            throw new IllegalStateException(
                    "Already connected. Disconnect first.");
        } 

        if (this.connector==null) {
        	this.connector = new NioSocketConnector();
        }
        
        if (this.address==null) {
        	this.address = new InetSocketAddress( "127.0.0.1", 9123);
        }
         
        try {
//            SocketConnectorConfig config = new SocketConnectorConfig();
//            if (useSsl) {
//                SSLContext sslContext = BogusSSLContextFactory
//                        .getInstance(false);
//                SSLFilter sslFilter = new SSLFilter(sslContext);
//                sslFilter.setUseClientMode(true);
//                config.getFilterChain().addLast("sslFilter", sslFilter);
//            }
            
            //connector.setHandler( arg0 );
            
            connector.getFilterChain().addLast(
                                               "codec",
                                               new ProtocolCodecFilter(
                                                       new ObjectSerializationCodecFactory()));

            ConnectFuture future1 = connector.connect( address );
            future1.join();
            if (!future1.isConnected()) {
                return false;
            }
            session = future1.getSession();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if ( session!= null && session.isConnected() ) {
            session.close();
            session.getCloseFuture().join();
        }
    }

	public void write(Object message) {
		session.write(message);
	}

	public BaseHandler getHandler() {
		return handler;
	}

	public String getName() {
		return name;
	}

	public AtomicInteger getCounter() {
		return counter;
	}

}