package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.MultiBinding;

@ImportStatic({Utils.class})
@NodeInfo(shortName = "INVOKEALLBINDINGS")
public abstract class InvokeReplaceCallin extends EspressoNode {

    final ObjectKlass teamKlass;
    final Method baseMethod;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final MultiBinding[] bindings;

    InvokeReplaceCallin(ObjectKlass teamKlass, Method baseMethod, MultiBinding[] bindings) {
        this.teamKlass = teamKlass;
        this.baseMethod = baseMethod;
        this.bindings = bindings;
    }

    public abstract Object execute(Object[] args, int index, StaticObject teams);

    @Specialization
    Object doCallReplace(Object[] args, int offset, StaticObject  teams,
                         @Bind("getBoundBase(args)") StaticObject boundBase,
                         @Bind("getCallinIds(args)") StaticObject callinIds,
                         @Bind("getBoundMethodId(args)") int boundMethodId,
                         @Bind("getTeam(teams, getLanguage(), offset)") StaticObject team,
                         @Bind("getOriginalArgs(args)") StaticObject originalArgs,
                         @Bind("getCallinId(callinIds, getLanguage(), offset)") Integer callinId,
                         @Bind("getBindingForId(bindings, callinId)") MultiBinding binding,
                         @Cached("createLifting(teamKlass, binding)") InvokeVirtual.WithoutNullCheck lift) {

        // TODO Lars: Lift and RoleCalls own AST nodes?
        StaticObject roleObject = (StaticObject) lift.execute(new Object[]{team, boundBase});
        /*
        DirectCallNode lift = DirectCallNode.create(liftMethod.getCallTarget());
        StaticObject roleObject = (StaticObject) lift.call(team, boundBase);
         */

        // TODO Lars: DirectCallNode vs InvokeVirtual
        Method roleMethod = Utils.lookupRoleMethod(teamKlass, (ObjectKlass) roleObject.getKlass(), binding);

        DirectCallNode roleNode = DirectCallNode.create(roleMethod.getCallTarget());

        StaticObject obj = originalArgs.get(getLanguage(), 0);
        Float farg = getMeta().unboxFloat(obj);
        Object result = roleNode.call(roleObject, boundBase, teams, offset, callinIds, boundMethodId, originalArgs, farg);
        return result;
    }

    static int getCallinId(StaticObject callinIds, EspressoLanguage language, int index) {
        return callinIds.<int[]>unwrap(language)[index];
    }

    static InvokeVirtual.WithoutNullCheck createLifting(ObjectKlass teamKlass, MultiBinding binding) {
        Method liftMethod = Utils.lookupLiftMethod(teamKlass, binding.getRoleClassNameIndex());
        return InvokeVirtualNodeGen.WithoutNullCheckNodeGen.create(liftMethod);
    }

    static StaticObject getBoundBase(Object[] args) {
        return (StaticObject) args[1];
    }

    static StaticObject getCallinIds(Object[] args) {
        return (StaticObject) args[4];
    }

    static StaticObject getOriginalArgs(Object[] args) {
        return (StaticObject) args[6];
    }

    static int getBoundMethodId(Object[] args) {
        return ((Integer) args[5]).intValue();
    }

    static StaticObject getTeam(StaticObject teams, EspressoLanguage language, int index) {
        return teams.get(language, index);
    }

}
