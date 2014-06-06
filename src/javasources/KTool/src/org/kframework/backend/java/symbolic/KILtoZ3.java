// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Z3Exception;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.builtins.IntToken;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.kil.Z3Term;
import org.kframework.kil.ASTNode;

import com.microsoft.z3.Context;

import java.util.Set;


/**
 * @author: AndreiS
 */
public class KILtoZ3 extends CopyOnWriteTransformer {

    private final Context context;
    private final Set<Variable> boundVariables;

    public KILtoZ3(Set<Variable> boundVariables, Context context) {
        this.boundVariables = boundVariables;
        this.context = context;
    }

    @Override
    public ASTNode transform(BoolToken boolToken) {
        try {
            return new Z3Term(context.MkBool(boolToken.booleanValue()));
        } catch (Z3Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public ASTNode transform(IntToken intToken) {
        try {
            return new Z3Term(context.MkInt(intToken.bigIntegerValue().longValue()));
        } catch (Z3Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public ASTNode transform(KItem kItem) {
        if (!(kItem.kLabel() instanceof KLabelConstant)) {
            throw new UnsupportedOperationException();
        }
        KLabelConstant kLabel = (KLabelConstant) kItem.kLabel();

        if (!(kItem.kList() instanceof KList)) {
            throw new UnsupportedOperationException();
        }
        KList kList = (KList) kItem.kList();
        
        if (kList.hasFrame()) {
            throw new UnsupportedOperationException();
        }

        // TODO(AndreiS): implement a more general mechanic
        try {
            if (kLabel.label().equals("'notBool_") && kList.size() == 1) {
                BoolExpr booleanExpression
                        = (BoolExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                return new Z3Term(context.MkNot(booleanExpression));
            } else if (kLabel.label().equals("'_andBool_") && kList.size() == 2) {
                BoolExpr[] booleanExpressions = {
                        (BoolExpr) ((Z3Term) kList.get(0).accept(this)).expression(),
                        (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression()};
                return new Z3Term(context.MkAnd(booleanExpressions));
            } else if (kLabel.label().equals("'_orBool_") && kList.size() == 2) {
                BoolExpr[] booleanExpressions = {
                        (BoolExpr) ((Z3Term) kList.get(0).accept(this)).expression(),
                        (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression()};
                return new Z3Term(context.MkOr(booleanExpressions));
            } else if (kLabel.label().equals("'_==Bool_") && kList.size() == 2) {
                BoolExpr booleanExpression1
                        = (BoolExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                BoolExpr booleanExpression2
                        = (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkEq(booleanExpression1, booleanExpression2));
            } else if (kLabel.label().equals("'_=/=Bool_") && kList.size() == 2) {
                BoolExpr booleanExpression1
                        = (BoolExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                BoolExpr booleanExpression2
                        = (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkNot(context.MkEq(
                        booleanExpression1,
                        booleanExpression2)));
            } else if (kLabel.label().equals("'_+Int_") && kList.size() == 2) {
                ArithExpr[] arithmeticExpressions = {
                        (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression(),
                        (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression()};
                return new Z3Term(context.MkAdd(arithmeticExpressions));
            } else if (kLabel.label().equals("'_-Int_") && kList.size() == 2) {
                ArithExpr[] arithmeticExpressions = {
                        (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression(),
                        (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression()};
                return new Z3Term(context.MkSub(arithmeticExpressions));
            } else if (kLabel.label().equals("'_*Int_") && kList.size() == 2) {
                ArithExpr[] arithmeticExpressions = {
                        (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression(),
                        (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression()};
                return new Z3Term(context.MkMul(arithmeticExpressions));
            } else if (kLabel.label().equals("'_/Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkDiv(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_>Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkGt(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_>=Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkGe(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_<Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkLt(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_<=Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkLe(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_==Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkEq(arithmeticExpression1, arithmeticExpression2));
            } else if (kLabel.label().equals("'_=/=Int_") && kList.size() == 2) {
                ArithExpr arithmeticExpression1
                        = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                ArithExpr arithmeticExpression2
                        = (ArithExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkNot(context.MkEq(
                        arithmeticExpression1,
                        arithmeticExpression2)));
            } else if (kLabel.label().equals("'_==K_") && kList.size() == 2) {
                Expr expression1 = ((Z3Term) kList.get(0).accept(this)).expression();
                Expr expression2 = ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkEq(expression1, expression2));
            } else if (kLabel.label().equals("'_=/=K_") && kList.size() == 2) {
                Expr expression1 = ((Z3Term) kList.get(0).accept(this)).expression();
                Expr expression2 = ((Z3Term) kList.get(1).accept(this)).expression();
                return new Z3Term(context.MkNot(context.MkEq(expression1, expression2)));
                
            }else if (kLabel.label().equals("'[E]K_._") && kList.size() == 2) {
                Expr expression1 = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                Expr expression2 = (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                Expr[] newExpr = new Expr[1];
                newExpr[0] = expression1;
                return new Z3Term(context.MkExists(newExpr, expression2, 1, null, null, null, null));

            }else if (kLabel.label().equals("'[A]K_._") && kList.size() == 2) {
                Expr expression1 = (ArithExpr) ((Z3Term) kList.get(0).accept(this)).expression();
                Expr expression2 = (BoolExpr) ((Z3Term) kList.get(1).accept(this)).expression();
                Expr[] newExpr = new Expr[1];
                newExpr[0] = expression1;
                return new Z3Term(context.MkForall(newExpr, expression2, 1, null, null, null, null));
            }
            
            else {
                throw new UnsupportedOperationException("cannot translate term to Z3 format " + kItem);
            }
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException(e.getMessage(), e.getCause());
        } catch (Z3Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public ASTNode transform(Variable variable) {
        return KILtoZ3.valueOf(variable, context);
    }

    public static Z3Term valueOf(Variable variable, Context context) {
        try {
            if (variable.sort().equals(BoolToken.SORT_NAME)) {
                //if (boundVariables.contains(variable)) {}
                return new Z3Term(context.MkBoolConst(variable.name()));
            } else /*if (variable.sort().equals(IntToken.SORT_NAME))*/ {
                return new Z3Term(context.MkIntConst(variable.name()));
            } /*else {
                throw new UnsupportedOperationException();
            }*/
        } catch (Z3Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e.getCause());
        }
    }

}
