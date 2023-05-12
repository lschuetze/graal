package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

@NodeInfo(shortName = "INVOKEALLBINDINGS")
@ImportStatic(Utils.class)
public abstract class InvokeCallAllBindings extends InvokeCallinNode {

    final Method baseMethod;

    InvokeCallAllBindings(Method baseMethod) {
        this.baseMethod = baseMethod;
    }

    public abstract Object execute(Object[] args);

    @Specialization(guards = { "teams != null", "teams.length(getLanguage()) > index" })
    Object doCallAllBindings(Object[] args,
                             @Bind("getTeams(args)") StaticObject teams,
                             @Bind("getIndex(args)") int index,
                             @Cached("getTeamKlass(teams, getLanguage(), index)") ObjectKlass teamKlass,
                             @Cached("createAllKlassBindings(teamKlass, baseMethod)") InvokeAllKlassBindings invoke) {
        // TODO Lars: if that is null it means we have a static callin
        // nullCheck(base);
        Object result = invoke.execute(args, teams);
        return result;
    }

    @Specialization(replaces = "doCallAllBindings", guards = "teams == null")
    Object doCallOrig(Object[] args,
                      @Bind("getTeams(args)") StaticObject teams,
                      @Cached("createOrig(baseMethod.getDeclaringKlass())") InvokeVirtual.WithoutNullCheck orig) {
        // TODO Lars: this needs yet to be tested
        Object[] newArgs = new Object[] {args[0], args[5], args[6]};
        return orig.execute(newArgs);
    }

    static InvokeAllKlassBindings createAllKlassBindings(ObjectKlass teamKlass, Method baseMethod) {
        return InvokeAllKlassBindingsNodeGen.create(teamKlass, baseMethod, getBindings(baseMethod, teamKlass), false);
    }
}
