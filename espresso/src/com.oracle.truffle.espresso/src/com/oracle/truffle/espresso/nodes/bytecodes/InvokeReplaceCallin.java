package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.MultiBinding;

@ImportStatic({Utils.class, CallinBindingsAttribute.class})
@NodeInfo(shortName = "INVOKEALLBINDINGS")
public abstract class InvokeReplaceCallin extends InvokeCallinNode {

    final Assumption hasPrecedence = Truffle.getRuntime().createAssumption();

    final ObjectKlass teamKlass;
    final Method baseMethod;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final MultiBinding[] bindings;

    final int length;

    InvokeReplaceCallin(ObjectKlass teamKlass, Method baseMethod, MultiBinding[] bindings) {
        this.teamKlass = teamKlass;
        this.baseMethod = baseMethod;
        this.bindings = bindings;
        int count = 0;
        for (MultiBinding mb : bindings) {
            if (mb.getCallinModifier() == CallinBindingsAttribute.KIND_REPLACE) {
                count++;
            }
        }
        this.length = count;
        if (count <= 1) {
            this.hasPrecedence.invalidate();
        }
    }

    public abstract Object execute(Object[] args, StaticObject teams);

    @Specialization(guards = "length > 0")
    Object doCallReplace(Object[] args, StaticObject  teams,
                         @Bind("getIndex(args)") int index,
                         @Bind("getBoundBase(args)") StaticObject boundBase,
                         @Bind("getCallinIds(args)") StaticObject callinIds,
                         @Bind("getBoundMethodId(args)") int boundMethodId,
                         @Bind("getTeam(teams, getLanguage(), index)") StaticObject team,
                         @Bind("getOriginalArgs(args)") StaticObject originalArgs,
                         @Bind("getCallinId(callinIds, getLanguage(), index)") Integer callinId,
                         @Bind("getBindingForId(bindings, callinId, KIND_REPLACE)") MultiBinding binding,
                         @Cached("createLifting(teamKlass, binding)") InvokeVirtual.WithoutNullCheck lift) {

        // TODO Lars: Lift and RoleCalls own AST nodes?
        Object[] liftArgs = new Object[]{team, boundBase};
        StaticObject roleObject = (StaticObject) lift.execute(liftArgs);
        /*
        DirectCallNode lift = DirectCallNode.create(liftMethod.getCallTarget());
        StaticObject roleObject = (StaticObject) lift.call(team, boundBase);
         */

        // TODO Lars: DirectCallNode vs InvokeVirtual
        Method roleMethod = Utils.lookupRoleMethod(teamKlass, (ObjectKlass) roleObject.getKlass(), binding);

        DirectCallNode roleNode = DirectCallNode.create(roleMethod.getCallTarget());

        StaticObject obj = originalArgs.get(getLanguage(), 0);
        // TODO Lars: Create general packing / unpacking
        Float farg = getMeta().unboxFloat(obj);
        Object result = roleNode.call(roleObject, boundBase, teams, index + 1, callinIds, boundMethodId, originalArgs, farg);
        return result;
    }

    @Specialization(guards = "length == 0", replaces = "doCallReplace")
    Object doCallOrig(Object[] args, StaticObject  teams,
                      @Bind("getIndex(args)") int index,
                      //@Bind("getTeams(args)") StaticObject teams,
                      @Cached("createOrig(baseMethod.getDeclaringKlass())") InvokeVirtual.WithoutNullCheck orig) {
        // TODO Lars: this needs yet to be tested
        Object[] newArgs = new Object[] {args[0], args[5], args[6]};
        return orig.execute(newArgs);
    }

}
