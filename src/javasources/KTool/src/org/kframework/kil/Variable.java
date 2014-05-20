// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import org.kframework.kil.loader.Constants;
import org.kframework.kil.visitors.Visitor;
import org.kframework.utils.StringUtil;
import org.w3c.dom.Element;

/**
 * Variables, used both in rules/contexts and for variables like {@code $PGM} in configurations.
 */
public class Variable extends Term {

    private static int nextVariableIndex = 0;

    private String name;
    /** True if the variable was written with an explicit type annotation */
    private boolean userTyped = false;
    private boolean fresh = false;
    private boolean syntactic = false;
    /** Used by the type inferencer  */
    private String expectedSort = null;
    private static final String GENERATED_FRESH_VAR = "GeneratedFreshVar";

    public String getExpectedSort() {
        return expectedSort;
    }

    public void setExpectedSort(String expectedSort) {
        this.expectedSort = expectedSort;
    }
    
    public Variable(Element element) {
        super(element);
        this.sort = element.getAttribute(Constants.SORT_sort_ATTR);
        this.name = element.getAttribute(Constants.NAME_name_ATTR);
        this.userTyped = element.getAttribute(Constants.TYPE_userTyped_ATTR).equals("true");
        if (this.name.startsWith("?")) {
            this.setFresh(true);
            this.name = this.name.substring(1);
        }
    }
    
    public Variable(String name, String sort) {
        super(sort);
        this.name = name;
    }

    public Variable(Variable variable) {
        super(variable);
        name = variable.name;
        fresh = variable.fresh;
        userTyped = variable.userTyped;
        syntactic = variable.syntactic;
        expectedSort = variable.expectedSort;
    }

    public static Variable getFreshVar(String sort) {
        return new Variable(GENERATED_FRESH_VAR + nextVariableIndex++, sort);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name + ":" + sort + " ";
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (this == obj)
            return true;
        if (!(obj instanceof Variable))
            return false;
        Variable var = (Variable) obj;

        return this.sort.equals(var.getSort()) && this.name.equals(var.getName());
    }

    @Override
    public int hashCode() {
        return sort.hashCode() + name.hashCode();
    }

    public void setUserTyped(boolean userTyped) {
        this.userTyped = userTyped;
    }

    public boolean isUserTyped() {
        return userTyped;
    }

    @Override
    public Variable shallowCopy() {
        return new Variable(this);
    }

    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    public boolean isFresh() {
        return fresh;
    }

    public boolean isGenerated(){
        return name.startsWith(GENERATED_FRESH_VAR);
    }

    public boolean isSyntactic() {
        return syntactic;
    }

    public void setSyntactic(boolean syntactic) {
        this.syntactic = syntactic;
    }

}
