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

package org.jbpm.process.core.context.variable;

import java.util.ArrayList;
import java.util.List;

import org.jbpm.process.core.Context;
import org.jbpm.process.core.context.AbstractContext;

/**
 * 
 * @author <a href="mailto:kris_verlaenen@hotmail.com">Kris Verlaenen</a>
 */
public class VariableScope extends AbstractContext {

    public static final String VARIABLE_SCOPE = "VariableScope";
    
    private static final long serialVersionUID = 510l;
    
    private List<Variable> variables;
    
    public VariableScope() {
        this.variables = new ArrayList<Variable>();
    }
    
    public String getType() {
        return VariableScope.VARIABLE_SCOPE;
    }
    
    public List<Variable> getVariables() {
        return this.variables;
    }

    public void setVariables(final List<Variable> variables) {
        if ( variables == null ) {
            throw new IllegalArgumentException( "Variables is null" );
        }
        this.variables = variables;
    }

    public String[] getVariableNames() {
        final String[] result = new String[this.variables.size()];
        if (this.variables != null) {
            for ( int i = 0; i < this.variables.size(); i++ ) {
                result[i] = ((Variable) this.variables.get( i )).getName();
            }
        }
        return result;
    }

    public Variable findVariable(String variableName) {
        for (Variable variable: getVariables()) {
            if (variable.getName().equals(variableName)) {
                return variable;
            }
        }
        return null;
    }

    public Context resolveContext(Object param) {
        if (param instanceof String) {
            return findVariable((String) param) == null ? null : this;
        }
        throw new IllegalArgumentException(
            "VariableScopes can only resolve variable names: " + param);
    }

}
