package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public class CallinBindingsAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.CallinBindings;

    static final short COVARIANT_BASE_RETURN = 8;
    static final short BASE_SUPER_CALL = 16;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final MultiBinding[] callinBindings;

    /** Represents all base method bindings of one callin binding. */
    public static class MultiBinding {
        private final int roleClassNameIndex;
        private final int callinLabelIndex;
        private final int baseClassNameIndex;
        private final int callinModifierIndex;
        private final boolean isHandleCovariantReturn;
        private final boolean requireBaseSuperCall;

        @CompilerDirectives.CompilationFinal(dimensions = 1) //
        private final BindingInfo[] baseMethods;

        public MultiBinding(int roleClassNameIndex, int callinLabelIndex, int baseClassNameIndex,
                            int callinModifierIndex, int flags, BindingInfo[] baseMethods) {
            this.roleClassNameIndex = roleClassNameIndex;
            this.callinLabelIndex = callinLabelIndex;
            this.baseClassNameIndex = baseClassNameIndex;
            //TODO Lars: Check if we need to map the callin Modifier to values later
            //BEFORE = 1, REPLACE = 2, AFTER = 3
            this.callinModifierIndex = callinModifierIndex;
            this.isHandleCovariantReturn = (flags & COVARIANT_BASE_RETURN) != 0;
            this.requireBaseSuperCall = (flags & BASE_SUPER_CALL) != 0;
            this.baseMethods = baseMethods;
        }

        // Holds all meta-data that a callin must know about its base methods
        public static class BindingInfo {
            private final int baseMethodNameIndex;
            private final int baseMethodSignatureIndex;
            private final int declaringBaseClassIndex;
            private final int callinId;
            private final int baseFlags;

            public BindingInfo(int baseMethodNameIndex, int baseMethodSignatureIndex,
                               int declaringBaseClassIndex, int callinId, int baseFlags) {
                this.baseMethodNameIndex = baseMethodNameIndex;
                this.baseMethodSignatureIndex = baseMethodSignatureIndex;
                this.declaringBaseClassIndex = declaringBaseClassIndex;
                this.callinId = callinId;
                this.baseFlags = baseFlags;
            }
        }
    }

    public CallinBindingsAttribute(Symbol<Name> name, MultiBinding[] callinBindings) {
        super(name, null);
        this.callinBindings = callinBindings;
    }
}
