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

package org.jbpm.process.core.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.drools.io.Resource;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.core.Process;
import org.jbpm.process.core.context.AbstractContext;

/**
 * Default implementation of a Process
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class ProcessImpl implements Process, Serializable, ContextResolver {
    
    private static final long serialVersionUID = 510l;

    private String id;
    private String name;
    private String version;
    private String type;
    private String packageName;
    private Resource resource;
    private ContextContainer contextContainer = new ContextContainerImpl();
    private Map<String, Object> metaData = new HashMap<String, Object>();
    private List<String> imports;
    private Map<String, String> globals;
    private List<String> functionImports;

    
    public void setId(final String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public String getVersion() {
        return this.version;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public List<Context> getContexts(String contextType) {
	    return this.contextContainer.getContexts(contextType);
	}
    
    public void addContext(Context context) {
        this.contextContainer.addContext(context);
        ((AbstractContext) context).setContextContainer(this);
    }
    
    public Context getContext(String contextType, long id) {
        return this.contextContainer.getContext(contextType, id);
    }

    public void setDefaultContext(Context context) {
        this.contextContainer.setDefaultContext(context);
    }
    
    public Context getDefaultContext(String contextType) {
        return this.contextContainer.getDefaultContext(contextType);
    }

    public boolean equals(final Object o) {
        if ( o instanceof ProcessImpl ) {
        	if (this.id != null) {
        		return ((ProcessImpl) o).getId() == null;
        	}
        	return this.id.equals(((ProcessImpl) o).getId());
        }
        return false;
    }

    public int hashCode() {
        return this.id == null ? 0 : 3 * this.id.hashCode();
    }

    public Context resolveContext(String contextId, Object param) {
        Context context = getDefaultContext(contextId);
        if (context != null) {
            context = context.resolveContext(param);
            if (context != null) {
                return context;
            }
        }
        return null;
    }
    
	public Map<String, Object> getMetaData() {
		return this.metaData;
	}

    public void setMetaData(String name, Object data) {
        this.metaData.put(name, data);
    }
    
    public Object getMetaData(String name) {
        return this.metaData.get(name);
    }

    public Resource getResource() {
        return this.resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;        
    }
    
    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }
    
    public List<String> getFunctionImports() {
        return functionImports;
    }

    public void setFunctionImports(List<String> functionImports) {
        this.functionImports = functionImports;
    }    
    
    public Map<String, String> getGlobals() {
        return globals;
    }

    public void setGlobals(Map<String, String> globals) {
        this.globals = globals;
    }

    public String[] getGlobalNames() {
        final List<String> result = new ArrayList<String>();
        if (this.globals != null) {
            for ( Iterator<String> iterator = this.globals.keySet().iterator(); iterator.hasNext(); ) {
                result.add(iterator.next());
            }
        }
        return result.toArray(new String[result.size()]);
    }
    
}
