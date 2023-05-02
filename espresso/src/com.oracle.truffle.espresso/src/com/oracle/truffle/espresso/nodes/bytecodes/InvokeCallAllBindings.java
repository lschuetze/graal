package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

@NodeInfo(shortName = "INVOKEALLBINDINGS")
public abstract class InvokeCallAllBindings extends EspressoNode {

    final Method baseMethod;

    InvokeCallAllBindings(Method baseMethod) {
        this.baseMethod = baseMethod;
    }

    public abstract Object execute(Object[] args);

    @Specialization
    Object doCallAllBindings(Object[] args,
                             @Bind("getTeams(args)") StaticObject teams,
                             @Bind("getIndex(args)") int index,
                             @Cached("getTeamKlass(teams, getLanguage(), index)") ObjectKlass teamKlass,
                             @Cached("createAllKlassBindings(teamKlass, baseMethod)") InvokeAllKlassBindings invoke) {
        // TODO Lars: if that is null it means we have a static callin
        // nullCheck(base);
/*
        if (teams == null) {
            // TODO Lars: If teams == null -> callOrig
        } else {
            // TODO Lars: If teams != null -> callCallBindings
*/
        return invoke.execute(args, teams, index);
    }

    static int getIndex(Object[] args) {
        Integer startingIndex = (Integer) args[3];
        return startingIndex.intValue();
    }

    static StaticObject getTeams(Object[] args) {
        return (StaticObject) args[2];
    }

    static ObjectKlass getTeamKlass(StaticObject teams, EspressoLanguage lang, int index) {
        StaticObject team = teams.get(lang, index);
        ObjectKlass teamKlass = (ObjectKlass) team.getKlass();
        return teamKlass;
    }

    static InvokeAllKlassBindings createAllKlassBindings(ObjectKlass teamKlass, Method baseMethod) {
        return InvokeAllKlassBindingsNodeGen.create(teamKlass, baseMethod);
    }
}
