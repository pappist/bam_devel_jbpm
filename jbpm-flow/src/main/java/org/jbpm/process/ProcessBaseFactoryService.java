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

package org.jbpm.process;

import java.util.Properties;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseConfiguration;
import org.drools.KnowledgeBaseFactoryService;
import org.drools.RuleBaseConfiguration;
import org.drools.SessionConfiguration;
import org.drools.impl.EnvironmentFactory;
import org.drools.runtime.Environment;
import org.drools.runtime.KnowledgeSessionConfiguration;

public class ProcessBaseFactoryService implements KnowledgeBaseFactoryService {

    public KnowledgeBaseConfiguration newKnowledgeBaseConfiguration() {
        return new RuleBaseConfiguration();
    }
        
    public KnowledgeBaseConfiguration newKnowledgeBaseConfiguration(Properties properties, ClassLoader... classLoaders) {
        return new RuleBaseConfiguration(properties, classLoaders);
    }        
    
    public KnowledgeSessionConfiguration newKnowledgeSessionConfiguration() {
        return new SessionConfiguration();
    }
        
    public KnowledgeSessionConfiguration newKnowledgeSessionConfiguration(Properties properties) {
        return new SessionConfiguration(properties);
    }        
    
    public KnowledgeBase newKnowledgeBase() {       
        return new ProcessBaseImpl();      
    }   
    
    public KnowledgeBase newKnowledgeBase( String kbaseId ) {       
        return new ProcessBaseImpl();      
    }   
    
    public KnowledgeBase newKnowledgeBase(KnowledgeBaseConfiguration conf) {
        return new ProcessBaseImpl();
    }

    public KnowledgeBase newKnowledgeBase(String kbaseId, 
                                          KnowledgeBaseConfiguration conf) {
        return new ProcessBaseImpl();
    }

	public Environment newEnvironment() {
		return EnvironmentFactory.newEnvironment();
	}
}
