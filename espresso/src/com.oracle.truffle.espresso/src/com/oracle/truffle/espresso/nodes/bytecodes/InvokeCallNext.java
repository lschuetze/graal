package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

@NodeInfo(shortName = "INVOKECALLNEXT")
@ImportStatic(Utils.class)
public abstract class InvokeCallNext extends InvokeCallinNode {

    final Method baseMethod;

    public InvokeCallNext(Method baseMethod) {
        this.baseMethod = baseMethod;
    }

    public abstract Object execute(Object[] args);

    @Specialization(guards = { "teams.length(getLanguage()) > index" })
    Object doCallAllBindings(Object[] args,
                             @Bind("getTeams(args)") StaticObject teams,
                             @Bind("getIndex(args)") int index,
                             @Cached("getTeamKlass(teams, getLanguage(), index)") ObjectKlass teamKlass,
                             @Bind("getBindings(baseMethod, teamKlass)") CallinBindingsAttribute.MultiBinding[] bindings,
                             @Cached("createAllKlassBindings(teamKlass, baseMethod, bindings)") InvokeAllKlassBindings invoke) {
        // TODO Lars: if that is null it means we have a static callin
        // nullCheck(base);
        Object result = invoke.execute(args, teams);
        return result;
    }

    @Specialization(replaces = "doCallAllBindings",  guards = "teams.length(getLanguage()) <= index")
    Object doCallOrig(Object[] args,
                      @Bind("getTeams(args)") StaticObject teams,
                      @Bind("getIndex(args)") int index,
                      @Cached("createOrig(baseMethod.getDeclaringKlass())") InvokeVirtual.WithoutNullCheck orig) {
        // TODO Lars: this needs yet to be tested
        Object[] newArgs = new Object[] {args[0], args[5], args[6]};
        return orig.execute(newArgs);
    }

    static InvokeAllKlassBindings createAllKlassBindings(ObjectKlass teamKlass, Method baseMethod, CallinBindingsAttribute.MultiBinding[] bindings) {
        return InvokeAllKlassBindingsNodeGen.create(teamKlass, baseMethod, bindings, true);
    }
}