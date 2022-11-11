package com.oracle.truffle.espresso.substitutions;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_org_objectteams_Team {

    @Substitution(hasReceiver = true, methodName = "_OT$callReplace")
    abstract static class CallReplace extends SubstitutionNode {

        static DirectCallNode createCallNode(EspressoContext context) {
            DirectCallNode directCallNode = DirectCallNode.create(
                    context.getMeta().org_objectteams_Team_callReplace
                            .getCallTargetNoSubstitution());
            directCallNode.forceInlining();
            return directCallNode;
        }

        abstract @JavaType(Object.class) StaticObject execute(
                @JavaType(internalName = "Lorg/objectteams/Team;") StaticObject self,
                @JavaType(internalName = "Lorg/objectteams/IBoundBase2;") StaticObject baze,
                @JavaType(internalName = "[Lorg/objectteams/ITeam;") StaticObject teams,
                int idx,
                @JavaType(int[].class) StaticObject callinIds,
                int boundMethodId,
                @JavaType(Object[].class) StaticObject args);

        @Specialization
        @JavaType(Object.class)
        StaticObject doForward(
                @JavaType(internalName = "Lorg/objectteams/Team;") StaticObject self,
                @JavaType(internalName = "Lorg/objectteams/IBoundBase2;") StaticObject baze,
                @JavaType(internalName = "[Lorg/objectteams/ITeam;") StaticObject teams,
                int idx,
                @JavaType(int[].class) StaticObject callinIds,
                int boundMethodId,
                @JavaType(Object[].class) StaticObject args,
                @Bind("getContext()") EspressoContext context,
                @Cached("createCallNode(context)") DirectCallNode original) {
            return (StaticObject) original.call(self);
        }
    }
}
