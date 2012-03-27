package org.jbpm.process.builder;

import org.drools.compiler.ReturnValueDescr;
import org.drools.rule.builder.PackageBuildContext;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.instance.impl.ReturnValueConstraintEvaluator;

public interface ReturnValueEvaluatorBuilder {

    public void build(final PackageBuildContext context,
                      final ReturnValueConstraintEvaluator returnValueConstraintEvaluator,
                      final ReturnValueDescr returnValueDescr,
                      final ContextResolver contextResolver);

}