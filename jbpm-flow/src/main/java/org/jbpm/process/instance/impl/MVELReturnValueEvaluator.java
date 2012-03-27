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

package org.jbpm.process.instance.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.List;

import org.drools.base.mvel.MVELCompilationUnit;
import org.drools.base.mvel.MVELCompileable;
import org.drools.base.mvel.MVELDebugHandler;
import org.drools.definition.KnowledgePackage;
import org.drools.definitions.impl.KnowledgePackageImp;
import org.drools.impl.InternalKnowledgeBase;
import org.drools.rule.MVELDialectRuntimeData;
import org.drools.rule.Package;
import org.drools.runtime.process.ProcessContext;
import org.drools.spi.GlobalResolver;
import org.mvel2.MVEL;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.debug.DebugTools;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

public class MVELReturnValueEvaluator
    implements
    ReturnValueEvaluator,
    MVELCompileable,
    Externalizable {
    private static final long   serialVersionUID = 510l;

    private MVELCompilationUnit unit;
    private String              id;

    private Serializable        expr;

    public MVELReturnValueEvaluator() {
    }

    public MVELReturnValueEvaluator(final MVELCompilationUnit unit,
                                    final String id) {
        this.unit = unit;
        this.id = id;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        id = in.readUTF();
        unit = (MVELCompilationUnit) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF( id );
        out.writeObject( unit );
    }

    public void compile(MVELDialectRuntimeData data) {
        expr = unit.getCompiledExpression( data );
    }

    public String getDialect() {
        return this.id;
    }

    public Object evaluate(ProcessContext context) throws Exception {
        int length = unit.getOtherIdentifiers().length;
        Object[] vars = new Object[ length ];
        if (unit.getOtherIdentifiers() != null) {
            for (int i = 0; i < length; i++ ) {
                vars[i] = context.getVariable( unit.getOtherIdentifiers()[i] );
            }
        }

        VariableResolverFactory factory = unit.getFactory( context, null, null, null, vars, null, (GlobalResolver) context.getKnowledgeRuntime().getGlobals() );
        
        // do we have any functions for this namespace?
        KnowledgePackage pkg = context.getKnowledgeRuntime().getKnowledgeBase().getKnowledgePackage( "MAIN" );
        if ( pkg != null && pkg instanceof KnowledgePackageImp) {
            MVELDialectRuntimeData data = ( MVELDialectRuntimeData ) ((KnowledgePackageImp) pkg).pkg.getDialectRuntimeRegistry().getDialectData( id );
            factory.setNextFactory( data.getFunctionFactory() );
        }

        Object value = MVEL.executeExpression( this.expr,
	                                           null,
	                                           factory );

        if ( !(value instanceof Boolean) ) {
            throw new RuntimeException( "Constraints must return boolean values: " + 
        		unit.getExpression() + " returns " + value + 
        		(value == null? "" : " (type=" + value.getClass()));
        }
        return ((Boolean) value).booleanValue();

    }

    public Serializable getCompExpr() {
        return expr;
    }
    
    public String toString() {
        return this.unit.getExpression();
    }    

}
